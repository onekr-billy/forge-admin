package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormSaveDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowTaskFormPolicyTest {

    private final BusinessFlowTaskFormPolicy policy = new BusinessFlowTaskFormPolicy();

    @Test
    void fieldPermissionsSupportSnakeAndCamelAliases() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("field", "orderNo");
        field.put("label", "单号");
        field.put("type", "input");
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("field", "order_no");
        permission.put("readable", true);
        permission.put("writable", true);

        List<Map<String, Object>> result = policy.buildFields(List.of(field), List.of(permission));

        assertEquals(1, result.size());
        assertTrue((Boolean) result.get(0).get("writable"));
        assertFalse((Boolean) result.get(0).get("readonly"));
    }

    @Test
    void savePayloadKeepsOnlyWritableFields() {
        BusinessTaskFormSaveDTO dto = new BusinessTaskFormSaveDTO();
        dto.setTaskId("task-1");
        dto.setObjectCode("order");
        dto.setData(new LinkedHashMap<>(Map.of("amount", 100, "owner", "ignored")));

        BusinessTaskFormSaveDTO filtered = policy.filterSaveData(dto, List.of(
                permission("amount", true, false),
                permission("owner", false, false)));

        assertEquals(Map.of("amount", 100), filtered.getData());
        assertEquals("task-1", filtered.getTaskId());
        assertEquals("order", filtered.getObjectCode());
    }

    @Test
    void requiredValidationAcceptsFalseAndZeroButRejectsBlankText() {
        List<Map<String, Object>> permissions = List.of(permission("enabled", true, true));

        policy.validateRequiredFields(permissions, Map.of("enabled", false), Map.of("enabled", false));
        policy.validateRequiredFields(permissions, Map.of("enabled", 0), Map.of("enabled", 0));

        assertThrows(BusinessException.class,
                () -> policy.validateRequiredFields(
                        permissions, Map.of("enabled", " "), Map.of("enabled", " ")));
    }

    @Test
    void readonlyProjectionClosesMainAndChildWrites() {
        BusinessTaskFormContextVO context = new BusinessTaskFormContextVO();
        Map<String, Object> mainField = new LinkedHashMap<>(Map.of("field", "name", "writable", true));
        Map<String, Object> childField = new LinkedHashMap<>(Map.of("field", "qty", "writable", true));
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("allowCreate", true);
        child.put("allowUpdate", true);
        child.put("allowDelete", true);
        child.put("fields", List.of(childField));
        context.setFields(List.of(mainField));
        context.setChildrenConfig(List.of(child));

        policy.makeReadonly(context);

        assertEquals(false, mainField.get("writable"));
        assertEquals(true, mainField.get("readonly"));
        assertEquals(false, child.get("allowCreate"));
        assertEquals(false, child.get("allowUpdate"));
        assertEquals(false, child.get("allowDelete"));
    }

    private Map<String, Object> permission(String field, boolean writable, boolean required) {
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("field", field);
        permission.put("readable", true);
        permission.put("writable", writable);
        permission.put("required", required);
        return permission;
    }
}
