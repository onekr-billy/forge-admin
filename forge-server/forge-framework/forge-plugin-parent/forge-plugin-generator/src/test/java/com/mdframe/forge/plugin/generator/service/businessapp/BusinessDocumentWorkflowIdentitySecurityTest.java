package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectRelation;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessDocumentConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectRelationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.id.service.ISequenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Business document and workflow identity security")
class BusinessDocumentWorkflowIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("document config rejects missing tenant before object and mapper access")
    void documentConfigRejectsMissingTenantBeforeDataAccess() {
        BusinessDocumentConfigMapper configMapper = mock(BusinessDocumentConfigMapper.class);
        BusinessBindingMapper bindingMapper = mock(BusinessBindingMapper.class);
        BusinessObjectService objectService = mock(BusinessObjectService.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ISequenceService sequenceService = mock(ISequenceService.class);
        BusinessDocumentConfigService service = new BusinessDocumentConfigService(
                configMapper, bindingMapper, objectService, crudConfigMapper, objectMapper, sequenceService);

        assertThrows(BusinessException.class, () -> service.getConfig(10L));

        verifyNoInteractions(configMapper, bindingMapper, objectService, crudConfigMapper, objectMapper, sequenceService);
    }

    @Test
    @DisplayName("document number generation rejects missing tenant before sequence allocation")
    void documentNumberRejectsMissingTenantBeforeSequenceAllocation() {
        ISequenceService sequenceService = mock(ISequenceService.class);
        BusinessDocumentNoRuleEngine engine = new BusinessDocumentNoRuleEngine(sequenceService);
        AiBusinessDocumentConfig config = new AiBusinessDocumentConfig();
        config.setDocumentEnabled(EnableStatus.ENABLED.getCode());
        config.setDocumentNoRule("DOC-${seq:4}");

        assertThrows(BusinessException.class, () -> engine.generate(config, java.util.Map.of()));

        verifyNoInteractions(sequenceService);
    }

    @Test
    @DisplayName("document runtime rejects missing tenant before config and process access")
    void documentRuntimeRejectsMissingTenantBeforeDataAccess() {
        BusinessDocumentConfigService configService = mock(BusinessDocumentConfigService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessProcessRunMapper processRunMapper = mock(BusinessProcessRunMapper.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessPermissionService permissionService = mock(BusinessPermissionService.class);
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        BusinessDocumentRuntimeService service = new BusinessDocumentRuntimeService(
                configService, linkMapper, processRunMapper, crudConfigMapper, objectMapper,
                permissionService, crudService);

        assertThrows(BusinessException.class, () -> service.getRuntime("ORDER", 10L));

        verifyNoInteractions(configService, linkMapper, processRunMapper, crudConfigMapper,
                objectMapper, permissionService, crudService);
    }

    @Test
    @DisplayName("flow variable resolution rejects missing tenant before external and object access")
    void flowVariablesRejectMissingTenantBeforeDataAccess() {
        BusinessObjectService objectService = mock(BusinessObjectService.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        BusinessCodeFormProviderRegistry registry = mock(BusinessCodeFormProviderRegistry.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        BusinessFlowVariableResolver resolver = new BusinessFlowVariableResolver(
                objectService, configMapper, registry, objectMapper);

        assertThrows(BusinessException.class, () -> resolver.resolve("approval", "ORDER"));

        verifyNoInteractions(objectService, configMapper, registry, objectMapper);
    }

    @Test
    @DisplayName("relation runtime rejects missing tenant before source object access")
    void relationRuntimeRejectsMissingTenantBeforeDataAccess() {
        BusinessObjectRelationMapper relationMapper = mock(BusinessObjectRelationMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        BusinessRelationRuntimeService service = new BusinessRelationRuntimeService(
                relationMapper, objectMapper, appMapper, configMapper);

        assertThrows(BusinessException.class, () -> service.relationRuntime(10L));

        verifyNoInteractions(relationMapper, objectMapper, appMapper, configMapper);
    }

    @Test
    @DisplayName("relation runtime uses tenant predicate and keeps enabled published targets open")
    void relationRuntimeUsesTenantPredicateAndKeepsEnabledTargetOpen() {
        BusinessObjectRelationMapper relationMapper = mock(BusinessObjectRelationMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        BusinessRelationRuntimeService service = new BusinessRelationRuntimeService(
                relationMapper, objectMapper, appMapper, configMapper);

        AiBusinessObject source = new AiBusinessObject();
        source.setId(10L);
        source.setTenantId(9L);
        source.setSuiteCode("CRM");
        source.setObjectCode("ORDER");
        source.setObjectName("订单");
        AiBusinessObjectRelation relation = new AiBusinessObjectRelation();
        relation.setId(20L);
        relation.setSuiteCode("CRM");
        relation.setSourceObjectCode("ORDER");
        relation.setTargetObjectCode("CUSTOMER");
        relation.setRelationType("MANY_TO_ONE");
        AiBusinessObject target = new AiBusinessObject();
        target.setObjectName("客户");
        AiBusinessApp targetApp = new AiBusinessApp();
        targetApp.setId(30L);
        targetApp.setAppCode("customer_app");
        targetApp.setConfigKey("customer_runtime");
        targetApp.setStatus(EnableStatus.ENABLED.getCode());
        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setStatus(String.valueOf(EnableStatus.ENABLED.getCode()));
        runtimeConfig.setPublishStatus("PUBLISHED");
        when(objectMapper.selectByIdForTenant(9L, 10L)).thenReturn(source);
        when(relationMapper.selectRuntimeRelationsBySource(9L, "CRM", "ORDER"))
                .thenReturn(List.of(relation));
        when(objectMapper.selectByObjectCode(9L, "CRM", "CUSTOMER")).thenReturn(target);
        when(appMapper.selectRuntimeAppByObject(9L, "CRM", "CUSTOMER")).thenReturn(targetApp);
        when(configMapper.selectByConfigKey(9L, "customer_runtime")).thenReturn(runtimeConfig);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L)) {
            var result = service.relationRuntime(10L);

            assertEquals(1, result.size());
            assertTrue(result.get(0).getCanOpen());
            assertEquals("/ai/crud-page/customer_runtime", result.get(0).getTargetUrl());
        }

        verify(objectMapper).selectByIdForTenant(9L, 10L);
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setTenantId(tenantId);
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", 7L, null, tenantId,
                "pc", "document-workflow-security-test", Set.of()));
    }
}
