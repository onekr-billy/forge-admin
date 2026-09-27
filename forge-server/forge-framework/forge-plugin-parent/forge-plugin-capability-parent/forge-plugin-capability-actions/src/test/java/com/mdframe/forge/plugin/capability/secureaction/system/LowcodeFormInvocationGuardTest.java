package com.mdframe.forge.plugin.capability.secureaction.system;

import com.mdframe.forge.plugin.capability.secureaction.mapper.LowcodeFormReceiptMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 XML 与主库事务；运行库用第二个隔离内存库，不冒充 MySQL 端到端测试。 */
class LowcodeFormInvocationGuardTest {
    private JdbcTemplate main;
    private JdbcTemplate runtime;
    private DataSourceTransactionManager transactions;
    private LowcodeFormReceiptMapper receipts;
    private LowcodeFormInvocationGuard guard;
    private final LowcodeFormInvocationGuard.Request request = request(1L, 3L, 5L, "a".repeat(64), "b".repeat(64));

    @BeforeEach
    void database() throws Exception {
        main = database("main");
        runtime = database("runtime");
        transactions = new DataSourceTransactionManager(main.getDataSource());
        main.execute("""
            CREATE TABLE ai_capability_form_receipt (
              id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, client_id BIGINT NOT NULL,
              capability_id BIGINT NOT NULL, key_hash CHAR(64) NOT NULL, request_digest CHAR(64) NOT NULL,
              record_id VARCHAR(128) DEFAULT '' NOT NULL, create_by BIGINT, create_dept BIGINT,
              create_time TIMESTAMP, update_by BIGINT, update_time TIMESTAMP,
              UNIQUE (tenant_id, client_id, capability_id, key_hash))
            """);
        for (JdbcTemplate jdbc : new JdbcTemplate[]{main, runtime}) {
            jdbc.execute("CREATE TABLE form_data (id BIGINT PRIMARY KEY, title VARCHAR(40))");
        }
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(main.getDataSource());
        factory.setMapperLocations(new ClassPathResource("mapper/capability/LowcodeFormReceiptMapper.xml"));
        receipts = new SqlSessionTemplate(factory.getObject()).getMapper(LowcodeFormReceiptMapper.class);
        guard = new LowcodeFormInvocationGuard(receipts, transactions);
    }

