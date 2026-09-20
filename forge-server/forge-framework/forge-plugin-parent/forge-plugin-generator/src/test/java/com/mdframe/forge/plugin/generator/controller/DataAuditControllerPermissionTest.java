package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditFieldQueryDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAuditControllerPermissionTest {

    private static final Set<String> READ_PERMISSIONS = Set.of(
            "ai:dataAudit:record",
            "ai:dataAudit:list",
            "ai:dataAudit:detail"
    );

    @Test
    void eventAndFieldDetailsAllowRecordOrAuditEntryPermission() throws NoSuchMethodException {
        Method detail = DataAuditController.class.getMethod(
                "detail", Long.class, String.class, String.class);
        Method fields = DataAuditController.class.getMethod(
                "fields", Long.class, DataAuditFieldQueryDTO.class, Integer.class, Integer.class);

        assertReadPermission(detail);
        assertReadPermission(fields);
    }

    private void assertReadPermission(Method method) {
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertEquals(SaMode.OR, permission.mode());
        assertEquals(READ_PERMISSIONS, Set.of(permission.value()));
        assertTrue(permission.value().length == READ_PERMISSIONS.size());
    }
}
