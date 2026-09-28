package com.mdframe.forge.flow.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.flow.entity.FlowEntry;
import com.mdframe.forge.starter.flow.service.FlowBusinessObjectRuntimeAdapter;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Flow business object adapter identity")
class FlowBusinessObjectRuntimeAdapterIdentityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
        TenantContextHolder.clear();
        TenantContextHolder.clearIgnore();
    }

    @Test
    @DisplayName("business record creation rejects missing identity before dynamic write")
    void creationRejectsMissingIdentityBeforeWrite() {
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        FlowBusinessObjectRuntimeAdapterImpl adapter = new FlowBusinessObjectRuntimeAdapterImpl(
                dynamicCrudService, linkMapper, new ObjectMapper());

        assertThrows(IllegalStateException.class,
                () -> adapter.createBusinessRecord(entry(9L), null, Map.of("name", "order")));

        verifyNoInteractions(dynamicCrudService, linkMapper);
    }

    @Test
    @DisplayName("business record creation rejects a cross-tenant entry before dynamic write")
    void creationRejectsCrossTenantEntryBeforeWrite() {
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        FlowBusinessObjectRuntimeAdapterImpl adapter = new FlowBusinessObjectRuntimeAdapterImpl(
                dynamicCrudService, linkMapper, new ObjectMapper());

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            assertThrows(IllegalStateException.class,
                    () -> adapter.createBusinessRecord(entry(8L), null, Map.of("name", "order")));
        }

        verifyNoInteractions(dynamicCrudService, linkMapper);
    }

    @Test
    @DisplayName("process link persists the trusted tenant and starter")
    void processLinkUsesTrustedActor() {
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        FlowBusinessObjectRuntimeAdapterImpl adapter = new FlowBusinessObjectRuntimeAdapterImpl(
                dynamicCrudService, linkMapper, new ObjectMapper());
        FlowBusinessObjectRuntimeAdapter.BusinessRecordCreateResult record =
                new FlowBusinessObjectRuntimeAdapter.BusinessRecordCreateResult();
        record.setObjectCode("ORDER");
        record.setRecordId(31L);
        record.setBusinessKey("ORDER:31");

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            adapter.afterProcessStarted(entry(9L), record, "process-1", Map.of("amount", 100));
        }

        ArgumentCaptor<AiBusinessFlowInstanceLink> captor =
                ArgumentCaptor.forClass(AiBusinessFlowInstanceLink.class);
        verify(linkMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getTenantId());
        assertEquals(7L, captor.getValue().getStartUserId());
    }

    private static FlowEntry entry(Long tenantId) {
        FlowEntry entry = new FlowEntry();
        entry.setTenantId(tenantId);
        entry.setConfigKey("orders");
        entry.setObjectCode("ORDER");
        entry.setModelKey("order-approval");
        return entry;
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId, Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setTenantId(tenantId);
        loginUser.setUserId(userId);
        loginUser.setUsername("tester");
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", userId, null, tenantId,
                "pc", "flow-adapter-security-test", Set.of()));
    }
}
