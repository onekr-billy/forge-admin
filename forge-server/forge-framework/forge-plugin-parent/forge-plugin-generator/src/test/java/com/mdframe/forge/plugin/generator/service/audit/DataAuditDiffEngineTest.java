package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditChangeType;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DataAuditDiffEngine")
class DataAuditDiffEngineTest {

    private final DataAuditDiffEngine engine = new DataAuditDiffEngine(new DataAuditValueNormalizer(new ObjectMapper()));

    @Test
    @DisplayName("只记录实际变化字段，同值不产生差异")
    void recordsOnlyChangedFields() {
        LowcodeFieldSchema name = field("name", "varchar", "input");
        LowcodeFieldSchema amount = field("amount", "decimal", "input-number");
        Map<String, LowcodeFieldSchema> fields = Map.of("name", name, "amount", amount);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", "A");
        before.put("amount", new BigDecimal("1.00"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", "B");
        after.put("amount", new BigDecimal("1"));

        List<DataAuditFieldChange> changes = engine.diffRows(
                before, after, fields, 1L, 2L, "10", "", DataAuditSourceType.FORM);

        assertEquals(1, changes.size());
        assertEquals("name", changes.get(0).getField().getField());
        assertEquals(DataAuditChangeType.UPDATE, changes.get(0).getChangeType());
    }

    @Test
    @DisplayName("null、空串、0、false 保持语义区别")
    void preservesNullEmptyZeroFalse() {
        LowcodeFieldSchema text = field("remark", "varchar", "input");
        LowcodeFieldSchema flag = field("enabled", "boolean", "switch");
        LowcodeFieldSchema count = field("count", "int", "input-number");
        Map<String, LowcodeFieldSchema> fields = new LinkedHashMap<>();
        fields.put("remark", text);
        fields.put("enabled", flag);
        fields.put("count", count);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("remark", null);
        before.put("enabled", false);
        before.put("count", 0);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("remark", "");
        after.put("enabled", false);
        after.put("count", 0);

        List<DataAuditFieldChange> changes = engine.diffRows(
                before, after, fields, 1L, 2L, "10", "", DataAuditSourceType.FORM);

        assertEquals(1, changes.size());
        assertEquals("remark", changes.get(0).getField().getField());
    }

    @Test
    @DisplayName("新增行字段变化类型为 ADD")
    void marksCreateAsAdd() {
        LowcodeFieldSchema name = field("name", "varchar", "input");
        Map<String, Object> after = Map.of("name", "created");
        List<DataAuditFieldChange> changes = engine.diffRows(
                null, after, Map.of("name", name), 1L, 2L, "11", "", DataAuditSourceType.FORM);
        assertEquals(1, changes.size());
        assertEquals(DataAuditChangeType.ADD, changes.get(0).getChangeType());
        assertTrue(changes.get(0).getBefore().getState().matches("ABSENT"));
    }

    private LowcodeFieldSchema field(String name, String dataType, String component) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(name);
        field.setColumnName(name);
        field.setLabel(name);
        field.setDataType(dataType);
        field.setComponentType(component);
        return field;
    }
}
