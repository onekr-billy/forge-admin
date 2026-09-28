package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessEventOutbox;
import com.mdframe.forge.plugin.generator.enums.BusinessEventOutboxStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessEventOutboxMapper;
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

/** 业务事件事务 Outbox 的追加、租约认领与结果状态机。 */
@Service
@RequiredArgsConstructor
public class BusinessEventOutboxService {

    private final BusinessEventOutboxMapper outboxMapper;

    @Value("${forge.business.event-outbox.max-retry-count:8}")
    private int maxRetryCount = 8;

    @Value("${forge.business.event-outbox.lock-timeout-seconds:300}")
    private long lockTimeoutSeconds = 300;

    @Value("${forge.business.event-outbox.retry-base-seconds:5}")
    private long retryBaseSeconds = 5;

    @Transactional(rollbackFor = Exception.class)
    public AiBusinessEventOutbox append(BusinessEvent event) {
        validateForAppend(event);
        String sourceDigest = BusinessEventEnvelope.logicalDigest(event);
        AiBusinessEventOutbox duplicate = TenantContextHolder.executeIgnore(
                () -> outboxMapper.selectByEventId(event.getTenantId(), event.getEventId()));
        if (duplicate != null) {
            return assertSameLogicalEvent(duplicate, sourceDigest);
        }

        String aggregateKey = BusinessEventEnvelope.aggregateKey(event);
        LocalDateTime now = LocalDateTime.now();
        int advanced = TenantContextHolder.executeIgnore(() -> outboxMapper.advanceAggregateSequence(
                event.getTenantId(), aggregateKey, event.getObjectCode(), normalizedRecordId(event), now));
        if (advanced <= 0) {
            throw new BusinessException("业务事件聚合序号分配失败");
        }
        Long aggregateSequence = TenantContextHolder.executeIgnore(
                () -> outboxMapper.selectAggregateSequence(event.getTenantId(), aggregateKey));
        if (aggregateSequence == null || aggregateSequence <= 0) {
            throw new BusinessException("业务事件聚合序号分配失败");
        }
        BusinessEventEnvelope.assignAggregateSequence(event, aggregateSequence);

        AiBusinessEventOutbox outbox = buildOutbox(event, sourceDigest, aggregateKey, now);
        try {
            int inserted = TenantContextHolder.executeIgnore(() -> outboxMapper.insert(outbox));
            if (inserted != 1) {
                throw new BusinessException("业务事件 Outbox 持久化失败");
            }
            return outbox;
        } catch (DuplicateKeyException duplicateKey) {
            AiBusinessEventOutbox existing = TenantContextHolder.executeIgnore(
                    () -> outboxMapper.selectByEventId(event.getTenantId(), event.getEventId()));
            if (existing != null) {
                return assertSameLogicalEvent(existing, sourceDigest);
            }
            throw duplicateKey;
        }
    }

