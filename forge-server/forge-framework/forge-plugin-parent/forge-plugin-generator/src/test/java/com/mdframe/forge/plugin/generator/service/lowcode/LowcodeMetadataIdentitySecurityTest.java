package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeModelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.GenTableColumnMapper;
import com.mdframe.forge.plugin.generator.service.AiCrudCodegenService;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Low-code metadata identity security")
class LowcodeMetadataIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
        TenantContextHolder.clear();
        TenantContextHolder.clearIgnore();
    }

    @Test
    @DisplayName("domain lookup rejects missing tenant before mapper access")
    void domainLookupRejectsMissingTenantBeforeDataAccess() {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        LowcodeDomainService service = new LowcodeDomainService(objectMapper);

        assertThrows(BusinessException.class,
                () -> service.page(mock(PageQuery.class), null, null, null));

        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("data model lookup rejects missing tenant before dependencies")
    void dataModelLookupRejectsMissingTenantBeforeDataAccess() {
        LowcodeDomainService domainService = mock(LowcodeDomainService.class);
        LowcodeSchemaValidator schemaValidator = mock(LowcodeSchemaValidator.class);
        LowcodeDdlService ddlService = mock(LowcodeDdlService.class);
        GenTableColumnMapper columnMapper = mock(GenTableColumnMapper.class);
        LowcodePolicyService policyService = mock(LowcodePolicyService.class);
        LowcodeModelSchemaNormalizer schemaNormalizer = mock(LowcodeModelSchemaNormalizer.class);
        BusinessObjectMapper businessObjectMapper = mock(BusinessObjectMapper.class);
        LowcodeDataModelService service = new LowcodeDataModelService(
                mock(ObjectMapper.class), domainService, schemaValidator, ddlService,
                columnMapper, policyService, schemaNormalizer, businessObjectMapper);

        assertThrows(BusinessException.class,
                () -> service.page(mock(PageQuery.class), null, null, null, null));

        verifyNoInteractions(domainService, schemaValidator, ddlService, columnMapper,
                policyService, schemaNormalizer, businessObjectMapper);
    }

    @Test
    @DisplayName("application lookup rejects missing tenant before domain and config access")
    void appLookupRejectsMissingTenantBeforeDataAccess() {
        AiCrudConfigService configService = mock(AiCrudConfigService.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        LowcodeDomainService domainService = mock(LowcodeDomainService.class);
        LowcodeAppService service = new LowcodeAppService(
                mock(ObjectMapper.class), configService, configMapper,
                mock(LowcodeSchemaValidator.class), domainService,
                mock(LowcodePolicyService.class), mock(LowcodeModelSchemaNormalizer.class));

        assertThrows(BusinessException.class,
                () -> service.page(mock(PageQuery.class), null, null, 1L, null, null));

        verifyNoInteractions(configService, configMapper, domainService);
    }

    @Test
    @DisplayName("code preview rejects missing tenant before application access")
    void codePreviewRejectsMissingTenantBeforeDataAccess() {
        CodegenFixture fixture = codegenFixture();

        assertThrows(BusinessException.class, () -> fixture.service().previewCode(1L, null));

        verifyNoInteractions(fixture.appService(), fixture.codegenService(), fixture.versionMapper());
    }

    @Test
    @DisplayName("code generation rejects a cross-tenant config")
    void codegenRejectsCrossTenantConfig() {
        CodegenFixture fixture = codegenFixture();
        AiCrudConfig config = new AiCrudConfig();
        config.setTenantId(8L);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L)) {
            assertThrows(BusinessException.class,
                    () -> fixture.service().prepareConfigForCodegen(config, null));
        }

        verifyNoInteractions(fixture.appService(), fixture.codegenService(), fixture.versionMapper());
    }

    @Test
    @DisplayName("publish rejects missing tenant before application access")
    void publishRejectsMissingTenantBeforeDataAccess() {
        PublishFixture fixture = publishFixture();

        assertThrows(BusinessException.class, () -> fixture.service().publish(1L, null));

        verifyNoInteractions(fixture.appService(), fixture.versionMapper());
    }

    @Test
    @DisplayName("post publish processing restores the event tenant scope")
    void postPublishRestoresTenantScope() {
        LowcodePublishService publishService = mock(LowcodePublishService.class);
        AiCrudConfigService configService = mock(AiCrudConfigService.class);
        LowcodePublishPostProcessor processor = new LowcodePublishPostProcessor(publishService, configService);
        AiCrudConfig config = config(9L);
        doAnswer(invocation -> {
            assertEquals(9L, TenantContextHolder.getTenantId());
            return null;
        }).when(publishService).registerOrUpdateMenuAsync(any(), anyBoolean(), any());

        processor.handlePostPublish(new LowcodePublishPostEvent(
                config, null, null, false, null, 9L));

        verify(publishService).syncBusinessRuntimeEntry(config, null, null);
        verify(configService).updateById(config);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("post publish processing rejects a mismatched event tenant")
    void postPublishRejectsMismatchedTenant() {
        LowcodePublishService publishService = mock(LowcodePublishService.class);
        AiCrudConfigService configService = mock(AiCrudConfigService.class);
        LowcodePublishPostProcessor processor = new LowcodePublishPostProcessor(publishService, configService);

        processor.handlePostPublish(new LowcodePublishPostEvent(
                config(8L), null, null, false, null, 9L));

        verifyNoInteractions(publishService, configService);
    }

    private static CodegenFixture codegenFixture() {
        LowcodeAppService appService = mock(LowcodeAppService.class);
        AiCrudCodegenService codegenService = mock(AiCrudCodegenService.class);
        AiCrudConfigVersionMapper versionMapper = mock(AiCrudConfigVersionMapper.class);
        LowcodeCodegenService service = new LowcodeCodegenService(
                mock(ObjectMapper.class), appService, mock(LowcodeDomainService.class),
                mock(LowcodeRuntimeConfigBuilder.class), codegenService,
                mock(AiCrudConfigService.class), versionMapper);
        return new CodegenFixture(service, appService, codegenService, versionMapper);
    }

    private static PublishFixture publishFixture() {
        LowcodeAppService appService = mock(LowcodeAppService.class);
        AiCrudConfigVersionMapper versionMapper = mock(AiCrudConfigVersionMapper.class);
        LowcodePublishService service = new LowcodePublishService(
                mock(ObjectMapper.class), mock(AiCrudConfigService.class), appService,
                mock(LowcodeDomainService.class), mock(LowcodeRuntimeConfigBuilder.class),
                mock(LowcodeSchemaValidator.class), mock(LowcodeDdlService.class),
                mock(LowcodePolicyService.class), mock(MenuRegisterAdapter.class),
                versionMapper, mock(BusinessObjectMapper.class), mock(BusinessAppMapper.class),
                mock(AiLowcodeModelMapper.class), mock(LowcodeRuntimeDataSourceResolver.class));
        return new PublishFixture(service, appService, versionMapper);
    }

    private static AiCrudConfig config(Long tenantId) {
        AiCrudConfig config = new AiCrudConfig();
        config.setId(1L);
        config.setTenantId(tenantId);
        config.setConfigKey("orders");
        return config;
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setTenantId(tenantId);
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", 7L, null, tenantId,
                "pc", "lowcode-metadata-security-test", Set.of()));
    }

    private record CodegenFixture(
            LowcodeCodegenService service,
            LowcodeAppService appService,
            AiCrudCodegenService codegenService,
            AiCrudConfigVersionMapper versionMapper) {
    }

    private record PublishFixture(
            LowcodePublishService service,
            LowcodeAppService appService,
            AiCrudConfigVersionMapper versionMapper) {
    }
}
