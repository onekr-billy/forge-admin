package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessEventOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessEventOutboxDispatcherTest {

    @Test
    void idlePollingShouldDefaultToFiveSeconds() throws NoSuchMethodException {
        Scheduled scheduled = BusinessEventOutboxDispatcher.class
                .getDeclaredMethod("dispatch")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${forge.business.event-outbox.scan-interval-ms:5000}");
    }

    @Test
    void claimedEventIsVerifiedDeliveredAndCompleted() {
        BusinessEventOutboxService outboxService = mock(BusinessEventOutboxService.class);
        BusinessEventDeliveryService deliveryService = mock(BusinessEventDeliveryService.class);
        BusinessEventOutboxDispatcher dispatcher = new BusinessEventOutboxDispatcher(outboxService, deliveryService);
        AiBusinessEventOutbox candidate = outbox(21L);
        AiBusinessEventOutbox claimed = outbox(21L);
        BusinessEvent event = mock(BusinessEvent.class);
        when(outboxService.claim(org.mockito.ArgumentMatchers.eq(candidate),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(claimed);
        when(outboxService.restore(claimed)).thenReturn(event);

        dispatcher.dispatch(candidate);

        verify(deliveryService).deliver(event);
        verify(outboxService).markDelivered(claimed);
    }

    @Test
    void deliveryFailureIsPersistedForLeaseRecovery() {
        BusinessEventOutboxService outboxService = mock(BusinessEventOutboxService.class);
        BusinessEventDeliveryService deliveryService = mock(BusinessEventDeliveryService.class);
        BusinessEventOutboxDispatcher dispatcher = new BusinessEventOutboxDispatcher(outboxService, deliveryService);
        AiBusinessEventOutbox candidate = outbox(22L);
        AiBusinessEventOutbox claimed = outbox(22L);
        BusinessEvent event = mock(BusinessEvent.class);
        when(outboxService.claim(org.mockito.ArgumentMatchers.eq(candidate),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(claimed);
        when(outboxService.restore(claimed)).thenReturn(event);
        IllegalStateException failure = new IllegalStateException("sensitive");
        doThrow(failure).when(deliveryService).deliver(event);

        dispatcher.dispatch(candidate);

        verify(outboxService).markFailed(claimed, failure);
    }

    private AiBusinessEventOutbox outbox(Long id) {
        AiBusinessEventOutbox outbox = new AiBusinessEventOutbox();
        outbox.setId(id);
        outbox.setTenantId(7L);
        outbox.setEventId("event-" + id);
        return outbox;
    }
}
