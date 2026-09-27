package com.mdframe.forge.plugin.external.service.impl;

import com.mdframe.forge.plugin.external.adapter.DataAdapterFactory;
import com.mdframe.forge.plugin.external.adapter.impl.JsonPathAdapter;
import com.mdframe.forge.plugin.external.adapter.impl.ScriptAdapter;
import com.mdframe.forge.plugin.external.constant.ExternalPermissions;
import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.service.ExternalApiLogService;
import com.mdframe.forge.plugin.external.service.ExternalApiService;
import com.mdframe.forge.plugin.external.service.ExternalSystemService;
import com.mdframe.forge.plugin.external.strategy.ExternalAuthStrategyFactory;
import com.mdframe.forge.plugin.external.support.ExternalPermissionGuard;
import com.mdframe.forge.plugin.external.support.ExternalRateLimitManager;
import com.mdframe.forge.plugin.external.support.ExternalResponseCache;
import com.mdframe.forge.plugin.external.support.ExternalRetryExecutor;
import com.mdframe.forge.plugin.external.support.ExternalSensitiveDataMasker;
import com.mdframe.forge.plugin.external.vo.ExternalApiDebugResult;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.crypto.crypto.EncryptorFactory;
import com.mdframe.forge.starter.outbound.client.SecureOutboundClient;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalProxyServiceImplTest {

    private final ExternalApiService apiService = mock(ExternalApiService.class);
    private final ExternalSystemService systemService = mock(ExternalSystemService.class);
    private final SecureOutboundClient outboundClient = mock(SecureOutboundClient.class);
    private final DataAdapterFactory adapterFactory = mock(DataAdapterFactory.class);
    private final ExternalProxyServiceImpl service = new ExternalProxyServiceImpl(
            apiService,
            systemService,
            mock(ExternalApiLogService.class),
            mock(ExternalAuthStrategyFactory.class),
            adapterFactory,
            mock(EncryptorFactory.class),
            new ExternalPermissionGuard(),
            mock(ExternalRateLimitManager.class),
            new ExternalResponseCache(mock(ICacheService.class)),
            new ExternalRetryExecutor(),
            new ExternalSensitiveDataMasker(),
            outboundClient);

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldReturnConfiguredMockResponseWithoutOutboundCall() {
        ExternalApi api = new ExternalApi();
        api.setId(1L);
        api.setSystemId(10L);
        api.setApiCode("member-by-mobile");
        api.setApiName("手机号查询会员");
        api.setApiMethod("POST");
        api.setApiStatus(1);
        api.setExecutionMode("MOCK");
        api.setMockResponseJson("{\"memberId\":\"M000001\",\"memberName\":\"测试会员\"}");
        secure(api);

        when(apiService.getById(1L)).thenReturn(api);

        Object response;
        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            response = service.proxyRequest(1L, Map.of("mobile", "13800138000"));
        }

        Map<?, ?> result = assertInstanceOf(Map.class, response);
        assertEquals("M000001", result.get("memberId"));
        assertEquals("测试会员", result.get("memberName"));
        verify(systemService, never()).getRuntimeById(10L);
        verify(outboundClient, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldExposeMockDebugMetadata() {
        ExternalApi api = new ExternalApi();
        api.setId(2L);
        api.setSystemId(10L);
        api.setApiCode("product-by-barcode");
        api.setApiName("条码查询商品");
        api.setApiMethod("POST");
        api.setApiStatus(1);
        api.setExecutionMode("MOCK");
        api.setMockResponseJson("{\"productName\":\"测试商品\"}");
        secure(api);

        when(apiService.getById(2L)).thenReturn(api);

        ExternalApiDebugResult result;
        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            result = service.debugRequest(2L, Map.of("barcode", "690000000001"));
        }

        assertTrue(result.getSuccess());
        assertEquals(200, result.getHttpStatusCode());
        Map<?, ?> payload = assertInstanceOf(Map.class, result.getResponseData());
        assertEquals("测试商品", payload.get("productName"));
    }

    @Test
    void shouldApplyRestrictedFieldMappingToMockResponse() {
        ExternalApi api = mockApi(3L);
        api.setMockResponseJson("{\"payload\":{\"items\":[{\"id\":\"M1\",\"profile\":{\"name\":\"会员甲\"}}]}}");
        api.setResponseTransformEnabled(true);
        api.setResponseTransformScript("""
                {"version":"FIELD_MAP_V1","sourcePath":"payload.items",
                 "fieldMapping":{"memberId":"id","memberName":"profile.name"},"targetPath":"records"}
                """);
        when(apiService.getById(3L)).thenReturn(api);
        when(adapterFactory.getRequiredAdapter("JsonPath")).thenReturn(new JsonPathAdapter());

        Object response;
        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            response = service.proxyRequest(3L, Map.of());
        }

        Map<?, ?> envelope = assertInstanceOf(Map.class, response);
        Map<?, ?> data = assertInstanceOf(Map.class, envelope.get("data"));
        java.util.List<?> records = assertInstanceOf(java.util.List.class, data.get("records"));
        Map<?, ?> record = assertInstanceOf(Map.class, records.get(0));
        assertEquals("M1", record.get("memberId"));
        assertEquals("会员甲", record.get("memberName"));
    }

    @Test
    void shouldRejectLegacyResponseScriptWithoutExecution() {
        ExternalApi api = mockApi(4L);
        api.setMockResponseJson("{\"value\":1}");
        api.setResponseTransformEnabled(true);
        api.setResponseTransformScript("while (true) { response.value++; }");
        when(apiService.getById(4L)).thenReturn(api);
        when(adapterFactory.getRequiredAdapter("JsonPath")).thenReturn(new JsonPathAdapter());
        when(adapterFactory.getRequiredAdapter("Script")).thenReturn(new ScriptAdapter());

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.proxyRequest(4L, Map.of()));
            assertEquals(ScriptAdapter.MIGRATION_MESSAGE, error.getMessage());
        }
    }

    private ExternalApi mockApi(Long id) {
        ExternalApi api = new ExternalApi();
        api.setId(id);
        api.setSystemId(10L);
        api.setApiCode("mock-" + id);
        api.setApiName("Mock " + id);
        api.setApiMethod("POST");
        api.setApiStatus(1);
        api.setExecutionMode("MOCK");
        secure(api);
        return api;
    }

    private void secure(ExternalApi api) {
        api.setPermissionCheckEnabled(true);
        api.setRequiredPermission(ExternalPermissions.PROXY_INVOKE);
    }

    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser();
        user.setUserId(8L);
        user.setTenantId(1L);
        user.setUserType(2);
        user.setPermissions(Set.of(ExternalPermissions.PROXY_INVOKE));
        return new ExecutionIdentity(user, "USER", 8L, null, 1L, "test", "token", Set.of());
    }
}
