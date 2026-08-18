package com.mdframe.forge.plugin.generator.controller;

import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodeQuerySourceControllerContractTest {

    @Test
    void shouldExposeOnlyCatalogMetadataAndExecuteUnderEncryptedAuthenticatedController() {
        RequestMapping mapping = LowcodeQuerySourceController.class.getAnnotation(RequestMapping.class);
        assertTrue(Arrays.asList(mapping.value()).contains("/ai/lowcode/query-source"));
        assertTrue(LowcodeQuerySourceController.class.isAnnotationPresent(ApiDecrypt.class));
        assertTrue(LowcodeQuerySourceController.class.isAnnotationPresent(ApiEncrypt.class));
        assertFalse(LowcodeQuerySourceController.class.isAnnotationPresent(ApiPermissionIgnore.class));

        assertTrue(hasGetPath("catalog", "/catalog"));
        assertTrue(hasPostPath("metadata", "/metadata"));
        assertTrue(hasPostPath("execute", "/execute"));
    }

    private boolean hasGetPath(String methodName, String path) {
        return Arrays.stream(LowcodeQuerySourceController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .anyMatch(mapping -> Arrays.asList(mapping.value()).contains(path));
    }

    private boolean hasPostPath(String methodName, String path) {
        return Arrays.stream(LowcodeQuerySourceController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .anyMatch(mapping -> Arrays.asList(mapping.value()).contains(path));
    }
}
