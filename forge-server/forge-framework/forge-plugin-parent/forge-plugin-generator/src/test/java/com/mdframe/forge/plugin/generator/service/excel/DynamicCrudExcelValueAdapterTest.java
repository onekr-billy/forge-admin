package com.mdframe.forge.plugin.generator.service.excel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudImportResult;
import com.mdframe.forge.starter.excel.model.ImportTemplateColumn;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicCrudExcelValueAdapterTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final DynamicCrudExcelValueAdapter adapter =
            new DynamicCrudExcelValueAdapter(new ObjectMapper(), jdbcTemplate);

    @Test
    void convertsImportCellsThroughOrderedValuePipeline() {
        DynamicCrudExcelColumn count = column("count", "数量", "int");
        DynamicCrudExcelColumn date = column("deliveryDate", "交付日期", "date");
        DynamicCrudExcelRow row = row(2, Map.of(0, " 12 ", 1, "2026/07/15"));
        DynamicCrudImportResult result = new DynamicCrudImportResult();

        List<Map<String, Object>> imported = adapter.buildImportRows(
                List.of(row), linkedMapping(count, date), result);

        assertTrue(result.getErrors().isEmpty());
        assertEquals(12, imported.get(0).get("count"));
        assertEquals("2026-07-15", imported.get(0).get("deliveryDate"));
    }

    @Test
    void mapsDictionaryLabelBeforeApplyingTargetDataType() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of("7"));
        DynamicCrudExcelColumn status = column("status", "状态", "int");
        status.setDictType("order_status");
        DynamicCrudImportResult result = new DynamicCrudImportResult();

        List<Map<String, Object>> imported = adapter.buildImportRows(
                List.of(row(2, Map.of(0, "已完成"))), linkedMapping(status), result);

        assertTrue(result.getErrors().isEmpty());
        assertEquals(7, imported.get(0).get("status"));
    }

    @Test
    void aggregatesRequiredAndConversionErrorsWithoutAbortingTheRow() {
        DynamicCrudExcelColumn required = column("name", "名称", "varchar");
        required.setRequired(true);
        DynamicCrudExcelColumn amount = column("amount", "金额", "decimal");
        DynamicCrudExcelRow row = row(3, Map.of(0, " ", 1, "not-a-number"));
        DynamicCrudImportResult result = new DynamicCrudImportResult();

        adapter.buildImportRows(List.of(row), linkedMapping(required, amount), result);

        assertEquals(2, result.getErrors().size());
        assertEquals(3, result.getErrors().get(0).getRowNum());
    }

    @Test
    void prefersDisplayFieldAndNormalizesStructuredExportValues() {
        DynamicCrudExcelColumn owner = column("ownerId", "负责人", "bigint");
        owner.setTargetField("ownerName");
        DynamicCrudExcelColumn payload = column("payload", "扩展信息", "json");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ownerId", 42L);
        row.put("ownerName", "张三");
        row.put("payload", Map.of("amount", new BigDecimal("12.30")));

        List<Object> exported = adapter.buildExportRow(row, List.of(owner, payload));

        assertEquals("张三", exported.get(0));
        assertEquals("{\"amount\":12.30}", exported.get(1));
    }

    @Test
    void buildsTemplateHintsFromColumnProtocol() {
        DynamicCrudExcelColumn column = column("startedAt", "开始时间", "datetime");
        column.setRequired(true);

        ImportTemplateColumn template = adapter.toImportTemplateColumn(column, 1L);

        assertEquals("2026-07-15 09:30:00", template.exampleValue());
        assertTrue(template.description().contains("yyyy-MM-dd HH:mm:ss"));
    }

    private DynamicCrudExcelColumn column(String field, String label, String dataType) {
        DynamicCrudExcelColumn column = new DynamicCrudExcelColumn();
        column.setField(field);
        column.setLabel(label);
        column.setDataType(dataType);
        return column;
    }

    private DynamicCrudExcelRow row(int rowNum, Map<Integer, Object> values) {
        DynamicCrudExcelRow row = new DynamicCrudExcelRow();
        row.setRowNum(rowNum);
        row.setValues(values);
        return row;
    }

    private Map<Integer, DynamicCrudExcelColumn> linkedMapping(DynamicCrudExcelColumn... columns) {
        Map<Integer, DynamicCrudExcelColumn> mapping = new LinkedHashMap<>();
        for (int index = 0; index < columns.length; index++) {
            mapping.put(index, columns[index]);
        }
        return mapping;
    }
}
