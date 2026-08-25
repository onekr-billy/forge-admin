package com.mdframe.forge.plugin.collaboration.service.directory;

import com.mdframe.forge.plugin.collaboration.domain.CollaborationDirectoryStatus;
import com.mdframe.forge.plugin.collaboration.domain.CollaborationSyncStatus;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialOrgMapping;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialSyncLog;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialTag;
import com.mdframe.forge.plugin.collaboration.domain.entity.SocialTagMember;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryMappingSnapshot;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncCommand;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncPlan;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectorySyncResult;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryWriteContext;
import com.mdframe.forge.plugin.collaboration.domain.model.DirectoryWriteResult;
import com.mdframe.forge.plugin.collaboration.mapper.SocialDirectoryMappingMapper;
import com.mdframe.forge.plugin.collaboration.mapper.SocialSyncLogMapper;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.connector.DirectoryConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.DirectorySnapshot;
import com.mdframe.forge.starter.collaboration.model.DirectorySyncScope;
import com.mdframe.forge.starter.collaboration.model.ExternalTag;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 目录全量同步编排器（Task 9）。
 * <p>
 * 流程：连接级分布式锁 → RUNNING 批次互斥 → FETCH → VALIDATE → PLAN → APPLY → FINALIZE。
 * 失败关闭：拉取中断、快照校验不通过、写入异常时批次收敛为 FAILED，不执行任何停用；
 * 只有完整快照成功应用后才按 last-seen 停用未出现对象。
 */
@Slf4j
@Service
@IgnoreTenant
@RequiredArgsConstructor
public class DirectorySyncOrchestrator {

    private static final String LOCK_KEY_PREFIX = "collab:dir:sync:";
    private static final int ERROR_SUMMARY_MAX_LENGTH = 200;

