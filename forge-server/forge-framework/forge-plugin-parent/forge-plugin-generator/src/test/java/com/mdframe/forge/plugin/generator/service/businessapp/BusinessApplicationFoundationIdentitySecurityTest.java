package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessSuite;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessBindingQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessSuiteMapper;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Business application foundation identity security")
class BusinessApplicationFoundationIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("entry open rejects missing tenant before entry and runtime config reads")
    void entryOpenRejectsMissingTenantBeforeDataAccess() {
        BusinessAppMapper appMapper = mock(BusinessAppMapper.class);
        AiCrudConfigMapper configMapper = mock(AiCrudConfigMapper.class);
        BusinessAppOpenService service = new BusinessAppOpenService(appMapper, configMapper);

        assertThrows(BusinessException.class, () -> service.openInfo(10L));

        verifyNoInteractions(appMapper, configMapper);
    }

    @Test
    @DisplayName("binding list rejects missing tenant before mapper access")
    void bindingRejectsMissingTenantBeforeDataAccess() throws Exception {
        BusinessBindingMapper mapper = mock(BusinessBindingMapper.class);
        BusinessBindingService service = new BusinessBindingService(
                mock(BusinessSuiteService.class), mock(BusinessObjectService.class),
                mock(BusinessApplicationService.class), mock(BusinessAppService.class));
        setBaseMapper(service, mapper);

        assertThrows(BusinessException.class, () -> service.list((BusinessBindingQueryDTO) null));

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("suite detail rejects missing tenant before mapper access")
    void suiteRejectsMissingTenantBeforeDataAccess() throws Exception {
        BusinessSuiteMapper mapper = mock(BusinessSuiteMapper.class);
        BusinessSuiteService service = suiteService(mapper);

        assertThrows(BusinessException.class, () -> service.detail(10L));

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("suite entity reads use an explicit tenant predicate")
    void suiteEntityUsesExplicitTenantPredicate() throws Exception {
        BusinessSuiteMapper mapper = mock(BusinessSuiteMapper.class);
        BusinessSuiteService service = suiteService(mapper);
        AiBusinessSuite suite = new AiBusinessSuite();
        suite.setId(10L);
        suite.setTenantId(9L);
        when(mapper.selectBySuiteId(9L, 10L)).thenReturn(suite);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L)) {
            assertEquals(10L, service.requireEntity(10L).getId());
        }

        verify(mapper).selectBySuiteId(9L, 10L);
    }

    @Test
    @DisplayName("suite acceptance rejects missing tenant before suite and object reads")
    void suiteAcceptanceRejectsMissingTenantBeforeDataAccess() {
        BusinessSuiteMapper suiteMapper = mock(BusinessSuiteMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessObjectReadinessService readinessService = mock(BusinessObjectReadinessService.class);
        BusinessSuiteAcceptanceService service = new BusinessSuiteAcceptanceService(
                suiteMapper, objectMapper, readinessService);

        assertThrows(BusinessException.class, () -> service.acceptance("CRM"));

        verifyNoInteractions(suiteMapper, objectMapper, readinessService);
    }

    private static BusinessSuiteService suiteService(BusinessSuiteMapper mapper) throws Exception {
        BusinessSuiteService service = new BusinessSuiteService(
                mock(MenuRegisterAdapter.class), mock(BusinessAppMapper.class),
                mock(BusinessApplicationMapper.class));
        setBaseMapper(service, mapper);
        return service;
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setTenantId(tenantId);
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", 7L, null, tenantId,
                "pc", "application-foundation-security-test", Set.of()));
    }

    private static void setBaseMapper(Object service, Object mapper) throws Exception {
        Field field = ServiceImpl.class.getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(service, mapper);
    }
}
