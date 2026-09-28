package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessEventOutbox;
import com.mdframe.forge.plugin.generator.enums.BusinessEventOutboxStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessEventOutboxMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessEventOutboxServiceTest {

    @Test
    void appendAssignsAggregateSequenceAndPersistsTrustedSnapshot() {
        BusinessEventOutboxMapper mapper = mock(BusinessEventOutboxMapper.class);
        BusinessEventOutboxService service = service(mapper);
        BusinessEvent event = event("stable-1");
        when(mapper.advanceAggregateSequence(eq(7L), any(), eq("purchase_order"), eq("1001"), any()))
                .thenReturn(1);
        when(mapper.selectAggregateSequence(eq(7L), any())).thenReturn(3L);
        doAnswer(invocation -> 1).when(mapper).insert(any(AiBusinessEventOutbox.class));

        AiBusinessEventOutbox outbox = service.append(event);
        BusinessEvent restored = service.restore(outbox);

        assertNotNull(outbox.getId());
        assertEquals(3L, outbox.getAggregateSequence());
        assertEquals(3L, restored.getAggregateSequence());
        assertEquals(BusinessEventOutboxStatus.PENDING.getCode(), outbox.getDeliveryStatus());
        assertTrue(BusinessEventEnvelope.isTrusted(restored));
        verify(mapper).advanceAggregateSequence(eq(7L), eq(outbox.getAggregateKey()),
                eq("purchase_order"), eq("1001"), any(LocalDateTime.class));
    }

    @Test
    void duplicateStableEventReturnsExistingRowWithoutAllocatingAnotherSequence() {
        BusinessEventOutboxMapper mapper = mock(BusinessEventOutboxMapper.class);
        BusinessEventOutboxService service = service(mapper);
        BusinessEvent event = event("stable-2");
        AiBusinessEventOutbox existing = new AiBusinessEventOutbox();
        existing.setId(9L);
        existing.setSourceDigest(BusinessEventEnvelope.logicalDigest(event));
        when(mapper.selectByEventId(7L, event.getEventId())).thenReturn(existing);

        assertEquals(existing, service.append(event));

        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .advanceAggregateSequence(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateEventIdWithDifferentPayloadIsRejected() {
        BusinessEventOutboxMapper mapper = mock(BusinessEventOutboxMapper.class);
        BusinessEventOutboxService service = service(mapper);
        BusinessEvent event = event("stable-3");
        AiBusinessEventOutbox existing = new AiBusinessEventOutbox();
        existing.setSourceDigest("different");
        when(mapper.selectByEventId(7L, event.getEventId())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.append(event));
    }

    @Test
    void restoreRejectsTamperedSnapshot() {
        BusinessEventOutboxMapper mapper = mock(BusinessEventOutboxMapper.class);
        BusinessEventOutboxService service = service(mapper);
        when(mapper.advanceAggregateSequence(eq(7L), any(), eq("purchase_order"), eq("1001"), any()))
                .thenReturn(1);
        when(mapper.selectAggregateSequence(eq(7L), any())).thenReturn(4L);
        when(mapper.insert(any(AiBusinessEventOutbox.class))).thenReturn(1);
        AiBusinessEventOutbox outbox = service.append(event("stable-4"));
        outbox.setEventPayload(outbox.getEventPayload().replace("DRAFT", "APPROVED"));

        assertThrows(BusinessException.class, () -> service.restore(outbox));
    }

    @Test
    void zeroRowOutboxInsertFailsClosedSoCallerTransactionCanRollback() {
        BusinessEventOutboxMapper mapper = mock(BusinessEventOutboxMapper.class);
        BusinessEventOutboxService service = service(mapper);
        when(mapper.advanceAggregateSequence(eq(7L), any(), eq("purchase_order"), eq("1001"), any()))
                .thenReturn(1);
        when(mapper.selectAggregateSequence(eq(7L), any())).thenReturn(5L);
        when(mapper.insert(any(AiBusinessEventOutbox.class))).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.append(event("stable-5")));
    }

    @Test
    void exhaustedFailureMovesToDeadWithoutPersistingSensitiveMessage() {
        BusinessEventOutboxMapper mapper = mock(BusinessEventOutboxMapper.class);
        BusinessEventOutboxService service = service(mapper);
        ReflectionTestUtils.setField(service, "maxRetryCount", 1);
        AiBusinessEventOutbox claimed = new AiBusinessEventOutbox();
        claimed.setId(11L);
        claimed.setTenantId(7L);
        claimed.setRetryCount(1);
        claimed.setDeliveryStatus(BusinessEventOutboxStatus.PROCESSING.getCode());
        claimed.setLockOwner("worker");
        when(mapper.markFailed(eq(claimed), eq(BusinessEventOutboxStatus.DEAD.getCode()),
                eq(null), eq("IllegalStateException"), any())).thenReturn(1);

        service.markFailed(claimed, new IllegalStateException("secret-payload"));

        verify(mapper).markFailed(eq(claimed), eq(BusinessEventOutboxStatus.DEAD.getCode()),
                eq(null), eq("IllegalStateException"), any());
    }

    @Test
    void expiredFinalLeaseIsConvertedToDeadAndNotRedelivered() {
        BusinessEventOutboxMapper mapper = mock(BusinessEventOutboxMapper.class);
        BusinessEventOutboxService service = service(mapper);
        AiBusinessEventOutbox candidate = new AiBusinessEventOutbox();
        candidate.setId(12L);
        candidate.setTenantId(7L);
        candidate.setRetryCount(8);
        candidate.setDeliveryStatus(BusinessEventOutboxStatus.PROCESSING.getCode());

        assertEquals(null, service.claim(candidate, "worker", LocalDateTime.now()));

        verify(mapper).expireExhaustedLease(eq(7L), eq(12L), any(), any(), eq(8));
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .claimDelivery(any(), any(), any(), any(), any(), anyInt());
    }

    private BusinessEventOutboxService service(BusinessEventOutboxMapper mapper) {
        return new BusinessEventOutboxService(mapper);
    }

    private BusinessEvent event(String stableKey) {
        return BusinessEventEnvelope.stamp(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_UPDATED)
                .suiteCode("PURCHASE")
                .objectCode("purchase_order")
                .configKey("purchase-order")
                .recordId("1001")
                .recordData(Map.of("status", "DRAFT"))
                .operatorId(8L)
                .tenantId(7L)
                .build(), BusinessEventEnvelope.SOURCE_DYNAMIC_CRUD, stableKey);
    }
}
