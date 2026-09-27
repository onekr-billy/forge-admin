package com.mdframe.forge.starter.flow.service;

import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import com.mdframe.forge.starter.flow.event.FlowProjectionEvent;
import com.mdframe.forge.starter.flow.event.FlowProjectionOutboxPayload;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public interface FlowProjectionOutboxService {

    FlowProjectionOutbox append(FlowProjectionEvent event);

    FlowProjectionOutboxPayload deserialize(FlowProjectionOutbox outbox);

    boolean markAppliedImmediately(FlowProjectionOutbox outbox, LocalDateTime now);

    List<FlowProjectionOutbox> findDispatchCandidates(LocalDateTime now, LocalDateTime staleBefore,
                                                       int maxRetryCount, int batchSize);

    FlowProjectionOutbox claimById(Long tenantId, Long id, String lockOwner,
                                   LocalDateTime now, LocalDateTime staleBefore, int maxRetryCount);

    boolean markApplied(FlowProjectionOutbox outbox, String lockOwner, LocalDateTime now);

    boolean markFailed(FlowProjectionOutbox outbox, String lockOwner, Throwable failure,
                       LocalDateTime now, int maxRetryCount, Duration retryBaseDelay);
}
