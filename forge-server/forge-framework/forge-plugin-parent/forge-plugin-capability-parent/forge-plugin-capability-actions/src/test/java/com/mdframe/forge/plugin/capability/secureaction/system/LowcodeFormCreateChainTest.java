package com.mdframe.forge.plugin.capability.secureaction.system;

import com.mdframe.forge.plugin.generator.manager.DynamicCrudMutationManager;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private DynamicCrudMutationManager create;
    private TransactionTemplate transaction;
    private final Map<String, Object> input = Map.of("title", "test");
    private final Map<String, Object> output = Map.of("id", 42L, "title", "test");

    @BeforeEach
    void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID());
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        create = new DynamicCrudMutationManager(records, events);
        when(records.insert("form", input)).thenReturn(output);
    }

    @Test
    void lowcodeFormCreateUsesSharedMutationLogicAndReturnsOriginalData() {
        assertThat(create.create("form", input)).isSameAs(output);
        verify(events).assertTransactionalPublishSupported("form");
        verify(records).insert("form", input);
        verify(events).publishRecordCreated("form", output);
    }

    @Test
    void appendRunsInsideCallingTransactionSoDataAndOutboxCanCommitAtomically() {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(events).publishRecordCreated("form", output);
        transaction.executeWithoutResult(status -> assertThat(create.create("form", input)).isSameAs(output));
        verify(events).publishRecordCreated("form", output);
    }

    @Test
    void insertFailureDoesNotAppendCreatedEvent() {
        when(records.insert("form", input)).thenThrow(new IllegalArgumentException("invalid field"));
        assertThatThrownBy(() -> create.create("form", input)).hasMessage("invalid field");
        verify(events).assertTransactionalPublishSupported("form");
        verify(events, never()).publishRecordCreated(anyString(), anyMap());
    }

    @Test
    void legacyNullReturnKeepsOriginalControllerFallback() {
        when(records.insert("form", input)).thenReturn(null);
        assertThat(create.create("form", input)).isSameAs(input);
        verify(events).publishRecordCreated("form", input);
    }
}
