package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import com.mdframe.forge.starter.flow.enums.FlowNotifyOutboxStatus;
import com.mdframe.forge.starter.flow.event.FlowNotifyOutboxPayload;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.mapper.FlowNotifyOutboxMapper;
import com.mdframe.forge.starter.flow.service.FlowNotifyOutboxService;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
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
import java.util.Objects;

/** 流程通知 Outbox 服务实现。 */
@Service
@RequiredArgsConstructor
public class FlowNotifyOutboxServiceImpl implements FlowNotifyOutboxService {

    private final FlowNotifyOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowNotifyOutbox append(FlowTaskNotifyEvent event) {
        validateEvent(event);
        String payload = serialize(FlowNotifyOutboxPayload.fromEvent(event));
        String payloadHash = sha256(payload);

        FlowNotifyOutbox outbox = new FlowNotifyOutbox();
        outbox.setTenantId(event.getTenantId());
        outbox.setEventId(event.getEventId());
        outbox.setEventVersion(event.getEventVersion());
        outbox.setOccurredAt(event.getOccurredAt());
        outbox.setEventType(event.getType().name());
        outbox.setAggregateType(resolveAggregateType(event));
        outbox.setAggregateId(resolveAggregateId(event));
        outbox.setPayload(payload);
        outbox.setPayloadHash(payloadHash);
        outbox.setDeliveryStatus(FlowNotifyOutboxStatus.PENDING.getCode());
        outbox.setRetryCount(0);
        try {
            outboxMapper.insert(outbox);
            return outbox;
        } catch (DuplicateKeyException duplicate) {
            FlowNotifyOutbox existing = selectByEventId(event.getTenantId(), event.getEventId());
            if (existing == null || !Objects.equals(existing.getPayloadHash(), payloadHash)) {
                throw new IllegalStateException("FLOW_NOTIFY_EVENT_ID_CONFLICT", duplicate);
            }
            return existing;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public FlowNotifyOutbox claimByEventId(Long tenantId, String eventId, String lockOwner,
                                           LocalDateTime now, LocalDateTime staleBefore, int maxRetryCount) {
        FlowNotifyOutbox outbox = selectByEventId(tenantId, eventId);
        return claim(outbox, lockOwner, now, staleBefore, maxRetryCount);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public FlowNotifyOutbox claimById(Long tenantId, Long id, String lockOwner,
                                      LocalDateTime now, LocalDateTime staleBefore, int maxRetryCount) {
        if (tenantId == null || tenantId <= 0 || id == null) {
            return null;
        }
        FlowNotifyOutbox outbox = TenantContextHolder.executeIgnore(
                () -> outboxMapper.selectByOutboxId(tenantId, id));
        return claim(outbox, lockOwner, now, staleBefore, maxRetryCount);
    }

    @Override
    public List<FlowNotifyOutbox> findDispatchCandidates(LocalDateTime now, LocalDateTime staleBefore,
                                                         int maxRetryCount, int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 1000));
        return TenantContextHolder.executeIgnore(() -> outboxMapper.selectDispatchCandidates(
                now, staleBefore, Math.max(1, maxRetryCount), safeBatchSize));
    }

    @Override
    public FlowTaskNotifyEvent deserialize(FlowNotifyOutbox outbox) {
        if (outbox == null || outbox.getPayload() == null) {
            throw new IllegalArgumentException("FLOW_NOTIFY_OUTBOX_PAYLOAD_REQUIRED");
        }
        try {
            FlowNotifyOutboxPayload payload = objectMapper.readValue(
                    outbox.getPayload(), FlowNotifyOutboxPayload.class);
            return payload.toEvent(outbox.getEventId(), outbox.getEventVersion(), outbox.getOccurredAt(),
                    outbox.getTenantId(), outbox.getId());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("FLOW_NOTIFY_OUTBOX_PAYLOAD_INVALID", exception);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markDelivered(FlowNotifyOutbox outbox, String lockOwner, LocalDateTime now) {
        if (outbox == null || outbox.getTenantId() == null || outbox.getId() == null) {
            return false;
        }
        return TenantContextHolder.executeIgnore(() -> outboxMapper.markDelivered(
                outbox.getTenantId(), outbox.getId(), lockOwner, now)) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markFailed(FlowNotifyOutbox outbox, String lockOwner, Throwable failure,
                              LocalDateTime now, int maxRetryCount, Duration retryBaseDelay) {
        if (outbox == null || outbox.getTenantId() == null || outbox.getId() == null) {
            return false;
        }
        int attempts = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        boolean dead = attempts >= Math.max(1, maxRetryCount);
        int status = dead ? FlowNotifyOutboxStatus.DEAD.getCode() : FlowNotifyOutboxStatus.FAILED.getCode();
        LocalDateTime nextRetryTime = dead ? null : now.plus(backoff(retryBaseDelay, attempts));
        String failureType = safeFailureType(failure);
        return TenantContextHolder.executeIgnore(() -> outboxMapper.markFailed(
                outbox.getTenantId(), outbox.getId(), lockOwner, status,
                nextRetryTime, failureType, now)) == 1;
    }

    private FlowNotifyOutbox claim(FlowNotifyOutbox outbox, String lockOwner, LocalDateTime now,
                                   LocalDateTime staleBefore, int maxRetryCount) {
        if (outbox == null || lockOwner == null || lockOwner.isBlank()) {
            return null;
        }
        int claimed = TenantContextHolder.executeIgnore(() -> outboxMapper.claim(
                outbox.getTenantId(), outbox.getId(), lockOwner, now, staleBefore,
                Math.max(1, maxRetryCount)));
        if (claimed != 1) {
            return null;
        }
        return TenantContextHolder.executeIgnore(() -> outboxMapper.selectByOutboxId(
                outbox.getTenantId(), outbox.getId()));
    }

    private FlowNotifyOutbox selectByEventId(Long tenantId, String eventId) {
        if (tenantId == null || tenantId <= 0 || eventId == null || eventId.isBlank()) {
            return null;
        }
        return TenantContextHolder.executeIgnore(() -> outboxMapper.selectByEventId(tenantId, eventId));
    }

    private void validateEvent(FlowTaskNotifyEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()
                || event.getEventVersion() == null || event.getEventVersion() <= 0
                || event.getOccurredAt() == null || event.getType() == null) {
            throw new IllegalArgumentException("FLOW_NOTIFY_EVENT_INVALID");
        }
        if (event.getTenantId() == null || event.getTenantId() <= 0) {
            throw new IllegalArgumentException("FLOW_NOTIFY_TENANT_REQUIRED");
        }
    }

    private String resolveAggregateType(FlowTaskNotifyEvent event) {
        if (event.getFlowTask() != null || event.getTaskId() != null
                || (event.getEventMessage() != null && event.getEventMessage().getTaskId() != null)) {
            return "TASK";
        }
        return "PROCESS";
    }

    private String resolveAggregateId(FlowTaskNotifyEvent event) {
        if (event.getFlowTask() != null && hasText(event.getFlowTask().getTaskId())) {
            return event.getFlowTask().getTaskId();
        }
        if (hasText(event.getTaskId())) {
            return event.getTaskId();
        }
        if (event.getEventMessage() != null) {
            if (hasText(event.getEventMessage().getTaskId())) {
                return event.getEventMessage().getTaskId();
            }
            if (hasText(event.getEventMessage().getProcessInstanceId())) {
                return event.getEventMessage().getProcessInstanceId();
            }
        }
        if (event.getBusiness() != null) {
            if (hasText(event.getBusiness().getProcessInstanceId())) {
                return event.getBusiness().getProcessInstanceId();
            }
            if (hasText(event.getBusiness().getBusinessKey())) {
                return event.getBusiness().getBusinessKey();
            }
        }
        return event.getEventId();
    }

    private String serialize(FlowNotifyOutboxPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("FLOW_NOTIFY_OUTBOX_PAYLOAD_SERIALIZE_FAILED", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Duration backoff(Duration baseDelay, int attempts) {
        Duration safeBase = baseDelay == null || baseDelay.isNegative() || baseDelay.isZero()
                ? Duration.ofSeconds(30) : baseDelay;
        int exponent = Math.max(0, Math.min(attempts - 1, 10));
        long multiplier = 1L << exponent;
        try {
            return safeBase.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return Duration.ofHours(24);
        }
    }

    private String safeFailureType(Throwable failure) {
        if (failure == null) {
            return "UnknownFailure";
        }
        String simpleName = failure.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "UnknownFailure" : simpleName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
