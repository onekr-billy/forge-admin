package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("BusinessObjectController contract")
class BusinessObjectControllerTest {

    @Test
    @DisplayName("object code availability is a tenant-scoped read endpoint")
    void objectCodeAvailabilityEndpointUsesListPermission() throws NoSuchMethodException {
        RequestMapping mapping = BusinessObjectController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/ai/business/object"}, mapping.value());

        Method method = BusinessObjectController.class.getDeclaredMethod(
                "codeAvailable", String.class, Long.class);
        assertArrayEquals(new String[]{"/code-available"}, method.getAnnotation(GetMapping.class).value());
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertNotNull(permission);
        assertArrayEquals(new String[]{"ai:businessObject:list"}, permission.value());
    }
}
