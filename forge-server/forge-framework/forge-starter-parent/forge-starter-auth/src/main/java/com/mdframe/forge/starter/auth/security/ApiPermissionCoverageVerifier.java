package com.mdframe.forge.starter.auth.security;

import cn.dev33.satoken.annotation.SaIgnore;
import com.mdframe.forge.starter.auth.service.IPermissionService;
import com.mdframe.forge.starter.auth.util.PathMatcher;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.context.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Verifies that every Controller route protected by {@code ApiPermissionInterceptor}
 * has a matching API resource. Missing resources and lookup failures are both
 * treated as uncovered so CI/startup can fail closed before traffic is accepted.
 */
@Slf4j
@Component
public class ApiPermissionCoverageVerifier implements ApplicationRunner {

    private static final String ALL_METHODS = "*";
    private static final int MAX_REPORT_LIMIT = 1000;
    private static final List<String> BUILT_IN_EXCLUDED_PATHS = List.of(
            "/crypto/**",
            "/.well-known/**",
            "/oauth2/**",
            "/ai/capability/oauth/**",
            "/mcp",
            "/openapi/v1/jobs",
            "/openapi/v1/jobs/**",
            "/openapi/v1/executions/**",
            "/openapi/v1/capabilities/**",
            "/static/**",
            "/css/**",
            "/js/**",
            "/images/**",
            "/error",
            "/actuator/health",
            "/health",
            "/ws/**"
    );

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final IPermissionService permissionService;
    private final AuthProperties authProperties;

