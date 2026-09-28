package com.mdframe.forge.plugin.generator.service.lowcode;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishDTO;
import com.mdframe.forge.plugin.generator.enums.LowcodePublishTaskStatus;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.LowcodePublishTaskMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 低代码发布可靠任务的追加、租约认领、不可变命令校验和状态迁移。 */
@Service
@RequiredArgsConstructor
public class LowcodePublishTaskService {

    static final int COMMAND_PROTOCOL_VERSION = 1;
    static final String STAGE_POST_SYNC = "POST_SYNC";
    static final String STAGE_DDL_PENDING = "DDL_PENDING";
    static final String STAGE_CONFIG_PENDING = "CONFIG_PENDING";
    static final String OPERATION_ONLINE_PUBLISH = "ONLINE_PUBLISH";
    private static final String OPERATION_PUBLISH = "PUBLISH";
    private static final String OPERATION_ROLLBACK = "ROLLBACK";

    private final LowcodePublishTaskMapper taskMapper;
    private final AiCrudConfigVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    @Value("${forge.lowcode.publish-task.max-retry-count:8}")
    private int maxRetryCount = 8;

    @Value("${forge.lowcode.publish-task.lock-timeout-seconds:300}")
    private long lockTimeoutSeconds = 300;

    @Value("${forge.lowcode.publish-task.retry-base-seconds:5}")
    private long retryBaseSeconds = 5;

    /** 必须与配置/版本写入处于同一事务，任务落库失败会回滚发布主事务。 */
    @Transactional(rollbackFor = Exception.class)
    public AiLowcodePublishTask appendPostSync(AiCrudConfig config,
                                               LowcodePublishDTO dto,
                                               AiCrudConfigVersion version,
                                               String operationType,
                                               boolean syncMenu,
                                               Long menuParentId) {
        validateAppendIdentity(config, version, operationType);
        Long operatorId = requirePositive(config.getPublishBy(), "低代码发布任务缺少可信操作者");
        String normalizedOperation = operationType.toUpperCase(Locale.ROOT);
        String requestId = requestId(config, version, normalizedOperation);
        LowcodePublishPostCommand command = new LowcodePublishPostCommand(
                COMMAND_PROTOCOL_VERSION,
                config.getTenantId(),
                config.getId(),
                config.getConfigKey(),
                version.getId(),
                version.getVersionNo(),
                normalizedOperation,
                syncMenu,
                menuParentId,
                dto == null ? null : dto.getBusinessSuiteCode(),
                dto == null ? null : dto.getBusinessObjectCode(),
                dto == null ? null : dto.getBusinessObjectName(),
                operatorId);
        String payload = writeCommand(command);
        String commandDigest = sha256(payload);

        AiLowcodePublishTask duplicate = TenantContextHolder.executeIgnore(
                () -> taskMapper.selectByRequestId(config.getTenantId(), requestId));
        if (duplicate != null) {
            return assertSameTask(duplicate, version, commandDigest);
        }

        LocalDateTime now = LocalDateTime.now();
        AiLowcodePublishTask task = new AiLowcodePublishTask();
        task.setId(IdWorker.getId());
        task.setTenantId(config.getTenantId());
        task.setRequestId(requestId);
        task.setOperationType(normalizedOperation);
        task.setConfigId(config.getId());
        task.setConfigKey(config.getConfigKey());
        task.setVersionId(version.getId());
        task.setVersionNo(version.getVersionNo());
        task.setSchemaHash(schemaHash(config));
        task.setRuntimeDatasourceId(config.getRuntimeDatasourceId());
        task.setRuntimeDatasourceCode(StringUtils.trimToNull(config.getRuntimeDatasourceCode()));
        task.setRuntimeTableName(StringUtils.trimToNull(config.getRuntimeTableName()));
        task.setOperatorId(operatorId);
        task.setCommandPayload(payload);
        task.setCommandDigest(commandDigest);
        task.setCurrentStage(STAGE_POST_SYNC);
        task.setTaskStatus(LowcodePublishTaskStatus.PENDING.getCode());
        task.setRetryCount(0);
        task.setCreateBy(operatorId);
        task.setUpdateBy(operatorId);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        try {
            int inserted = TenantContextHolder.executeIgnore(() -> taskMapper.insert(task));
            if (inserted != 1) {
                throw new BusinessException("低代码发布可靠任务持久化失败");
            }
            return task;
        } catch (DuplicateKeyException duplicateKey) {
            AiLowcodePublishTask existing = TenantContextHolder.executeIgnore(
                    () -> taskMapper.selectByRequestId(config.getTenantId(), requestId));
            if (existing != null) {
                return assertSameTask(existing, version, commandDigest);
            }
            throw duplicateKey;
        }
    }

