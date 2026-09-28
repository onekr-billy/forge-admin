package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessQuantityOperationDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessRecordSelectorQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityBalanceMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLedgerMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessQuantityLockMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Business runtime data identity security")
class BusinessRuntimeDataIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("record selector rejects missing tenant before object and data access")
    void recordSelectorRejectsMissingTenantBeforeDataAccess() {
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        BusinessPermissionService permissionService = mock(BusinessPermissionService.class);
        BusinessRecordSelectorService service = new BusinessRecordSelectorService(
                objectMapper, crudService, permissionService);
        BusinessRecordSelectorQueryDTO query = new BusinessRecordSelectorQueryDTO();
        query.setObjectCode("ORDER");

        assertThrows(BusinessException.class, () -> service.query(query, new PageQuery()));

        verifyNoInteractions(objectMapper, crudService, permissionService);
    }

    @Test
    @DisplayName("record metadata rejects an object from another tenant before runtime config access")
    void recordMetadataRejectsCrossTenantObject() {
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        BusinessRecordSelectorService service = new BusinessRecordSelectorService(
                mock(BusinessObjectMapper.class), crudService, mock(BusinessPermissionService.class));
        AiBusinessObject object = new AiBusinessObject();
        object.setTenantId(8L);
        object.setConfigKey("runtime_order");

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L)) {
            assertThrows(BusinessException.class, () -> service.fieldTypeSchemas(object));
        }

        verifyNoInteractions(crudService);
    }

    @Test
    @DisplayName("quantity query rejects missing tenant before mapper access")
    void quantityQueryRejectsMissingTenantBeforeDataAccess() {
        BusinessQuantityBalanceMapper balanceMapper = mock(BusinessQuantityBalanceMapper.class);
        BusinessQuantityLedgerMapper ledgerMapper = mock(BusinessQuantityLedgerMapper.class);
        BusinessQuantityLockMapper lockMapper = mock(BusinessQuantityLockMapper.class);
        BusinessQuantityQueryService service = new BusinessQuantityQueryService(
                balanceMapper, ledgerMapper, lockMapper);

        assertThrows(BusinessException.class, () -> service.selectBalancePage(null));

        verifyNoInteractions(balanceMapper, ledgerMapper, lockMapper);
    }

    @Test
    @DisplayName("quantity mutation rejects missing tenant before idempotency and balance access")
    void quantityMutationRejectsMissingTenantBeforeDataAccess() {
        BusinessQuantityBalanceMapper balanceMapper = mock(BusinessQuantityBalanceMapper.class);
        BusinessQuantityLedgerMapper ledgerMapper = mock(BusinessQuantityLedgerMapper.class);
        BusinessQuantityLockMapper lockMapper = mock(BusinessQuantityLockMapper.class);
        BusinessQuantityLedgerService service = new BusinessQuantityLedgerService(
                new ObjectMapper(), balanceMapper, ledgerMapper, lockMapper);

        assertThrows(BusinessException.class, () -> service.inbound(new BusinessQuantityOperationDTO()));

        verifyNoInteractions(balanceMapper, ledgerMapper, lockMapper);
    }

    @Test
    @DisplayName("object readiness rejects missing tenant before object lookup")
    void objectReadinessRejectsMissingTenantBeforeDataAccess() {
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessTriggerMapper triggerMapper = mock(BusinessTriggerMapper.class);
        BusinessObjectReadinessService service = new BusinessObjectReadinessService(
                objectMapper, appMapper, configMapper, documentConfigService, triggerMapper);

        assertThrows(BusinessException.class, () -> service.readiness(10L));

        verifyNoInteractions(objectMapper, appMapper, configMapper, documentConfigService, triggerMapper);
    }

    @Test
    @DisplayName("object readiness loads the object through an explicit tenant predicate")
    void objectReadinessUsesExplicitTenantPredicate() {
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessTriggerMapper triggerMapper = mock(BusinessTriggerMapper.class);
        BusinessObjectReadinessService service = new BusinessObjectReadinessService(
                objectMapper, appMapper, configMapper, documentConfigService, triggerMapper);
        AiBusinessObject object = new AiBusinessObject();
        object.setId(10L);
        object.setTenantId(9L);
        object.setSuiteCode("sales");
        object.setObjectCode("ORDER");
        object.setObjectName("订单");
        object.setStatus(1);
        when(objectMapper.selectByIdForTenant(9L, 10L)).thenReturn(object);
        when(documentConfigService.getConfig(10L)).thenReturn(new BusinessDocumentConfigVO());

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L)) {
            assertEquals(10L, service.readiness(10L).getObjectId());
        }

        verify(objectMapper).selectByIdForTenant(9L, 10L);
    }

    @Test
    @DisplayName("engine summary rejects missing tenant before binding statistics")
    void engineSummaryRejectsMissingTenantBeforeDataAccess() {
        BusinessBindingMapper bindingMapper = mock(BusinessBindingMapper.class);
        BusinessEngineSummaryService service = new BusinessEngineSummaryService(bindingMapper);

        assertThrows(BusinessException.class, service::summary);

        verifyNoInteractions(bindingMapper);
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setTenantId(tenantId);
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", 7L, null, tenantId,
                "pc", "runtime-data-security-test", Set.of()));
    }
}
