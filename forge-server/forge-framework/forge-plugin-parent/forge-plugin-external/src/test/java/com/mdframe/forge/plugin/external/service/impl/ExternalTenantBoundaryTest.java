package com.mdframe.forge.plugin.external.service.impl;

import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.entity.ExternalApiLog;
import com.mdframe.forge.plugin.external.entity.ExternalSystem;
import com.mdframe.forge.plugin.external.mapper.ExternalApiLogMapper;
import com.mdframe.forge.plugin.external.mapper.ExternalApiMapper;
import com.mdframe.forge.plugin.external.mapper.ExternalSystemMapper;
import com.mdframe.forge.plugin.external.support.ExternalSecretService;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalTenantBoundaryTest {

    private static final Long TENANT_ID = 7L;

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void apiReadsMustPassTrustedTenantToMapper() {
        ExternalApiMapper apiMapper = mock(ExternalApiMapper.class);
        ExternalSystemMapper systemMapper = mock(ExternalSystemMapper.class);
        ExternalApiServiceImpl service = new ExternalApiServiceImpl(apiMapper, systemMapper);
        ExternalApi api = new ExternalApi();
        when(apiMapper.selectApiById(11L, TENANT_ID)).thenReturn(api);
        when(apiMapper.deleteApiById(11L, TENANT_ID)).thenReturn(1);

        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity()) {
            assertThat(service.getManagementById(11L)).isSameAs(api);
            service.listBySystemId(22L);
            service.getByCode("members", 22L);
            assertThat(service.removeApi(11L)).isTrue();
        }

        verify(apiMapper).selectApiById(11L, TENANT_ID);
        verify(apiMapper).selectApisBySystemId(22L, TENANT_ID);
        verify(apiMapper).selectApiByCode("members", 22L, TENANT_ID);
        verify(apiMapper).deleteApiById(11L, TENANT_ID);
    }

    @Test
    void apiWriteMustRejectSystemOutsideCurrentTenant() {
        ExternalApiMapper apiMapper = mock(ExternalApiMapper.class);
        ExternalSystemMapper systemMapper = mock(ExternalSystemMapper.class);
        ExternalApiServiceImpl service = new ExternalApiServiceImpl(apiMapper, systemMapper);
        ExternalApi api = new ExternalApi();
        api.setSystemId(99L);

        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity()) {
            assertThatThrownBy(() -> service.saveApi(api))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("外部系统不存在");
        }

        verify(systemMapper).selectSystemById(99L, TENANT_ID);
        verify(apiMapper, never()).insert(api);
    }

    @Test
    void apiWriteMustBindCurrentTenantAndRejectCrossTenantRecord() {
        ExternalApiMapper apiMapper = mock(ExternalApiMapper.class);
        ExternalSystemMapper systemMapper = mock(ExternalSystemMapper.class);
        ExternalApiServiceImpl service = new ExternalApiServiceImpl(apiMapper, systemMapper);
        ExternalSystem system = new ExternalSystem();
        system.setId(99L);
        when(systemMapper.selectSystemById(99L, TENANT_ID)).thenReturn(system);
        when(apiMapper.insert(org.mockito.ArgumentMatchers.any(ExternalApi.class))).thenReturn(1);

        ExternalApi api = new ExternalApi();
        api.setSystemId(99L);
        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity()) {
            assertThat(service.saveApi(api)).isTrue();
        }

        ArgumentCaptor<ExternalApi> captor = ArgumentCaptor.forClass(ExternalApi.class);
        verify(apiMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);

        ExternalApi foreignUpdate = new ExternalApi();
        foreignUpdate.setId(123L);
        foreignUpdate.setSystemId(99L);
        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity()) {
            assertThatThrownBy(() -> service.updateApi(foreignUpdate))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("外部接口不存在");
        }
        verify(apiMapper, never()).updateById(foreignUpdate);
    }

    @Test
    void systemAndLogDetailAndDeleteMustUseCurrentTenant() {
        ExternalSystemMapper systemMapper = mock(ExternalSystemMapper.class);
        ExternalSecretService secretService = mock(ExternalSecretService.class);
        ExternalSystemServiceImpl systemService = new ExternalSystemServiceImpl(systemMapper, secretService);
        ExternalSystem system = new ExternalSystem();
        when(systemMapper.selectSystemById(31L, TENANT_ID)).thenReturn(system);
        when(secretService.forManagement(system)).thenReturn(system);
        when(systemMapper.deleteSystemById(31L, TENANT_ID)).thenReturn(1);

        ExternalApiLogMapper logMapper = mock(ExternalApiLogMapper.class);
        ExternalApiLogServiceImpl logService = new ExternalApiLogServiceImpl(logMapper);
        ExternalApiLog log = new ExternalApiLog();
        when(logMapper.selectLogById(41L, TENANT_ID)).thenReturn(log);
        when(logMapper.deleteLogById(41L, TENANT_ID)).thenReturn(1);

        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity()) {
            assertThat(systemService.getManagementById(31L)).isSameAs(system);
            assertThat(systemService.removeSystem(31L)).isTrue();
            assertThat(logService.getScopedById(41L)).isSameAs(log);
            assertThat(logService.removeScopedById(41L)).isTrue();
        }

        verify(systemMapper).selectSystemById(31L, TENANT_ID);
        verify(systemMapper).deleteSystemById(31L, TENANT_ID);
        verify(logMapper).selectLogById(41L, TENANT_ID);
        verify(logMapper).deleteLogById(41L, TENANT_ID);
    }

    private ExecutionIdentityContextHolder.Scope openIdentity() {
        LoginUser user = new LoginUser();
        user.setUserId(8L);
        user.setTenantId(TENANT_ID);
        user.setUserType(2);
        user.setPermissions(Set.of());
        return ExecutionIdentityContextHolder.open(
                new ExecutionIdentity(user, "USER", 8L, null, TENANT_ID, "test", "token", Set.of()));
    }
}
