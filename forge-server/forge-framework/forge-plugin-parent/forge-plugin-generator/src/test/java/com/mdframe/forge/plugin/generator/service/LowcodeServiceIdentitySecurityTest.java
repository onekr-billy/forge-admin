package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.mapper.AiCrudExportTaskMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessMessageChannelMapper;
import com.mdframe.forge.plugin.generator.mapper.CustomQuerySchemeMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessMessageChannelService;
import com.mdframe.forge.plugin.generator.service.excel.DynamicCrudExcelValueAdapter;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.datascope.service.IDataScopeService;
import com.mdframe.forge.starter.excel.spi.ExcelConfigProvider;
import com.mdframe.forge.starter.file.core.FileManager;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Low-code service identity security")
class LowcodeServiceIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
        TenantContextHolder.clear();
        TenantContextHolder.clearIgnore();
    }

    @Test
    @DisplayName("custom query schemes reject missing tenant before mapper access")
    void customQueryRejectsMissingTenantBeforeDataAccess() {
        CustomQuerySchemeMapper mapper = mock(CustomQuerySchemeMapper.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        CustomQueryService service = new CustomQueryService(mapper, dynamicCrudService, objectMapper);

        assertThrows(BusinessException.class, () -> service.listSchemes("orders"));

        verifyNoInteractions(mapper, dynamicCrudService, objectMapper);
    }

    @Test
    @DisplayName("message channel lookup rejects missing tenant before mapper access")
    void messageChannelRejectsMissingTenantBeforeDataAccess() {
        BusinessMessageChannelMapper mapper = mock(BusinessMessageChannelMapper.class);
        BusinessMessageChannelService service = new BusinessMessageChannelService(mapper);

        assertThrows(BusinessException.class, () -> service.resolveChannel("wechat_work"));

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("message recipient lookup rejects an explicit cross-tenant request")
    void messageRecipientLookupRejectsCrossTenantRequest() {
        BusinessMessageChannelMapper mapper = mock(BusinessMessageChannelMapper.class);
        BusinessMessageChannelService service = new BusinessMessageChannelService(mapper);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            assertThrows(BusinessException.class,
                    () -> service.selectUserIdsByRoleIds(List.of(1L), 8L, 2L));
        }

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("dynamic import template rejects missing tenant before runtime config access")
    void dynamicImportTemplateRejectsMissingTenantBeforeDataAccess() {
        ExcelFixture fixture = excelFixture();

        assertThrows(BusinessException.class,
                () -> fixture.service().downloadImportTemplate("orders", mock(HttpServletResponse.class)));

        verifyNoInteractions(fixture.dynamicCrudService(), fixture.exportTaskMapper());
    }

    @Test
    @DisplayName("async export loads the task through tenant and user ownership")
    void asyncExportUsesScopedTaskLookup() {
        ExcelFixture fixture = excelFixture();
        DynamicCrudExcelService.ExportExecutionContext context =
                new DynamicCrudExcelService.ExportExecutionContext(
                        5L, 9L, 7L, 2L, null, 0L, 100, 24);
        when(fixture.exportTaskMapper().selectTaskById(9L, 7L, 5L)).thenReturn(null);

        fixture.service().executeAsyncExportTask(5L, "orders", new DynamicCrudQuery(), context);

        verify(fixture.exportTaskMapper()).selectTaskById(9L, 7L, 5L);
        verify(fixture.exportTaskMapper(), never()).selectById(any());
        verifyNoInteractions(fixture.dynamicCrudService());
    }

    private static ExcelFixture excelFixture() {
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        AiCrudExportTaskMapper exportTaskMapper = mock(AiCrudExportTaskMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ExcelConfigProvider> excelConfigProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DynamicCrudAsyncExportWorker> asyncExportWorker = mock(ObjectProvider.class);
        DynamicCrudExcelService service = new DynamicCrudExcelService(
                dynamicCrudService,
                mock(ObjectMapper.class),
                mock(DynamicCrudExcelValueAdapter.class),
                excelConfigProvider,
                asyncExportWorker,
                exportTaskMapper,
                mock(FileManager.class),
                mock(IDataScopeService.class));
        return new ExcelFixture(service, dynamicCrudService, exportTaskMapper);
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId, Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setTenantId(tenantId);
        loginUser.setUserId(userId);
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", userId, null, tenantId,
                "pc", "lowcode-service-security-test", Set.of()));
    }

    private record ExcelFixture(
            DynamicCrudExcelService service,
            DynamicCrudService dynamicCrudService,
            AiCrudExportTaskMapper exportTaskMapper) {
    }
}
