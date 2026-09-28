package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import com.mdframe.forge.starter.flow.event.FlowProjectionOutboxPayload;
import com.mdframe.forge.starter.flow.service.FlowProjectionHandler;
import com.mdframe.forge.starter.flow.service.FlowProjectionOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 认领并补偿失败的流程任务、候选人与业务状态镜像投影。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowProjectionOutboxDispatcher {

    private final FlowProjectionOutboxService outboxService;
    private final FlowProjectionHandler projectionHandler;
    private final String workerId = UUID.randomUUID().toString();

    @Value("${forge.flow.projection-outbox.enabled:true}")
    private boolean enabled;

    @Value("${forge.flow.projection-outbox.batch-size:100}")
    private int batchSize;

    @Value("${forge.flow.projection-outbox.max-retry-count:8}")
    private int maxRetryCount;

    @Value("${forge.flow.projection-outbox.lock-timeout-seconds:300}")
    private long lockTimeoutSeconds;

    @Value("${forge.flow.projection-outbox.retry-base-seconds:30}")
    private long retryBaseSeconds;

    @Scheduled(fixedDelayString = "${forge.flow.projection-outbox.scan-interval-ms:30000}")
    public void dispatchPending() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<FlowProjectionOutbox> candidates = outboxService.findDispatchCandidates(
                now, staleBefore(now), safeMaxRetryCount(), Math.max(1, batchSize));
        for (FlowProjectionOutbox candidate : candidates) {
            LocalDateTime claimTime = LocalDateTime.now();
            FlowProjectionOutbox claimed = outboxService.claimById(
                    candidate.getTenantId(), candidate.getId(), workerId,
                    claimTime, staleBefore(claimTime), safeMaxRetryCount());
            dispatch(claimed);
        }
    }

    private void dispatch(FlowProjectionOutbox outbox) {
        if (outbox == null) {
            return;
        }
        try {
            FlowProjectionOutboxPayload payload = outboxService.deserialize(outbox);
            projectionHandler.apply(outbox, payload);
            if (!outboxService.markApplied(outbox, workerId, LocalDateTime.now())) {
                log.warn("流程镜像投影成功状态 CAS 失败: eventId={}, outboxId={}",
                        outbox.getEventId(), outbox.getId());
            }
        } catch (Exception failure) {
            boolean marked = outboxService.markFailed(outbox, workerId, failure, LocalDateTime.now(),
                    safeMaxRetryCount(), Duration.ofSeconds(Math.max(1, retryBaseSeconds)));
            log.warn("流程镜像投影补偿失败: eventId={}, outboxId={}, failureType={}, recorded={}",
                    outbox.getEventId(), outbox.getId(), safeFailureType(failure), marked);
        }
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minusSeconds(Math.max(30, lockTimeoutSeconds));
    }

    private int safeMaxRetryCount() {
        return Math.max(1, maxRetryCount);
    }

    private String safeFailureType(Throwable failure) {
        String name = failure == null ? null : failure.getClass().getSimpleName();
        return name == null || name.isBlank() ? "UnknownFailure" : name;
    }
}
