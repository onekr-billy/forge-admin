package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import com.mdframe.forge.starter.flow.enums.FlowProjectionOutboxStatus;
import com.mdframe.forge.starter.flow.event.FlowProjectionEvent;
import com.mdframe.forge.starter.flow.event.FlowProjectionOutboxPayload;
import com.mdframe.forge.starter.flow.mapper.FlowProjectionOutboxMapper;
import com.mdframe.forge.starter.flow.service.FlowProjectionOutboxService;
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

/** 流程镜像投影 Outbox 的持久化、认领和重试状态机。 */
@Service
@RequiredArgsConstructor
public class FlowProjectionOutboxServiceImpl implements FlowProjectionOutboxService {

    private final FlowProjectionOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowProjectionOutbox append(FlowProjectionEvent event) {
        validate(event);
        String payload = serialize(FlowProjectionOutboxPayload.fromEvent(event));
        String payloadHash = sha256(payload);

        FlowProjectionOutbox outbox = new FlowProjectionOutbox();
        outbox.setTenantId(event.getTenantId());
        outbox.setEventId(event.getEventId());
        outbox.setEventVersion(event.getEventVersion());
        outbox.setOccurredAt(event.getOccurredAt());
        outbox.setEventType(event.getType().name());
        outbox.setAggregateType(event.getAggregateType());
        outbox.setAggregateId(event.getAggregateId());
        outbox.setPayload(payload);
        outbox.setPayloadHash(payloadHash);
        outbox.setProjectionStatus(FlowProjectionOutboxStatus.PENDING.getCode());
        outbox.setRetryCount(0);
        try {
            TenantContextHolder.executeIgnore(() -> outboxMapper.insert(outbox));
            return outbox;
        } catch (DuplicateKeyException duplicate) {
            FlowProjectionOutbox existing = TenantContextHolder.executeIgnore(
                    () -> outboxMapper.selectByEventId(event.getTenantId(), event.getEventId()));
            if (existing == null || !Objects.equals(existing.getPayloadHash(), payloadHash)) {
                throw new IllegalStateException("FLOW_PROJECTION_EVENT_ID_CONFLICT", duplicate);
            }
            return existing;
        }
    }

    @Override
    public FlowProjectionOutboxPayload deserialize(FlowProjectionOutbox outbox) {
        if (outbox == null || outbox.getPayload() == null || outbox.getPayload().isBlank()) {
            throw new IllegalArgumentException("FLOW_PROJECTION_PAYLOAD_REQUIRED");
        }
        if (!Objects.equals(outbox.getPayloadHash(), sha256(outbox.getPayload()))) {
            throw new IllegalStateException("FLOW_PROJECTION_PAYLOAD_HASH_MISMATCH");
        }
        try {
            return objectMapper.readValue(outbox.getPayload(), FlowProjectionOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("FLOW_PROJECTION_PAYLOAD_INVALID", exception);
        }
    }

    @Override
    public boolean markAppliedImmediately(FlowProjectionOutbox outbox, LocalDateTime now) {
        if (!validIdentity(outbox)) {
            return false;
        }
        return TenantContextHolder.executeIgnore(() -> outboxMapper.markAppliedImmediately(
                outbox.getTenantId(), outbox.getId(), now)) == 1;
    }

    @Override
    public List<FlowProjectionOutbox> findDispatchCandidates(LocalDateTime now, LocalDateTime staleBefore,
                                                              int maxRetryCount, int batchSize) {
        return TenantContextHolder.executeIgnore(() -> outboxMapper.selectDispatchCandidates(
                now, staleBefore, Math.max(1, maxRetryCount), Math.max(1, Math.min(batchSize, 1000))));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public FlowProjectionOutbox claimById(Long tenantId, Long id, String lockOwner,
                                          LocalDateTime now, LocalDateTime staleBefore, int maxRetryCount) {
        if (tenantId == null || tenantId <= 0 || id == null || lockOwner == null || lockOwner.isBlank()) {
            return null;
        }
        int claimed = TenantContextHolder.executeIgnore(() -> outboxMapper.claim(
                tenantId, id, lockOwner, now, staleBefore, Math.max(1, maxRetryCount)));
        if (claimed != 1) {
            return null;
        }
        return TenantContextHolder.executeIgnore(() -> outboxMapper.selectByOutboxId(tenantId, id));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markApplied(FlowProjectionOutbox outbox, String lockOwner, LocalDateTime now) {
        if (!validIdentity(outbox) || lockOwner == null || lockOwner.isBlank()) {
            return false;
        }
        return TenantContextHolder.executeIgnore(() -> outboxMapper.markApplied(
                outbox.getTenantId(), outbox.getId(), lockOwner, now)) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean markFailed(FlowProjectionOutbox outbox, String lockOwner, Throwable failure,
                              LocalDateTime now, int maxRetryCount, Duration retryBaseDelay) {
        if (!validIdentity(outbox) || lockOwner == null || lockOwner.isBlank()) {
            return false;
        }
        int attempts = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        boolean dead = attempts >= Math.max(1, maxRetryCount);
        int status = dead ? FlowProjectionOutboxStatus.DEAD.getCode()
                : FlowProjectionOutboxStatus.FAILED.getCode();
        LocalDateTime nextRetryTime = dead ? null : now.plus(backoff(retryBaseDelay, attempts));
        return TenantContextHolder.executeIgnore(() -> outboxMapper.markFailed(
                outbox.getTenantId(), outbox.getId(), lockOwner, status, nextRetryTime,
                safeFailureType(failure), now)) == 1;
    }

    private void validate(FlowProjectionEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()
                || event.getEventVersion() == null || event.getEventVersion() <= 0
                || event.getOccurredAt() == null || event.getType() == null
                || event.getAggregateType() == null || event.getAggregateType().isBlank()
                || event.getAggregateId() == null || event.getAggregateId().isBlank()) {
            throw new IllegalArgumentException("FLOW_PROJECTION_EVENT_INVALID");
        }
        if (event.getTenantId() == null || event.getTenantId() <= 0) {
            throw new IllegalArgumentException("FLOW_PROJECTION_TENANT_REQUIRED");
        }
    }

    private boolean validIdentity(FlowProjectionOutbox outbox) {
        return outbox != null && outbox.getTenantId() != null && outbox.getTenantId() > 0
                && outbox.getId() != null;
    }

    private String serialize(FlowProjectionOutboxPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("FLOW_PROJECTION_PAYLOAD_SERIALIZE_FAILED", exception);
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
        try {
            return safeBase.multipliedBy(1L << exponent);
        } catch (ArithmeticException exception) {
            return Duration.ofHours(24);
        }
    }

    private String safeFailureType(Throwable failure) {
        String name = failure == null ? null : failure.getClass().getSimpleName();
        return name == null || name.isBlank() ? "UnknownFailure" : name;
    }
}
