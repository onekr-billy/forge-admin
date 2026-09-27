package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowCallbackInbox;
import com.mdframe.forge.plugin.generator.enums.BusinessFlowCallbackInboxStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowCallbackInboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowCallbackInboxServiceTest {

    private BusinessFlowCallbackInboxMapper mapper;
    private BusinessFlowCallbackInboxService service;
    private FlowEventContext event;

    @BeforeEach
    void setUp() {
        mapper = mock(BusinessFlowCallbackInboxMapper.class);
        service = new BusinessFlowCallbackInboxService(mapper);
        event = FlowEventContext.builder()
                .eventId("event-10")
                .eventVersion(1)
                .eventSequence(10L)
                .event("TASK_COMPLETED")
                .tenantId(1L)
                .processInstanceId("process-42")
                .businessKey("purchase:42")
                .taskId("task-7")
                .variables(Map.of("approved", true))
                .build();
    }

    @Test
    void persistsAndRestoresImmutableEvent() {
        when(mapper.insert(any(AiBusinessFlowCallbackInbox.class))).thenReturn(1);

        AiBusinessFlowCallbackInbox inbox = service.enqueue(event);
        FlowEventContext restored = service.restore(inbox);

        assertEquals(BusinessFlowCallbackInboxStatus.PENDING.getCode(), inbox.getConsumeStatus());
        assertEquals("event-10", restored.getEventId());
        assertEquals(10L, restored.getEventSequence());
        verify(mapper).insert(inbox);
    }

    @Test
    void rejectsTamperedEventSnapshot() {
        when(mapper.insert(any(AiBusinessFlowCallbackInbox.class))).thenReturn(1);
        AiBusinessFlowCallbackInbox inbox = service.enqueue(event);
        inbox.setEventDigest("0".repeat(64));

        assertThrows(RuntimeException.class, () -> service.restore(inbox));
    }

    @Test
    void fencesLateEventBehindCompletedHigherSequence() {
        when(mapper.insert(any(AiBusinessFlowCallbackInbox.class))).thenReturn(1);
        AiBusinessFlowCallbackInbox inbox = service.enqueue(event);
        when(mapper.selectLatestCompletedSequence(1L, inbox.getAggregateKey())).thenReturn(12L);

        assertTrue(service.isStale(inbox));
    }
}