    /** 在任何在线 DDL 之前提交恢复任务，并为首次调用者持有初始租约。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiLowcodePublishTask stageOnlinePublish(LowcodeOnlinePublishPlan plan, String owner) {
        validateOnlinePlan(plan, owner);
        String planPayload = writeJson(plan, "在线发布计划序列化失败");
        String requestDigest = sha256(planPayload);
        String requestId = "lowcode-online:" + requestDigest;
        AiLowcodePublishTask existing = TenantContextHolder.executeIgnore(
                () -> taskMapper.selectByRequestId(plan.tenantId(), requestId));
        if (existing != null) {
            return assertSameOnlineTask(existing, plan, requestDigest);
        }

        Integer maxVersionNo = TenantContextHolder.executeIgnore(
                () -> versionMapper.selectMaxVersionNo(plan.tenantId(), plan.configId()));
        int latestVersionNo = Math.max(maxVersionNo == null ? 0 : maxVersionNo,
                plan.expectedPublishedVersion());
        Integer versionNo = latestVersionNo + 1;
        Long versionId = IdWorker.getId();
        LowcodeOnlinePublishCommand command = new LowcodeOnlinePublishCommand(
                COMMAND_PROTOCOL_VERSION,
                plan.tenantId(),
                plan.configId(),
                plan.configKey(),
                plan.expectedDraftVersion(),
                plan.expectedPublishedVersion(),
                versionId,
                versionNo,
                plan.operatorId(),
                requestDigest,
                plan.configSnapshot(),
                plan.syncMenu(),
                plan.requestedMenuParentId(),
                plan.businessSuiteCode(),
                plan.businessObjectCode(),
                plan.businessObjectName(),
                plan.remark());
        String payload = writeJson(command, "在线发布命令序列化失败");
        LocalDateTime now = LocalDateTime.now();
        AiLowcodePublishTask task = new AiLowcodePublishTask();
        task.setId(IdWorker.getId());
        task.setTenantId(plan.tenantId());
        task.setRequestId(requestId);
        task.setOperationType(OPERATION_ONLINE_PUBLISH);
        task.setConfigId(plan.configId());
        task.setConfigKey(plan.configKey());
        task.setVersionId(versionId);
        task.setVersionNo(versionNo);
        task.setSchemaHash(schemaHash(plan.configSnapshot()));
        task.setRuntimeDatasourceId(plan.configSnapshot().getRuntimeDatasourceId());
        task.setRuntimeDatasourceCode(StringUtils.trimToNull(
                plan.configSnapshot().getRuntimeDatasourceCode()));
        task.setRuntimeTableName(StringUtils.trimToNull(
                plan.configSnapshot().getRuntimeTableName()));
        task.setOperatorId(plan.operatorId());
        task.setCommandPayload(payload);
        task.setCommandDigest(sha256(payload));
        task.setCurrentStage(STAGE_DDL_PENDING);
        task.setTaskStatus(LowcodePublishTaskStatus.PROCESSING.getCode());
        task.setRetryCount(1);
        task.setLockOwner(owner);
        task.setLockTime(now);
        task.setCreateBy(plan.operatorId());
        task.setUpdateBy(plan.operatorId());
        task.setCreateTime(now);
        task.setUpdateTime(now);
        try {
            int inserted = TenantContextHolder.executeIgnore(() -> taskMapper.insert(task));
            if (inserted != 1) {
                throw new BusinessException("在线发布任务持久化失败");
            }
            return task;
        } catch (DuplicateKeyException duplicateKey) {
            AiLowcodePublishTask duplicate = TenantContextHolder.executeIgnore(
                    () -> taskMapper.selectByRequestId(plan.tenantId(), requestId));
            if (duplicate != null) {
                return assertSameOnlineTask(duplicate, plan, requestDigest);
            }
            throw duplicateKey;
        }
    }

    public List<AiLowcodePublishTask> findCandidates(LocalDateTime now, int batchSize) {
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        return TenantContextHolder.executeIgnore(() -> taskMapper.selectCandidates(
                scanTime, staleBefore(scanTime), safeMaxRetryCount(), safeBatchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiLowcodePublishTask claim(AiLowcodePublishTask candidate, String owner, LocalDateTime now) {
        if (candidate == null || candidate.getTenantId() == null || candidate.getId() == null
                || StringUtils.isBlank(owner)) {
            return null;
        }
        LocalDateTime claimTime = now == null ? LocalDateTime.now() : now;
        if (LowcodePublishTaskStatus.PROCESSING.matches(candidate.getTaskStatus())
                && candidate.getRetryCount() != null
                && candidate.getRetryCount() >= safeMaxRetryCount()) {
            TenantContextHolder.executeIgnore(() -> taskMapper.expireExhaustedLease(
                    candidate.getTenantId(), candidate.getId(), claimTime,
                    staleBefore(claimTime), safeMaxRetryCount()));
            return null;
        }
        int claimed = TenantContextHolder.executeIgnore(() -> taskMapper.claim(
                candidate.getTenantId(), candidate.getId(), owner, claimTime,
                staleBefore(claimTime), safeMaxRetryCount()));
        if (claimed != 1) {
            return null;
        }
        return TenantContextHolder.executeIgnore(
                () -> taskMapper.selectByTaskId(candidate.getTenantId(), candidate.getId()));
    }

    public LowcodePublishPostCommand restore(AiLowcodePublishTask task) {
        validateClaim(task);
        return restorePostCommand(task);
    }

    /** DEAD 重新入队前验证阶段、摘要和不可变命令身份，不执行任何副作用。 */
    public void validateReplayable(AiLowcodePublishTask task) {
        validateTaskIdentity(task);
        if (isOnlinePublishTask(task)) {
            if (!STAGE_DDL_PENDING.equals(task.getCurrentStage())
                    && !STAGE_CONFIG_PENDING.equals(task.getCurrentStage())
                    && !STAGE_POST_SYNC.equals(task.getCurrentStage())) {
                throw new BusinessException("在线发布死信阶段不支持人工重放");
            }
            restoreOnlinePublish(task);
            return;
        }
        restorePostCommand(task);
    }

