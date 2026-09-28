package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectRelationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessPermissionMapper;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.datascope.service.IDataScopeService;
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

@DisplayName("Business application catalog identity security")
class BusinessApplicationCatalogIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("access entry rejects missing tenant before data access")
    void accessEntryRejectsMissingTenantBeforeDataAccess() throws Exception {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessAppOpenService openService = mock(BusinessAppOpenService.class);
        BusinessAppService service = new BusinessAppService(
                mock(BusinessSuiteService.class), mock(BusinessObjectService.class),
                mock(BusinessApplicationService.class), openService, mock(MenuRegisterAdapter.class));
        setBaseMapper(service, mapper);

        assertThrows(BusinessException.class, () -> service.detail(10L));
        assertThrows(BusinessException.class, () -> service.openInfo(10L));

        verifyNoInteractions(mapper, openService);
    }

    @Test
    @DisplayName("permission overview rejects missing tenant before scope and binding reads")
    void permissionOverviewRejectsMissingTenantBeforeDataAccess() {
        IDataScopeService dataScopeService = mock(IDataScopeService.class);
        BusinessBindingMapper bindingMapper = mock(BusinessBindingMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessPermissionMapper permissionMapper = mock(BusinessPermissionMapper.class);
        BusinessPermissionService service = new BusinessPermissionService(
                dataScopeService, bindingMapper, objectMapper, permissionMapper);

        assertThrows(BusinessException.class, () -> service.getPermissionOverview("ORDER"));

        verifyNoInteractions(dataScopeService, bindingMapper, objectMapper, permissionMapper);
    }

    @Test
    @DisplayName("permission summary reads the object through an explicit tenant predicate")
    void permissionSummaryUsesExplicitTenantPredicate() {
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessPermissionMapper permissionMapper = mock(BusinessPermissionMapper.class);
        BusinessPermissionService service = new BusinessPermissionService(
                mock(IDataScopeService.class), mock(BusinessBindingMapper.class),
                objectMapper, permissionMapper);
        AiBusinessObject object = new AiBusinessObject();
        object.setId(10L);
        object.setObjectCode("ORDER");
        object.setObjectName("订单");
        when(objectMapper.selectByIdForTenant(9L, 10L)).thenReturn(object);
        when(permissionMapper.selectExistingPermissions(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.List.of());

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L)) {
            assertEquals(10L, service.documentActionSummary(10L).getObjectId());
        }

        verify(objectMapper).selectByIdForTenant(9L, 10L);
    }

    @Test
    @DisplayName("object relation rejects missing tenant before resolving the object")
    void objectRelationRejectsMissingTenantBeforeDataAccess() throws Exception {
        BusinessObjectService objectService = mock(BusinessObjectService.class);
        BusinessObjectRelationMapper mapper = mock(BusinessObjectRelationMapper.class);
        BusinessObjectRelationService service = new BusinessObjectRelationService(objectService);
        setBaseMapper(service, mapper);

        assertThrows(BusinessException.class, () -> service.listByObject(10L));

        verifyNoInteractions(objectService, mapper);
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(7L);
        loginUser.setTenantId(tenantId);
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", 7L, null, tenantId,
                "pc", "application-catalog-security-test", Set.of()));
    }

    private static void setBaseMapper(Object service, Object mapper) throws Exception {
        Field field = ServiceImpl.class.getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(service, mapper);
    }
}