    public ApiPermissionCoverageVerifier(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappingHandlerMapping,
            IPermissionService permissionService,
            AuthProperties authProperties) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.permissionService = permissionService;
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        verifyAndEnforce();
    }

    public CoverageReport verifyAndEnforce() {
        if (!Boolean.TRUE.equals(authProperties.getApiPermissionCoverageEnabled())) {
            log.warn("API权限资源覆盖检查已关闭；仅允许用于受控灰度盘点");
            return CoverageReport.disabled();
        }

        CoverageReport report = verifyCoverage();
        logReport(report);
        if (Boolean.TRUE.equals(authProperties.getApiPermissionCoverageFailOnMissing())
                && !report.missingRoutes().isEmpty()) {
            throw new IllegalStateException("API权限资源覆盖检查失败，缺失或不可验证路由: "
                    + summarizeMissing(report.missingRoutes()));
        }
        return report;
    }

    public CoverageReport verifyCoverage() {
        Map<String, RouteCandidate> protectedRoutes = new TreeMap<>();
        Set<String> exemptRoutes = new TreeSet<>();

        collectRoutes(protectedRoutes, exemptRoutes);

        List<RouteCoverage> missingRoutes = new ArrayList<>();
        int configuredRouteCount = 0;

        for (RouteCandidate route : protectedRoutes.values()) {
            try {
                if (permissionService.isApiPermissionConfigured(route.path(), route.requestMethod())) {
                    configuredRouteCount++;
                } else {
                    missingRoutes.add(RouteCoverage.missing(
                            route.requestMethod(), route.path(), route.handler()));
                }
            } catch (RuntimeException exception) {
                missingRoutes.add(RouteCoverage.lookupFailure(
                        route.requestMethod(), route.path(), route.handler()));
            }
        }

        missingRoutes.sort(Comparator.comparing(RouteCoverage::routeKey));
        return new CoverageReport(true, protectedRoutes.size(), configuredRouteCount,
                exemptRoutes.size(), List.copyOf(missingRoutes));
    }

    private void collectRoutes(Map<String, RouteCandidate> protectedRoutes, Set<String> exemptRoutes) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            for (String path : extractPaths(entry.getKey())) {
                for (String requestMethod : extractMethods(entry.getKey())) {
                    String routeKey = requestMethod + " " + path;
                    if (isExempt(handler, path)) {
                        if (!protectedRoutes.containsKey(routeKey)) {
                            exemptRoutes.add(routeKey);
                        }
                    } else {
                        protectedRoutes.putIfAbsent(routeKey,
                                new RouteCandidate(requestMethod, path, handler));
                        exemptRoutes.remove(routeKey);
                    }
                }
            }
        }
    }

    private boolean isExempt(HandlerMethod handler, String path) {
        Class<?> beanType = handler.getBeanType();
        if (handler.hasMethodAnnotation(ApiPermissionIgnore.class)
                || AnnotatedElementUtils.hasAnnotation(beanType, ApiPermissionIgnore.class)
                || handler.hasMethodAnnotation(SaIgnore.class)
                || AnnotatedElementUtils.hasAnnotation(beanType, SaIgnore.class)) {
            return true;
        }
        if (PathMatcher.matchAny(BUILT_IN_EXCLUDED_PATHS, path)) {
            return true;
        }
        String[] configuredExcludes = authProperties.getApiPermissionExcludePaths();
        return configuredExcludes != null && PathMatcher.matchAny(List.of(configuredExcludes), path);
    }

    private List<String> extractPaths(RequestMappingInfo mappingInfo) {
        Set<String> paths = new LinkedHashSet<>();
        if (mappingInfo.getPathPatternsCondition() != null) {
            for (PathPattern pattern : mappingInfo.getPathPatternsCondition().getPatterns()) {
                paths.add(pattern.getPatternString());
            }
        }
        if (paths.isEmpty() && mappingInfo.getPatternsCondition() != null) {
            paths.addAll(mappingInfo.getPatternsCondition().getPatterns());
        }
        return paths.stream().sorted().toList();
    }

    private List<String> extractMethods(RequestMappingInfo mappingInfo) {
        Set<RequestMethod> requestMethods = mappingInfo.getMethodsCondition().getMethods();
        if (requestMethods == null || requestMethods.isEmpty()) {
            return List.of(ALL_METHODS);
        }
        return requestMethods.stream().map(RequestMethod::name).sorted().toList();
    }

    private void logReport(CoverageReport report) {
        if (report.missingRoutes().isEmpty()) {
            log.info("API权限资源覆盖检查通过: protected={}, configured={}, exempt={}",
                    report.protectedRouteCount(), report.configuredRouteCount(), report.exemptRouteCount());
            return;
        }
        int reportLimit = normalizeReportLimit(authProperties.getApiPermissionCoverageReportLimit());
        List<String> sample = report.missingRoutes().stream()
                .limit(reportLimit)
                .map(RouteCoverage::displayValue)
                .toList();
        log.error("API权限资源覆盖检查发现缺失或不可验证路由: protected={}, configured={}, exempt={}, missing={}, sample={}",
                report.protectedRouteCount(), report.configuredRouteCount(), report.exemptRouteCount(),
                report.missingRoutes().size(), sample);
    }

    private String summarizeMissing(List<RouteCoverage> missingRoutes) {
        int limit = normalizeReportLimit(authProperties.getApiPermissionCoverageReportLimit());
        String summary = String.join(", ", missingRoutes.stream()
                .limit(limit)
                .map(RouteCoverage::routeKey)
                .toList());
        if (missingRoutes.size() > limit) {
            summary += ", ... total=" + missingRoutes.size();
        }
        return summary;
    }

    private int normalizeReportLimit(Integer configuredLimit) {
        if (configuredLimit == null) {
            return 100;
        }
        return Math.max(1, Math.min(configuredLimit, MAX_REPORT_LIMIT));
    }

    private record RouteCandidate(String requestMethod, String path, HandlerMethod handler) {
    }

    public record CoverageReport(boolean enabled,
                                 int protectedRouteCount,
                                 int configuredRouteCount,
                                 int exemptRouteCount,
                                 List<RouteCoverage> missingRoutes) {

        private static CoverageReport disabled() {
            return new CoverageReport(false, 0, 0, 0, List.of());
        }
    }

    public record RouteCoverage(String requestMethod,
                                String path,
                                String handler,
                                boolean lookupFailed) {

        private static RouteCoverage missing(String requestMethod, String path, HandlerMethod handler) {
            return new RouteCoverage(requestMethod, path, handlerName(handler), false);
        }

        private static RouteCoverage lookupFailure(String requestMethod, String path, HandlerMethod handler) {
            return new RouteCoverage(requestMethod, path, handlerName(handler), true);
        }

        public String routeKey() {
            return requestMethod + " " + path;
        }

        private String displayValue() {
            return routeKey() + " -> " + handler + (lookupFailed ? " [lookup-failed]" : "");
        }

        private static String handlerName(HandlerMethod handler) {
            return handler.getBeanType().getName() + "#" + handler.getMethod().getName();
        }
    }
}
