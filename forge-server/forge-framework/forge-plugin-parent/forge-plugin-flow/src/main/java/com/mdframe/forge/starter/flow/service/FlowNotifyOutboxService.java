package com.mdframe.forge.starter.flow.service;

import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 流程通知 Outbox 持久化与原子状态转换。 */
public interface FlowNotifyOutboxService {

    FlowNotifyOutbox append(FlowTaskNotifyEvent event);

    FlowNotifyOutbox claimByEventId(Long tenantId, String eventId, String lockOwner,
                                    LocalDateTime now, LocalDateTime staleBefore, int maxRetryCount);

    FlowNotifyOutbox claimById(Long tenantId, Long id, String lockOwner,
                               LocalDateTime now, LocalDateTime staleBefore, int maxRetryCount);

    List<FlowNotifyOutbox> findDispatchCandidates(LocalDateTime now, LocalDateTime staleBefore,
                                                  int maxRetryCount, int batchSize);

    FlowTaskNotifyEvent deserialize(FlowNotifyOutbox outbox);

    boolean markDelivered(FlowNotifyOutbox outbox, String lockOwner, LocalDateTime now);

    boolean markFailed(FlowNotifyOutbox outbox, String lockOwner, Throwable failure,
                       LocalDateTime now, int maxRetryCount, Duration retryBaseDelay);
}
