package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serializes starts for one tenant and business record through transaction completion. */
@Slf4j
final class BusinessFlowStartLockManager {

    private static final String LOCK_PREFIX = "forge:business-flow:start:";
    private static final long LOCK_WAIT_SECONDS = 5L;

    private final Map<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    <T> T execute(Long tenantId, String businessKey,
                  ObjectProvider<RedissonClient> redissonClientProvider, Supplier<T> action) {
        LockHandle handle = acquire(lockKey(tenantId, businessKey), redissonClientProvider);
        boolean unlockInFinally = true;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        release(handle);
                    }
                });
                unlockInFinally = false;
            }
            return action.get();
        } finally {
            if (unlockInFinally) {
                release(handle);
            }
        }
    }

    private LockHandle acquire(String key, ObjectProvider<RedissonClient> redissonClientProvider) {
        RedissonClient client = redissonClientProvider.getIfAvailable();
        if (client != null) {
            RLock lock = client.getLock(key);
            try {
                if (!lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new BusinessException("流程正在发起，请勿重复提交");
                }
                return new LockHandle(key, lock, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("流程发起锁等待被中断，请稍后重试");
            }
        }

        ReentrantLock local = localLocks.computeIfAbsent(key, ignored -> new ReentrantLock());
        try {
            if (!local.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException("流程正在发起，请勿重复提交");
            }
            return new LockHandle(key, null, local);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("流程发起锁等待被中断，请稍后重试");
        }
    }

    private void release(LockHandle handle) {
        if (handle.redissonLock() != null) {
            try {
                if (handle.redissonLock().isHeldByCurrentThread()) {
                    handle.redissonLock().unlock();
                }
            } catch (Exception e) {
                log.warn("[低代码流程启动] 释放流程发起分布式锁失败: lockKey={}, error={}",
                        handle.key(), e.getMessage());
            }
            return;
        }

        ReentrantLock local = handle.localLock();
        if (local.isHeldByCurrentThread()) {
            local.unlock();
            if (!local.isLocked() && !local.hasQueuedThreads()) {
                localLocks.remove(handle.key(), local);
            }
        }
    }

    private String lockKey(Long tenantId, String businessKey) {
        return LOCK_PREFIX + safeToken(tenantId) + ":" + safeToken(businessKey);
    }

    private String safeToken(Object value) {
        return value == null ? "null" : String.valueOf(value).replaceAll("[^A-Za-z0-9:_-]", "_");
    }

    private record LockHandle(String key, RLock redissonLock, ReentrantLock localLock) {
    }
}