    private final RedissonClient redissonClient;
    private final ISocialConfigService configService;
    private final ISocialAppConfigService appConfigService;
    private final CollaborationProviderRegistry providerRegistry;
    private final DirectorySnapshotValidator snapshotValidator;
    private final DirectorySyncPlanner syncPlanner;
    private final ForgeDirectoryWriter directoryWriter;
    private final SocialSyncLogMapper syncLogMapper;
    private final SocialDirectoryMappingMapper mappingMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 执行连接的目录同步；同一连接同时只允许一个批次
     */
    public DirectorySyncResult synchronize(Long connectionId, DirectorySyncCommand command) {
        DirectorySyncCommand effective = command == null ? DirectorySyncCommand.manualFull(null) : command;
        SysSocialConfig connection = requireConnection(connectionId);
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + connectionId);
        boolean locked = false;
        try {
            // leaseTime 不指定，由 Redisson 看门狗续期，避免长批次锁提前失效
            locked = lock.tryLock();
            if (!locked) {
                throw new BusinessException("该连接目录同步正在进行中，请稍后再试");
            }
            // 活动批次全程持有连接锁，持锁后仍存在的 RUNNING 批次必为孤儿
            // （进程中断/历史缺陷导致状态未收敛），收敛为 FAILED 后放行本次同步
            SocialSyncLog orphan = syncLogMapper.selectRunningLog(connection.getTenantId(), connectionId);
            if (orphan != null) {
                failLog(orphan, new BusinessException("批次未正常收敛（进程中断或异常退出），已自动判定失败"));
                log.warn("目录同步发现孤儿RUNNING批次并已收敛为FAILED: connectionId={}, syncLogId={}",
                        connectionId, orphan.getId());
            }
            SocialSyncLog syncLog = createRunningLog(connection, effective);
            try {
                return doSynchronize(connection, syncLog, effective);
            } catch (Exception exception) {
                failLog(syncLog, exception);
                log.warn("目录同步批次失败: connectionId={}, syncLogId={}, reason={}",
                        connectionId, syncLog.getId(), exception.getMessage());
                if (exception instanceof BusinessException businessException) {
                    throw businessException;
                }
                throw new BusinessException("目录同步失败: " + sanitizeSummary(exception));
            }
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private DirectorySyncResult doSynchronize(SysSocialConfig connection, SocialSyncLog syncLog,
                                              DirectorySyncCommand command) {
        Long tenantId = connection.getTenantId();
        Long connectionId = connection.getId();
        Long runId = syncLog.getId();
        CollaborationExecutionContext context = buildContext(connection);
        DirectoryConnector connector = providerRegistry.requireConnector(
                connection.getPlatform(), CollaborationCapability.DIRECTORY, DirectoryConnector.class);

        // FETCH：完整读取全部分页，中断直接抛错，不产生任何落库
        DirectorySnapshot snapshot = connector.fetchSnapshot(context, command.scope());

        // VALIDATE：结构校验失败关闭
        syncLogMapper.updateSyncLogStage(runId, tenantId, "VALIDATE", null);
        snapshotValidator.validate(snapshot, command.policy());

        // PLAN：与本地映射基线比较
        syncLogMapper.updateSyncLogStage(runId, tenantId, "PLAN", null);
        DirectoryMappingSnapshot current = loadMappingSnapshot(tenantId, connectionId);
        DirectorySyncPlan plan = syncPlanner.plan(snapshot, current);

        // APPLY：部门/用户走 Forge 写入适配器，标签由编排层事务内落库
        syncLogMapper.updateSyncLogStage(runId, tenantId, "APPLY", null);
        DirectoryWriteContext writeContext = new DirectoryWriteContext(tenantId, connectionId, runId,
                connection.getDefaultOrgId(), connection.getDirectoryAuthority(),
                connection.getIdentityPolicy(), command.operatorId());
        DirectoryWriteResult writeResult = directoryWriter.apply(plan, writeContext);
        int tagChanged = applyTags(plan, tenantId, connectionId, runId);
        markSeenUnchanged(plan, tenantId, connectionId, runId);

        // FINALIZE：只有完整快照成功应用后才停用未出现对象
        int inactivated = finalizeInactivation(plan, tenantId, connectionId, runId);

        int created = writeResult.createdCount() + plan.tagCreates().size();
        int updated = writeResult.updatedCount() + (tagChanged - plan.tagCreates().size());
        int issues = writeResult.issueCount();
        String status = issues > 0 ? CollaborationSyncStatus.PARTIAL.getCode() : CollaborationSyncStatus.SUCCESS.getCode();
        completeLog(syncLog, snapshot, status, created, updated, inactivated, issues, null, null);
        log.info("目录同步批次完成: connectionId={}, syncLogId={}, status={}, created={}, updated={}, inactivated={}, issues={}",
                connectionId, runId, status, created, updated, inactivated, issues);
        return new DirectorySyncResult(runId, status, snapshot.departments().size(),
                snapshot.users().size(), snapshot.tags().size(), created, updated, inactivated, issues);
    }

    /**
     * 标签落库：创建/更新/标记出现，并对变更标签物理重建成员关系；返回变更标签数
     */
    private int applyTags(DirectorySyncPlan plan, Long tenantId, Long connectionId, Long runId) {
        if (plan.tagCreates().isEmpty() && plan.tagUpdates().isEmpty() && plan.tagUnchangedIds().isEmpty()) {
            return 0;
        }
        return transactionTemplate.execute(status -> {
            if (!plan.tagCreates().isEmpty()) {
                List<SocialTag> creates = new ArrayList<>(plan.tagCreates().size());
                for (ExternalTag tag : plan.tagCreates()) {
                    creates.add(buildTag(tag, tenantId, connectionId, runId));
                }
                mappingMapper.insertTags(creates);
            }
            for (ExternalTag tag : plan.tagUpdates()) {
                mappingMapper.updateTagByExternalId(buildTag(tag, tenantId, connectionId, runId));
            }
            for (String externalTagId : plan.tagUnchangedIds()) {
                mappingMapper.markTagSeen(tenantId, connectionId, externalTagId, runId);
            }
            rebuildTagMembers(plan, tenantId, connectionId);
            return plan.tagCreates().size() + plan.tagUpdates().size();
        });
    }

    /**
     * 变更标签（含新建）成员物理替换；成员本地目标从部门/用户映射解析，未映射时置空
     */
    private void rebuildTagMembers(DirectorySyncPlan plan, Long tenantId, Long connectionId) {
        List<ExternalTag> changedTags = new ArrayList<>(plan.tagCreates());
        changedTags.addAll(plan.tagUpdates());
        if (changedTags.isEmpty()) {
            return;
        }
        Map<String, Long> tagIdByExternalId = toMap(
                mappingMapper.selectTags(tenantId, connectionId), SocialTag::getExternalTagId, SocialTag::getId);
        Map<String, Long> orgIdByExternalId = toMap(
                mappingMapper.selectOrgMappings(tenantId, connectionId, null),
                SocialOrgMapping::getExternalDeptId, SocialOrgMapping::getOrgId);
        Map<String, Long> userIdByUuid = toMap(
                mappingMapper.selectSyncManagedUserBindings(tenantId, connectionId),
                SysUserSocial::getUuid, SysUserSocial::getUserId);
        for (ExternalTag tag : changedTags) {
            Long tagId = tagIdByExternalId.get(tag.externalTagId());
            if (tagId == null) {
                throw new BusinessException("标签落库后未找到本地标签: " + tag.externalTagId());
            }
            mappingMapper.deleteTagMembersByTagId(tenantId, tagId);
            List<SocialTagMember> members = new ArrayList<>();
            for (String userId : tag.memberUserIds()) {
                members.add(buildTagMember(tenantId, connectionId, tagId, "USER", userId, userIdByUuid.get(userId)));
            }
            for (String deptId : tag.departmentIds()) {
                members.add(buildTagMember(tenantId, connectionId, tagId, "DEPT", deptId, orgIdByExternalId.get(deptId)));
            }
            if (!members.isEmpty()) {
                mappingMapper.insertTagMembers(members);
            }
        }
    }

    /**
     * 未变化对象仅刷新 last-seen；创建/更新对象的 last-seen 由写入方落库
     */
    private void markSeenUnchanged(DirectorySyncPlan plan, Long tenantId, Long connectionId, Long runId) {
        if (plan.deptUnchangedIds().isEmpty() && plan.userUnchangedIds().isEmpty()) {
            return;
        }
        Map<String, String> deptHashById = new HashMap<>();
        plan.snapshot().departments().forEach(dept -> deptHashById.put(dept.externalId(), dept.sourceHash()));
        Map<String, String> userHashById = new HashMap<>();
        plan.snapshot().users().forEach(user -> userHashById.put(user.externalUserId(), user.sourceHash()));
        transactionTemplate.executeWithoutResult(status -> {
            for (String externalId : plan.deptUnchangedIds()) {
                mappingMapper.markOrgSeen(tenantId, connectionId, externalId, runId, deptHashById.get(externalId));
            }
            for (String uuid : plan.userUnchangedIds()) {
                mappingMapper.markUserSeen(tenantId, connectionId, uuid, userHashById.get(uuid));
            }
        });
    }

    /**
     * 完整快照成功应用后收敛未出现对象；按范围只停用本批次覆盖的对象类型
     */
    private int finalizeInactivation(DirectorySyncPlan plan, Long tenantId, Long connectionId, Long runId) {
        DirectorySnapshot snapshot = plan.snapshot();
        if (!snapshot.complete()) {
            return 0;
        }
        Integer inactivated = transactionTemplate.execute(status -> {
            int count = 0;
            if (snapshot.scope() != DirectorySyncScope.TAG_ONLY) {
                count += mappingMapper.markUnseenOrgInactive(tenantId, connectionId, runId);
                for (String uuid : plan.userMissingUuids()) {
                    count += mappingMapper.updateUserExternalStatus(tenantId, connectionId, uuid, "DELETED");
                }
            }
            if (snapshot.scope() != DirectorySyncScope.DIRECTORY_ONLY) {
                count += mappingMapper.markUnseenTagInactive(tenantId, connectionId, runId);
            }
            return count;
        });
        return inactivated == null ? 0 : inactivated;
    }

    private DirectoryMappingSnapshot loadMappingSnapshot(Long tenantId, Long connectionId) {
        Map<String, SocialOrgMapping> orgMappings = toMap(
                mappingMapper.selectOrgMappings(tenantId, connectionId, null),
                SocialOrgMapping::getExternalDeptId, Function.identity());
        Map<String, SocialTag> tags = toMap(
                mappingMapper.selectTags(tenantId, connectionId), SocialTag::getExternalTagId, Function.identity());
        Map<String, SysUserSocial> userBindings = toMap(
                mappingMapper.selectSyncManagedUserBindings(tenantId, connectionId),
                SysUserSocial::getUuid, Function.identity());
        Map<String, com.mdframe.forge.plugin.collaboration.domain.entity.SocialPostMapping> postMappings = toMap(
                mappingMapper.selectPostMappings(tenantId, connectionId),
                com.mdframe.forge.plugin.collaboration.domain.entity.SocialPostMapping::getExternalPostCode,
                Function.identity());
        return new DirectoryMappingSnapshot(orgMappings, postMappings, tags, userBindings);
    }

    private SysSocialConfig requireConnection(Long connectionId) {
        if (connectionId == null) {
            throw new BusinessException("连接ID不能为空");
        }
        SysSocialConfig connection = configService.selectConfigById(connectionId);
        if (connection == null) {
            throw new BusinessException("企业协同连接不存在");
        }
        if (!EnableStatus.ENABLED.matches(connection.getStatus())) {
            throw new BusinessException("企业协同连接已停用");
        }
        return connection;
    }

    private CollaborationExecutionContext buildContext(SysSocialConfig connection) {
        SysSocialAppConfig app = appConfigService.requireEnabledApp(connection.getTenantId(),
                connection.getId(), CollaborationCapability.DIRECTORY);
        return new CollaborationExecutionContext(connection.getTenantId(), connection.getId(),
                connection.getConnectionCode(), connection.getPlatform(), connection.getEnterpriseId(),
                app.getId(), app.getAppCode(), app.getAgentId(), Map.of());
    }

    private SocialSyncLog createRunningLog(SysSocialConfig connection, DirectorySyncCommand command) {
        SocialSyncLog syncLog = new SocialSyncLog();
        syncLog.setTenantId(connection.getTenantId());
        syncLog.setConnectionId(connection.getId());
        syncLog.setSyncType(command.syncType());
        syncLog.setTriggerSource(command.triggerSource());
        syncLog.setStage("FETCH");
        syncLog.setStatus(CollaborationSyncStatus.RUNNING.getCode());
        syncLog.setStartTime(LocalDateTime.now());
        syncLog.setCreateBy(command.operatorId());
        syncLog.setCreateTime(LocalDateTime.now());
        syncLogMapper.insert(syncLog);
        return syncLog;
    }

    private void completeLog(SocialSyncLog syncLog, DirectorySnapshot snapshot, String status,
                             int created, int updated, int inactivated, int issues,
                             String errorCode, String errorSummary) {
        SocialSyncLog finished = new SocialSyncLog();
        finished.setId(syncLog.getId());
        finished.setTenantId(syncLog.getTenantId());
        finished.setStatus(status);
        finished.setDeptCount(snapshot == null ? 0 : snapshot.departments().size());
        finished.setUserCount(snapshot == null ? 0 : snapshot.users().size());
        finished.setTagCount(snapshot == null ? 0 : snapshot.tags().size());
        finished.setCreatedCount(created);
        finished.setUpdatedCount(updated);
        finished.setInactivatedCount(inactivated);
        finished.setIssueCount(issues);
        finished.setErrorCode(errorCode);
        finished.setErrorSummary(errorSummary);
        syncLogMapper.completeSyncLog(finished);
    }

    private void failLog(SocialSyncLog syncLog, Exception exception) {
        try {
            completeLog(syncLog, null, CollaborationSyncStatus.FAILED.getCode(), 0, 0, 0, 0,
                    exception.getClass().getSimpleName(), sanitizeSummary(exception));
        } catch (Exception logException) {
            log.error("目录同步批次失败状态写入异常: syncLogId={}", syncLog.getId(), logException);
        }
    }

    /**
     * 脱敏错误摘要：只保留异常类型与截断消息，不写入密文/凭据/敏感明文
     */
    private String sanitizeSummary(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() > ERROR_SUMMARY_MAX_LENGTH
                ? normalized.substring(0, ERROR_SUMMARY_MAX_LENGTH)
                : normalized;
    }

    private SocialTag buildTag(ExternalTag tag, Long tenantId, Long connectionId, Long runId) {
        SocialTag entity = new SocialTag();
        entity.setTenantId(tenantId);
        entity.setConnectionId(connectionId);
        entity.setExternalTagId(tag.externalTagId());
        entity.setTagName(tag.name());
        entity.setStatus(CollaborationDirectoryStatus.ACTIVE.getCode());
        entity.setLastSeenRunId(runId);
        entity.setSourceHash(tag.sourceHash());
        return entity;
    }

    private SocialTagMember buildTagMember(Long tenantId, Long connectionId, Long tagId,
                                           String memberType, String externalMemberId, Long localTargetId) {
        SocialTagMember member = new SocialTagMember();
        member.setTenantId(tenantId);
        member.setConnectionId(connectionId);
        member.setTagId(tagId);
        member.setMemberType(memberType);
        member.setExternalMemberId(externalMemberId);
        member.setLocalTargetId(localTargetId);
        return member;
    }

    private <T, K, V> Map<K, V> toMap(List<T> list, Function<T, K> keyMapper, Function<T, V> valueMapper) {
        Map<K, V> result = new LinkedHashMap<>();
        for (T item : list) {
            K key = keyMapper.apply(item);
            if (key != null) {
                result.putIfAbsent(key, valueMapper.apply(item));
            }
        }
        return result;
    }
}
