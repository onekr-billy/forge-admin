package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.enums.DataAuditValueState;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 按字段类型规范化落库值，供差异比较与历史存储。
 */
@Component
public class DataAuditValueNormalizer {

    public static final int MAX_FIELD_BYTES = 1024 * 1024;
    public static final int MAX_EVENT_BYTES = 8 * 1024 * 1024;

    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "id", "tenant_id", "create_by", "create_time", "create_dept",
            "update_by", "update_time", "del_flag", "deleted"
    );
    private static final Set<String> MULTI_SELECT_COMPONENTS = Set.of(
            "select", "dictSelect", "userSelect", "orgTreeSelect", "objectReference", "recordSelector"
    );
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;

    public DataAuditValueNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isSystemColumn(String columnName, String fieldCode) {
        String column = columnName == null ? "" : columnName.trim().toLowerCase(Locale.ROOT);
        String field = fieldCode == null ? "" : fieldCode.trim();
        if (SYSTEM_COLUMNS.contains(column)) {
            return true;
        }
        return "tenantId".equals(field)
                || "createBy".equals(field)
                || "createTime".equals(field)
                || "createDept".equals(field)
                || "updateBy".equals(field)
                || "updateTime".equals(field)
                || "delFlag".equals(field);
    }

    public DataAuditNormalizedValue normalize(Object raw, LowcodeFieldSchema field) {
        if (raw instanceof DataAuditNormalizedValue value) {
            return value;
        }
        String type = resolveType(field);
        if (raw == null) {
            return DataAuditNormalizedValue.nil(type);
        }
        try {
            return switch (type) {
                case "NUMBER", "MONEY" -> normalizeNumber(raw, field, type);
                case "BOOLEAN" -> normalizeBoolean(raw);
                case "DATE" -> normalizeDate(raw);
                case "DATETIME" -> normalizeDateTime(raw);
                case "JSON" -> normalizeJson(raw, false);
                case "ARRAY" -> normalizeArray(raw, field);
                case "FILE" -> normalizeFile(raw);
                default -> normalizeString(raw);
            };
        } catch (BusinessCapacityException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("字段值规范化失败");
        }
    }

    public boolean equals(DataAuditNormalizedValue left, DataAuditNormalizedValue right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.getState() != right.getState()) {
            return false;
        }
        if (left.getState() != DataAuditValueState.VALUE) {
            return true;
        }
        Object a = left.getCanonical();
        Object b = right.getCanonical();
        if (a instanceof BigDecimal leftNumber && b instanceof BigDecimal rightNumber) {
            return leftNumber.compareTo(rightNumber) == 0;
        }
        if (a instanceof JsonNode leftNode && b instanceof JsonNode rightNode) {
            return leftNode.equals(rightNode);
        }
        if (a instanceof List<?> leftList && b instanceof List<?> rightList) {
            return leftList.equals(rightList);
        }
        return java.util.Objects.equals(a, b);
    }

    public String encode(DataAuditNormalizedValue value) {
        if (value == null || value.getState() != DataAuditValueState.VALUE) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", value.getType(),
                    "value", value.getCanonical()
            ));
        } catch (JsonProcessingException ex) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("字段值序列化失败");
        }
    }

    public Object decodeCanonical(String encoded, String type) {
        if (StringUtils.isBlank(encoded)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(encoded);
            JsonNode value = node.get("value");
            if (value == null || value.isNull()) {
                return null;
            }
            if ("NUMBER".equals(type) || "MONEY".equals(type)) {
                return new BigDecimal(value.asText());
            }
            if (value.isTextual()) {
                return value.asText();
            }
            if (value.isBoolean()) {
                return value.booleanValue();
            }
            if (value.isNumber()) {
                return value.decimalValue();
            }
            return objectMapper.convertValue(value, Object.class);
        } catch (Exception ex) {
            return encoded;
        }
    }

    private DataAuditNormalizedValue normalizeNumber(Object raw, LowcodeFieldSchema field, String type) {
        BigDecimal number = toDecimal(raw);
        if (number == null) {
            return DataAuditNormalizedValue.nil(type);
        }
        Integer precision = field == null ? null : field.getPrecision();
        if (precision != null && precision >= 0) {
            number = number.setScale(precision, RoundingMode.UNNECESSARY);
        } else {
            number = number.stripTrailingZeros();
        }
        return sized(DataAuditValueState.VALUE, type, number, number.toPlainString());
    }

    private DataAuditNormalizedValue normalizeBoolean(Object raw) {
        Boolean value;
        if (raw instanceof Boolean bool) {
            value = bool;
        } else {
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                return DataAuditNormalizedValue.nil("BOOLEAN");
            }
            value = "1".equals(text) || "true".equalsIgnoreCase(text);
        }
        return sized(DataAuditValueState.VALUE, "BOOLEAN", value, String.valueOf(value));
    }

    private DataAuditNormalizedValue normalizeDate(Object raw) {
        String text = stringify(raw);
        if (text.isEmpty()) {
            return DataAuditNormalizedValue.nil("DATE");
        }
        String canonical = text.length() >= 10 ? text.substring(0, 10) : text;
        try {
            canonical = LocalDate.parse(canonical, DATE).toString();
        } catch (Exception ignored) {
            // keep original
        }
        return sized(DataAuditValueState.VALUE, "DATE", canonical, canonical);
    }

    private DataAuditNormalizedValue normalizeDateTime(Object raw) {
        if (raw instanceof LocalDateTime dateTime) {
            String text = dateTime.format(DATE_TIME);
            return sized(DataAuditValueState.VALUE, "DATETIME", text, text);
        }
        String text = stringify(raw);
        if (text.isEmpty()) {
            return DataAuditNormalizedValue.nil("DATETIME");
        }
        String canonical = text.replace('T', ' ');
        if (canonical.length() > 19) {
            canonical = canonical.substring(0, 19);
        }
        return sized(DataAuditValueState.VALUE, "DATETIME", canonical, canonical);
    }

    private DataAuditNormalizedValue normalizeJson(Object raw, boolean arrayAsSet) {
        JsonNode node = toJsonNode(raw);
        JsonNode normalized = sortNode(node, arrayAsSet);
        return sized(DataAuditValueState.VALUE, arrayAsSet ? "ARRAY" : "JSON", normalized, normalized.toString());
    }

    private DataAuditNormalizedValue normalizeArray(Object raw, LowcodeFieldSchema field) {
        boolean ordered = field != null && "fileUpload".equals(field.getComponentType());
        if (ordered) {
            List<Object> items = toList(raw);
            return sized(DataAuditValueState.VALUE, "ARRAY", items, items.toString());
        }
        if (isMultiSelect(field)) {
            TreeSet<String> set = new TreeSet<>();
            for (Object item : toList(raw)) {
                if (item != null) {
                    set.add(String.valueOf(item));
                }
            }
            return sized(DataAuditValueState.VALUE, "ARRAY", new ArrayList<>(set), set.toString());
        }
        return normalizeJson(raw, true);
    }

    private DataAuditNormalizedValue normalizeFile(Object raw) {
        List<String> ids = new ArrayList<>();
        for (Object item : toList(raw)) {
            if (item instanceof Map<?, ?> map) {
                Object id = map.get("fileId") != null ? map.get("fileId") : map.get("id");
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            } else if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                ids.add(String.valueOf(item));
            }
        }
        return sized(DataAuditValueState.VALUE, "FILE", ids, String.join(",", ids));
    }

    private DataAuditNormalizedValue normalizeString(Object raw) {
        String text = raw instanceof String string ? string : String.valueOf(raw);
        return sized(DataAuditValueState.VALUE, "STRING", text, text);
    }

    private DataAuditNormalizedValue sized(DataAuditValueState state, String type, Object canonical, String display) {
        int size = encodedSize(canonical, display);
        if (size > MAX_FIELD_BYTES) {
            throw new BusinessCapacityException();
        }
        return DataAuditNormalizedValue.builder()
                .state(state)
                .type(type)
                .canonical(canonical)
                .display(display)
                .encodedSize(size)
                .build();
    }

    private int encodedSize(Object canonical, String display) {
        int displaySize = display == null ? 0 : display.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int canonicalSize;
        try {
            canonicalSize = objectMapper.writeValueAsBytes(canonical).length;
        } catch (Exception ex) {
            canonicalSize = displaySize;
        }
        return canonicalSize + displaySize;
    }

    private String resolveType(LowcodeFieldSchema field) {
        if (field == null) {
            return "STRING";
        }
        String component = StringUtils.defaultString(field.getComponentType());
        String dataType = StringUtils.defaultString(field.getDataType()).toLowerCase(Locale.ROOT);
        String business = StringUtils.defaultString(field.getBusinessFieldType()).toUpperCase(Locale.ROOT);
        if ("MONEY".equals(business) || "money".equals(component)) {
            return "MONEY";
        }
        if ("fileUpload".equals(component) || "imageUpload".equals(component) || "FILE".equals(business)) {
            return "FILE";
        }
        if ("switch".equals(component) || "boolean".equals(dataType) || "tinyint".equals(dataType) && "BOOLEAN".equals(business)) {
            return "BOOLEAN";
        }
        if ("date".equals(component) || "date".equals(dataType)) {
            return "DATE";
        }
        if ("datetime".equals(component) || "datetime".equals(dataType) || "timestamp".equals(dataType)) {
            return "DATETIME";
        }
        if ("json".equals(dataType) || "JSON".equals(business)) {
            return "JSON";
        }
        if (isMultiSelect(field) || dataType.contains("json") || "array".equals(dataType)) {
            return "ARRAY";
        }
        if (dataType.contains("int") || dataType.contains("decimal") || dataType.contains("numeric")
                || "NUMBER".equals(business) || "input-number".equals(component)) {
            return "NUMBER";
        }
        return "STRING";
    }

    private boolean isMultiSelect(LowcodeFieldSchema field) {
        if (field == null) {
            return false;
        }
        Object multiple = field.getBasicProps() == null ? null : field.getBasicProps().get("multiple");
        return Boolean.TRUE.equals(multiple) && MULTI_SELECT_COMPONENTS.contains(StringUtils.defaultString(field.getComponentType()));
    }

    private BigDecimal toDecimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = stringify(raw);
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private String stringify(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private List<Object> toList(Object raw) {
        if (raw instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                try {
                    return objectMapper.readValue(trimmed, objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, Object.class));
                } catch (Exception ignored) {
                    return List.of(trimmed);
                }
            }
            if (trimmed.contains(",")) {
                List<Object> items = new ArrayList<>();
                for (String part : trimmed.split(",")) {
                    if (StringUtils.isNotBlank(part)) {
                        items.add(part.trim());
                    }
                }
                return items;
            }
            if (trimmed.isEmpty()) {
                return List.of();
            }
            return List.of(trimmed);
        }
        return raw == null ? List.of() : List.of(raw);
    }

    private JsonNode toJsonNode(Object raw) {
        if (raw instanceof JsonNode node) {
            return node;
        }
        if (raw instanceof String text) {
            try {
                return objectMapper.readTree(text);
            } catch (Exception ex) {
                return objectMapper.getNodeFactory().textNode(text);
            }
        }
        return objectMapper.valueToTree(raw);
    }

    private JsonNode sortNode(JsonNode node, boolean arrayAsSet) {
        if (node == null || node.isNull()) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                sorted.set(name, sortNode(node.get(name), false));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            if (arrayAsSet) {
                TreeSet<String> items = new TreeSet<>();
                for (JsonNode child : node) {
                    items.add(sortNode(child, false).toString());
                }
                for (String item : items) {
                    try {
                        array.add(objectMapper.readTree(item));
                    } catch (Exception ex) {
                        array.add(item);
                    }
                }
                return array;
            }
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                array.add(sortNode(iterator.next(), false));
            }
            return array;
        }
        return node;
    }

    public static final class BusinessCapacityException extends RuntimeException {
        public BusinessCapacityException() {
            super(DataAuditErrorCode.AUDIT_VALUE_TOO_LARGE.getMessage());
        }
    }
}
