package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessValidationContextResolver;
import com.mdframe.forge.plugin.generator.dto.AiCrudConfigRenderVO;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessExtensionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Business application runtime identity security")
class BusinessApplicationRuntimeIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("runtime rejects missing tenant before application or version access")
    void runtimeRejectsMissingTenantBeforeDataAccess() {
        BusinessApplicationService applicationService = mock(BusinessApplicationService.class);
        BusinessApplicationVersionService versionService = mock(BusinessApplicationVersionService.class);
        BusinessApplicationSnapshotService snapshotService = mock(BusinessApplicationSnapshotService.class);
        BusinessApplicationRuntimeService service = new BusinessApplicationRuntimeService(
                applicationService, versionService, snapshotService, new ObjectMapper());

        assertThrows(BusinessException.class, () -> service.runtimeById(10L));

        verifyNoInteractions(applicationService, versionService, snapshotService);
    }

    @Test
    @DisplayName("asset selection rejects missing tenant before querying application assets")
    void assetSelectionRejectsMissingTenantBeforeDataAccess() {
        BusinessApplicationObjectService objectService = mock(BusinessApplicationObjectService.class);
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        BusinessExtensionMapper extensionMapper = mock(BusinessExtensionMapper.class);
        BusinessProcessMapper processMapper = mock(BusinessProcessMapper.class);
        BusinessApplicationAssetSelectionService service = new BusinessApplicationAssetSelectionService(
                objectService, appMapper, extensionMapper, processMapper);

        assertThrows(BusinessException.class, () -> service.resolveContext(10L, null));

        verifyNoInteractions(objectService, appMapper, extensionMapper, processMapper);
    }

    @Test
    @DisplayName("readiness rejects missing tenant before resolving publish context")
    void readinessRejectsMissingTenantBeforeDataAccess() {
        BusinessApplicationService applicationService = mock(BusinessApplicationService.class);
        BusinessApplicationAssetSelectionService selectionService =
                mock(BusinessApplicationAssetSelectionService.class);
        BusinessObjectPublishService objectPublishService = mock(BusinessObjectPublishService.class);
        BusinessPermissionService permissionService = mock(BusinessPermissionService.class);
        BusinessBindingMapper bindingMapper = mock(BusinessBindingMapper.class);
        BusinessApplicationPageDependencyInspector pageInspector =
                mock(BusinessApplicationPageDependencyInspector.class);
        BusinessObjectTableMappingService tableMappingService = mock(BusinessObjectTableMappingService.class);
        BusinessProcessSchemaValidator schemaValidator = mock(BusinessProcessSchemaValidator.class);
        BusinessProcessValidationContextResolver contextResolver =
                mock(BusinessProcessValidationContextResolver.class);
        BusinessApplicationReadinessService service = new BusinessApplicationReadinessService(
                applicationService, selectionService, objectPublishService, permissionService,
                bindingMapper, pageInspector, tableMappingService, schemaValidator, contextResolver);

        assertThrows(BusinessException.class, () -> service.check(10L));

        verifyNoInteractions(applicationService, selectionService, objectPublishService,
                permissionService, bindingMapper, pageInspector, tableMappingService,
                schemaValidator, contextResolver);
    }

    @Test
    @DisplayName("runtime overlay rejects missing tenant before resolving an entry")
    void overlayRejectsMissingTenantBeforeDataAccess() {
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        BusinessApplicationObjectService objectService = mock(BusinessApplicationObjectService.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        BusinessProcessRuntimeActionProjectionService projectionService =
                mock(BusinessProcessRuntimeActionProjectionService.class);
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(
                        appMapper, objectService, runtimeService, new ObjectMapper(), projectionService);

        assertThrows(BusinessException.class,
                () -> service.overlay("customer", 10L, new AiCrudConfigRenderVO()));

        verifyNoInteractions(appMapper, objectService, runtimeService, projectionService);
    }
}
