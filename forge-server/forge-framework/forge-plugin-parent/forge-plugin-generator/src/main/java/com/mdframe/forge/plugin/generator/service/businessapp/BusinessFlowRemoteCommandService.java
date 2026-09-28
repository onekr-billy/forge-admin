package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowRemoteCommand;
import com.mdframe.forge.plugin.generator.enums.BusinessFlowRemoteCommandStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowRemoteCommandMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** FlowClient 远程命令的持久化、租约、远端幂等恢复与提交后完成状态机。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessFlowRemoteCommandService {

    private final BusinessFlowRemoteCommandMapper commandMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${forge.business.flow-remote-command.max-retry-count:8}")
    private int maxRetryCount = 8;

    @Value("${forge.business.flow-remote-command.lock-timeout-seconds:120}")
    private long lockTimeoutSeconds = 120;

    @Value("${forge.business.flow-remote-command.retry-base-seconds:5}")
    private long retryBaseSeconds = 5;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiBusinessFlowRemoteCommand prepareStart(BusinessFlowRemoteStartRequest request) {
        validateRequest(request);
        String commandKey = BusinessFlowRemoteStartEnvelope.commandKey(request);
        String requestDigest = BusinessFlowRemoteStartEnvelope.requestDigest(request);
        AiBusinessFlowRemoteCommand existing = selectByCommandKey(request.getTenantId(), commandKey);
        if (existing != null) {
            return assertSameRequest(existing, requestDigest);
        }

        LocalDateTime now = LocalDateTime.now();
        AiBusinessFlowRemoteCommand command = new AiBusinessFlowRemoteCommand();
        command.setId(IdWorker.getId());
        command.setTenantId(request.getTenantId());
        command.setCommandKey(commandKey);
        command.setCommandType(BusinessFlowRemoteStartEnvelope.COMMAND_TYPE);
        command.setRequestDigest(requestDigest);
        command.setRequestPayload(BusinessFlowRemoteStartEnvelope.requestPayload(request));
        command.setObjectCode(request.getObjectCode());
        command.setRecordId(request.getRecordId());
        command.setBusinessKey(request.getBusinessKey());
        command.setFlowBusinessKey(request.getFlowBusinessKey());
        command.setFlowModelKey(request.getFlowModelKey());
        command.setCommandStatus(BusinessFlowRemoteCommandStatus.PENDING.getCode());
        command.setRetryCount(0);
        command.setCreateBy(request.getStarterUserId());
        command.setUpdateBy(request.getStarterUserId());
        command.setCreateTime(now);
        command.setUpdateTime(now);
        try {
            int inserted = TenantContextHolder.executeIgnore(() -> commandMapper.insert(command));
            if (inserted != 1) {
                throw new BusinessException("流程远程启动命令持久化失败");
            }
            return command;
        } catch (DuplicateKeyException duplicateKey) {
            AiBusinessFlowRemoteCommand duplicate = selectByCommandKey(request.getTenantId(), commandKey);
            if (duplicate != null) {
                return assertSameRequest(duplicate, requestDigest);
            }
            throw duplicateKey;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiBusinessFlowRemoteCommand prepareTask(BusinessFlowRemoteTaskRequest request) {
        validateTaskRequest(request);
        String commandKey = BusinessFlowRemoteTaskEnvelope.commandKey(request);
        String requestDigest = BusinessFlowRemoteTaskEnvelope.requestDigest(request);
        AiBusinessFlowRemoteCommand existing = selectByCommandKey(request.getTenantId(), commandKey);
        if (existing != null) {
            return assertSameTaskRequest(existing, requestDigest, request.getCommandType());
        }

        LocalDateTime now = LocalDateTime.now();
        AiBusinessFlowRemoteCommand command = new AiBusinessFlowRemoteCommand();
        command.setId(IdWorker.getId());
        command.setTenantId(request.getTenantId());
        command.setCommandKey(commandKey);
        command.setCommandType(request.getCommandType());
        command.setRequestDigest(requestDigest);
        command.setRequestPayload(BusinessFlowRemoteTaskEnvelope.requestPayload(request));
        command.setObjectCode(request.getObjectCode());
        command.setRecordId(request.getRecordId());
        command.setBusinessKey(request.getBusinessKey());
        command.setFlowBusinessKey(request.getBusinessKey());
        command.setFlowModelKey(request.getFlowModelKey());
        command.setProcessInstanceId(request.getProcessInstanceId());
        command.setCommandStatus(BusinessFlowRemoteCommandStatus.PENDING.getCode());
        command.setRetryCount(0);
        command.setCreateBy(request.getOperatorUserId());
        command.setUpdateBy(request.getOperatorUserId());
        command.setCreateTime(now);
        command.setUpdateTime(now);
        try {
            int inserted = TenantContextHolder.executeIgnore(() -> commandMapper.insert(command));
            if (inserted != 1) {
                throw new BusinessException("流程远程任务命令持久化失败");
            }
            return command;
        } catch (DuplicateKeyException duplicateKey) {
            AiBusinessFlowRemoteCommand duplicate = selectByCommandKey(request.getTenantId(), commandKey);
            if (duplicate != null) {
                return assertSameTaskRequest(duplicate, requestDigest, request.getCommandType());
            }
            throw duplicateKey;
        }
    }

    public AiBusinessFlowRemoteCommand findTaskCommand(
            Long tenantId, String commandType, String idempotencyKey) {
        if (tenantId == null || tenantId <= 0
                || !BusinessFlowRemoteTaskEnvelope.supports(commandType)
                || StringUtils.isBlank(idempotencyKey)) {
            return null;
        }
        return selectByCommandKey(tenantId,
                BusinessFlowRemoteTaskEnvelope.commandKey(tenantId, commandType, idempotencyKey));
    }

    public AiBusinessFlowRemoteCommand requireCommand(Long tenantId, Long commandId) {
        if (tenantId == null || commandId == null) {
            throw new BusinessException("流程远程命令身份不能为空");
        }
        AiBusinessFlowRemoteCommand command = TenantContextHolder.executeIgnore(
                () -> commandMapper.selectByCommandId(tenantId, commandId));
        if (command == null) {
            throw new BusinessException("流程远程命令不存在");
        }
        return command;
    }

    public BusinessFlowRemoteStartRequest restore(AiBusinessFlowRemoteCommand command) {
        if (command == null
                || !BusinessFlowRemoteStartEnvelope.COMMAND_TYPE.equals(command.getCommandType())
                || StringUtils.isBlank(command.getRequestPayload())) {
            throw new BusinessException("流程远程命令缺少请求快照");
        }
        BusinessFlowRemoteStartRequest request;
        try {
            request = BusinessFlowRemoteStartEnvelope.restore(command.getRequestPayload());
        } catch (RuntimeException invalidPayload) {
            throw new BusinessException("流程远程命令请求快照无效");
        }
        if (request == null
                || !Objects.equals(command.getTenantId(), request.getTenantId())
                || !Objects.equals(command.getObjectCode(), request.getObjectCode())
                || !Objects.equals(command.getRecordId(), request.getRecordId())
                || !Objects.equals(command.getBusinessKey(), request.getBusinessKey())
                || !Objects.equals(command.getFlowBusinessKey(), request.getFlowBusinessKey())
                || !Objects.equals(command.getFlowModelKey(), request.getFlowModelKey())
                || !Objects.equals(command.getCommandKey(), BusinessFlowRemoteStartEnvelope.commandKey(request))
                || !Objects.equals(command.getRequestDigest(), BusinessFlowRemoteStartEnvelope.requestDigest(request))) {
            throw new BusinessException("流程远程命令身份或请求摘要校验失败");
        }
        return request;
    }

    public BusinessFlowRemoteTaskRequest restoreTask(AiBusinessFlowRemoteCommand command) {
        if (command == null || !BusinessFlowRemoteTaskEnvelope.supports(command.getCommandType())
                || StringUtils.isBlank(command.getRequestPayload())) {
            throw new BusinessException("流程远程任务命令缺少请求快照");
        }
        BusinessFlowRemoteTaskRequest request;
        try {
            request = BusinessFlowRemoteTaskEnvelope.restore(command.getRequestPayload());
        } catch (RuntimeException invalidPayload) {
            throw new BusinessException("流程远程任务命令请求快照无效");
        }
        if (request == null
                || !Objects.equals(command.getTenantId(), request.getTenantId())
                || !Objects.equals(command.getCommandType(), request.getCommandType())
                || !Objects.equals(command.getObjectCode(), request.getObjectCode())
                || !Objects.equals(command.getRecordId(), request.getRecordId())
                || !Objects.equals(command.getBusinessKey(), request.getBusinessKey())
                || !Objects.equals(command.getFlowModelKey(), request.getFlowModelKey())
                || !Objects.equals(command.getProcessInstanceId(), request.getProcessInstanceId())
                || !Objects.equals(command.getCommandKey(), BusinessFlowRemoteTaskEnvelope.commandKey(request))
                || !Objects.equals(command.getRequestDigest(), BusinessFlowRemoteTaskEnvelope.requestDigest(request))) {
            throw new BusinessException("流程远程任务命令身份或请求摘要校验失败");
        }
        validateTaskRequest(request);
        return request;
    }

    public AiBusinessFlowRemoteCommand executeStart(AiBusinessFlowRemoteCommand command, FlowClient flowClient) {
        if (command == null || flowClient == null) {
            throw new BusinessException("流程远程启动执行条件不完整");
        }
        if ((BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED.matches(command.getCommandStatus())
                || BusinessFlowRemoteCommandStatus.COMPLETED.matches(command.getCommandStatus()))
                && StringUtils.isNotBlank(command.getProcessInstanceId())) {
            return command;
        }
        if (BusinessFlowRemoteCommandStatus.DEAD.matches(command.getCommandStatus())) {
            throw new BusinessException("流程远程启动命令已进入死信，请人工核对后恢复");
        }

        AiBusinessFlowRemoteCommand claimed = claim(command);
        if (claimed == null) {
            AiBusinessFlowRemoteCommand latest = requireCommand(command.getTenantId(), command.getId());
            if ((BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED.matches(latest.getCommandStatus())
                    || BusinessFlowRemoteCommandStatus.COMPLETED.matches(latest.getCommandStatus()))
                    && StringUtils.isNotBlank(latest.getProcessInstanceId())) {
                return latest;
            }
            throw new BusinessException("流程远程启动命令正在处理或等待重试");
        }

        BusinessFlowRemoteStartRequest request = restore(claimed);
        try {
            if (StringUtils.isNotBlank(claimed.getProcessInstanceId())) {
                return markRemoteSucceeded(claimed, claimed.getProcessInstanceId());
            }
            String recoveredProcessId = recoverProcessInstanceId(flowClient, request.getFlowBusinessKey());
            if (StringUtils.isNotBlank(recoveredProcessId)) {
                return markRemoteSucceeded(claimed, recoveredProcessId);
            }
            FlowResult<String> result = request.isDelegated()
                    ? flowClient.startProcessForDelegatedUser(
                            request.getFlowModelKey(), request.getFlowBusinessKey(), request.getObjectCode(),
                            request.getTitle(), request.getVariables())
                    : flowClient.startProcess(
                            request.getFlowModelKey(), request.getFlowBusinessKey(), request.getTitle(),
                            request.getVariables(), text(request.getStarterUserId()), request.getStarterUserName(),
                            null, null);
            if (result != null && result.isSuccess() && StringUtils.isNotBlank(result.getData())) {
                return markRemoteSucceeded(claimed, result.getData());
            }
            String processIdAfterFailure = recoverProcessInstanceId(flowClient, request.getFlowBusinessKey());
            if (StringUtils.isNotBlank(processIdAfterFailure)) {
                return markRemoteSucceeded(claimed, processIdAfterFailure);
            }
            BusinessException failure = new BusinessException("流程发起失败: "
                    + (result == null ? "无返回结果" : StringUtils.defaultIfBlank(result.getMsg(), "未知错误")));
            markAttemptFailed(claimed, failure);
            throw failure;
        } catch (BusinessException failure) {
            if (BusinessFlowRemoteCommandStatus.PROCESSING.matches(claimed.getCommandStatus())) {
                markAttemptFailedIfProcessing(claimed, failure);
            }
            throw failure;
        } catch (RuntimeException failure) {
            String recoveredProcessId = recoverProcessInstanceId(flowClient, request.getFlowBusinessKey());
            if (StringUtils.isNotBlank(recoveredProcessId)) {
                return markRemoteSucceeded(claimed, recoveredProcessId);
            }
            markAttemptFailedIfProcessing(claimed, failure);
            throw new BusinessException("流程发起结果未知，已记录恢复命令，请稍后重试");
        }
    }

    public AiBusinessFlowRemoteCommand executeTask(AiBusinessFlowRemoteCommand command, FlowClient flowClient) {
        if (command == null || flowClient == null) {
            throw new BusinessException("流程远程任务执行条件不完整");
        }
        if (BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED.matches(command.getCommandStatus())
                || BusinessFlowRemoteCommandStatus.COMPLETED.matches(command.getCommandStatus())) {
            return command;
        }
        if (BusinessFlowRemoteCommandStatus.DEAD.matches(command.getCommandStatus())) {
            throw new BusinessException("流程远程任务命令已进入死信，请人工核对后恢复");
        }

        AiBusinessFlowRemoteCommand claimed = claim(command);
        if (claimed == null) {
            AiBusinessFlowRemoteCommand latest = requireCommand(command.getTenantId(), command.getId());
            if (BusinessFlowRemoteCommandStatus.REMOTE_SUCCEEDED.matches(latest.getCommandStatus())
                    || BusinessFlowRemoteCommandStatus.COMPLETED.matches(latest.getCommandStatus())) {
                return latest;
            }
            throw new BusinessException("流程远程任务命令正在处理或等待重试");
        }

        BusinessFlowRemoteTaskRequest request = restoreTask(claimed);
        try {
            FlowResult<Void> result = executeRemoteTask(flowClient, request);
            if (result != null && result.isSuccess()) {
                return markRemoteSucceeded(claimed, request.getProcessInstanceId());
            }
            BusinessException failure = new BusinessException("流程远程任务执行失败: "
                    + (result == null ? "无返回结果" : StringUtils.defaultIfBlank(result.getMsg(), "未知错误")));
            markAttemptFailed(claimed, failure);
            throw failure;
        } catch (BusinessException failure) {
            markAttemptFailedIfProcessing(claimed, failure);
            throw failure;
        } catch (RuntimeException failure) {
            markAttemptFailedIfProcessing(claimed, failure);
            throw new BusinessException("流程远程任务结果未知，已记录恢复命令，请稍后重试");
        }
    }

    public List<AiBusinessFlowRemoteCommand> findRecoveryCandidates(LocalDateTime now, int batchSize) {
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        return TenantContextHolder.executeIgnore(() -> commandMapper.selectRecoveryCandidates(
                scanTime, staleBefore(scanTime), safeMaxRetryCount(), Math.max(1, Math.min(batchSize, 200))));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordRecoveryFailure(Long tenantId, Long commandId, Throwable failure) {
        AiBusinessFlowRemoteCommand command = requireCommand(tenantId, commandId);
        if (BusinessFlowRemoteCommandStatus.COMPLETED.matches(command.getCommandStatus())
                || BusinessFlowRemoteCommandStatus.DEAD.matches(command.getCommandStatus())
                || BusinessFlowRemoteCommandStatus.RETRY.matches(command.getCommandStatus())
                || BusinessFlowRemoteCommandStatus.PROCESSING.matches(command.getCommandStatus())) {
            return;
        }
        int nextAttempt = (command.getRetryCount() == null ? 0 : command.getRetryCount()) + 1;
        boolean exhausted = nextAttempt >= safeMaxRetryCount();
        LocalDateTime now = LocalDateTime.now();
        int updated = TenantContextHolder.executeIgnore(() -> commandMapper.markRecoveryFailed(
                tenantId, commandId,
                exhausted ? BusinessFlowRemoteCommandStatus.DEAD.getCode()
                        : BusinessFlowRemoteCommandStatus.RETRY.getCode(),
                exhausted ? null : now.plus(backoff(nextAttempt)),
                safeFailureType(failure), now, safeMaxRetryCount()));
        if (updated != 1) {
            throw new BusinessException("流程远程命令恢复失败状态已被其他工作节点更新");
        }
    }

    public void completeAfterCommit(AiBusinessFlowRemoteCommand command) {
        if (command == null) {
            return;
        }
        Runnable completion = () -> markCompleted(command.getTenantId(), command.getId());
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        completion.run();
                    } catch (RuntimeException failure) {
                        log.error("流程远程命令提交后完成状态更新失败: commandId={}, failureType={}",
                                command.getId(), safeFailureType(failure));
                    }
                }
            });
            return;
        }
        completion.run();
    }

    public void markCompleted(Long tenantId, Long commandId) {
        requiresNew(() -> {
            AiBusinessFlowRemoteCommand command = requireCommand(tenantId, commandId);
            if (BusinessFlowRemoteCommandStatus.COMPLETED.matches(command.getCommandStatus())) {
                return null;
            }
            int updated = TenantContextHolder.executeIgnore(
                    () -> commandMapper.markCompleted(tenantId, commandId, LocalDateTime.now()));
            if (updated != 1) {
                throw new BusinessException("流程远程命令完成状态更新失败");
            }
            return null;
        });
    }

    protected AiBusinessFlowRemoteCommand claim(AiBusinessFlowRemoteCommand command) {
        return requiresNew(() -> {
            String owner = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            int claimed = TenantContextHolder.executeIgnore(() -> commandMapper.claim(
                    command.getTenantId(), command.getId(), owner, now,
                    staleBefore(now), safeMaxRetryCount()));
            if (claimed != 1) {
                return null;
            }
            return requireCommand(command.getTenantId(), command.getId());
        });
    }

    protected AiBusinessFlowRemoteCommand markRemoteSucceeded(
            AiBusinessFlowRemoteCommand claimed, String processInstanceId) {
        return requiresNew(() -> {
            int updated = TenantContextHolder.executeIgnore(() -> commandMapper.markRemoteSucceeded(
                    claimed, processInstanceId, LocalDateTime.now()));
            if (updated != 1) {
                throw new BusinessException("流程远程成功状态已被其他工作节点更新");
            }
            return requireCommand(claimed.getTenantId(), claimed.getId());
        });
    }

    protected void markAttemptFailed(AiBusinessFlowRemoteCommand claimed, Throwable failure) {
        requiresNew(() -> {
            int attempts = claimed.getRetryCount() == null ? 0 : claimed.getRetryCount();
            boolean exhausted = attempts >= safeMaxRetryCount();
            LocalDateTime now = LocalDateTime.now();
            int updated = TenantContextHolder.executeIgnore(() -> commandMapper.markAttemptFailed(
                    claimed,
                    exhausted ? BusinessFlowRemoteCommandStatus.DEAD.getCode()
                            : BusinessFlowRemoteCommandStatus.RETRY.getCode(),
                    exhausted ? null : now.plus(backoff(attempts)),
                    safeFailureType(failure), now));
            if (updated != 1) {
                throw new BusinessException("流程远程命令失败状态已被其他工作节点更新");
            }
            return null;
        });
    }

    private void markAttemptFailedIfProcessing(AiBusinessFlowRemoteCommand claimed, Throwable failure) {
        AiBusinessFlowRemoteCommand latest = requireCommand(claimed.getTenantId(), claimed.getId());
        if (BusinessFlowRemoteCommandStatus.PROCESSING.matches(latest.getCommandStatus())
                && Objects.equals(claimed.getLockOwner(), latest.getLockOwner())) {
            markAttemptFailed(claimed, failure);
        }
    }

    private String recoverProcessInstanceId(FlowClient flowClient, String flowBusinessKey) {
        try {
            FlowResult<Map<String, Object>> status = flowClient.getProcessStatus(flowBusinessKey);
            if (status == null || !status.isSuccess() || status.getData() == null) {
                return null;
            }
            Object processInstanceId = status.getData().get("processInstanceId");
            return processInstanceId == null ? null : StringUtils.trimToNull(String.valueOf(processInstanceId));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private AiBusinessFlowRemoteCommand selectByCommandKey(Long tenantId, String commandKey) {
        return TenantContextHolder.executeIgnore(() -> commandMapper.selectByCommandKey(tenantId, commandKey));
    }

    private AiBusinessFlowRemoteCommand assertSameRequest(
            AiBusinessFlowRemoteCommand existing, String requestDigest) {
        if (!Objects.equals(existing.getRequestDigest(), requestDigest)) {
            throw new BusinessException("流程远程命令幂等键与请求摘要冲突");
        }
        return existing;
    }

    private AiBusinessFlowRemoteCommand assertSameTaskRequest(
            AiBusinessFlowRemoteCommand existing, String requestDigest, String commandType) {
        if (!Objects.equals(existing.getCommandType(), commandType)
                || !Objects.equals(existing.getRequestDigest(), requestDigest)) {
            throw new BusinessException("流程远程任务命令幂等键与请求摘要冲突");
        }
        return existing;
    }

    private void validateRequest(BusinessFlowRemoteStartRequest request) {
        if (request == null || request.getTenantId() == null || request.getTenantId() <= 0
                || request.getRecordId() == null
                || StringUtils.isAnyBlank(request.getObjectCode(), request.getBusinessKey(),
                request.getFlowBusinessKey(), request.getFlowModelKey())) {
            throw new BusinessException("流程远程启动命令缺少可信业务身份");
        }
    }

    private void validateTaskRequest(BusinessFlowRemoteTaskRequest request) {
        if (request == null || request.getTenantId() == null || request.getTenantId() <= 0
                || request.getOperatorUserId() == null || request.getOperatorUserId() <= 0
                || !BusinessFlowRemoteTaskEnvelope.supports(request.getCommandType())
                || StringUtils.isAnyBlank(request.getTaskId(), request.getProcessInstanceId(),
                request.getBusinessKey(), request.getObjectCode(), request.getFlowModelKey(),
                request.getIdempotencyKey(), request.getActionRequestDigest())) {
            throw new BusinessException("流程远程任务命令缺少可信业务身份");
        }
        if (BusinessFlowRemoteTaskEnvelope.COMMAND_RESUBMIT.equals(request.getCommandType())
                && request.getRecordId() == null) {
            throw new BusinessException("流程重提远程命令缺少可信业务记录身份");
        }
    }

    private FlowResult<Void> executeRemoteTask(FlowClient flowClient, BusinessFlowRemoteTaskRequest request) {
        Map<String, Object> variables = request.getVariables() == null ? Map.of() : request.getVariables();
        String userId = text(request.getOperatorUserId());
        return switch (request.getCommandType()) {
            case BusinessFlowRemoteTaskEnvelope.COMMAND_APPROVE -> flowClient.approve(
                    request.getTaskId(), userId, request.getComment(), request.getSignature(), variables,
                    request.getTenantId(), request.getIdempotencyKey(), request.getActionRequestDigest(),
                    request.getApprovalPointResults());
            case BusinessFlowRemoteTaskEnvelope.COMMAND_REJECT -> flowClient.reject(
                    request.getTaskId(), userId, request.getComment(), request.getSignature(),
                    request.getTenantId(), request.getIdempotencyKey(), request.getActionRequestDigest());
            case BusinessFlowRemoteTaskEnvelope.COMMAND_REJECT_TO_START -> flowClient.rejectToStart(
                    request.getTaskId(), userId, request.getComment(), request.getSignature(),
                    request.getTenantId(), request.getIdempotencyKey(), request.getActionRequestDigest());
            case BusinessFlowRemoteTaskEnvelope.COMMAND_RETURN -> flowClient.returnTask(
                    request.getTaskId(), userId, request.getComment(), request.getSignature(),
                    StringUtils.trimToNull(request.getTargetActivityId()), request.getTenantId(),
                    request.getIdempotencyKey(), request.getActionRequestDigest());
            case BusinessFlowRemoteTaskEnvelope.COMMAND_RESUBMIT -> flowClient.approve(
                    request.getTaskId(), userId, request.getComment(), null, variables,
                    request.getTenantId(), request.getIdempotencyKey(), request.getActionRequestDigest());
            default -> throw new BusinessException("不支持的流程远程任务命令");
        };
    }

    private int safeMaxRetryCount() {
        return Math.max(1, maxRetryCount);
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minusSeconds(Math.max(30, lockTimeoutSeconds));
    }

    private Duration backoff(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, 10));
        return Duration.ofSeconds(Math.max(1, retryBaseSeconds)).multipliedBy(1L << exponent);
    }

    private String safeFailureType(Throwable failure) {
        String name = failure == null ? null : failure.getClass().getSimpleName();
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(name, "UnknownFailure"), 128);
    }

    private String text(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private <T> T requiresNew(java.util.function.Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }
}
