package com.mdframe.forge.plugin.external.service.impl;

import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.mapper.ExternalApiMapper;
import com.mdframe.forge.plugin.external.service.ExternalProxyService;
import com.mdframe.forge.plugin.external.support.ExternalPermissionGuard;
import com.mdframe.forge.plugin.external.support.ExternalQueryContractValidator;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalQuerySourceServiceImplTest {

    private final ExternalApiMapper mapper = mock(ExternalApiMapper.class);
    private final ExternalProxyService proxyService = mock(ExternalProxyService.class);
    private final ExternalQuerySourceServiceImpl service = new ExternalQuerySourceServiceImpl(
            mapper, proxyService, new ExternalPermissionGuard(), new ExternalQueryContractValidator());

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldListOnlySourcesAllowedByConfiguredPermission() {
        ExternalApi allowed = source("crm", "member_query", "external:member:query");
        ExternalApi denied = source("erp", "product_query", "external:product:query");
        when(mapper.selectLowcodeQuerySources(1L)).thenReturn(List.of(allowed, denied));

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            List<ExternalApi> result = service.listAvailable();

            assertEquals(List.of(allowed), result);
            assertEquals("crm/member_query", service.sourceKey(allowed));
        }
    }

    @Test
    void shouldValidateInputBeforeDelegatingToExistingProxy() {
        ExternalApi source = source("crm", "member_query", "external:member:query");
        source.setInputSchemaJson("[{\"name\":\"mobile\",\"type\":\"string\",\"required\":true}]");
        when(mapper.selectLowcodeQuerySourceByKey(1L, "crm", "member_query")).thenReturn(source);
        when(proxyService.proxyRequest(eq(20L), eq(Map.of("mobile", "13800138000"))))
                .thenReturn(Map.of("memberId", "M1"));

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            Object result = service.execute("crm/member_query", Map.of("mobile", "13800138000"));

            assertEquals(Map.of("memberId", "M1"), result);
            verify(proxyService).proxyRequest(20L, Map.of("mobile", "13800138000"));
            assertThrows(BusinessException.class,
                    () -> service.execute("crm/member_query", Map.of("unexpected", "value")));
        }
    }

    private ExternalApi source(String systemCode, String apiCode, String permission) {
        ExternalApi api = new ExternalApi();
        api.setId(20L);
        api.setSystemCode(systemCode);
        api.setApiCode(apiCode);
        api.setPermissionCheckEnabled(true);
        api.setRequiredPermission(permission);
        api.setLowcodeQueryEnabled(true);
        api.setInputSchemaJson("[]");
        api.setOutputSchemaJson("[{\"name\":\"id\",\"path\":\"id\",\"type\":\"string\"}]");
        return api;
    }

    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser();
        user.setUserId(8L);
        user.setTenantId(1L);
        user.setUserType(2);
        user.setPermissions(Set.of("external:member:query"));
        return new ExecutionIdentity(user, "USER", 8L, null, 1L, "test", "token", Set.of());
    }
}
