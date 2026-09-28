package com.mdframe.forge.starter.auth.interceptor;

import cn.dev33.satoken.exception.NotPermissionException;
import com.mdframe.forge.starter.apiconfig.domain.dto.ApiConfigInfo;
import com.mdframe.forge.starter.apiconfig.service.IApiConfigManager;
import com.mdframe.forge.starter.auth.service.IPermissionService;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.context.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiPermissionInterceptorTest {

    private final IPermissionService permissionService = mock(IPermissionService.class);
    private final IApiConfigManager apiConfigManager = mock(IApiConfigManager.class);
    private final AuthProperties authProperties = new AuthProperties();
    private ApiPermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        authProperties.setEnableApiPermission(true);
        interceptor = new ApiPermissionInterceptor(permissionService, authProperties, apiConfigManager);
    }

    @Test
    void shouldAllowEndpointWithoutExplicitPermissionResource() throws Exception {
        MockHttpServletRequest request = request("/system/missing");
        when(permissionService.isApiPermissionConfigured("/system/missing", "OPTIONS")).thenReturn(false);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), securedHandler())).isTrue();

        verify(permissionService, never()).hasApiPermission("/system/missing", "OPTIONS");
    }

    @Test
    void shouldRejectWhenPermissionConfigurationLookupFails() throws Exception {
        MockHttpServletRequest request = request("/system/users");
        when(permissionService.isApiPermissionConfigured("/system/users", "OPTIONS"))
                .thenThrow(new IllegalStateException("cache unavailable"));

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), securedHandler()))
                .isInstanceOf(NotPermissionException.class);
    }

    @Test
    void shouldRejectWhenPermissionEvaluationFails() throws Exception {
        MockHttpServletRequest request = request("/system/users");
        when(permissionService.isApiPermissionConfigured("/system/users", "OPTIONS")).thenReturn(true);
        when(permissionService.hasApiPermission("/system/users", "OPTIONS"))
                .thenThrow(new IllegalStateException("permission cache unavailable"));

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), securedHandler()))
                .isInstanceOf(NotPermissionException.class);
    }

    @Test
    void shouldAllowOnlyExplicitAnonymousApiConfiguration() throws Exception {
        MockHttpServletRequest request = request("/public/status");
        ApiConfigInfo apiConfig = new ApiConfigInfo();
        apiConfig.setNeedAuth(false);
        when(apiConfigManager.getApiConfig("/public/status", "OPTIONS")).thenReturn(apiConfig);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), securedHandler())).isTrue();
        verify(permissionService, never()).isApiPermissionConfigured("/public/status", "OPTIONS");
    }

    @Test
    void shouldAllowAnnotationExemptionWithoutDependingOnConfigCache() throws Exception {
        MockHttpServletRequest request = request("/public/health");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), anonymousHandler())).isTrue();
        verify(apiConfigManager, never()).getApiConfig("/public/health", "OPTIONS");
    }

    @Test
    void shouldAllowConfiguredEndpointWhenUserHasPermission() throws Exception {
        MockHttpServletRequest request = request("/system/users");
        when(permissionService.isApiPermissionConfigured("/system/users", "OPTIONS")).thenReturn(true);
        when(permissionService.hasApiPermission("/system/users", "OPTIONS")).thenReturn(true);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), securedHandler())).isTrue();
    }

    @Test
    void shouldRejectConfiguredEndpointWhenUserDoesNotHavePermission() throws Exception {
        MockHttpServletRequest request = request("/system/users");
        when(permissionService.isApiPermissionConfigured("/system/users", "OPTIONS")).thenReturn(true);
        when(permissionService.hasApiPermission("/system/users", "OPTIONS")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), securedHandler()))
                .isInstanceOf(NotPermissionException.class)
                .hasMessageContaining("/system/users");
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", uri);
        request.setRequestURI(uri);
        return request;
    }

    private HandlerMethod securedHandler() throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod("secured"));
    }

    private HandlerMethod anonymousHandler() throws NoSuchMethodException {
        return new HandlerMethod(new TestController(), TestController.class.getMethod("anonymous"));
    }

    private static final class TestController {

        public void secured() {
        }

        @ApiPermissionIgnore
        public void anonymous() {
        }
    }
}
