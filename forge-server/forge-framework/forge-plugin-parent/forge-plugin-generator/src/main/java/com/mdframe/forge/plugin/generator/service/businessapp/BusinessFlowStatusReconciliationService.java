package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.enums.BusinessFlowStatusSyncStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Maintains the durable lease and retry state for proactive Flow status reconciliation.
 * Remote reads run outside these short local transactions.
 */
@Service
@RequiredArgsConstructor
public class BusinessFlowStatusReconciliationService {

    private final BusinessFlowInstanceLinkMapper linkMapper;

    @Value("${forge.business.flow-status-sync.max-retry-count:8}")
    private int maxRetryCount = 8;

    @Value("${forge.business.flow-status-sync.lock-timeout-seconds:120}")
    private long lockTimeoutSeconds = 120;

    @Value("${forge.business.flow-status-sync.retry-base-seconds:10}")
    private long retryBaseSeconds = 10;

    @Value("${forge.business.flow-status-sync.running-interval-seconds:300}")
    private long runningIntervalSeconds = 300;

    public List<AiBusinessFlowInstanceLink> findCandidates(LocalDateTime now, int batchSize) {
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        return TenantContextHolder.executeIgnore(() -> linkMapper.selectStatusSyncCandidates(
                scanTime, staleBefore(scanTime), safeMaxRetryCount(), Math.max(1, Math.min(batchSize, 200))));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiBusinessFlowInstanceLink claim(AiBusinessFlowInstanceLink candidate) {
        if (candidate == null || candidate.getTenantId() == null || candidate.getId() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        String owner = UUID.randomUUID().toString();
        int updated = TenantContextHolder.executeIgnore(() -> linkMapper.claimStatusSync(
                candidate.getTenantId(), candidate.getId(), owner, now,
                staleBefore(now), safeMaxRetryCount()));
        return updated == 1 ? requireLink(candidate.getTenantId(), candidate.getId()) : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRunning(AiBusinessFlowInstanceLink claimed, String remoteStatus) {
        validateClaim(claimed);
        LocalDateTime now = LocalDateTime.now();
        int updated = TenantContextHolder.executeIgnore(() -> linkMapper.markStatusSyncWaiting(
                claimed, remoteStatus, now.plusSeconds(Math.max(5, runningIntervalSeconds)), now));
        requireUpdated(updated, "流程状态对账运行态已被其他工作节点更新");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markCompleted(AiBusinessFlowInstanceLink claimed, String remoteStatus) {
        validateClaim(claimed);
        int updated = TenantContextHolder.executeIgnore(() -> linkMapper.markStatusSyncCompleted(
                claimed, remoteStatus, LocalDateTime.now()));
        requireUpdated(updated, "流程状态对账完成态已被其他工作节点更新");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(AiBusinessFlowInstanceLink claimed, Throwable failure) {
        validateClaim(claimed);
        int attempts = claimed.getStatusSyncRetryCount() == null ? 0 : claimed.getStatusSyncRetryCount();
        boolean exhausted = attempts >= safeMaxRetryCount();
        LocalDateTime now = LocalDateTime.now();
        int updated = TenantContextHolder.executeIgnore(() -> linkMapper.markStatusSyncFailed(
                claimed,
                exhausted ? BusinessFlowStatusSyncStatus.DEAD.getCode()
                        : BusinessFlowStatusSyncStatus.RETRY.getCode(),
                exhausted ? null : now.plus(backoff(attempts)),
                safeFailureType(failure), now));
        requireUpdated(updated, "流程状态对账失败状态已被其他工作节点更新");
    }

    /**
     * Requeues a tenant-scoped DEAD record without performing the remote read in the HTTP transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public AiBusinessFlowInstanceLink requeueDead(Long linkId, Long replayedBy, String replayReason) {
        if (linkId == null || linkId <= 0) {
            throw new BusinessException(400, "流程状态对账记录ID不能为空");
        }
        Long tenantId = requireCurrentTenantId();
        Long operatorId = requireOperator(replayedBy);
        String reason = normalizeReason(replayReason);
        LocalDateTime now = LocalDateTime.now();
        int updated = TenantContextHolder.executeIgnore(() -> linkMapper.requeueDeadStatusSync(
                tenantId, linkId, operatorId, reason, now));
        if (updated != 1) {
            AiBusinessFlowInstanceLink existing = TenantContextHolder.executeIgnore(
                    () -> linkMapper.selectByLinkId(tenantId, linkId));
            if (existing == null) {
                throw new BusinessException(404, "流程状态对账死信不存在或不属于当前租户");
            }
            throw new BusinessException(409, "仅允许重放仍处于非终态的流程状态对账死信");
        }
        return requireLink(tenantId, linkId);
    }

    private AiBusinessFlowInstanceLink requireLink(Long tenantId, Long id) {
        AiBusinessFlowInstanceLink link = TenantContextHolder.executeIgnore(
                () -> linkMapper.selectByLinkId(tenantId, id));
        if (link == null) {
            throw new BusinessException("流程状态对账关联不存在");
        }
        return link;
    }

    private void validateClaim(AiBusinessFlowInstanceLink claimed) {
        if (claimed == null || claimed.getTenantId() == null || claimed.getId() == null
                || StringUtils.isBlank(claimed.getStatusSyncLockOwner())
                || !BusinessFlowStatusSyncStatus.PROCESSING.matches(claimed.getStatusSyncStatus())) {
            throw new BusinessException("流程状态对账租约无效");
        }
    }

    private void requireUpdated(int updated, String message) {
        if (updated != 1) {
            throw new BusinessException(message);
        }
    }

    private Long requireCurrentTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (RuntimeException noSession) {
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(403, "无法确定当前租户，禁止重放流程状态对账死信");
        }
        return tenantId;
    }

    private Long requireOperator(Long replayedBy) {
        if (replayedBy == null || replayedBy <= 0) {
            throw new BusinessException(403, "无法确定流程状态对账重放操作人");
        }
        return replayedBy;
    }

    private String normalizeReason(String replayReason) {
        String reason = StringUtils.trimToNull(replayReason);
        if (reason == null) {
            throw new BusinessException(400, "人工重放原因不能为空");
        }
        if (reason.length() > 500) {
            throw new BusinessException(400, "人工重放原因不能超过500个字符");
        }
        return reason;
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
}
