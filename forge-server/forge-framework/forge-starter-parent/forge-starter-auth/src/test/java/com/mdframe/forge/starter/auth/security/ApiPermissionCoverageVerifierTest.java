package com.mdframe.forge.starter.auth.security;

import cn.dev33.satoken.annotation.SaIgnore;
import com.mdframe.forge.starter.auth.service.IPermissionService;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.context.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiPermissionCoverageVerifierTest {

    private final RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
    private final IPermissionService permissionService = mock(IPermissionService.class);
    private final AuthProperties authProperties = new AuthProperties();
    private ApiPermissionCoverageVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        authProperties.setApiPermissionCoverageEnabled(true);
        authProperties.setApiPermissionCoverageFailOnMissing(true);
        authProperties.setApiPermissionCoverageReportLimit(20);
        verifier = new ApiPermissionCoverageVerifier(mapping, permissionService, authProperties);

        TestController controller = new TestController();
        mapping.registerMapping(
                RequestMappingInfo.paths("/secured/{id}").methods(RequestMethod.GET).build(),
                controller,
                TestController.class.getMethod("secured"));
        mapping.registerMapping(
                RequestMappingInfo.paths("/write", "/write-alias")
                        .methods(RequestMethod.POST, RequestMethod.PUT).build(),
                controller,
                TestController.class.getMethod("write"));
        mapping.registerMapping(
                RequestMappingInfo.paths("/anonymous").methods(RequestMethod.GET).build(),
                controller,
                TestController.class.getMethod("anonymous"));
        mapping.registerMapping(
                RequestMappingInfo.paths("/sa-anonymous").methods(RequestMethod.GET).build(),
                controller,
                TestController.class.getMethod("saAnonymous"));
        mapping.registerMapping(
                RequestMappingInfo.paths("/auth/login").methods(RequestMethod.POST).build(),
                controller,
                TestController.class.getMethod("configuredExclude"));
    }

    @Test
    void shouldExpandEveryPathAndMethodAndReportMissingRoutes() {
        when(permissionService.isApiPermissionConfigured("/secured/{id}", "GET")).thenReturn(true);
        when(permissionService.isApiPermissionConfigured("/write", "POST")).thenReturn(true);
        when(permissionService.isApiPermissionConfigured("/write", "PUT")).thenReturn(false);
        when(permissionService.isApiPermissionConfigured("/write-alias", "POST"))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(permissionService.isApiPermissionConfigured("/write-alias", "PUT")).thenReturn(false);

        ApiPermissionCoverageVerifier.CoverageReport report = verifier.verifyCoverage();

        assertThat(report.protectedRouteCount()).isEqualTo(5);
        assertThat(report.configuredRouteCount()).isEqualTo(2);
        assertThat(report.exemptRouteCount()).isEqualTo(3);
        assertThat(report.missingRoutes())
                .extracting(ApiPermissionCoverageVerifier.RouteCoverage::routeKey)
                .containsExactly(
                        "POST /write-alias",
                        "PUT /write",
                        "PUT /write-alias");
        assertThat(report.missingRoutes().get(0).lookupFailed()).isTrue();
    }

    @Test
    void shouldBlockStartupInEnforceModeWhenProtectedRouteIsMissing() {
        when(permissionService.isApiPermissionConfigured("/secured/{id}", "GET")).thenReturn(true);
        when(permissionService.isApiPermissionConfigured("/write", "POST")).thenReturn(true);
        when(permissionService.isApiPermissionConfigured("/write", "PUT")).thenReturn(true);
        when(permissionService.isApiPermissionConfigured("/write-alias", "POST")).thenReturn(true);
        when(permissionService.isApiPermissionConfigured("/write-alias", "PUT")).thenReturn(false);

        assertThatThrownBy(verifier::verifyAndEnforce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUT /write-alias");
    }

    @Test
    void shouldOnlyReportWhenEnforcementIsDisabled() {
        authProperties.setApiPermissionCoverageFailOnMissing(false);

        assertThat(verifier.verifyAndEnforce().missingRoutes()).hasSize(5);
    }

    @Test
    void shouldUseReportModeByDefaultDuringLegacyResourceInventory() throws Exception {
        AuthProperties defaultProperties = new AuthProperties();
        defaultProperties.setApiPermissionCoverageEnabled(true);
        defaultProperties.setApiPermissionCoverageReportLimit(20);
        ApiPermissionCoverageVerifier defaultVerifier =
                new ApiPermissionCoverageVerifier(mapping, permissionService, defaultProperties);

        assertThat(defaultProperties.getApiPermissionCoverageFailOnMissing()).isFalse();
        assertThat(defaultVerifier.verifyAndEnforce().missingRoutes()).hasSize(5);
    }

    public static final class TestController {

        public void secured() {
        }

        public void write() {
        }

        @ApiPermissionIgnore
        public void anonymous() {
        }

        @SaIgnore
        public void saAnonymous() {
        }

        public void configuredExclude() {
        }
    }
}
