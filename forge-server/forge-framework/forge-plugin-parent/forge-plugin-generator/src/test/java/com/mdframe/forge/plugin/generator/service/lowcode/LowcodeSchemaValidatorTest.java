package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LowcodeSchemaValidatorTest {

    private final LowcodeSchemaValidator validator = new LowcodeSchemaValidator();

    @Test
    void validatesCharacterCapacityBeforeDdl() {
        LowcodeFieldSchema field = field("title", "varchar");
        field.setLength(2048);
        assertDoesNotThrow(() -> validator.validateModel(model(field)));

        field.setLength(2049);
        assertThrows(BusinessException.class, () -> validator.validateModel(model(field)));
    }

    @Test
    void validatesDecimalDigitsAndConfiguredRange() {
        LowcodeFieldSchema field = field("amount", "decimal");
        field.setLength(10);
        field.setPrecision(2);
        field.setBasicProps(Map.of("min", 0, "max", 100));
        assertDoesNotThrow(() -> validator.validateModel(model(field)));

        field.setPrecision(10);
        assertThrows(BusinessException.class, () -> validator.validateModel(model(field)));

        field.setPrecision(2);
        field.setBasicProps(Map.of("min", 101, "max", 100));
        assertThrows(BusinessException.class, () -> validator.validateModel(model(field)));
    }

    private LowcodeFieldSchema field(String fieldName, String dataType) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(fieldName);
        field.setColumnName(fieldName);
        field.setLabel("测试字段");
        field.setDataType(dataType);
        field.setComponentType("decimal".equals(dataType) ? "number" : "input");
        return field;
    }

    private LowcodeModelSchema model(LowcodeFieldSchema field) {
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setAppType("SINGLE");
        model.setTableMode("EXISTING");
        model.setTableName("field_constraint_test");
        model.setFields(List.of(field));
        return model;
    }
}
