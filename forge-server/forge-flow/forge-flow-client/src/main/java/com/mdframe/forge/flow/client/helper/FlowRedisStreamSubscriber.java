package com.mdframe.forge.flow.client.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Redis Stream 流程事件消费者。
 * <p>
 * 每个应用使用独立 consumer group，同一应用的多个实例共享组；只有严格回调成功后才 XACK。
 * 回调失败或 ACK 结果未知时消息保留在 pending list，并由任一存活实例超时认领。
 */
@Slf4j
public class FlowRedisStreamSubscriber implements SmartLifecycle {

    public static final String DEFAULT_STREAM_KEY = "flow:event:stream";

    private static final String PAYLOAD_FIELD = "payload";
    private static final String EVENT_ID_FIELD = "eventId";

    private final StringRedisTemplate redisTemplate;
    private final FlowEventSubscriber eventSubscriber;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private final int batchSize;
    private final Duration pollInterval;
    private final Duration pendingIdleTimeout;
    private final Duration pendingRecoveryInterval;

    private volatile boolean running;
    private volatile boolean groupReady;
    private volatile long lastPendingRecoveryNanos;
    private ScheduledExecutorService executor;

    public FlowRedisStreamSubscriber(StringRedisTemplate redisTemplate,
                                     FlowEventSubscriber eventSubscriber,
                                     String streamKey,
                                     String groupName,
                                     String consumerName,
                                     int batchSize,
                                     Duration pollInterval,
                                     Duration pendingIdleTimeout,
                                     Duration pendingRecoveryInterval) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.eventSubscriber = Objects.requireNonNull(eventSubscriber, "eventSubscriber");
        this.streamKey = requireText(streamKey, "flow:event:stream");
        this.groupName = requireText(groupName, "forge-flow-client");
        this.consumerName = requireText(consumerName, "consumer");
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
        this.pollInterval = safeDuration(pollInterval, Duration.ofSeconds(1));
        this.pendingIdleTimeout = safeDuration(pendingIdleTimeout, Duration.ofSeconds(60));
        this.pendingRecoveryInterval = safeDuration(pendingRecoveryInterval, Duration.ofSeconds(30));
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        lastPendingRecoveryNanos = 0;
        executor = Executors.newSingleThreadScheduledExecutor(threadFactory());
        executor.scheduleWithFixedDelay(
                this::runCycleSafely,
                0,
                Math.max(100, pollInterval.toMillis()),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        running = false;
        groupReady = false;
        lastPendingRecoveryNanos = 0;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    void runCycleSafely() {
        if (!running) {
            return;
        }
        try {
            ensureConsumerGroup();
            recoverPendingIfDue();
            pollNewMessages();
        } catch (RuntimeException failure) {
            groupReady = false;
            log.warn("[FlowCallback] Redis Stream 消费周期失败: stream={}, group={}, failureType={}",
                    streamKey, groupName, failure.getClass().getSimpleName());
        }
    }

    void ensureConsumerGroup() {
        if (groupReady) {
            return;
        }
        byte[] rawStreamKey = redisTemplate.getStringSerializer().serialize(streamKey);
        if (rawStreamKey == null) {
            throw new IllegalStateException("FLOW_EVENT_STREAM_KEY_INVALID");
        }
        try {
            redisTemplate.execute((RedisCallback<String>) connection ->
                    connection.streamCommands().xGroupCreate(
                            rawStreamKey, groupName, ReadOffset.from("0-0"), true));
        } catch (DataAccessException failure) {
            if (!isBusyGroup(failure)) {
                throw failure;
            }
        }
        groupReady = true;
    }

    @SuppressWarnings("unchecked")
    void pollNewMessages() {
        StreamOperations<String, String, String> streamOperations = redisTemplate.opsForStream();
        List<MapRecord<String, String, String>> records = streamOperations.read(
                Consumer.from(groupName, consumerName),
                StreamReadOptions.empty().count(batchSize),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
        processRecords(records, streamOperations);
    }

    void recoverPendingMessages() {
        StreamOperations<String, String, String> streamOperations = redisTemplate.opsForStream();
        PendingMessages pendingMessages = streamOperations.pending(
                streamKey, groupName, Range.unbounded(), batchSize);
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }
        List<RecordId> claimable = new ArrayList<>();
        for (PendingMessage pendingMessage : pendingMessages) {
            if (pendingMessage.getElapsedTimeSinceLastDelivery().compareTo(pendingIdleTimeout) >= 0) {
                claimable.add(pendingMessage.getId());
            }
        }
        if (claimable.isEmpty()) {
            return;
        }
        List<MapRecord<String, String, String>> records = streamOperations.claim(
                streamKey, groupName, consumerName, pendingIdleTimeout,
                claimable.toArray(RecordId[]::new));
        processRecords(records, streamOperations);
    }

    private void recoverPendingIfDue() {
        long now = System.nanoTime();
        long elapsed = now - lastPendingRecoveryNanos;
        if (lastPendingRecoveryNanos != 0
                && elapsed < pendingRecoveryInterval.toNanos()) {
            return;
        }
        lastPendingRecoveryNanos = now;
        recoverPendingMessages();
    }

    private void processRecords(List<MapRecord<String, String, String>> records,
                                StreamOperations<String, String, String> streamOperations) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MapRecord<String, String, String> record : records) {
            processRecord(record, streamOperations);
        }
    }

    private void processRecord(MapRecord<String, String, String> record,
                               StreamOperations<String, String, String> streamOperations) {
        Map<String, String> fields = record.getValue();
        String payload = fields == null ? null : fields.get(PAYLOAD_FIELD);
        String eventId = fields == null ? null : fields.get(EVENT_ID_FIELD);
        if (payload == null || payload.isBlank()) {
            log.warn("[FlowCallback] Redis Stream 消息缺少载荷，保留 pending: recordId={}, eventId={}",
                    record.getId(), eventId);
            return;
        }
        try {
            eventSubscriber.onMessageReliable(payload);
            Long acknowledged = streamOperations.acknowledge(streamKey, groupName, record.getId());
            if (acknowledged == null || acknowledged != 1L) {
                log.warn("[FlowCallback] Redis Stream ACK 未确认，保留 pending: recordId={}, eventId={}",
                        record.getId(), eventId);
            }
        } catch (RuntimeException failure) {
            log.warn("[FlowCallback] Redis Stream 消费失败，保留 pending: recordId={}, eventId={}, failureType={}",
                    record.getId(), eventId, failure.getClass().getSimpleName());
        }
    }

    private boolean isBusyGroup(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ThreadFactory threadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "flow-stream-" + consumerName);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Duration safeDuration(Duration duration, Duration fallback) {
        return duration == null || duration.isNegative() || duration.isZero() ? fallback : duration;
    }

    private static String requireText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