    private JdbcTemplate database(String label) {
        JdbcDataSource source = new JdbcDataSource();
        // 与 MySQL 一样保留列别名的大小写，避免 H2 把 requestDigest 强制转换成小写。
        source.setURL("jdbc:h2:mem:" + label + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE");
        return new JdbcTemplate(source);
    }

    @AfterEach
    void shutdownIsolatedDatabases() {
        if (main != null) { main.execute("SHUTDOWN"); }
        if (runtime != null) { runtime.execute("SHUTDOWN"); }
    }

    @Test
    void localReceiptAndRowRollbackTogetherAndCanBeRetried() {
        LowcodeFormReceiptMapper failed = spy(receipts);
        doReturn(0).when(failed).complete(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong());
        var failingGuard = new LowcodeFormInvocationGuard(failed, transactions);
        assertThatThrownBy(() -> failingGuard.execute(request, true, create(main, 1L))).hasMessageContaining("回执保存失败");
        assertThat(count(main, "form_data")).isZero();
        assertThat(count(main, "ai_capability_form_receipt")).isZero();
        assertThat(guard.execute(request, true, create(main, 1L))).containsEntry("recordId", "1");
        assertThat(count(main, "form_data")).isEqualTo(1);
    }

    @Test
    void externalCreateObservesCommittedReservationAndDuplicateReturnsOriginal() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Map<String, Object>> create = () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(count(main, "ai_capability_form_receipt")).isEqualTo(1);
            calls.incrementAndGet();
            return create(runtime, 20L).get();
        };
        assertThat(guard.execute(request, false, create)).containsEntry("idempotentHit", false);
        assertThat(guard.execute(request, false, create)).containsEntry("recordId", "20").containsEntry("idempotentHit", true);
        assertThat(calls).hasValue(1);
        assertThat(count(runtime, "form_data")).isEqualTo(1);
        assertThat(count(main, "form_data")).isZero();
    }

    @Test
    void unfinishedCrossDatabaseClaimCannotBeReplayedAsLocalAfterRoutingChange() {
        assertThatThrownBy(() -> guard.execute(request, false, () -> { throw new IllegalStateException("unknown"); }))
                .hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
        assertThatThrownBy(() -> guard.execute(request, true, create(main, 55L))).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
        assertThat(count(main, "form_data")).isZero();
    }

    @Test
    void receiptFailureAfterExternalWriteMustNeverCauseAutomaticSecondInsert() {
        LowcodeFormReceiptMapper failed = spy(receipts);
        doReturn(0).when(failed).complete(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong());
        var failingGuard = new LowcodeFormInvocationGuard(failed, transactions);
        assertThatThrownBy(() -> failingGuard.execute(request, false, create(runtime, 21L))).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
        assertThat(count(runtime, "form_data")).isEqualTo(1);
        assertThatThrownBy(() -> guard.execute(request, false, create(runtime, 22L))).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
        assertThat(count(runtime, "form_data")).isEqualTo(1);
        assertThat(main.queryForObject("SELECT record_id FROM ai_capability_form_receipt", String.class)).isEmpty();
    }

    @Test
    void uncertainExceptionBeforeReturnKeepsDurableClaimEvenWhenOuterTransactionRollsBack() {
        var outer = new TransactionTemplate(transactions);
        outer.executeWithoutResult(status -> {
            assertThatThrownBy(() -> guard.execute(request, false, () -> {
                create(runtime, 23L).get();
                throw new IllegalStateException("connection response lost");
            })).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
            status.setRollbackOnly();
        });
        assertThat(count(main, "ai_capability_form_receipt")).isEqualTo(1);
        assertThatThrownBy(() -> guard.execute(request, false, create(runtime, 24L))).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
        assertThat(count(runtime, "form_data")).isEqualTo(1);
    }

    @Test
    void changedBodyConflictsAndTenantClientCapabilityRemainSeparate() {
        guard.execute(request, false, create(runtime, 30L));
        var changed = request(1L, 3L, 5L, request.keyHash(), "c".repeat(64));
        assertThatThrownBy(() -> guard.execute(changed, false, create(runtime, 31L))).hasMessageContaining("IDEMPOTENCY_CONFLICT");
        guard.execute(request(2L, 3L, 5L, request.keyHash(), request.digest()), false, create(runtime, 32L));
        guard.execute(request(1L, 4L, 5L, request.keyHash(), request.digest()), false, create(runtime, 33L));
        guard.execute(request(1L, 3L, 6L, request.keyHash(), request.digest()), false, create(runtime, 34L));
        assertThat(count(runtime, "form_data")).isEqualTo(4);
    }

    @Test
    void concurrentExternalRequestDoesNotRunCreateWhileOwnerIsInFlight() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> guard.execute(request, false, () -> {
                entered.countDown();
                await(finish);
                return create(runtime, 40L).get();
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> assertThatThrownBy(() -> guard.execute(request, false, create(runtime, 41L)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN"));
            second.get(5, TimeUnit.SECONDS);
            finish.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).containsEntry("recordId", "40");
            assertThat(guard.execute(request, false, create(runtime, 42L))).containsEntry("recordId", "40");
            assertThat(count(runtime, "form_data")).isEqualTo(1);
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reservationFailureNeverCallsBusinessAndMissingRecordIdIsNotTreatedAsSuccess() {
        LowcodeFormReceiptMapper failed = spy(receipts);
        doThrow(new IllegalStateException("receipt unavailable")).when(failed)
                .reserve(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong());
        var failingGuard = new LowcodeFormInvocationGuard(failed, transactions);
        assertThatThrownBy(() -> failingGuard.execute(request, false, create(runtime, 50L))).hasMessageContaining("receipt unavailable");
        assertThat(count(runtime, "form_data")).isZero();
        assertThatThrownBy(() -> guard.execute(request, false, Map::of)).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
        assertThatThrownBy(() -> guard.execute(request, false, create(runtime, 51L))).hasMessageContaining("FORM_SUBMISSION_UNCERTAIN");
        assertThat(count(runtime, "form_data")).isZero();
    }

    private Supplier<Map<String, Object>> create(JdbcTemplate jdbc, Long id) {
        return () -> { jdbc.update("INSERT INTO form_data (id, title) VALUES (?, ?)", id, "test"); return Map.of("id", id); };
    }
    private int count(JdbcTemplate jdbc, String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private static LowcodeFormInvocationGuard.Request request(Long tenant, Long client, Long capability, String key, String digest) {
        return new LowcodeFormInvocationGuard.Request(tenant, client, capability, key, digest, 7L, 2L);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("test timed out"); } }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
