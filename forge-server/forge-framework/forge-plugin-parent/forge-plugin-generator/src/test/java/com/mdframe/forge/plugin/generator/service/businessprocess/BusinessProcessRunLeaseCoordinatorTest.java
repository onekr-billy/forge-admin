package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessProcessRunLeaseCoordinatorTest {

    private BusinessProcessRunLeaseCoordinator coordinator;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        if (coordinator != null) {
            coordinator.destroy();
        }
    }

    @Test
    void heartbeatBindsTenantAndRestoresWorkerContext() {
        AtomicReference<Long> observedTenantId = new AtomicReference<>();
        BusinessProcessRunMapper mapper = (BusinessProcessRunMapper) Proxy.newProxyInstance(
                BusinessProcessRunMapper.class.getClassLoader(),
                new Class<?>[]{BusinessProcessRunMapper.class},
                (proxy, method, args) -> {
                    if ("renewLease".equals(method.getName())) {
                        observedTenantId.set(TenantContextHolder.getTenantId());
                        return 1;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        coordinator = new BusinessProcessRunLeaseCoordinator(mapper);

        TenantContextHolder.setTenantId(99L);
        try (BusinessProcessRunLeaseCoordinator.LeaseHandle handle =
                     coordinator.monitor(42L, 1001L, 7L, "worker-1")) {
            observedTenantId.set(null);
            TenantContextHolder.clear();

            handle.renewFromHeartbeat();

            assertEquals(42L, observedTenantId.get());
            assertNull(TenantContextHolder.getTenantId());
        }
    }

    @Test
    void afterCommitDispatchUsesIndependentThreadAndTrustedTenant() throws Exception {
        BusinessProcessRunMapper mapper = (BusinessProcessRunMapper) Proxy.newProxyInstance(
                BusinessProcessRunMapper.class.getClassLoader(),
                new Class<?>[]{BusinessProcessRunMapper.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
        coordinator = new BusinessProcessRunLeaseCoordinator(mapper);
        Thread callerThread = Thread.currentThread();
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        AtomicReference<Long> observedTenantId = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        TenantContextHolder.setTenantId(99L);
        coordinator.dispatchAfterCommit(42L, () -> {
            executionThread.set(Thread.currentThread());
            observedTenantId.set(TenantContextHolder.getTenantId());
            completed.countDown();
        });

        assertTrue(completed.await(3, TimeUnit.SECONDS));
        assertNotEquals(callerThread, executionThread.get());
        assertEquals(42L, observedTenantId.get());
        assertEquals(99L, TenantContextHolder.getTenantId());
    }
}
