package com.mdframe.forge.plugin.capability.secureaction.system;

import com.mdframe.forge.plugin.generator.controller.DynamicCrudController;
import com.mdframe.forge.plugin.generator.manager.DynamicCrudCreateManager;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LowcodeFormCreateChainTest {
    private final DynamicCrudService records = mock(DynamicCrudService.class);
    private final BusinessEventPublisher events = mock(BusinessEventPublisher.class);
    private DynamicCrudCreateManager create;
    private TransactionTemplate transaction;
    private JdbcTemplate jdbc;
    private final Map<String, Object> input = Map.of("title", "test");
    private final Map<String, Object> output = Map.of("id", 42L, "title", "test");

    @BeforeEach
    void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE created_event (record_id BIGINT PRIMARY KEY)");
        var manager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(manager);
        create = new DynamicCrudCreateManager(records, events, manager);
        when(records.insert("form", input)).thenReturn(output);
    }

    @AfterEach
    void shutdownIsolatedDatabase() { jdbc.execute("SHUTDOWN"); }

    @Test
    void ordinaryPostCreateUsesSharedLogicAndReturnsOriginalData() {
        var controller = new DynamicCrudController(records, null, events, create);
        assertThat(controller.create("form", input).getData()).isSameAs(output);
        verify(records).insert("form", input);
        verify(events).publishRecordCreated("form", output);
    }

    @Test
    void outerTransactionDispatchesOnlyAfterCommitOutsideCompletedTransaction() {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            jdbc.update("INSERT INTO created_event (record_id) VALUES (?)", 42L);
            return null;
        }).when(events).publishRecordCreated("form", output);
        transaction.executeWithoutResult(status -> {
            assertThat(create.create("form", input)).isSameAs(output);
            verifyNoInteractions(events);
        });
        verify(events).publishRecordCreated("form", output);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM created_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void outerRollbackAndInsertFailureDoNotPublishCreatedEvent() {
        transaction.executeWithoutResult(status -> { create.create("form", input); status.setRollbackOnly(); });
        verifyNoInteractions(events);
        when(records.insert("form", input)).thenThrow(new IllegalArgumentException("invalid field"));
        assertThatThrownBy(() -> create.create("form", input)).hasMessage("invalid field");
        verifyNoInteractions(events);
    }

    @Test
    void legacyNullReturnKeepsOriginalControllerFallback() {
        when(records.insert("form", input)).thenReturn(null);
        assertThat(create.create("form", input)).isSameAs(input);
        verify(events).publishRecordCreated("form", input);
    }
}
