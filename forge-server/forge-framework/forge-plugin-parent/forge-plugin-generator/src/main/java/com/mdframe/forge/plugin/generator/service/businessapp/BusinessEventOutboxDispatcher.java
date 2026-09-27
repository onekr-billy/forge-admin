package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessEventOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 按业务聚合顺序投递 Outbox，并恢复超时租约与瞬时失败。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.business.event-outbox", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BusinessEventOutboxDispatcher {

    private final BusinessEventOutboxService outboxService;
    private final BusinessEventDeliveryService deliveryService;
    private final String workerId = UUID.randomUUID().toString();

    @Value("${forge.business.event-outbox.batch-size:100}")
    private int batchSize = 100;

    @Scheduled(fixedDelayString = "${forge.business.event-outbox.scan-interval-ms:1000}")
    public void dispatch() {
        List<AiBusinessEventOutbox> candidates = outboxService.findDeliveryCandidates(
                LocalDateTime.now(), Math.max(1, batchSize));
        for (AiBusinessEventOutbox candidate : candidates) {
            dispatch(candidate);
        }
    }

    void dispatch(AiBusinessEventOutbox candidate) {
        AiBusinessEventOutbox claimed = outboxService.claim(candidate, workerId, LocalDateTime.now());
        if (claimed == null) {
            return;
        }
        try {
            deliveryService.deliver(outboxService.restore(claimed));
            outboxService.markDelivered(claimed);
        } catch (Exception failure) {
            try {
                outboxService.markFailed(claimed, failure);
            } catch (Exception stateFailure) {
                log.error("业务事件投递失败且无法更新 Outbox 状态: outboxId={}, failureType={}",
                        claimed.getId(), safeFailureType(stateFailure));
            }
            log.warn("业务事件投递失败: outboxId={}, eventId={}, failureType={}",
                    claimed.getId(), claimed.getEventId(), safeFailureType(failure));
        }
    }

    private String safeFailureType(Throwable failure) {
        String name = failure == null ? null : failure.getClass().getSimpleName();
        return name == null || name.isBlank() ? "UnknownFailure" : name;
    }
}
