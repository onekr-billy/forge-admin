package com.mdframe.forge.starter.cache.managed.store;

import com.mdframe.forge.starter.cache.managed.model.ManagedCacheInvalidationMessage;
import com.mdframe.forge.starter.cache.managed.model.ManagedCacheValue;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.StatusListener;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MultiLevelCacheHandleTest {

    @Test
    void shouldApplyIndependentLocalTtlForRegularAndNullValues() {
        AtomicLong now = new AtomicLong();
        RemoteStore remote = new RemoteStore();
        TopicBroker topic = new TopicBroker();
        ManagedCacheValue regular = value("value", 60);
        ManagedCacheValue nullValue = value(null, 10);
        remote.values.put("regular", regular);
        remote.values.put("null", nullValue);

        MultiLevelCacheHandle handle = new MultiLevelCacheHandle(
                remote.cache(), topic.topic(), 100, now::get, "instance-one");
        assertThat(handle.get("regular").value()).isEqualTo("value");
        assertThat(handle.get("null").hit()).isTrue();
        assertThat(remote.readCount).hasValue(2);

        now.set(TimeUnit.SECONDS.toNanos(11));

        assertThat(handle.get("regular").value()).isEqualTo("value");
        assertThat(handle.get("null").hit()).isTrue();
        assertThat(remote.readCount).hasValue(3);
    }

    @Test
    void shouldSynchronizeKeyAndFullInvalidationAcrossInstances() {
        RemoteStore remote = new RemoteStore();
        TopicBroker topic = new TopicBroker();
        MultiLevelCacheHandle first = handle(remote, topic, "instance-one");
        MultiLevelCacheHandle second = handle(remote, topic, "instance-two");

        first.put("key", value("v1", 60), 300);
        assertThat(remote.lastTtlSeconds).hasValue(300);
        assertThat(first.get("key").value()).isEqualTo("v1");
        assertThat(remote.readCount).hasValue(0);
        assertThat(second.get("key").value()).isEqualTo("v1");
        assertThat(remote.readCount).hasValue(1);

        first.put("key", value("v2", 60), 300);
        assertThat(second.get("key").value()).isEqualTo("v2");
        assertThat(remote.readCount).hasValue(2);

        first.evict("key");
        assertThat(second.get("key").hit()).isFalse();
        assertThat(remote.readCount).hasValue(3);

        first.put("key", value("v3", 60), 300);
        assertThat(second.get("key").value()).isEqualTo("v3");
        first.clear();
        assertThat(second.get("key").hit()).isFalse();
    }

    @Test
    void shouldClearLocalCacheWhenTopicSubscribesAgain() {
        RemoteStore remote = new RemoteStore();
        TopicBroker topic = new TopicBroker();
        MultiLevelCacheHandle handle = handle(remote, topic, "instance-one");
        remote.values.put("key", value("v1", 60));

        assertThat(handle.get("key").value()).isEqualTo("v1");
        remote.values.put("key", value("v2", 60));
        assertThat(handle.get("key").value()).isEqualTo("v1");

        topic.resubscribe();

        assertThat(handle.get("key").value()).isEqualTo("v2");
    }

    private MultiLevelCacheHandle handle(RemoteStore remote, TopicBroker topic, String sourceId) {
        return new MultiLevelCacheHandle(
                remote.cache(), topic.topic(), 100, com.github.benmanes.caffeine.cache.Ticker.systemTicker(), sourceId);
    }

    private ManagedCacheValue value(Object value, long localTtlSeconds) {
        return new ManagedCacheValue(
                value, value == null, TimeUnit.SECONDS.toNanos(localTtlSeconds));
    }

    private static final class RemoteStore {
        private final Map<String, ManagedCacheValue> values = new LinkedHashMap<>();
        private final AtomicInteger readCount = new AtomicInteger();
        private final AtomicLong lastTtlSeconds = new AtomicLong();

        @SuppressWarnings("unchecked")
        private RMapCache<String, ManagedCacheValue> cache() {
            return (RMapCache<String, ManagedCacheValue>) Proxy.newProxyInstance(
                    RMapCache.class.getClassLoader(),
                    new Class<?>[]{RMapCache.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "get" -> {
                            readCount.incrementAndGet();
                            yield values.get(args[0]);
                        }
                        case "fastPut" -> {
                            lastTtlSeconds.set(TimeUnit.SECONDS.convert(
                                    ((Number) args[2]).longValue(), (TimeUnit) args[3]));
                            yield values.put((String) args[0], (ManagedCacheValue) args[1]) == null;
                        }
                        case "fastRemove" -> removeKeys((Object[]) args[0]);
                        case "clear" -> {
                            values.clear();
                            yield null;
                        }
                        case "toString" -> "multi-level-remote-test";
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private long removeKeys(Object[] keys) {
            long removed = 0;
            for (Object key : keys) {
                if (values.remove(key) != null) {
                    removed++;
                }
            }
            return removed;
        }
    }

    private static final class TopicBroker {
        private final AtomicInteger sequence = new AtomicInteger();
        private final Map<Integer, MessageListener<ManagedCacheInvalidationMessage>> messageListeners =
                new LinkedHashMap<>();
        private final Map<Integer, StatusListener> statusListeners = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        private RTopic topic() {
            return (RTopic) Proxy.newProxyInstance(
                    RTopic.class.getClassLoader(),
                    new Class<?>[]{RTopic.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "addListener" -> addListener(args);
                        case "publish" -> publish((ManagedCacheInvalidationMessage) args[0]);
                        case "removeListener" -> {
                            removeListeners((Integer[]) args[0]);
                            yield null;
                        }
                        case "toString" -> "multi-level-topic-test";
                        default -> defaultValue(method.getReturnType());
                    });
        }

        @SuppressWarnings("unchecked")
        private int addListener(Object[] args) {
            int listenerId = sequence.incrementAndGet();
            if (args[0] instanceof Class<?>) {
                messageListeners.put(listenerId,
                        (MessageListener<ManagedCacheInvalidationMessage>) args[1]);
            } else {
                StatusListener listener = (StatusListener) args[0];
                statusListeners.put(listenerId, listener);
                listener.onSubscribe("test-channel");
            }
            return listenerId;
        }

        private long publish(ManagedCacheInvalidationMessage message) {
            messageListeners.values().forEach(listener -> listener.onMessage("test-channel", message));
            return messageListeners.size();
        }

        private void resubscribe() {
            statusListeners.values().forEach(listener -> listener.onSubscribe("test-channel"));
        }

        private void removeListeners(Integer[] listenerIds) {
            for (Integer listenerId : listenerIds) {
                messageListeners.remove(listenerId);
                statusListeners.remove(listenerId);
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }
}