    private LowcodePublishPostCommand restorePostCommand(AiLowcodePublishTask task) {
        validateTaskIdentity(task);
        if ((!OPERATION_PUBLISH.equals(task.getOperationType())
                && !OPERATION_ROLLBACK.equals(task.getOperationType()))
                || !STAGE_POST_SYNC.equals(task.getCurrentStage())
                || StringUtils.isAnyBlank(task.getCommandPayload(), task.getCommandDigest())) {
            throw new BusinessException("低代码发布任务阶段或命令快照无效");
        }
        if (!digestEquals(task.getCommandDigest(), sha256(task.getCommandPayload()))) {
            throw new BusinessException("低代码发布任务命令摘要校验失败");
        }
        LowcodePublishPostCommand command;
        try {
            command = objectMapper.readValue(task.getCommandPayload(), LowcodePublishPostCommand.class);
        } catch (Exception invalidJson) {
            throw new BusinessException("低代码发布任务命令格式无效");
        }
        if (command.protocolVersion() == null || command.protocolVersion() != COMMAND_PROTOCOL_VERSION
                || !Objects.equals(task.getTenantId(), command.tenantId())
                || !Objects.equals(task.getConfigId(), command.configId())
                || !Objects.equals(task.getConfigKey(), command.configKey())
                || !Objects.equals(task.getVersionId(), command.versionId())
                || !Objects.equals(task.getVersionNo(), command.versionNo())
                || !Objects.equals(task.getOperationType(), command.operationType())
                || !Objects.equals(task.getOperatorId(), command.operatorId())) {
            throw new BusinessException("低代码发布任务命令身份校验失败");
        }
        return command;
    }

