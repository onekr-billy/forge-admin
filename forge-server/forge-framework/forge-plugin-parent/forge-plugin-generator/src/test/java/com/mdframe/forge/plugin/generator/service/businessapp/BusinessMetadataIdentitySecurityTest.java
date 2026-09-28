package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeDomainMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeModelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessSuiteMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDdlService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeRuntimeConfigBuilder;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Business metadata identity security")
class BusinessMetadataIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("field template lookup rejects missing tenant before mapper access")
    void fieldTemplatesRejectMissingTenantBeforeDataAccess() {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        BusinessFieldSchemaService fieldSchemaService = mock(BusinessFieldSchemaService.class);
        BusinessFieldTemplateService service = new BusinessFieldTemplateService(objectMapper, fieldSchemaService);

        assertThrows(BusinessException.class, () -> service.listTemplates("CRM"));

        verifyNoInteractions(objectMapper, fieldSchemaService);
    }

    @Test
    @DisplayName("table mapping rejects missing tenant before design context access")
    void tableMappingRejectsMissingTenantBeforeContextAccess() {
        BusinessObjectDesignContextProvider contextProvider = mock(BusinessObjectDesignContextProvider.class);
        LowcodeDdlService ddlService = mock(LowcodeDdlService.class);
        BusinessApplicationObjectMapper applicationObjectMapper = mock(BusinessApplicationObjectMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessObjectTableMappingService service = new BusinessObjectTableMappingService(
                contextProvider, ddlService, applicationObjectMapper, objectMapper);

        assertThrows(BusinessException.class, () -> service.getTableMapping(10L));

        verifyNoInteractions(contextProvider, ddlService, applicationObjectMapper, objectMapper);
    }

    @Test
    @DisplayName("flow app config rejects missing tenant before object and flow access")
    void flowAppConfigRejectsMissingTenantBeforeDataAccess() {
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessFlowService flowService = mock(BusinessFlowService.class);
        BusinessFlowAppConfigService service = new BusinessFlowAppConfigService(
                objectMapper, documentConfigService, flowService);

        assertThrows(BusinessException.class, () -> service.getConfig("ORDER"));

        verifyNoInteractions(objectMapper, documentConfigService, flowService);
    }

    @Test
    @DisplayName("bootstrap rejects missing tenant before reading lowcode metadata")
    void bootstrapRejectsMissingTenantBeforeDataAccess() {
        AiLowcodeDomainMapper domainMapper = mock(AiLowcodeDomainMapper.class);
        AiLowcodeModelMapper modelMapper = mock(AiLowcodeModelMapper.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        BusinessSuiteMapper suiteMapper = mock(BusinessSuiteMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        BusinessNamingService namingService = mock(BusinessNamingService.class);
        BusinessBootstrapService service = new BusinessBootstrapService(
                domainMapper, modelMapper, configMapper, suiteMapper, objectMapper, appMapper, namingService);

        assertThrows(BusinessException.class, service::syncAppsFromPublishedCrudConfigs);

        verifyNoInteractions(domainMapper, modelMapper, configMapper, suiteMapper,
                objectMapper, appMapper, namingService);
    }

    @Test
    @DisplayName("deployment publish validation rejects missing tenant before application access")
    void deploymentValidationRejectsMissingTenantBeforeDataAccess() {
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        LowcodeRuntimeDataSourceResolver dataSourceResolver = mock(LowcodeRuntimeDataSourceResolver.class);
        LowcodeDdlService ddlService = mock(LowcodeDdlService.class);
        BusinessObjectDeploymentPublishValidator validator = new BusinessObjectDeploymentPublishValidator(
                mock(ObjectMapper.class), appMapper, dataSourceResolver, ddlService);

        assertThrows(BusinessException.class,
                () -> validator.validateEntry(context(9L), new ArrayList<>()));

        verifyNoInteractions(appMapper, dataSourceResolver, ddlService);
    }

    @Test
    @DisplayName("design publish validation rejects missing tenant before relation access")
    void designValidationRejectsMissingTenantBeforeDataAccess() {
        BusinessObjectDesignerService designerService = mock(BusinessObjectDesignerService.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        LowcodeRuntimeConfigBuilder runtimeConfigBuilder = mock(LowcodeRuntimeConfigBuilder.class);
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessTriggerMapper triggerMapper = mock(BusinessTriggerMapper.class);
        BusinessPermissionService permissionService = mock(BusinessPermissionService.class);
        BusinessObjectDesignPublishValidator validator = new BusinessObjectDesignPublishValidator(
                mock(ObjectMapper.class), designerService, objectMapper, runtimeConfigBuilder,
                documentConfigService, triggerMapper, permissionService);

        assertThrows(BusinessException.class,
                () -> validator.validateSchema(context(9L), new ArrayList<>()));

        verifyNoInteractions(designerService, objectMapper, runtimeConfigBuilder,
                documentConfigService, triggerMapper, permissionService);
    }

    @Test
    @DisplayName("deployment publish validation rejects a cross-tenant context")
    void deploymentValidationRejectsCrossTenantContext() {
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        LowcodeRuntimeDataSourceResolver dataSourceResolver = mock(LowcodeRuntimeDataSourceResolver.class);
        LowcodeDdlService ddlService = mock(LowcodeDdlService.class);
        BusinessObjectDeploymentPublishValidator validator = new BusinessObjectDeploymentPublishValidator(
                mock(ObjectMapper.class), appMapper, dataSourceResolver, ddlService);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L)) {
            assertThrows(BusinessException.class,
                    () -> validator.validateEntry(context(8L), new ArrayList<>()));
        }

        verifyNoInteractions(appMapper, dataSourceResolver, ddlService);
    }

    private static BusinessObjectDesignerService.DesignerContext context(Long tenantId) {
        AiBusinessObject object = new AiBusinessObject();
        object.setId(10L);
        object.setTenantId(tenantId);
        object.setSuiteCode("CRM");
        object.setObjectCode("ORDER");
        BusinessObjectDesignerService.DesignerContext context = new BusinessObjectDesignerService.DesignerContext();
        context.setObject(object);
        return context;
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setTenantId(tenantId);
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", 7L, null, tenantId,
                "pc", "business-metadata-security-test", Set.of()));
    }
}
