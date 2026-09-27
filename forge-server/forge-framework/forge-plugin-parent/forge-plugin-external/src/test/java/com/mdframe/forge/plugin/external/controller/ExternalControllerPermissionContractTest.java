package com.mdframe.forge.plugin.external.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.external.constant.ExternalPermissions;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalControllerPermissionContractTest {

    @Test
    void managementProxyDebugAndLogCleanupMustDeclareExplicitPermissions() throws Exception {
        assertPermission(ExternalSystemController.class.getMethod("page",
                com.mdframe.forge.plugin.external.dto.ExternalSystemQuery.class), ExternalPermissions.SYSTEM_QUERY);
        assertPermission(ExternalSystemController.class.getMethod("add",
                com.mdframe.forge.plugin.external.dto.ExternalSystemDTO.class), ExternalPermissions.SYSTEM_ADD);
        assertPermission(ExternalApiController.class.getMethod("page",
                com.mdframe.forge.plugin.external.dto.ExternalApiQuery.class), ExternalPermissions.API_QUERY);
        assertPermission(ExternalApiController.class.getMethod("add",
                com.mdframe.forge.plugin.external.dto.ExternalApiDTO.class), ExternalPermissions.API_ADD);
        assertSensitiveWriteAudit(ExternalApiController.class.getMethod("add",
                com.mdframe.forge.plugin.external.dto.ExternalApiDTO.class));
        assertSensitiveWriteAudit(ExternalApiController.class.getMethod("edit",
                com.mdframe.forge.plugin.external.dto.ExternalApiDTO.class));
        assertPermission(ExternalProxyController.class.getMethod("proxyPost", Long.class, java.util.Map.class),
                ExternalPermissions.PROXY_INVOKE);
        assertPermission(ExternalProxyController.class.getMethod("debug", Long.class, java.util.Map.class),
                ExternalPermissions.PROXY_DEBUG);
        assertPermission(ExternalApiLogController.class.getMethod("page",
                com.mdframe.forge.plugin.external.dto.ExternalApiLogQuery.class), ExternalPermissions.LOG_QUERY);
        assertPermission(ExternalApiLogController.class.getMethod("clear",
                com.mdframe.forge.plugin.external.dto.ExternalApiLogQuery.class), ExternalPermissions.LOG_CLEAR);
    }

    private void assertPermission(Method method, String expected) {
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertThat(annotation).as(method.toGenericString()).isNotNull();
        assertThat(annotation.value()).containsExactly(expected);
    }

    private void assertSensitiveWriteAudit(Method method) {
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        assertThat(annotation).as(method.toGenericString()).isNotNull();
        assertThat(annotation.saveRequestParams()).isFalse();
        assertThat(annotation.saveResponseResult()).isFalse();
    }
}
