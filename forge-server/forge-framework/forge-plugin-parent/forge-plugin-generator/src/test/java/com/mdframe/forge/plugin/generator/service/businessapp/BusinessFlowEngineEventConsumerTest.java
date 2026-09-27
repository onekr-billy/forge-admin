package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.annotation.FlowBind;
import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowCallbackInbox;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowEngineEventConsumerTest {

    private BusinessFlowCallbackInboxService inboxService;
    private BusinessFlowService businessFlowService;
    private BusinessFlowEngineEventConsumer consumer;
    private FlowEventContext event;
    private AiBusinessFlowCallbackInbox pending;
    private AiBusinessFlowCallbackInbox claimed;

    @BeforeEach
    void setUp() {
        inboxService = mock(BusinessFlowCallbackInboxService.class);
        businessFlowService = mock(BusinessFlowService.class);
        consumer = new BusinessFlowEngineEventConsumer(inboxService, businessFlowService);
        event = FlowEventContext.builder()
                .eventId("event-10")
                .eventVersion(1)
                .eventSequence(10L)
                .event("PROCESS_COMPLETED")
                .tenantId(1L)
                .processInstanceId("process-42")
                .businessKey("purchase:42")
                .build();
        pending = inbox(10L, "PENDING", null);
        claimed = inbox(10L, "PROCESSING", "worker");
        when(inboxService.enqueue(event)).thenReturn(pending);
        when(inboxService.claim(any(), anyString(), any(LocalDateTime.class))).thenReturn(claimed);
        when(inboxService.restore(claimed)).thenReturn(event);
        when(inboxService.isStale(claimed)).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void commitsBusinessCallbackBeforeCompletingInbox() {
        TenantContextHolder.setTenantId(99L);

        consumer.onFlowEvent(event);

        var ordered = inOrder(businessFlowService, inboxService);
        ordered.verify(businessFlowService).handleFlowEngineEvent(event);
        ordered.verify(inboxService).markCompleted(claimed);
        assertEquals(99L, TenantContextHolder.getTenantId());
    }

    @Test
    void exposesSingleDiscoverableFlowCallbackEntry() throws NoSuchMethodException {
        FlowBind bind = BusinessFlowEngineEventConsumer.class.getAnnotation(FlowBind.class);
        Method consumerMethod = BusinessFlowEngineEventConsumer.class
                .getDeclaredMethod("onFlowEvent", FlowEventContext.class);
        Method legacyMethod = BusinessFlowService.class
                .getDeclaredMethod("handleFlowEngineEvent", FlowEventContext.class);

        assertNotNull(bind);
        assertEquals("*", bind.modelKey());
        assertEquals("lowcode-business", bind.businessType());
        assertNotNull(consumerMethod.getAnnotation(FlowCallback.class));
        assertNull(BusinessFlowService.class.getAnnotation(FlowBind.class));
        assertNull(legacyMethod.getAnnotation(FlowCallback.class));
    }

    @Test
    void recordsFailureForLeaseRecovery() {
        RuntimeException failure = new IllegalStateException("local write failed");
        org.mockito.Mockito.doThrow(failure).when(businessFlowService).handleFlowEngineEvent(event);

        assertThrows(IllegalStateException.class, () -> consumer.onFlowEvent(event));

        verify(inboxService).markFailed(claimed, failure);
        verify(inboxService, never()).markCompleted(claimed);
    }

    @Test
    void skipsBusinessMutationForLateSequence() {
        when(inboxService.isStale(claimed)).thenReturn(true);

        consumer.onFlowEvent(event);

        verify(businessFlowService, never()).handleFlowEngineEvent(event);
        verify(inboxService).markCompleted(claimed);
    }

    @Test
    void legacyEventUsesCompatibilityPath() {
        FlowEventContext legacy = FlowEventContext.builder()
                .event("PROCESS_COMPLETED")
                .tenantId(1L)
                .processInstanceId("process-42")
                .build();

        consumer.onFlowEvent(legacy);

        verify(businessFlowService).handleFlowEngineEvent(legacy);
        verify(inboxService, never()).enqueue(any());
    }

    private AiBusinessFlowCallbackInbox inbox(Long sequence, String status, String owner) {
        AiBusinessFlowCallbackInbox inbox = new AiBusinessFlowCallbackInbox();
        inbox.setId(100L);
        inbox.setTenantId(1L);
        inbox.setEventId("event-10");
        inbox.setEventSequence(sequence);
        inbox.setConsumeStatus(status);
        inbox.setLockOwner(owner);
        return inbox;
    }
}
