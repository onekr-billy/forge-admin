package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import com.mdframe.forge.starter.flow.enums.FlowNotifyOutboxStatus;
import com.mdframe.forge.starter.flow.event.FlowTaskNotifyEvent;
import com.mdframe.forge.starter.flow.mapper.FlowNotifyOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowNotifyOutboxServiceImplTest {

    @Test
    void appendAndDeserializePreserveStableEnvelopeAndSequence() {
        FlowNotifyOutboxMapper mapper = mock(FlowNotifyOutboxMapper.class);
        doAnswer(invocation -> {
            FlowNotifyOutbox record = invocation.getArgument(0);
            record.setId(42L);
            return 1;
        }).when(mapper).insert(any(FlowNotifyOutbox.class));
        FlowNotifyOutboxServiceImpl service = service(mapper);
        FlowTaskNotifyEvent source = FlowTaskNotifyEvent.processResult(business(7L), Map.of("approved", true), false);

        FlowNotifyOutbox saved = service.append(source);
        FlowTaskNotifyEvent restored = service.deserialize(saved);

        assertEquals(42L, saved.getId());
        assertEquals(source.getEventId(), restored.getEventId());
        assertEquals(FlowTaskNotifyEvent.CURRENT_VERSION, restored.getEventVersion());
        assertEquals(7L, restored.getTenantId());
        assertEquals(42L, saved.getId());
        assertEquals(64, saved.getPayloadHash().length());
        assertEquals(FlowNotifyOutboxStatus.PENDING.getCode(), saved.getDeliveryStatus());
    }

    @Test
    void duplicateEventRequiresTheSamePayloadHash() {
        FlowNotifyOutboxMapper mapper = mock(FlowNotifyOutboxMapper.class);
        FlowNotifyOutboxServiceImpl service = service(mapper);
        FlowTaskNotifyEvent event = FlowTaskNotifyEvent.processResult(business(9L), Map.of(), false);
        when(mapper.insert(any(FlowNotifyOutbox.class))).thenThrow(new DuplicateKeyException("duplicate"));

        FlowNotifyOutbox conflicting = new FlowNotifyOutbox();
        conflicting.setPayloadHash("different");
        when(mapper.selectByEventId(9L, event.getEventId())).thenReturn(conflicting);

        assertThrows(IllegalStateException.class, () -> service.append(event));
    }

    @Test
    void eventPublishRestoresOutboxIdentityIntoDownstreamEnvelope() {
        FlowNotifyOutboxMapper mapper = mock(FlowNotifyOutboxMapper.class);
        doAnswer(invocation -> {
            FlowNotifyOutbox record = invocation.getArgument(0);
            record.setId(77L);
            return 1;
        }).when(mapper).insert(any(FlowNotifyOutbox.class));
        FlowNotifyOutboxServiceImpl service = service(mapper);
        FlowEventMessage message = FlowEventMessage.builder()
                .eventType(FlowEventMessage.PROCESS_COMPLETED)
                .processInstanceId("process-1")
                .processDefKey("leave")
                .tenantId("6")
                .build();
        FlowTaskNotifyEvent source = FlowTaskNotifyEvent.eventPublish(message, "leave");

        FlowTaskNotifyEvent restored = service.deserialize(service.append(source));

        assertEquals(source.getEventId(), restored.getEventMessage().getEventId());
        assertEquals(FlowTaskNotifyEvent.CURRENT_VERSION, restored.getEventMessage().getEventVersion());
        assertEquals(77L, restored.getEventMessage().getEventSequence());
    }

    @Test
    void failedDeliveryUsesBackoffThenMovesToDeadLetterAtAttemptLimit() {
        FlowNotifyOutboxMapper mapper = mock(FlowNotifyOutboxMapper.class);
        FlowNotifyOutboxServiceImpl service = service(mapper);
        when(mapper.markFailed(anyLong(), anyLong(), any(), anyInt(), any(), any(), any())).thenReturn(1);
        FlowNotifyOutbox outbox = new FlowNotifyOutbox();
        outbox.setId(11L);
        outbox.setTenantId(3L);
        outbox.setRetryCount(2);
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 5, 0);

        service.markFailed(outbox, "worker", new IllegalStateException("secret"),
                now, 5, Duration.ofSeconds(30));

        ArgumentCaptor<Integer> status = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<LocalDateTime> nextRetry = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> failureType = ArgumentCaptor.forClass(String.class);
        verify(mapper).markFailed(eq(3L), eq(11L), eq("worker"), status.capture(),
                nextRetry.capture(), failureType.capture(), eq(now));
        assertEquals(FlowNotifyOutboxStatus.FAILED.getCode(), status.getValue());
        assertEquals(now.plusSeconds(60), nextRetry.getValue());
        assertEquals("IllegalStateException", failureType.getValue());

        outbox.setRetryCount(5);
        service.markFailed(outbox, "worker", new IllegalArgumentException("hidden"),
                now, 5, Duration.ofSeconds(30));
        assertNotNull(outbox.getId());
        verify(mapper).markFailed(3L, 11L, "worker", FlowNotifyOutboxStatus.DEAD.getCode(),
                null, "IllegalArgumentException", now);
    }

    private FlowNotifyOutboxServiceImpl service(FlowNotifyOutboxMapper mapper) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new FlowNotifyOutboxServiceImpl(mapper, objectMapper);
    }

    private FlowBusiness business(Long tenantId) {
        FlowBusiness business = new FlowBusiness();
        business.setTenantId(tenantId);
        business.setProcessInstanceId("process-1");
        business.setBusinessKey("business-1");
        return business;
    }
}
