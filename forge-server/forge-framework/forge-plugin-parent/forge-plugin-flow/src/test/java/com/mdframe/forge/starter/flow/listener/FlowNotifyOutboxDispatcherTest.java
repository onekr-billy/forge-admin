package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.service.FlowNotifyOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowNotifyOutboxDispatcherTest {

    @Test
    void afterCommitDispatchClaimsThenMarksDelivered() {
        FlowNotifyOutboxService service = mock(FlowNotifyOutboxService.class);
        FlowTaskNotificationHandler handler = mock(FlowTaskNotificationHandler.class);
        FlowNotifyOutboxDispatcher dispatcher = dispatcher(service, handler);
        FlowTaskNotifyEvent event = event();
        FlowNotifyOutbox claimed = outbox(event);
        when(service.claimByEventId(eq(1L), eq(event.getEventId()), anyString(), any(), any(), eq(5)))
                .thenReturn(claimed);
        when(service.deserialize(claimed)).thenReturn(event);
        when(service.markDelivered(eq(claimed), anyString(), any())).thenReturn(true);

        dispatcher.onNotifyEvent(event);

        verify(handler).handle(event);
        verify(service).markDelivered(eq(claimed), anyString(), any(LocalDateTime.class));
    }

    @Test
    void handlerFailureIsRecordedForRetryInsteadOfBeingSwallowed() {
        FlowNotifyOutboxService service = mock(FlowNotifyOutboxService.class);
        FlowTaskNotificationHandler handler = mock(FlowTaskNotificationHandler.class);
        FlowNotifyOutboxDispatcher dispatcher = dispatcher(service, handler);
        FlowTaskNotifyEvent event = event();
        FlowNotifyOutbox claimed = outbox(event);
        when(service.claimByEventId(eq(1L), eq(event.getEventId()), anyString(), any(), any(), anyInt()))
                .thenReturn(claimed);
        when(service.deserialize(claimed)).thenReturn(event);
        doThrow(new IllegalStateException("secret-url")).when(handler).handle(event);
        when(service.markFailed(eq(claimed), anyString(), any(), any(), anyInt(), any())).thenReturn(true);

        dispatcher.onNotifyEvent(event);

        verify(service).markFailed(eq(claimed), anyString(), any(IllegalStateException.class),
                any(LocalDateTime.class), eq(5), any());
    }

    private FlowNotifyOutboxDispatcher dispatcher(FlowNotifyOutboxService service,
                                                   FlowTaskNotificationHandler handler) {
        FlowNotifyOutboxDispatcher dispatcher = new FlowNotifyOutboxDispatcher(service, handler);
        ReflectionTestUtils.setField(dispatcher, "enabled", true);
        ReflectionTestUtils.setField(dispatcher, "maxRetryCount", 5);
        ReflectionTestUtils.setField(dispatcher, "lockTimeoutSeconds", 300L);
        ReflectionTestUtils.setField(dispatcher, "retryBaseSeconds", 30L);
        return dispatcher;
    }

    private FlowTaskNotifyEvent event() {
        FlowBusiness business = new FlowBusiness();
        business.setTenantId(1L);
        business.setProcessInstanceId("process-1");
        return FlowTaskNotifyEvent.processResult(business, Map.of(), false);
    }

    private FlowNotifyOutbox outbox(FlowTaskNotifyEvent event) {
        FlowNotifyOutbox outbox = new FlowNotifyOutbox();
        outbox.setId(1L);
        outbox.setTenantId(event.getTenantId());
        outbox.setEventId(event.getEventId());
        outbox.setRetryCount(1);
        return outbox;
    }
}
