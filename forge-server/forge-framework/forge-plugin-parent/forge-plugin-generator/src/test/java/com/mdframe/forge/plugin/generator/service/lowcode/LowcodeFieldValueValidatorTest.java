package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodeFieldValueValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesTextAndSerializedStructuredValueLength() {
        LowcodeFieldSchema field = field("tags", "tags_json", "标签", "varchar", 5, null);

        assertDoesNotThrow(() -> validate(field, Map.of("tags", "abcde")));
        BusinessException textError = assertThrows(BusinessException.class,
                () -> validate(field, Map.of("tags", "abcdef")));
        BusinessException jsonError = assertThrows(BusinessException.class,
                () -> validate(field, Map.of("tags", List.of("a", "b"))));

        assertTrue(textError.getMessage().contains("标签"));
        assertTrue(textError.getMessage().contains("5"));
        assertTrue(jsonError.getMessage().contains("当前为"));
    }

    @Test
    void validatesIntegerRangeAndRejectsFraction() {
        LowcodeFieldSchema field = field("quantity", "field_number", "数量", "int", null, 0);

        assertDoesNotThrow(() -> validate(field, Map.of("field_number", "2147483647")));
        BusinessException overflow = assertThrows(BusinessException.class,
                () -> validate(field, Map.of("quantity", "2147483648")));
        BusinessException fraction = assertThrows(BusinessException.class,
                () -> validate(field, Map.of("quantity", "1.5")));

        assertTrue(overflow.getMessage().contains("2147483647"));
        assertTrue(fraction.getMessage().contains("整数"));
    }

    @Test
    void validatesDecimalTotalDigitsAndScale() {
        LowcodeFieldSchema field = field("amount", "amount", "金额", "decimal", 10, 2);

        assertDoesNotThrow(() -> validate(field, Map.of("amount", "99999999.99")));
        assertThrows(BusinessException.class, () -> validate(field, Map.of("amount", "100000000.00")));
        BusinessException scale = assertThrows(BusinessException.class,
                () -> validate(field, Map.of("amount", "1.234")));

        assertTrue(scale.getMessage().contains("2 位小数"));
    }

    @Test
    void appliesConfiguredRangeWithoutAllowingDatabaseRangeToBeWidened() {
        LowcodeFieldSchema field = field("quantity", "quantity", "数量", "int", null, 0);
        field.setBasicProps(Map.of("min", -9_999_999_999L, "max", 100));

        assertDoesNotThrow(() -> validate(field, Map.of("quantity", 100)));
        assertThrows(BusinessException.class, () -> validate(field, Map.of("quantity", 101)));
        BusinessException databaseLimit = assertThrows(BusinessException.class,
                () -> validate(field, Map.of("quantity", -3_000_000_000L)));

        assertTrue(databaseLimit.getMessage().contains("-2147483648"));
    }

    @Test
    void validatesMinorUnitMoneyInMajorUnitInput() {
        LowcodeFieldSchema field = field("cashAmount", "cash_amount", "金额", "bigint", null, 2);
        field.setBusinessFieldType("MONEY");

        assertDoesNotThrow(() -> validate(field, Map.of("cashAmount", "92233720368547758.07")));
        assertThrows(BusinessException.class,
                () -> validate(field, Map.of("cashAmount", "92233720368547758.08")));
        assertThrows(BusinessException.class, () -> validate(field, Map.of("cashAmount", "1.001")));
    }

    private void validate(LowcodeFieldSchema field, Map<String, Object> values) {
        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setFields(List.of(field));
        LowcodeFieldValueValidator.validate(schema, values, objectMapper);
    }

    private LowcodeFieldSchema field(String field,
                                     String column,
                                     String label,
                                     String dataType,
                                     Integer length,
                                     Integer precision) {
        LowcodeFieldSchema schema = new LowcodeFieldSchema();
        schema.setField(field);
        schema.setColumnName(column);
        schema.setLabel(label);
        schema.setDataType(dataType);
        schema.setLength(length);
        schema.setPrecision(precision);
        return schema;
    }
}
