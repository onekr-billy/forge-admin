package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.service.FlowNotifyOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 流程通知 Outbox 的提交后投递、失败重试和过期租约接管。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowNotifyOutboxDispatcher {

    private final FlowNotifyOutboxService outboxService;
    private final FlowTaskNotificationHandler notificationHandler;
    private final String workerId = UUID.randomUUID().toString();

    @Value("${forge.flow.notify-outbox.enabled:true}")
    private boolean enabled;

    @Value("${forge.flow.notify-outbox.batch-size:100}")
    private int batchSize;

    @Value("${forge.flow.notify-outbox.max-retry-count:5}")
    private int maxRetryCount;

    @Value("${forge.flow.notify-outbox.lock-timeout-seconds:300}")
    private long lockTimeoutSeconds;

    @Value("${forge.flow.notify-outbox.retry-base-seconds:30}")
    private long retryBaseSeconds;

    @Async("flowEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotifyEvent(FlowTaskNotifyEvent event) {
        if (!enabled || event == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        FlowNotifyOutbox claimed = outboxService.claimByEventId(
                event.getTenantId(), event.getEventId(), workerId, now, staleBefore(now), safeMaxRetryCount());
        dispatch(claimed);
    }

    @Scheduled(fixedDelayString = "${forge.flow.notify-outbox.scan-interval-ms:30000}")
    public void dispatchPending() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<FlowNotifyOutbox> candidates = outboxService.findDispatchCandidates(
                now, staleBefore(now), safeMaxRetryCount(), Math.max(1, batchSize));
        for (FlowNotifyOutbox candidate : candidates) {
            FlowNotifyOutbox claimed = outboxService.claimById(
                    candidate.getTenantId(), candidate.getId(), workerId,
                    LocalDateTime.now(), staleBefore(LocalDateTime.now()), safeMaxRetryCount());
            dispatch(claimed);
        }
    }

    private void dispatch(FlowNotifyOutbox outbox) {
        if (outbox == null) {
            return;
        }
        try {
            FlowTaskNotifyEvent event = outboxService.deserialize(outbox);
            notificationHandler.handle(event);
            if (!outboxService.markDelivered(outbox, workerId, LocalDateTime.now())) {
                log.warn("流程通知 Outbox 成功状态 CAS 失败: eventId={}, outboxId={}",
                        outbox.getEventId(), outbox.getId());
            }
        } catch (Exception failure) {
            boolean marked = outboxService.markFailed(outbox, workerId, failure, LocalDateTime.now(),
                    safeMaxRetryCount(), Duration.ofSeconds(Math.max(1, retryBaseSeconds)));
            log.warn("流程通知 Outbox 投递失败: eventId={}, outboxId={}, failureType={}, recorded={}",
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
        String simpleName = failure == null ? null : failure.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "UnknownFailure" : simpleName;
    }
}