    public List<AiBusinessEventOutbox> findDeliveryCandidates(LocalDateTime now, int batchSize) {
        LocalDateTime scanTime = now == null ? LocalDateTime.now() : now;
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        return TenantContextHolder.executeIgnore(() -> outboxMapper.selectDeliveryCandidates(
                scanTime, staleBefore(scanTime), safeMaxRetryCount(), safeBatchSize));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AiBusinessEventOutbox claim(AiBusinessEventOutbox candidate, String owner, LocalDateTime now) {
        if (candidate == null || candidate.getTenantId() == null || candidate.getId() == null
                || StringUtils.isBlank(owner)) {
            return null;
        }
        LocalDateTime claimTime = now == null ? LocalDateTime.now() : now;
        if (BusinessEventOutboxStatus.PROCESSING.matches(candidate.getDeliveryStatus())
                && candidate.getRetryCount() != null
                && candidate.getRetryCount() >= safeMaxRetryCount()) {
            TenantContextHolder.executeIgnore(() -> outboxMapper.expireExhaustedLease(
                    candidate.getTenantId(), candidate.getId(), claimTime,
                    staleBefore(claimTime), safeMaxRetryCount()));
            return null;
        }
        int claimed = TenantContextHolder.executeIgnore(() -> outboxMapper.claimDelivery(
                candidate.getTenantId(), candidate.getId(), owner, claimTime,
                staleBefore(claimTime), safeMaxRetryCount()));
        if (claimed != 1) {
            return null;
        }
        return TenantContextHolder.executeIgnore(
                () -> outboxMapper.selectByOutboxId(candidate.getTenantId(), candidate.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markDelivered(AiBusinessEventOutbox claimed) {
        validateClaim(claimed);
        int updated = TenantContextHolder.executeIgnore(
                () -> outboxMapper.markDelivered(claimed, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException("业务事件 Outbox 完成状态已被其他工作节点更新");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(AiBusinessEventOutbox claimed, Throwable failure) {
        validateClaim(claimed);
        int attempts = claimed.getRetryCount() == null ? 0 : claimed.getRetryCount();
        boolean exhausted = attempts >= safeMaxRetryCount();
        String status = exhausted
                ? BusinessEventOutboxStatus.DEAD.getCode()
                : BusinessEventOutboxStatus.FAILED.getCode();
        LocalDateTime nextRetryTime = exhausted ? null : LocalDateTime.now().plus(backoff(attempts));
        String errorType = safeFailureType(failure);
        int updated = TenantContextHolder.executeIgnore(() -> outboxMapper.markFailed(
                claimed, status, nextRetryTime, errorType, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException("业务事件 Outbox 失败状态已被其他工作节点更新");
        }
    }

    BusinessEvent restore(AiBusinessEventOutbox outbox) {
        if (outbox == null || StringUtils.isBlank(outbox.getEventPayload())) {
            throw new BusinessException("业务事件 Outbox 缺少事件快照");
        }
        BusinessEvent event;
        try {
            event = JSON.parseObject(outbox.getEventPayload(), BusinessEvent.class);
        } catch (RuntimeException invalidJson) {
            throw new BusinessException("业务事件 Outbox 快照格式无效");
        }
        if (!BusinessEventEnvelope.isTrusted(event)
                || !Objects.equals(outbox.getTenantId(), event.getTenantId())
                || !Objects.equals(outbox.getEventId(), event.getEventId())
                || !Objects.equals(outbox.getEventDigest(), event.getEventDigest())
                || !Objects.equals(outbox.getSourceDigest(), BusinessEventEnvelope.logicalDigest(event))
                || !Objects.equals(outbox.getAggregateKey(), BusinessEventEnvelope.aggregateKey(event))
                || !Objects.equals(outbox.getAggregateSequence(), event.getAggregateSequence())) {
            throw new BusinessException("业务事件 Outbox 快照身份或摘要校验失败");
        }
        return event;
    }

    private AiBusinessEventOutbox buildOutbox(BusinessEvent event, String sourceDigest,
                                               String aggregateKey, LocalDateTime now) {
        AiBusinessEventOutbox outbox = new AiBusinessEventOutbox();
        outbox.setId(IdWorker.getId());
        outbox.setTenantId(event.getTenantId());
        outbox.setEventId(event.getEventId());
        outbox.setEventSource(event.getEventSource());
        outbox.setEventVersion(event.getEventVersion());
        outbox.setSourceDigest(sourceDigest);
        outbox.setEventDigest(event.getEventDigest());
        outbox.setEventType(event.getEventType());
        outbox.setSuiteCode(event.getSuiteCode());
        outbox.setObjectCode(event.getObjectCode());
        outbox.setConfigKey(event.getConfigKey());
        outbox.setRecordId(normalizedRecordId(event));
        outbox.setAggregateKey(aggregateKey);
        outbox.setAggregateSequence(event.getAggregateSequence());
        outbox.setEventPayload(JSON.toJSONString(event));
        outbox.setDeliveryStatus(BusinessEventOutboxStatus.PENDING.getCode());
        outbox.setRetryCount(0);
        outbox.setCreateBy(event.getOperatorId());
        outbox.setUpdateBy(event.getOperatorId());
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        return outbox;
    }

    private AiBusinessEventOutbox assertSameLogicalEvent(AiBusinessEventOutbox existing, String sourceDigest) {
        if (!Objects.equals(existing.getSourceDigest(), sourceDigest)) {
            throw new BusinessException("业务事件ID与逻辑载荷摘要冲突");
        }
        return existing;
    }

    private void validateForAppend(BusinessEvent event) {
        if (!BusinessEventEnvelope.isTrusted(event)
                || StringUtils.isAnyBlank(event.getObjectCode(), event.getRecordId(), event.getEventType())) {
            throw new BusinessException("拒绝写入缺少可信身份的业务事件");
        }
        if (event.getAggregateSequence() != null) {
            throw new BusinessException("业务事件聚合序号只能由 Outbox 分配");
        }
    }

    private void validateClaim(AiBusinessEventOutbox claimed) {
        if (claimed == null || claimed.getTenantId() == null || claimed.getId() == null
                || StringUtils.isBlank(claimed.getLockOwner())
                || !BusinessEventOutboxStatus.PROCESSING.matches(claimed.getDeliveryStatus())) {
            throw new BusinessException("业务事件 Outbox 租约身份无效");
        }
    }

    private String normalizedRecordId(BusinessEvent event) {
        return StringUtils.defaultIfBlank(event.getRecordId(), event.getEventId());
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
}
