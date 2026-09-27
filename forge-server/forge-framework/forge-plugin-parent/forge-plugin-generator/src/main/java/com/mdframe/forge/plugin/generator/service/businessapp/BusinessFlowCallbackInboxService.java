package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowCallbackInbox;
import com.mdframe.forge.plugin.generator.enums.BusinessFlowCallbackInboxStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowCallbackInboxMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Flow 回调 Inbox 的持久化、顺序认领、租约接管和摘要复验。 */
@Service
@RequiredArgsConstructor
public class BusinessFlowCallbackInboxService {

    private final BusinessFlowCallbackInboxMapper inboxMapper;

    @Value("${forge.business.flow-callback-inbox.max-retry-count:8}")
    private int maxRetryCount = 8;

    @Value("${forge.business.flow-callback-inbox.lock-timeout-seconds:120}")
    private long lockTimeoutSeconds = 120;

    @Value("${forge.business.flow-callback-inbox.retry-base-seconds:5}")
    private long retryBaseSeconds = 5;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiBusinessFlowCallbackInbox enqueue(FlowEventContext event) {
        if (!BusinessFlowCallbackInboxEnvelope.hasReliableIdentity(event)) {
            throw new BusinessException("流程回调缺少可靠事件身份");
        }
        String digest = BusinessFlowCallbackInboxEnvelope.digest(event);
        AiBusinessFlowCallbackInbox duplicate = selectByEventId(event.getTenantId(), event.getEventId());
        if (duplicate != null) {
            return assertSameEvent(duplicate, digest);
        }

        LocalDateTime now = LocalDateTime.now();
        AiBusinessFlowCallbackInbox inbox = new AiBusinessFlowCallbackInbox();
        inbox.setId(IdWorker.getId());
        inbox.setTenantId(event.getTenantId());
        inbox.setEventId(event.getEventId());
        inbox.setEventVersion(event.getEventVersion());
        inbox.setEventSequence(event.getEventSequence());
        inbox.setAggregateKey(BusinessFlowCallbackInboxEnvelope.aggregateKey(event));
        inbox.setEventType(event.getEvent());
        inbox.setProcessInstanceId(StringUtils.trimToNull(event.getProcessInstanceId()));
        inbox.setBusinessKey(StringUtils.trimToNull(event.getBusinessKey()));
        inbox.setTaskId(StringUtils.trimToNull(event.getTaskId()));
        inbox.setEventDigest(digest);
        inbox.setEventPayload(BusinessFlowCallbackInboxEnvelope.payload(event));
        inbox.setConsumeStatus(BusinessFlowCallbackInboxStatus.PENDING.getCode());
        inbox.setRetryCount(0);
        inbox.setCreateTime(now);
        inbox.setUpdateTime(now);
        try {
            int inserted = TenantContextHolder.executeIgnore(() -> inboxMapper.insert(inbox));
            if (inserted != 1) {
                throw new BusinessException("流程回调 Inbox 持久化失败");
            }
            return inbox;
        } catch (DuplicateKeyException duplicateKey) {
            AiBusinessFlowCallbackInbox existing = selectByEventId(event.getTenantId(), event.getEventId());
            if (existing != null) {
                return assertSameEvent(existing, digest);
            }
            throw duplicateKey;
        }
    }

