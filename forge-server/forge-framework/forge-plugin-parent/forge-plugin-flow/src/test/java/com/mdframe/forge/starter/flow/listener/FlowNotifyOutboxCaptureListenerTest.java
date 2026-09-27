package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.event.FlowNotifyOutboxPersistenceException;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.service.FlowNotifyOutboxService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class FlowNotifyOutboxCaptureListenerTest {

    @Test
    void appendFailureMustEscapeAsReliableListenerFailure() {
        FlowNotifyOutboxService service = mock(FlowNotifyOutboxService.class);
        FlowNotifyOutboxCaptureListener listener = new FlowNotifyOutboxCaptureListener(service);
        FlowBusiness business = new FlowBusiness();
        business.setTenantId(1L);
        FlowTaskNotifyEvent event = FlowTaskNotifyEvent.processResult(business, Map.of(), false);
        doThrow(new IllegalStateException("database unavailable")).when(service).append(event);

        assertThrows(FlowNotifyOutboxPersistenceException.class, () -> listener.capture(event));
    }
}
