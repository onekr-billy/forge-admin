package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import com.mdframe.forge.starter.flow.enums.FlowProjectionType;
import com.mdframe.forge.starter.flow.event.FlowProjectionOutboxPayload;
import com.mdframe.forge.starter.flow.service.FlowProjectionHandler;
import com.mdframe.forge.starter.flow.service.FlowProjectionOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowProjectionOutboxDispatcherTest {

    @Test
    void scheduledRecoveryClaimsAppliesAndMarksProjection() {
        FlowProjectionOutboxService service = mock(FlowProjectionOutboxService.class);
        FlowProjectionHandler handler = mock(FlowProjectionHandler.class);
        FlowProjectionOutboxDispatcher dispatcher = dispatcher(service, handler);
        FlowProjectionOutbox candidate = outbox();
        FlowProjectionOutboxPayload payload = new FlowProjectionOutboxPayload(
                FlowProjectionType.TASK_CREATED, null, null, null);
        when(service.findDispatchCandidates(any(), any(), eq(8), eq(100)))
                .thenReturn(List.of(candidate));
        when(service.claimById(eq(7L), eq(21L), anyString(), any(), any(), eq(8)))
                .thenReturn(candidate);
        when(service.deserialize(candidate)).thenReturn(payload);
        when(service.markApplied(eq(candidate), anyString(), any())).thenReturn(true);

        dispatcher.dispatchPending();

        verify(handler).apply(candidate, payload);
        verify(service).markApplied(eq(candidate), anyString(), any(LocalDateTime.class));
    }

    @Test
    void projectionFailureIsPersistedForBackoffRetry() {
        FlowProjectionOutboxService service = mock(FlowProjectionOutboxService.class);
        FlowProjectionHandler handler = mock(FlowProjectionHandler.class);
        FlowProjectionOutboxDispatcher dispatcher = dispatcher(service, handler);
        FlowProjectionOutbox candidate = outbox();
        FlowProjectionOutboxPayload payload = new FlowProjectionOutboxPayload(
                FlowProjectionType.TASK_CREATED, null, null, null);
        when(service.findDispatchCandidates(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(candidate));
        when(service.claimById(eq(7L), eq(21L), anyString(), any(), any(), anyInt()))
                .thenReturn(candidate);
        when(service.deserialize(candidate)).thenReturn(payload);
        doThrow(new IllegalStateException("sensitive-row")).when(handler).apply(candidate, payload);
        when(service.markFailed(eq(candidate), anyString(), any(), any(), eq(8), any()))
                .thenReturn(true);

        dispatcher.dispatchPending();

        verify(service).markFailed(eq(candidate), anyString(), any(IllegalStateException.class),
                any(LocalDateTime.class), eq(8), any());
    }

    private FlowProjectionOutboxDispatcher dispatcher(FlowProjectionOutboxService service,
                                                       FlowProjectionHandler handler) {
        FlowProjectionOutboxDispatcher dispatcher = new FlowProjectionOutboxDispatcher(service, handler);
        ReflectionTestUtils.setField(dispatcher, "enabled", true);
        ReflectionTestUtils.setField(dispatcher, "batchSize", 100);
        ReflectionTestUtils.setField(dispatcher, "maxRetryCount", 8);
        ReflectionTestUtils.setField(dispatcher, "lockTimeoutSeconds", 300L);
        ReflectionTestUtils.setField(dispatcher, "retryBaseSeconds", 30L);
        return dispatcher;
    }

    private FlowProjectionOutbox outbox() {
        FlowProjectionOutbox outbox = new FlowProjectionOutbox();
        outbox.setId(21L);
        outbox.setTenantId(7L);
        outbox.setEventId("event-21");
        outbox.setRetryCount(1);
        return outbox;
    }
}