    public LowcodeOnlinePublishCommand restoreOnlinePublish(AiLowcodePublishTask task) {
        validateTaskIdentity(task);
        if (!OPERATION_ONLINE_PUBLISH.equals(task.getOperationType())
                || StringUtils.isAnyBlank(task.getCommandPayload(), task.getCommandDigest())) {
            throw new BusinessException("在线发布任务类型或命令快照无效");
        }
        if (!digestEquals(task.getCommandDigest(), sha256(task.getCommandPayload()))) {
            throw new BusinessException("在线发布任务命令摘要校验失败");
        }
        LowcodeOnlinePublishCommand command;
        try {
            command = objectMapper.readValue(
                    task.getCommandPayload(), LowcodeOnlinePublishCommand.class);
        } catch (Exception invalidJson) {
            throw new BusinessException("在线发布任务命令格式无效");
        }
        if (command.protocolVersion() == null || command.protocolVersion() != COMMAND_PROTOCOL_VERSION
                || !Objects.equals(task.getTenantId(), command.tenantId())
                || !Objects.equals(task.getConfigId(), command.configId())
                || !Objects.equals(task.getConfigKey(), command.configKey())
                || !Objects.equals(task.getVersionId(), command.versionId())
                || !Objects.equals(task.getVersionNo(), command.versionNo())
                || !Objects.equals(task.getOperatorId(), command.operatorId())
                || !Objects.equals(task.getRequestId(), "lowcode-online:" + command.requestDigest())
                || command.expectedDraftVersion() == null || command.expectedDraftVersion() < 0
                || command.expectedPublishedVersion() == null || command.expectedPublishedVersion() < 0
                || command.versionNo() == null || command.versionNo() <= command.expectedPublishedVersion()
                || command.configSnapshot() == null
                || StringUtils.isAnyBlank(command.configSnapshot().getModelSchema(),
                        command.configSnapshot().getPageSchema())
                || !digestEquals(task.getSchemaHash(), schemaHash(command.configSnapshot()))
                || !Objects.equals(task.getRuntimeDatasourceId(),
                        command.configSnapshot().getRuntimeDatasourceId())
                || !Objects.equals(StringUtils.trimToNull(task.getRuntimeDatasourceCode()),
                        StringUtils.trimToNull(command.configSnapshot().getRuntimeDatasourceCode()))
                || !Objects.equals(StringUtils.trimToNull(task.getRuntimeTableName()),
                        StringUtils.trimToNull(command.configSnapshot().getRuntimeTableName()))) {
            throw new BusinessException("在线发布任务命令身份校验失败");
        }
        return command;
    }

