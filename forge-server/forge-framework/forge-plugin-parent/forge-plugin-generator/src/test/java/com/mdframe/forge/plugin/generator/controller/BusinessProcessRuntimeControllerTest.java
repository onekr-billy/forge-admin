package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessManualStartDTO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("BusinessProcessRuntimeController contract")
class BusinessProcessRuntimeControllerTest {

    @Test
    @DisplayName("runtime APIs stay in the encrypted business process namespace")
    void namespaceAndEncryptionContract() {
        RequestMapping mapping = BusinessProcessRuntimeController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/ai/business/process"}, mapping.value());
        assertNotNull(BusinessProcessRuntimeController.class.getAnnotation(ApiDecrypt.class));
        assertNotNull(BusinessProcessRuntimeController.class.getAnnotation(ApiEncrypt.class));
    }

    @Test
    @DisplayName("start and run endpoints use dedicated runtime permissions")
    void endpointAndPermissionContract() throws NoSuchMethodException {
        Method start = BusinessProcessRuntimeController.class.getDeclaredMethod(
                "start", String.class, String.class, BusinessProcessManualStartDTO.class);
        Method page = BusinessProcessRuntimeController.class.getDeclaredMethod(
                "page", Integer.class, Integer.class, Long.class, Long.class,
                String.class, String.class, String.class, String.class);
        Method detail = BusinessProcessRuntimeController.class.getDeclaredMethod("detail", Long.class);
        Method retry = BusinessProcessRuntimeController.class.getDeclaredMethod("retry", Long.class);
        Method cancel = BusinessProcessRuntimeController.class.getDeclaredMethod("cancel", Long.class);

        assertArrayEquals(new String[]{"/runtime/{applicationCode}/{processCode}/start"},
                start.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/run/page"}, page.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/run/{id}"}, detail.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/run/{id}/retry"}, retry.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/run/{id}/cancel"}, cancel.getAnnotation(PostMapping.class).value());
        assertPermission(start, "ai:businessProcess:start");
        assertPermission(page, "ai:businessProcess:run:list");
        assertPermission(detail, "ai:businessProcess:run:detail");
        assertPermission(retry, "ai:businessProcess:run:retry");
        assertPermission(cancel, "ai:businessProcess:run:cancel");

        Parameter[] parameters = page.getParameters();
        assertEquals("1", parameters[0].getAnnotation(RequestParam.class).defaultValue());
        assertEquals("10", parameters[1].getAnnotation(RequestParam.class).defaultValue());
    }

    private void assertPermission(Method method, String permission) {
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{permission}, annotation.value());
    }
}
