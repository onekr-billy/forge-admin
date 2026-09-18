package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusinessApplicationPageFieldGuardTest {

    @Test
    @DisplayName("fields may still be redesigned before the object contains records")
    void emptyObjectAllowsFieldChanges() {
        assertDoesNotThrow(() -> BusinessApplicationPageFieldGuard.assertCompatible(
                0, "", List.of(existingTextField()), List.of(numberField())));
    }

    @Test
    @DisplayName("a persisted field cannot be removed when column has data")
    void dataFieldCannotBeRemoved() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertCompatible(
                        3, "bfma_biz_order", List.of(existingTextField()), List.of()));

        assertEquals("字段“客户名称”已有数据，不能删除（数据表 bfma_biz_order）；可先清理该字段数据后再删除",
                error.getMessage());
    }

    @Test
    @DisplayName("field removal is allowed when column has no data despite table having rows")
    void dataFieldRemovalAllowedWhenColumnEmpty() {
        // 表有 7 行数据，但该列无数据 → 允许删除
        assertDoesNotThrow(() -> BusinessApplicationPageFieldGuard.assertCompatible(
                7, "cgou_approval_vger", List.of(existingTextField()), List.of(),
                columnName -> false));
    }

    @Test
    @DisplayName("field removal is blocked when column has data")
    void dataFieldRemovalBlockedWhenColumnHasData() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertCompatible(
                        7, "cgou_approval_vger", List.of(existingTextField()), List.of(),
                        columnName -> true));

        assertEquals("字段“客户名称”已有数据，不能删除（数据表 cgou_approval_vger）；可先清理该字段数据后再删除",
                error.getMessage());
    }

    @Test
    @DisplayName("blank column name in field schema falls back to blocking")
    void blankColumnNameFallsBackToBlocking() {
        LowcodeFieldSchema field = existingTextField();
        field.setColumnName(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertCompatible(
                        5, "bfma_biz_order", List.of(field), List.of(),
                        columnName -> columnName.isEmpty()));

        assertEquals("字段“客户名称”已有数据，不能删除（数据表 bfma_biz_order）；可先清理该字段数据后再删除",
                error.getMessage());
    }

    @Test
    @DisplayName("field type change is allowed (warned) when records exist — DDL layer handles actual schema changes")
    void dataFieldChangeTypeAllowed() {
        // 字段类型差异降级为警告，不再抛异常阻断保存
        assertDoesNotThrow(() -> BusinessApplicationPageFieldGuard.assertCompatible(
                3, "bfma_biz_order", List.of(existingTextField()), List.of(numberField())));
    }

    @Test
    @DisplayName("a persisted form binding cannot change field code after records exist")
    void dataFieldBindingCannotChangeCode() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                        formSchema("input", "customerName"),
                        formSchema("input", "customerAlias"),
                        List.of(existingTextField()),
                        3, "bfma_biz_order"));

        assertEquals("字段“客户名称”已有数据，不能修改字段编码（共 3 条数据，数据表 bfma_biz_order）",
                error.getMessage());
    }

    @Test
    @DisplayName("component key change is allowed when field-level storage type is unchanged")
    void dataFieldComponentChangeAllowed() {
        // 组件类型变更不再拦截，字段级 assertCompatible 已保证数据存储类型不变
        assertDoesNotThrow(() -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                formSchema("input", "customerName"),
                formSchema("number", "customerName"),
                List.of(existingTextField()),
                3, "bfma_biz_order"));
    }

    @Test
    @DisplayName("an unchanged persisted form binding remains valid even before the lock flag is backfilled")
    void unchangedDataFieldBindingIsAccepted() {
        assertDoesNotThrow(() -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                formSchema("input", "customerName"),
                formSchema("input", "customerName"),
                List.of(existingTextField()),
                3, "bfma_biz_order"));
    }

    @Test
    @DisplayName("component-level deletion allowed when column has no data despite table having rows")
    void componentDeletionAllowedWhenColumnEmpty() {
        assertDoesNotThrow(() -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                formSchema("input", "customerName"),
                Map.of("components", List.of()),
                List.of(existingTextField()),
                7, "cgou_approval_vger",
                columnName -> false));
    }

    @Test
    @DisplayName("component-level deletion blocked when column has data")
    void componentDeletionBlockedWhenColumnHasData() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                        formSchema("input", "customerName"),
                        Map.of("components", List.of()),
                        List.of(existingTextField()),
                        7, "cgou_approval_vger",
                        columnName -> true));

        assertEquals("字段“客户名称”已有数据，不能删除（数据表 cgou_approval_vger）；可先清理该字段数据后再删除",
                error.getMessage());
    }

    private LowcodeFieldSchema existingTextField() {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField("customerName");
        field.setLabel("客户名称");
        field.setBusinessFieldType("TEXT");
        field.setDataType("varchar");
        field.setLength(128);
        field.setPrecision(0);
        field.setSystemField(false);
        field.setFieldStatus("ENABLED");
        return field;
    }

    private BusinessFieldDTO numberField() {
        BusinessFieldDTO field = new BusinessFieldDTO();
        field.setFieldCode("customerName");
        field.setFieldName("客户名称");
        field.setFieldType("NUMBER");
        field.setDataType("int");
        field.setLength(11);
        field.setPrecision(0);
        return field;
    }

    private Map<String, Object> formSchema(String componentKey, String fieldCode) {
        return Map.of("components", List.of(Map.of(
                "id", "cmp_customerName",
                "componentKey", componentKey,
                "label", "客户名称",
                "fieldBinding", Map.of(
                        "mode", "field",
                        "fieldCode", fieldCode,
                        "locked", false))));
    }
}
