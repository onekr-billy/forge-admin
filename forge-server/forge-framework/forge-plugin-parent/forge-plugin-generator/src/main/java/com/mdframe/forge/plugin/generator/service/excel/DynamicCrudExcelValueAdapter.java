package com.mdframe.forge.plugin.generator.service.excel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudImportResult;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.excel.model.ImportTemplateColumn;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adapts Excel cell values to and from the low-code runtime data protocol.
 *
 * <p>The service orchestrates workbook IO and transactions; this adapter owns
 * the deterministic conversion pipeline and dictionary boundary.</p>
 */
@Component
@RequiredArgsConstructor
public class DynamicCrudExcelValueAdapter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public List<Map<String, Object>> buildImportRows(List<DynamicCrudExcelRow> rows,
                                                      Map<Integer, DynamicCrudExcelColumn> headerMapping,
                                                      DynamicCrudImportResult result) {
        List<Map<String, Object>> importRows = new ArrayList<>();
        for (DynamicCrudExcelRow excelRow : rows) {
            if (isBlankRow(excelRow.getValues())) {
                continue;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<Integer, DynamicCrudExcelColumn> entry : headerMapping.entrySet()) {
                Object rawValue = excelRow.getValues().get(entry.getKey());
                DynamicCrudExcelColumn column = entry.getValue();
                Object normalizedValue = normalizeCellValue(rawValue);
                if (isEmptyValue(normalizedValue)) {
                    if (column.isRequired()) {
                        result.addError(excelRow.getRowNum(), column.getField(), column.getLabel(), rawValue,
                                column.getLabel() + "不能为空");
                    }
                    continue;
                }
                try {
                    data.put(column.getField(), convertImportValue(column, normalizedValue));
                } catch (Exception e) {
                    result.addError(excelRow.getRowNum(), column.getField(), column.getLabel(), rawValue, e.getMessage());
                }
            }
            if (!data.isEmpty()) {
                importRows.add(data);
            }
        }
        return importRows;
    }

    public List<Object> buildExportRow(Map<String, Object> row, List<DynamicCrudExcelColumn> columns) {
        List<Object> values = new ArrayList<>();
        for (DynamicCrudExcelColumn column : columns) {
            Object value = row.get(column.getField());
            if (StringUtils.isNotBlank(column.getTargetField())) {
                Object displayValue = row.get(column.getTargetField());
                if (!isEmptyValue(displayValue)) {
                    value = displayValue;
                }
            }
            values.add(normalizeExportCellValue(column, value));
        }
        return values;
    }

    public ImportTemplateColumn toImportTemplateColumn(DynamicCrudExcelColumn column, Long tenantId) {
        List<String> dropdownOptions = selectDictLabels(column.getDictType(), tenantId);
        return new ImportTemplateColumn(
                column.getField(),
                column.getLabel(),
                column.isRequired(),
                resolveImportTemplateExample(column, dropdownOptions),
                resolveImportTemplateDescription(column, dropdownOptions),
                dropdownOptions
        );
    }

    public String normalizeHeader(String value) {
        return StringUtils.defaultString(value).trim();
    }

    private Object convertImportValue(DynamicCrudExcelColumn column, Object value) {
        Object convertedValue = value;
        if (StringUtils.isNotBlank(column.getDictType())) {
            String dictValue = resolveDictValue(column.getDictType(), toCellText(value));
            if (dictValue == null) {
                throw new BusinessException("无法识别字典值: " + toCellText(value));
            }
            convertedValue = dictValue;
        }
        return convertByDataType(column, convertedValue);
    }

    private Object convertByDataType(DynamicCrudExcelColumn column, Object value) {
        String dataType = StringUtils.defaultIfBlank(column.getDataType(), "").toLowerCase(Locale.ROOT);
        String componentType = StringUtils.defaultIfBlank(column.getType(), "").toLowerCase(Locale.ROOT);
        if (Set.of("int", "tinyint").contains(dataType)) {
            return Integer.valueOf(toNumberText(value));
        }
        if ("bigint".equals(dataType)) {
            return Long.valueOf(toNumberText(value));
        }
        if ("decimal".equals(dataType) || "number".equals(componentType) || "inputnumber".equals(componentType)) {
            return new BigDecimal(toNumberText(value));
        }
        if ("date".equals(dataType) || "date".equals(componentType)) {
            return formatDateValue(value);
        }
        if ("datetime".equals(dataType) || "datetime".equals(componentType)) {
            return formatDateTimeValue(value);
        }
        if ("time".equals(dataType) || "time".equals(componentType)) {
            return formatTimeValue(value);
        }
        return value;
    }

    private String resolveDictValue(String dictType, String labelOrValue) {
        if (StringUtils.isBlank(dictType) || StringUtils.isBlank(labelOrValue)) {
            return null;
        }
        StringBuilder sql = new StringBuilder("""
                SELECT dict_value
                FROM sys_dict_data
                WHERE dict_type = :dictType
                  AND (dict_label = :value OR dict_value = :value)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dictType", dictType)
                .addValue("value", labelOrValue);
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            sql.append(" AND tenant_id = :tenantId");
            params.addValue("tenantId", tenantId);
        }
        sql.append(" ORDER BY CASE WHEN dict_label = :value THEN 0 ELSE 1 END, dict_sort ASC LIMIT 1");
        List<String> values = namedJdbcTemplate.queryForList(sql.toString(), params, String.class);
        return values.isEmpty() ? null : values.get(0);
    }

    private Object normalizeExportCellValue(DynamicCrudExcelColumn column, Object value) {
        if (value == null) {
            return null;
        }
        String dataType = StringUtils.defaultIfBlank(column.getDataType(), "").toLowerCase(Locale.ROOT);
        String componentType = StringUtils.defaultIfBlank(column.getType(), "").toLowerCase(Locale.ROOT);
        if ("date".equals(dataType) || "date".equals(componentType)) {
            return formatDateValue(value);
        }
        if ("datetime".equals(dataType) || "datetime".equals(componentType)) {
            return formatDateTimeValue(value);
        }
        if ("time".equals(dataType) || "time".equals(componentType)) {
            return formatTimeValue(value);
        }
        if (value instanceof Date date) {
            return DATETIME_FORMATTER.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()));
        }
        if (value instanceof LocalDate localDate) {
            return DATE_FORMATTER.format(localDate);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return DATETIME_FORMATTER.format(localDateTime);
        }
        if (value instanceof LocalTime localTime) {
            return TIME_FORMATTER.format(localTime);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return value;
    }

    private String resolveImportTemplateExample(DynamicCrudExcelColumn column, List<String> dropdownOptions) {
        if (StringUtils.isNotBlank(column.getExampleValue())) {
            return column.getExampleValue();
        }
        if (dropdownOptions != null && !dropdownOptions.isEmpty()) {
            return dropdownOptions.get(0);
        }
        if (StringUtils.isNotBlank(column.getDictType())) {
            return "字典选项示例";
        }
        String dataType = StringUtils.defaultIfBlank(column.getDataType(), "").toLowerCase(Locale.ROOT);
        String componentType = StringUtils.defaultIfBlank(column.getType(), "").toLowerCase(Locale.ROOT);
        if ("date".equals(dataType) || "date".equals(componentType)) {
            return "2026-07-15";
        }
        if ("datetime".equals(dataType) || "datetime".equals(componentType)) {
            return "2026-07-15 09:30:00";
        }
        if ("time".equals(dataType) || "time".equals(componentType)) {
            return "09:30:00";
        }
        if (Set.of("int", "tinyint", "bigint", "decimal").contains(dataType)
                || Set.of("number", "inputnumber", "input-number").contains(componentType)) {
            return "100";
        }
        if (Set.of("switch", "boolean", "checkbox").contains(componentType)) {
            return "是";
        }
        return StringUtils.defaultIfBlank(column.getLabel(), column.getField()) + "示例";
    }

    private String resolveImportTemplateDescription(DynamicCrudExcelColumn column, List<String> dropdownOptions) {
        List<String> descriptions = new ArrayList<>();
        if (StringUtils.isNotBlank(column.getDescription())) {
            descriptions.add(column.getDescription());
        }
        if (StringUtils.isNotBlank(column.getDictType())) {
            if (dropdownOptions != null && !dropdownOptions.isEmpty()) {
                descriptions.add("请从下拉列表中选择字典标签，也可填写字典值。字典类型：" + column.getDictType());
            } else {
                descriptions.add("填写字典标签或字典值，字典类型：" + column.getDictType());
            }
        }
        String dataType = StringUtils.defaultIfBlank(column.getDataType(), "").toLowerCase(Locale.ROOT);
        String componentType = StringUtils.defaultIfBlank(column.getType(), "").toLowerCase(Locale.ROOT);
        if ("date".equals(dataType) || "date".equals(componentType)) {
            descriptions.add("日期格式：yyyy-MM-dd");
        } else if ("datetime".equals(dataType) || "datetime".equals(componentType)) {
            descriptions.add("日期时间格式：yyyy-MM-dd HH:mm:ss");
        } else if ("time".equals(dataType) || "time".equals(componentType)) {
            descriptions.add("时间格式：HH:mm:ss");
        }
        if (descriptions.isEmpty()) {
            descriptions.add("按" + StringUtils.defaultIfBlank(column.getLabel(), column.getField()) + "的业务含义填写");
        }
        return String.join("；", descriptions);
    }

    private List<String> selectDictLabels(String dictType, Long tenantId) {
        if (StringUtils.isBlank(dictType)) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT dict_label
                FROM sys_dict_data
                WHERE dict_type = :dictType
                  AND dict_status = 1
                  AND del_flag = 0
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("dictType", dictType);
        if (tenantId != null) {
            sql.append(" AND tenant_id = :tenantId");
            params.addValue("tenantId", tenantId);
        }
        sql.append(" ORDER BY dict_sort ASC, dict_code ASC");
        return namedJdbcTemplate.queryForList(sql.toString(), params, String.class).stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean isBlankRow(Map<Integer, Object> values) {
        return values == null || values.isEmpty() || values.values().stream()
                .map(this::normalizeCellValue)
                .allMatch(this::isEmptyValue);
    }

    private Object normalizeCellValue(Object value) {
        return value instanceof String str ? StringUtils.trimToNull(str) : value;
    }

    private boolean isEmptyValue(Object value) {
        return value == null || value instanceof String str && StringUtils.isBlank(str);
    }

    private String toCellText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Date date) {
            return DATETIME_FORMATTER.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()));
        }
        return StringUtils.trimToEmpty(String.valueOf(value));
    }

    private String toNumberText(Object value) {
        String text = toCellText(value);
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("数字不能为空");
        }
        return text;
    }

    private String formatDateValue(Object value) {
        if (value instanceof Date date) {
            return DATE_FORMATTER.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()));
        }
        String text = normalizeDateText(value);
        try {
            return DATE_FORMATTER.format(LocalDate.parse(text.substring(0, Math.min(10, text.length())), DATE_FORMATTER));
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式应为 yyyy-MM-dd");
        }
    }

    private String formatDateTimeValue(Object value) {
        if (value instanceof Date date) {
            return DATETIME_FORMATTER.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()));
        }
        String text = normalizeDateText(value);
        if (text.length() == 10) {
            text += " 00:00:00";
        }
        try {
            return DATETIME_FORMATTER.format(LocalDateTime.parse(text, DATETIME_FORMATTER));
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String formatTimeValue(Object value) {
        if (value instanceof Date date) {
            return TIME_FORMATTER.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault()));
        }
        try {
            return TIME_FORMATTER.format(LocalTime.parse(toCellText(value), TIME_FORMATTER));
        } catch (DateTimeParseException e) {
            throw new BusinessException("时间格式应为 HH:mm:ss");
        }
    }

    private String normalizeDateText(Object value) {
        String text = toCellText(value).replace("/", "-").replace("T", " ");
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("日期不能为空");
        }
        return text;
    }
}
