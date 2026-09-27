package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowStartLockManagerTest {

    private final BusinessFlowStartLockManager manager = new BusinessFlowStartLockManager();
    private final ObjectProvider<RedissonClient> noRedisson =
            new StaticListableBeanFactory().getBeanProvider(RedissonClient.class);

    @Test
    void keepsTheStartLockUntilTheTransactionCompletes() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals("first", manager.execute(1L, "order:42", noRedisson, () -> "first"));
            CountDownLatch competitorStarted = new CountDownLatch(1);
            Future<String> competitor = executor.submit(() -> {
                competitorStarted.countDown();
                return manager.execute(1L, "order:42", noRedisson, () -> "second");
            });
            assertTrue(competitorStarted.await(1, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> competitor.get(100, TimeUnit.MILLISECONDS));

            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertEquals("second", competitor.get(2, TimeUnit.SECONDS));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            executor.shutdownNow();
        }
    }

    @Test
    void releasesTheLockWhenTheActionFailsWithoutATransaction() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.execute(1L, "order:42", noRedisson, () -> {
                    throw new IllegalStateException("failed");
                }));
        assertEquals("failed", error.getMessage());
        assertEquals("retry", manager.execute(1L, "order:42", noRedisson, () -> "retry"));
    }

    @Test
    void prefersTheDistributedLockWhenRedissonIsAvailable() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("forge:business-flow:start:1:order:42")).thenReturn(lock);
        when(lock.tryLock(5L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("redissonClient", client);

        assertEquals("started", manager.execute(1L, "order:42",
                factory.getBeanProvider(RedissonClient.class), () -> "started"));
        verify(lock).unlock();
    }
}
