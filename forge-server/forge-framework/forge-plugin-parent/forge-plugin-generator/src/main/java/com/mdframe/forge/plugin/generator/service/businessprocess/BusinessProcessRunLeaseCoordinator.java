package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务流程运行租约守卫。
 *
 * <p>数据库中的 executionToken 是最终 fencing 边界；心跳线程只负责延长当前令牌的有效期，
 * 任何一次续租失败都会让本地执行器停止提交后续状态。</p>
 */
@Component
public class BusinessProcessRunLeaseCoordinator {

    static final int LEASE_SECONDS = 90;
    private static final int HEARTBEAT_SECONDS = 20;
    private static final int HEARTBEAT_THREADS = 2;
    private static final int EXECUTION_THREADS = 4;
    private static final int EXECUTION_QUEUE_CAPACITY = 256;

    private final BusinessProcessRunMapper runMapper;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ExecutorService executionExecutor;

    public BusinessProcessRunLeaseCoordinator(BusinessProcessRunMapper runMapper) {
        this.runMapper = runMapper;
        this.heartbeatExecutor = Executors.newScheduledThreadPool(
                HEARTBEAT_THREADS, contextClearingThreadFactory("business-process-run-heartbeat"));
        this.executionExecutor = new ThreadPoolExecutor(
                EXECUTION_THREADS,
                EXECUTION_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(EXECUTION_QUEUE_CAPACITY),
                contextClearingThreadFactory("business-process-run-executor"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    private ThreadFactory contextClearingThreadFactory(String prefix) {
        AtomicInteger threadSequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(() -> {
                TenantContextHolder.clear();
                try {
                    runnable.run();
                } finally {
                    TenantContextHolder.clear();
                }
            }, prefix + "-" + threadSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 在独立线程和可信租户上下文中执行提交后的编排，避免复用 afterCommit 尚未解绑的事务资源。
     */
    public void dispatchAfterCommit(Long tenantId, Runnable task) {
        if (tenantId == null || tenantId <= 0 || task == null) {
            throw new BusinessException("业务流程提交后执行参数无效");
        }
        executionExecutor.execute(() -> TenantContextHolder.executeWithTenant(tenantId, task));
    }

    public LeaseHandle monitor(Long tenantId, Long runId, Long executionToken, String leaseOwner) {
        if (executionToken == null || executionToken <= 0 || leaseOwner == null || leaseOwner.isBlank()) {
            throw new BusinessException("业务流程执行租约无效");
        }
        LeaseHandle handle = new LeaseHandle(tenantId, runId, executionToken, leaseOwner);
        handle.renewNow();
        handle.future = heartbeatExecutor.scheduleWithFixedDelay(
                handle::renewFromHeartbeat,
                HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS,
                TimeUnit.SECONDS);
        return handle;
    }

    @PreDestroy
    public void destroy() {
        executionExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    public final class LeaseHandle implements AutoCloseable {

        private final Long tenantId;
        private final Long runId;
        private final Long executionToken;
        private final String leaseOwner;
        private final AtomicBoolean owned = new AtomicBoolean(true);
        private volatile ScheduledFuture<?> future;

        private LeaseHandle(Long tenantId, Long runId, Long executionToken, String leaseOwner) {
            this.tenantId = tenantId;
            this.runId = runId;
            this.executionToken = executionToken;
            this.leaseOwner = leaseOwner;
        }

        public Long executionToken() {
            return executionToken;
        }

        public String leaseOwner() {
            return leaseOwner;
        }

        public void renewNow() {
            assertOwned();
            try {
                int updated = runMapper.renewLease(
                        tenantId, runId, executionToken, leaseOwner, LEASE_SECONDS);
                if (updated != 1) {
                    owned.set(false);
                    throw leaseLost();
                }
            } catch (BusinessException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                owned.set(false);
                throw new BusinessException(409, "业务流程执行租约续期失败", exception);
            }
        }

        public void assertOwned() {
            if (!owned.get()) {
                throw leaseLost();
            }
        }

        void renewFromHeartbeat() {
            if (!owned.get()) {
                return;
            }
            try {
                TenantContextHolder.executeWithTenant(tenantId, this::renewNow);
            } catch (RuntimeException ignored) {
                // renewNow 已将租约标记为丢失；主执行线程会在下一个提交点 fail-closed。
            }
        }

        private BusinessException leaseLost() {
            return new BusinessException(409, "业务流程执行租约已失效，请等待接管或重试");
        }

        @Override
        public void close() {
            ScheduledFuture<?> scheduled = future;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }
    }
}
