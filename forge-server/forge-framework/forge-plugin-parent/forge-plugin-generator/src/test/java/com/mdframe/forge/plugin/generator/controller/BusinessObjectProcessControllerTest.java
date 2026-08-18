package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("BusinessObject process query controller contract")
class BusinessObjectProcessControllerTest {

    @Test
    @DisplayName("object process summary uses the object route and process list permission")
    void objectProcessSummaryRouteAndPermission() throws NoSuchMethodException {
        Method method = BusinessObjectController.class.getDeclaredMethod("listProcesses", String.class);

        assertArrayEquals(new String[]{"/{objectCode}/processes"},
                method.getAnnotation(GetMapping.class).value());
        assertNotNull(method.getParameters()[0].getAnnotation(PathVariable.class));
        assertArrayEquals(new String[]{"ai:businessProcess:list"},
                method.getAnnotation(SaCheckPermission.class).value());
    }
}
