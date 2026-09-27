package com.mdframe.forge.plugin.generator.manager;

import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicCrudMutationManagerTest {

    @Test
    void createWritesRecordBeforeAppendingOutboxEvent() {
        DynamicCrudService records = mock(DynamicCrudService.class);
        BusinessEventPublisher events = mock(BusinessEventPublisher.class);
        DynamicCrudMutationManager manager = new DynamicCrudMutationManager(records, events);
        Map<String, Object> request = Map.of("name", "order");
        Map<String, Object> created = Map.of("id", 31L, "name", "order");
        when(records.insert("order", request)).thenReturn(created);

        assertSame(created, manager.create("order", request));

        InOrder order = inOrder(records, events);
        order.verify(records).insert("order", request);
        order.verify(events).publishRecordCreated("order", created);
    }

    @Test
    void outboxFailurePropagatesSoTransactionCanRollback() {
        DynamicCrudService records = mock(DynamicCrudService.class);
        BusinessEventPublisher events = mock(BusinessEventPublisher.class);
        DynamicCrudMutationManager manager = new DynamicCrudMutationManager(records, events);
        Map<String, Object> request = Map.of("name", "order");
        when(records.insert("order", request)).thenReturn(Map.of("id", 32L));
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox unavailable"))
                .when(events).publishRecordCreated("order", Map.of("id", 32L));

        assertThrows(IllegalStateException.class, () -> manager.create("order", request));
    }

    @Test
    void everyWriteUseCaseIsTransactional() throws NoSuchMethodException {
        for (Method method : new Method[]{
                DynamicCrudMutationManager.class.getMethod("create", String.class, Map.class),
                DynamicCrudMutationManager.class.getMethod("update", String.class, Map.class),
                DynamicCrudMutationManager.class.getMethod("delete", String.class, String.class),
                DynamicCrudMutationManager.class.getMethod("remove", String.class,
                        com.mdframe.forge.plugin.generator.dto.audit.DataAuditRemoveDTO.class),
                DynamicCrudMutationManager.class.getMethod("batchDelete", String.class, java.util.List.class)}) {
            assertTrue(method.isAnnotationPresent(Transactional.class), method.getName());
        }
    }
}
