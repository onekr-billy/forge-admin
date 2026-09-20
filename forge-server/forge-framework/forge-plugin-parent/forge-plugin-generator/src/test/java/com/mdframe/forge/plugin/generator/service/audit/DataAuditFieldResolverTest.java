package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DataAuditFieldResolver")
class DataAuditFieldResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataAuditValueNormalizer normalizer = new DataAuditValueNormalizer(objectMapper);
    private final DataAuditFieldResolver resolver = new DataAuditFieldResolver(objectMapper, normalizer);
    private final DataAuditDiffEngine diffEngine = new DataAuditDiffEngine(normalizer);

    @Test
    @DisplayName("模型字段非空时仍补齐模型和编辑表单都遗漏的快照列")
    void supplementsPhysicalColumnMissingFromAllSchemas() {
        LowcodeFieldSchema knownField = new LowcodeFieldSchema();
        knownField.setField("knownField");
        knownField.setColumnName("known_field");
        knownField.setLabel("已知字段");
        knownField.setDataType("varchar");

        Map<String, LowcodeFieldSchema> fields = resolver.resolveSnapshotFields(
                Map.of("knownField", knownField),
                Map.of("known_field", "不变", "field_number", new BigDecimal("1")),
                Map.of("known_field", "不变", "field_number", new BigDecimal("2")));
        List<DataAuditFieldChange> changes = diffEngine.diffRows(
                Map.of("known_field", "不变", "field_number", new BigDecimal("1")),
                Map.of("known_field", "不变", "field_number", new BigDecimal("2")),
                fields, 1L, 2L, "10", "", DataAuditSourceType.FORM);

        assertNotNull(fields.get("fieldNumber"));
        assertEquals("field_number", fields.get("fieldNumber").getColumnName());
        assertEquals(1, changes.size());
        assertEquals("fieldNumber", changes.get(0).getField().getField());
    }

    @Test
    @DisplayName("模型字段非空时仍补齐快照中的主表字段并保留编辑表单中文名")
    void supplementsChangedSnapshotColumnWhenModelFieldsAreIncomplete() {
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("demo_form");
        config.setTableName("demo_form");
        config.setModelSchema("""
                {
                  "tableName": "demo_form",
                  "fields": [
                    {
                      "field": "knownField",
                      "columnName": "known_field",
                      "label": "已知字段",
                      "dataType": "varchar"
                    }
                  ]
                }
                """);
        config.setEditSchema("""
                [
                  {"field":"knownField","label":"已知字段","type":"input"},
                  {"field":"fieldNumber","label":"业务数量","type":"number"}
                ]
                """);

        Map<String, LowcodeFieldSchema> configured = resolver.load(config).get(config.getTableName());
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("known_field", "不变");
        before.put("field_number", new BigDecimal("1"));
        before.put("update_by", 1L);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("known_field", "不变");
        after.put("field_number", new BigDecimal("2"));
        after.put("update_by", 2L);

        Map<String, LowcodeFieldSchema> fields = resolver.resolveSnapshotFields(configured, before, after);
        List<DataAuditFieldChange> changes = diffEngine.diffRows(
                before, after, fields, 1L, 2L, "10", "", DataAuditSourceType.FORM);

        assertEquals("已知字段", fields.get("knownField").getLabel());
        LowcodeFieldSchema number = fields.get("fieldNumber");
        assertNotNull(number);
        assertEquals("field_number", number.getColumnName());
        assertEquals("业务数量", number.getLabel());
        assertEquals(1, changes.size());
        assertEquals("fieldNumber", changes.get(0).getField().getField());
        assertEquals("业务数量", changes.get(0).getField().getLabel());
        assertFalse(fields.values().stream().anyMatch(field -> "update_by".equals(field.getColumnName())));
    }

    @Test
    @DisplayName("模型列映射过时时按真实快照列修正且不丢失模型元数据")
    void alignsStaleModelColumnWithPhysicalSnapshotColumn() {
        LowcodeFieldSchema configuredField = new LowcodeFieldSchema();
        configuredField.setField("fieldNumber");
        configuredField.setColumnName("legacy_number");
        configuredField.setLabel("数量");
        configuredField.setDataType("decimal");
        configuredField.setSensitiveType("CUSTOM");

        Map<String, LowcodeFieldSchema> fields = resolver.resolveSnapshotFields(
                Map.of("fieldNumber", configuredField),
                Map.of("field_number", new BigDecimal("3")),
                Map.of("field_number", new BigDecimal("5")));

        LowcodeFieldSchema aligned = fields.get("fieldNumber");
        assertEquals("field_number", aligned.getColumnName());
        assertEquals("数量", aligned.getLabel());
        assertEquals("CUSTOM", aligned.getSensitiveType());
        assertEquals("legacy_number", configuredField.getColumnName());
        assertTrue(diffEngine.diffRows(
                Map.of("field_number", new BigDecimal("3")),
                Map.of("field_number", new BigDecimal("5")),
                fields, 1L, 2L, "10", "", DataAuditSourceType.FORM
        ).stream().anyMatch(change -> "fieldNumber".equals(change.getField().getField())));
    }
}
