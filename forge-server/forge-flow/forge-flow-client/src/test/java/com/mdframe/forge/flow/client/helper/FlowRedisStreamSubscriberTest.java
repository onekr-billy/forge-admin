package com.mdframe.forge.flow.client.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowRedisStreamSubscriberTest {

    private static final String STREAM = "flow:event:stream";
    private static final String GROUP = "admin-service";

    private StringRedisTemplate redisTemplate;
    private StreamOperations<String, String, String> streamOperations;
    private FlowEventSubscriber eventSubscriber;
    private FlowRedisStreamSubscriber subscriber;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        eventSubscriber = mock(FlowEventSubscriber.class);
        when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);
        subscriber = new FlowRedisStreamSubscriber(
                redisTemplate,
                eventSubscriber,
                STREAM,
                GROUP,
                "admin-1",
                100,
                Duration.ofSeconds(1),
                Duration.ofSeconds(60),
                Duration.ofSeconds(30));
    }

    @Test
    void acknowledgesOnlyAfterReliableCallbackReturns() {
        MapRecord<String, String, String> record = record("1-0", "event-1", "{\"event\":\"ok\"}");
        when(streamOperations.read(
                eq(Consumer.from(GROUP, "admin-1")),
                any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of(record));
        when(streamOperations.acknowledge(STREAM, GROUP, record.getId())).thenReturn(1L);

        subscriber.pollNewMessages();

        InOrder order = inOrder(eventSubscriber, streamOperations);
        order.verify(eventSubscriber).onMessageReliable("{\"event\":\"ok\"}");
        order.verify(streamOperations).acknowledge(STREAM, GROUP, record.getId());
    }

    @Test
    void callbackFailureLeavesMessagePendingWithoutAck() {
        MapRecord<String, String, String> record = record("2-0", "event-2", "{\"event\":\"fail\"}");
        when(streamOperations.read(
                eq(Consumer.from(GROUP, "admin-1")),
                any(StreamReadOptions.class),
                any(StreamOffset.class))).thenReturn(List.of(record));
        doThrow(new IllegalStateException("database unavailable"))
                .when(eventSubscriber).onMessageReliable("{\"event\":\"fail\"}");

        subscriber.pollNewMessages();

        verify(streamOperations, never()).acknowledge(eq(STREAM), eq(GROUP), any(RecordId.class));
    }

    @Test
    void claimsExpiredPendingMessageAndAcknowledgesAfterReplay() {
        RecordId recordId = RecordId.of("3-0");
        PendingMessage pendingMessage = new PendingMessage(
                recordId, Consumer.from(GROUP, "dead-consumer"), Duration.ofMinutes(2), 1);
        PendingMessages pendingMessages = new PendingMessages(GROUP, List.of(pendingMessage));
        MapRecord<String, String, String> record = record("3-0", "event-3", "{\"event\":\"recover\"}");
        when(streamOperations.pending(eq(STREAM), eq(GROUP), any(Range.class), eq(100L)))
                .thenReturn(pendingMessages);
        when(streamOperations.claim(STREAM, GROUP, "admin-1", Duration.ofSeconds(60), recordId))
                .thenReturn(List.of(record));
        when(streamOperations.acknowledge(STREAM, GROUP, recordId)).thenReturn(1L);

        subscriber.recoverPendingMessages();

        verify(streamOperations).claim(
                STREAM, GROUP, "admin-1", Duration.ofSeconds(60), recordId);
        verify(eventSubscriber).onMessageReliable("{\"event\":\"recover\"}");
        verify(streamOperations).acknowledge(STREAM, GROUP, recordId);
    }

    private MapRecord<String, String, String> record(String id, String eventId, String payload) {
        return MapRecord.create(STREAM, Map.of("eventId", eventId, "payload", payload))
                .withId(RecordId.of(id));
    }
}
