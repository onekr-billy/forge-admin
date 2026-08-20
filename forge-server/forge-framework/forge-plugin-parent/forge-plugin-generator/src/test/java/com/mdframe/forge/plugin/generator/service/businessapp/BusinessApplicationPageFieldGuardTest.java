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
                false, List.of(existingTextField()), List.of(numberField())));
    }

    @Test
    @DisplayName("a persisted field cannot be removed after records exist")
    void dataFieldCannotBeRemoved() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertCompatible(
                        true, List.of(existingTextField()), List.of()));

        assertEquals("字段“客户名称”已有数据，不能删除", error.getMessage());
    }

    @Test
    @DisplayName("a persisted field cannot change storage type after records exist")
    void dataFieldCannotChangeType() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertCompatible(
                        true, List.of(existingTextField()), List.of(numberField())));

        assertEquals("字段“客户名称”已有数据，不能修改字段类型", error.getMessage());
    }

    @Test
    @DisplayName("a persisted form binding cannot change field code after records exist")
    void dataFieldBindingCannotChangeCode() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                        formSchema("input", "customerName"),
                        formSchema("input", "customerAlias"),
                        List.of(existingTextField())));

        assertEquals("字段“客户名称”已有数据，不能修改字段编码", error.getMessage());
    }

    @Test
    @DisplayName("a persisted form component cannot change type after records exist")
    void dataFieldComponentCannotChangeType() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                        formSchema("input", "customerName"),
                        formSchema("number", "customerName"),
                        List.of(existingTextField())));

        assertEquals("字段“客户名称”已有数据，不能修改字段类型", error.getMessage());
    }

    @Test
    @DisplayName("an unchanged persisted form binding remains valid even before the lock flag is backfilled")
    void unchangedDataFieldBindingIsAccepted() {
        assertDoesNotThrow(() -> BusinessApplicationPageFieldGuard.assertLockedFormComponentsUnchanged(
                formSchema("input", "customerName"),
                formSchema("input", "customerName"),
                List.of(existingTextField())));
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
