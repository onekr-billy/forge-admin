package com.mdframe.forge.starter.cache.managed.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.mdframe.forge.starter.cache.managed.model.CacheLookup;
import com.mdframe.forge.starter.cache.managed.model.ManagedCacheInvalidationMessage;
import com.mdframe.forge.starter.cache.managed.model.ManagedCacheValue;
import org.redisson.api.RMapCache;
import org.redisson.api.RTopic;
import org.redisson.api.listener.StatusListener;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MultiLevelCacheHandle implements ManagedCacheHandle {

    private final RMapCache<String, ManagedCacheValue> remoteCache;
    private final RTopic invalidationTopic;
    private final Cache<String, ManagedCacheValue> localCache;
    private final String sourceId;
    private final int invalidationListenerId;
    private final int statusListenerId;
    private final AtomicBoolean closed = new AtomicBoolean();

    public MultiLevelCacheHandle(RMapCache<String, ManagedCacheValue> remoteCache,
                                 RTopic invalidationTopic,
                                 int maximumSize) {
        this(remoteCache, invalidationTopic, maximumSize, Ticker.systemTicker(), UUID.randomUUID().toString());
    }

    MultiLevelCacheHandle(RMapCache<String, ManagedCacheValue> remoteCache,
                          RTopic invalidationTopic,
                          int maximumSize,
                          Ticker ticker,
                          String sourceId) {
        this.remoteCache = remoteCache;
        this.invalidationTopic = invalidationTopic;
        this.sourceId = sourceId;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(ticker)
                .expireAfter(new Expiry<String, ManagedCacheValue>() {
                    @Override
                    public long expireAfterCreate(String key, ManagedCacheValue value, long currentTime) {
                        return value.localTtlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, ManagedCacheValue value,
                                                  long currentTime, long currentDuration) {
                        return value.localTtlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, ManagedCacheValue value,
                                                long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
        this.invalidationListenerId = invalidationTopic.addListener(
                ManagedCacheInvalidationMessage.class,
                (channel, message) -> onInvalidation(message));
        this.statusListenerId = invalidationTopic.addListener(new StatusListener() {
            @Override
            public void onSubscribe(String channel) {
                localCache.invalidateAll();
            }

            @Override
            public void onUnsubscribe(String channel) {
                // 重新订阅时统一清空，断开期间继续由当前 L1 TTL 约束陈旧窗口。
            }
        });
    }

    @Override
    public CacheLookup get(String key) {
        ManagedCacheValue localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            return CacheLookup.hit(localValue.value());
        }
        ManagedCacheValue value = remoteCache.get(key);
        if (value == null) {
            return CacheLookup.miss();
        }
        localCache.put(key, value);
        return CacheLookup.hit(value.value());
    }

    @Override
    public void put(String key, ManagedCacheValue value, long ttlSeconds) {
        remoteCache.fastPut(key, value, ttlSeconds, TimeUnit.SECONDS);
        localCache.put(key, value);
        invalidationTopic.publish(new ManagedCacheInvalidationMessage(sourceId, key));
    }

    @Override
    public void evict(String key) {
        remoteCache.fastRemove(key);
        localCache.invalidate(key);
        invalidationTopic.publish(new ManagedCacheInvalidationMessage(sourceId, key));
    }

    @Override
    public void clear() {
        remoteCache.clear();
        localCache.invalidateAll();
        invalidationTopic.publish(new ManagedCacheInvalidationMessage(sourceId, null));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        localCache.invalidateAll();
        invalidationTopic.removeListener(invalidationListenerId, statusListenerId);
    }

    private void onInvalidation(ManagedCacheInvalidationMessage message) {
        if (message == null || sourceId.equals(message.sourceId())) {
            return;
        }
        if (message.key() == null) {
            localCache.invalidateAll();
        } else {
            localCache.invalidate(message.key());
        }
    }
}