    public List<AiBusinessFlowCallbackInbox> findRecoveryCandidates(LocalDateTime now, int batchSize) {
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        return TenantContextHolder.executeIgnore(() -> inboxMapper.selectRecoveryCandidates(
                scanTime, staleBefore(scanTime), safeMaxRetryCount(), Math.max(1, Math.min(batchSize, 200))));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiBusinessFlowCallbackInbox claim(
            AiBusinessFlowCallbackInbox candidate, String owner, LocalDateTime now) {
        if (candidate == null || candidate.getTenantId() == null || candidate.getId() == null
                || StringUtils.isBlank(owner)) {
            return null;
        }
        if (BusinessFlowCallbackInboxStatus.COMPLETED.matches(candidate.getConsumeStatus())
                || BusinessFlowCallbackInboxStatus.DEAD.matches(candidate.getConsumeStatus())) {
            return null;
        }
        LocalDateTime claimTime = now == null ? LocalDateTime.now() : now;
        if (BusinessFlowCallbackInboxStatus.PROCESSING.matches(candidate.getConsumeStatus())
                && candidate.getRetryCount() != null
                && candidate.getRetryCount() >= safeMaxRetryCount()) {
            TenantContextHolder.executeIgnore(() -> inboxMapper.expireExhaustedLease(
                    candidate.getTenantId(), candidate.getId(), claimTime,
                    staleBefore(claimTime), safeMaxRetryCount()));
            return null;
        }
        int claimed = TenantContextHolder.executeIgnore(() -> inboxMapper.claim(
                candidate.getTenantId(), candidate.getId(), owner, claimTime,
                staleBefore(claimTime), safeMaxRetryCount()));
        if (claimed != 1) {
            return null;
        }
        return requireInbox(candidate.getTenantId(), candidate.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markCompleted(AiBusinessFlowCallbackInbox claimed) {
        validateClaim(claimed);
        int updated = TenantContextHolder.executeIgnore(
                () -> inboxMapper.markCompleted(claimed, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException("流程回调 Inbox 完成状态已被其他工作节点更新");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(AiBusinessFlowCallbackInbox claimed, Throwable failure) {
        validateClaim(claimed);
        int attempts = claimed.getRetryCount() == null ? 0 : claimed.getRetryCount();
        boolean exhausted = attempts >= safeMaxRetryCount();
        LocalDateTime now = LocalDateTime.now();
        int updated = TenantContextHolder.executeIgnore(() -> inboxMapper.markFailed(
                claimed,
                exhausted ? BusinessFlowCallbackInboxStatus.DEAD.getCode()
                        : BusinessFlowCallbackInboxStatus.FAILED.getCode(),
                exhausted ? null : now.plus(backoff(attempts)),
                safeFailureType(failure), now));
        if (updated != 1) {
            throw new BusinessException("流程回调 Inbox 失败状态已被其他工作节点更新");
        }
    }

    public FlowEventContext restore(AiBusinessFlowCallbackInbox inbox) {
        if (inbox == null || StringUtils.isBlank(inbox.getEventPayload())) {
            throw new BusinessException("流程回调 Inbox 缺少事件快照");
        }
        FlowEventContext event;
        try {
            event = BusinessFlowCallbackInboxEnvelope.restore(inbox.getEventPayload());
        } catch (RuntimeException invalidPayload) {
            throw new BusinessException("流程回调 Inbox 事件快照无效");
        }
        if (!BusinessFlowCallbackInboxEnvelope.hasReliableIdentity(event)
                || !Objects.equals(inbox.getTenantId(), event.getTenantId())
                || !Objects.equals(inbox.getEventId(), event.getEventId())
                || !Objects.equals(inbox.getEventVersion(), event.getEventVersion())
                || !Objects.equals(inbox.getEventSequence(), event.getEventSequence())
                || !Objects.equals(inbox.getAggregateKey(), BusinessFlowCallbackInboxEnvelope.aggregateKey(event))
                || !Objects.equals(inbox.getEventDigest(), BusinessFlowCallbackInboxEnvelope.digest(event))) {
            throw new BusinessException("流程回调 Inbox 身份或事件摘要校验失败");
        }
        return event;
    }

    public boolean isStale(AiBusinessFlowCallbackInbox claimed) {
        if (claimed == null || claimed.getTenantId() == null
                || StringUtils.isBlank(claimed.getAggregateKey()) || claimed.getEventSequence() == null) {
            throw new BusinessException("流程回调 Inbox 缺少顺序身份");
        }
        Long completedSequence = TenantContextHolder.executeIgnore(
                () -> inboxMapper.selectLatestCompletedSequence(
                        claimed.getTenantId(), claimed.getAggregateKey()));
        return completedSequence != null && completedSequence >= claimed.getEventSequence();
    }

    private AiBusinessFlowCallbackInbox requireInbox(Long tenantId, Long id) {
        AiBusinessFlowCallbackInbox inbox = TenantContextHolder.executeIgnore(
                () -> inboxMapper.selectByInboxId(tenantId, id));
        if (inbox == null) {
            throw new BusinessException("流程回调 Inbox 不存在");
        }
        return inbox;
    }

    private AiBusinessFlowCallbackInbox selectByEventId(Long tenantId, String eventId) {
        return TenantContextHolder.executeIgnore(() -> inboxMapper.selectByEventId(tenantId, eventId));
    }

    private AiBusinessFlowCallbackInbox assertSameEvent(
            AiBusinessFlowCallbackInbox existing, String digest) {
        if (!Objects.equals(existing.getEventDigest(), digest)) {
            throw new BusinessException("流程回调事件ID与载荷摘要冲突");
        }
        return existing;
    }

    private void validateClaim(AiBusinessFlowCallbackInbox claimed) {
        if (claimed == null || claimed.getTenantId() == null || claimed.getId() == null
                || StringUtils.isBlank(claimed.getLockOwner())
                || !BusinessFlowCallbackInboxStatus.PROCESSING.matches(claimed.getConsumeStatus())) {
            throw new BusinessException("流程回调 Inbox 租约身份无效");
        }
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
