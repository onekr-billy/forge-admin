package com.mdframe.forge.starter.flow.event;

import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowEventPublisherTest {

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, String, String> streamOperations;
    private FlowEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);
        publisher = new FlowEventPublisher(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesDurableStreamRecordBeforeCompatibilityBroadcast() {
        when(streamOperations.add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class)))
                .thenReturn(RecordId.of("1-0"));

        publisher.publish(message());

        ArgumentCaptor<MapRecord<String, String, String>> recordCaptor =
                ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(recordCaptor.capture(), any(RedisStreamCommands.XAddOptions.class));
        assertEquals(FlowEventPublisher.STREAM_KEY, recordCaptor.getValue().getStream());
        assertEquals("event-1", recordCaptor.getValue().getValue().get("eventId"));

        InOrder order = inOrder(streamOperations, redisTemplate);
        order.verify(streamOperations).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
        order.verify(redisTemplate).convertAndSend("flow:event:purchase-flow", recordCaptor.getValue()
                .getValue().get("payload"));
        order.verify(redisTemplate).convertAndSend("flow:event:all", recordCaptor.getValue()
                .getValue().get("payload"));
    }

    @Test
    void rejectsMessageWithoutDurableIdentityBeforeRedisWrite() {
        FlowEventMessage message = message();
        message.setEventId(null);

        assertThrows(IllegalArgumentException.class, () -> publisher.publish(message));

        verify(streamOperations, never()).add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class));
    }

    @Test
    void xaddFailureEscapesSoNotificationOutboxCanRetry() {
        when(streamOperations.add(any(MapRecord.class), any(RedisStreamCommands.XAddOptions.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> publisher.publish(message()));

        assertEquals("FLOW_EVENT_STREAM_PUBLISH_FAILED", failure.getMessage());
        verify(redisTemplate, never()).convertAndSend(any(), any());
    }

    private FlowEventMessage message() {
        return FlowEventMessage.builder()
                .eventId("event-1")
                .eventVersion(1)
                .eventSequence(15L)
                .eventType(FlowEventMessage.PROCESS_COMPLETED)
                .eventTime(LocalDateTime.now())
                .processInstanceId("process-42")
                .processDefKey("purchase-flow")
                .businessKey("purchase:42")
                .tenantId("7")
                .build();
    }
}