    public boolean isOnlinePublishTask(AiLowcodePublishTask task) {
        return task != null && OPERATION_ONLINE_PUBLISH.equals(task.getOperationType());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void advanceStage(AiLowcodePublishTask task, String expectedStage, String nextStage) {
        validateClaim(task);
        int updated = TenantContextHolder.executeIgnore(() -> taskMapper.advanceStage(
                task, expectedStage, nextStage, LocalDateTime.now()));
        requireSingleUpdate(updated, "在线发布任务阶段已被其他节点更新");
        task.setCurrentStage(nextStage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void releaseStage(AiLowcodePublishTask task, String expectedStage, String nextStage) {
        validateClaim(task);
        int updated = TenantContextHolder.executeIgnore(() -> taskMapper.releaseStage(
                task, expectedStage, nextStage, LocalDateTime.now()));
        requireSingleUpdate(updated, "在线发布任务释放阶段已被其他节点更新");
        task.setCurrentStage(nextStage);
        task.setTaskStatus(LowcodePublishTaskStatus.PENDING.getCode());
        task.setRetryCount(0);
        task.setLockOwner(null);
        task.setLockTime(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markCompleted(AiLowcodePublishTask task, boolean superseded) {
        validateClaim(task);
        String status = superseded
                ? LowcodePublishTaskStatus.SUPERSEDED.getCode()
                : LowcodePublishTaskStatus.COMPLETED.getCode();
        int updated = TenantContextHolder.executeIgnore(
                () -> taskMapper.markTerminal(task, status, LocalDateTime.now()));
        requireSingleUpdate(updated, "低代码发布任务完成状态已被其他节点更新");
        task.setTaskStatus(status);
        task.setLockOwner(null);
        task.setLockTime(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(AiLowcodePublishTask task, Throwable failure) {
        validateClaim(task);
        int attempts = task.getRetryCount() == null ? 0 : task.getRetryCount();
        boolean exhausted = attempts >= safeMaxRetryCount();
        String status = exhausted
                ? LowcodePublishTaskStatus.DEAD.getCode()
                : LowcodePublishTaskStatus.RETRY.getCode();
        LocalDateTime nextRetryTime = exhausted ? null : LocalDateTime.now().plus(backoff(attempts));
        int updated = TenantContextHolder.executeIgnore(() -> taskMapper.markFailed(
                task, status, nextRetryTime, safeFailureType(failure), LocalDateTime.now()));
        requireSingleUpdate(updated, "低代码发布任务失败状态已被其他节点更新");
        task.setTaskStatus(status);
        task.setNextRetryTime(nextRetryTime);
        task.setLockOwner(null);
        task.setLockTime(null);
    }

    private void validateAppendIdentity(AiCrudConfig config,
                                        AiCrudConfigVersion version,
                                        String operationType) {
        if (config == null || version == null
                || config.getTenantId() == null || config.getTenantId() <= 0
                || config.getId() == null || StringUtils.isBlank(config.getConfigKey())
                || version.getId() == null || version.getVersionNo() == null
                || !Objects.equals(config.getTenantId(), version.getTenantId())
                || !Objects.equals(config.getId(), version.getConfigId())
                || !Objects.equals(config.getConfigKey(), version.getConfigKey())) {
            throw new BusinessException("低代码发布任务身份不完整");
        }
        String operation = StringUtils.trimToEmpty(operationType).toUpperCase(Locale.ROOT);
        if (!OPERATION_PUBLISH.equals(operation) && !OPERATION_ROLLBACK.equals(operation)) {
            throw new BusinessException("低代码发布任务类型不支持");
        }
    }

    private AiLowcodePublishTask assertSameTask(AiLowcodePublishTask existing,
                                                AiCrudConfigVersion version,
                                                String commandDigest) {
        if (!Objects.equals(existing.getVersionId(), version.getId())
                || !Objects.equals(existing.getVersionNo(), version.getVersionNo())
                || !digestEquals(existing.getCommandDigest(), commandDigest)) {
            throw new BusinessException("低代码发布请求ID与命令摘要冲突");
        }
        return existing;
    }

    private AiLowcodePublishTask assertSameOnlineTask(AiLowcodePublishTask existing,
                                                      LowcodeOnlinePublishPlan plan,
                                                      String requestDigest) {
        if (!OPERATION_ONLINE_PUBLISH.equals(existing.getOperationType())
                || !Objects.equals(existing.getTenantId(), plan.tenantId())
                || !Objects.equals(existing.getConfigId(), plan.configId())
                || !Objects.equals(existing.getConfigKey(), plan.configKey())
                || !Objects.equals(existing.getOperatorId(), plan.operatorId())
                || !Objects.equals(existing.getRequestId(), "lowcode-online:" + requestDigest)) {
            throw new BusinessException("在线发布请求ID与任务身份冲突");
        }
        return existing;
    }

    private void validateOnlinePlan(LowcodeOnlinePublishPlan plan, String owner) {
        if (plan == null || plan.protocolVersion() == null
                || plan.protocolVersion() != COMMAND_PROTOCOL_VERSION
                || plan.tenantId() == null || plan.tenantId() <= 0
                || plan.configId() == null || StringUtils.isBlank(plan.configKey())
                || plan.expectedDraftVersion() == null || plan.expectedDraftVersion() < 0
                || plan.expectedPublishedVersion() == null || plan.expectedPublishedVersion() < 0
                || plan.operatorId() == null || plan.operatorId() <= 0
                || plan.configSnapshot() == null
                || StringUtils.isAnyBlank(plan.configSnapshot().getModelSchema(),
                        plan.configSnapshot().getPageSchema())
                || StringUtils.isBlank(owner)) {
            throw new BusinessException("在线发布计划身份不完整");
        }
    }

    private void validateClaim(AiLowcodePublishTask task) {
        if (task == null || task.getTenantId() == null || task.getId() == null
                || StringUtils.isBlank(task.getLockOwner())
                || !LowcodePublishTaskStatus.PROCESSING.matches(task.getTaskStatus())) {
            throw new BusinessException("低代码发布任务租约身份无效");
        }
    }

    private void validateTaskIdentity(AiLowcodePublishTask task) {
        if (task == null || task.getTenantId() == null || task.getTenantId() <= 0
                || task.getId() == null || StringUtils.isBlank(task.getRequestId())) {
            throw new BusinessException("低代码发布任务身份无效");
        }
    }

    private String writeCommand(LowcodePublishPostCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (Exception serializationFailure) {
            throw new BusinessException("低代码发布任务命令序列化失败");
        }
    }

    private String writeJson(Object value, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception serializationFailure) {
            throw new BusinessException(message);
        }
    }

    private String requestId(AiCrudConfig config,
                             AiCrudConfigVersion version,
                             String operationType) {
        return "lowcode-publish:" + config.getTenantId() + ':' + config.getId()
                + ':' + version.getId() + ':' + operationType;
    }

    private String schemaHash(AiCrudConfig config) {
        return sha256(String.join("\u001f",
                StringUtils.defaultString(config.getModelSchema()),
                StringUtils.defaultString(config.getPageSchema()),
                StringUtils.defaultString(config.getRuntimeDatasourceCode()),
                StringUtils.defaultString(config.getRuntimeTableName())));
    }

    private String schemaHash(LowcodeOnlinePublishConfigSnapshot snapshot) {
        return sha256(String.join("\u001f",
                StringUtils.defaultString(snapshot.getModelSchema()),
                StringUtils.defaultString(snapshot.getPageSchema()),
                StringUtils.defaultString(snapshot.getRuntimeDatasourceCode()),
                StringUtils.defaultString(snapshot.getRuntimeTableName())));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private boolean digestEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private int safeMaxRetryCount() {
        return Math.max(1, maxRetryCount);
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minusSeconds(Math.max(30, lockTimeoutSeconds));
    }

    private Duration backoff(int attempts) {
        long baseSeconds = Math.max(1, retryBaseSeconds);
        int exponent = Math.max(0, Math.min(attempts - 1, 10));
        try {
            return Duration.ofSeconds(baseSeconds).multipliedBy(1L << exponent);
        } catch (ArithmeticException overflow) {
            return Duration.ofHours(24);
        }
    }

    private String safeFailureType(Throwable failure) {
        String name = failure == null ? null : failure.getClass().getSimpleName();
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(name, "UnknownFailure"), 128);
    }

    private Long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private void requireSingleUpdate(int updated, String message) {
        if (updated != 1) {
            throw new BusinessException(message);
        }
    }
}
