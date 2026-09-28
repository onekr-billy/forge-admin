package com.mdframe.forge.starter.flow.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 动态表单对象数组权限校验。
 *
 * <p>数组字段是 Flowable 流程变量，不经过业务对象 CRUD，因此必须在任务完成前
 * 重新依据当前 BPMN 节点的 schema 和权限校验，不能信任前端禁用态。</p>
 */
public final class DynamicFormArrayPermissionValidator {

    private DynamicFormArrayPermissionValidator() {
    }

    public static void validate(ObjectMapper objectMapper,
                                String schemaJson,
                                String permissionJson,
                                Map<String, Object> existingVariables,
                                Map<String, Object> submittedVariables) {
        if (submittedVariables == null || submittedVariables.isEmpty() || !StringUtils.hasText(schemaJson)) {
            return;
        }
        Map<String, ArraySchema> schemas = parseArraySchemas(objectMapper, schemaJson);
        if (schemas.isEmpty()) {
            return;
        }
        PermissionBundle permissions = parsePermissions(objectMapper, permissionJson);
        Map<String, Object> existing = existingVariables == null ? Map.of() : existingVariables;
        for (ArraySchema schema : schemas.values()) {
            if (!submittedVariables.containsKey(schema.field())) {
                continue;
            }
            Object submittedRaw = submittedVariables.get(schema.field());
            List<Map<String, Object>> submitted = requireRows(schema.field(), submittedRaw);
            List<Map<String, Object>> current = readRows(existing.get(schema.field()));
            validateArray(schema, permissions, current, submitted);
        }
    }

    private static void validateArray(ArraySchema schema,
                                      PermissionBundle bundle,
                                      List<Map<String, Object>> current,
                                      List<Map<String, Object>> submitted) {
        FieldPermission parent = bundle.mainFields().get(schema.field());
        if (parent != null && !parent.readable() && !current.equals(submitted)) {
            throw denied(schema.field(), "字段不可见，不允许修改");
        }
        if (parent != null && !parent.writable() && !current.equals(submitted)) {
            throw denied(schema.field(), "字段不可编辑");
        }

        ArrayPermission operation = bundle.arrays().get(schema.field());
        if (operation != null && !operation.readable() && !current.equals(submitted)) {
            throw denied(schema.field(), "数组明细不可见，不允许修改");
        }
        boolean permissionManaged = bundle.managed();
        boolean allowCreate = operation != null ? operation.allowCreate() : (!permissionManaged && schema.allowCreate());
        boolean allowUpdate = operation != null ? operation.allowUpdate() : (parent == null || parent.writable());
        boolean allowDelete = operation != null ? operation.allowDelete() : (!permissionManaged && schema.allowDelete());

        if (submitted.size() > current.size() && !allowCreate) {
            throw denied(schema.field(), "未授权新增明细");
        }
        if (submitted.size() < current.size() && !allowDelete) {
            throw denied(schema.field(), "未授权删除明细");
        }
        if (submitted.size() < schema.min()) {
            throw denied(schema.field(), "明细数量少于最小值 " + schema.min());
        }
        if (schema.max() > 0 && submitted.size() > schema.max()) {
            throw denied(schema.field(), "明细数量超过最大值 " + schema.max());
        }

        Map<String, FieldPermission> itemPermissions = bundle.itemFields().getOrDefault(schema.field(), Map.of());
        boolean arrayPermissionManaged = operation != null || bundle.itemFields().containsKey(schema.field());
        Set<String> writableFields = new HashSet<>();
        Set<String> createWritableFields = new HashSet<>();
        Set<String> createRequiredFields = new HashSet<>();
        Set<String> protectedFields = new HashSet<>();
        current.forEach(row -> row.keySet().stream()
                .filter(field -> !schema.itemFields().contains(field))
                .forEach(protectedFields::add));
        submitted.forEach(row -> row.keySet().stream()
                .filter(field -> !schema.itemFields().contains(field))
                .forEach(protectedFields::add));
        for (String field : schema.itemFields()) {
            FieldPermission permission = itemPermissions.get(field);
            boolean fieldWritable = permission != null ? permission.writable() : !arrayPermissionManaged;
            if (fieldWritable) {
                createWritableFields.add(field);
            }
            boolean requiredOnCreate = permission != null
                    ? permission.required()
                    : schema.requiredFields().contains(field);
            if (requiredOnCreate && createWritableFields.contains(field)) {
                createRequiredFields.add(field);
            }
            boolean writable = allowUpdate && fieldWritable;
            if (writable) {
                writableFields.add(field);
            } else {
                protectedFields.add(field);
            }
        }
        submitted.forEach(row -> validateRowShape(schema, itemPermissions, writableFields, row));

        if (!canTransformRows(current, submitted, schema.itemFields(), protectedFields, createWritableFields,
                createRequiredFields, allowCreate, allowDelete)) {
            throw denied(schema.field(), "包含不可写行字段或未授权的行替换");
        }
    }

    private static void validateRowShape(ArraySchema schema,
                                         Map<String, FieldPermission> permissions,
                                         Set<String> writableFields,
                                         Map<String, Object> row) {
        for (String field : schema.requiredFields()) {
            FieldPermission permission = permissions.get(field);
            boolean required = permission != null ? permission.required() : schema.requiredFields().contains(field);
            if (required && writableFields.contains(field) && isEmpty(row.get(field))) {
                throw denied(schema.field(), "行字段 " + field + " 必填");
            }
        }
        for (FieldPermission permission : permissions.values()) {
            if (permission.required() && writableFields.contains(permission.field()) && isEmpty(row.get(permission.field()))) {
                throw denied(schema.field(), "行字段 " + permission.field() + " 必填");
            }
        }
    }

    /**
     * 顺序语义的行合并校验：匹配行必须保持全部受保护字段；跳过旧行视为删除，
     * 跳过新行视为新增。这样无需信任客户端行 ID，也不会把中间删除误判为字段篡改。
     */
    private static boolean canTransformRows(List<Map<String, Object>> current,
                                            List<Map<String, Object>> submitted,
                                            Set<String> knownFields,
                                            Set<String> protectedFields,
                                            Set<String> createWritableFields,
                                            Set<String> createRequiredFields,
                                            boolean allowCreate,
                                            boolean allowDelete) {
        boolean[][] reachable = new boolean[current.size() + 1][submitted.size() + 1];
        reachable[0][0] = true;
        for (int oldIndex = 0; oldIndex <= current.size(); oldIndex++) {
            for (int newIndex = 0; newIndex <= submitted.size(); newIndex++) {
                if (!reachable[oldIndex][newIndex]) {
                    continue;
                }
                if (oldIndex < current.size() && newIndex < submitted.size()
                        && protectedFieldsEqual(current.get(oldIndex), submitted.get(newIndex), protectedFields)) {
                    reachable[oldIndex + 1][newIndex + 1] = true;
                }
                if (allowDelete && oldIndex < current.size()) {
                    reachable[oldIndex + 1][newIndex] = true;
                }
                if (allowCreate && newIndex < submitted.size()
                        && canCreateRow(submitted.get(newIndex), knownFields,
                        createWritableFields, createRequiredFields)) {
                    reachable[oldIndex][newIndex + 1] = true;
                }
            }
        }
        return reachable[current.size()][submitted.size()];
    }

    private static boolean canCreateRow(Map<String, Object> row,
                                        Set<String> knownFields,
                                        Set<String> createWritableFields,
                                        Set<String> createRequiredFields) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!knownFields.contains(entry.getKey())) {
                return false;
            }
            if (!createWritableFields.contains(entry.getKey()) && !isEmpty(entry.getValue())) {
                return false;
            }
        }
        for (String field : createRequiredFields) {
            if (isEmpty(row.get(field))) {
                return false;
            }
        }
        return true;
    }

    private static boolean protectedFieldsEqual(Map<String, Object> current,
                                                Map<String, Object> submitted,
                                                Set<String> protectedFields) {
        for (String field : protectedFields) {
            if (!java.util.Objects.deepEquals(current.get(field), submitted.get(field))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, ArraySchema> parseArraySchemas(ObjectMapper objectMapper, String schemaJson) {
        Map<String, ArraySchema> result = new LinkedHashMap<>();
        try {
            collectArraySchemas(objectMapper.readTree(schemaJson), result);
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("动态表单 schema 解析失败", e);
        }
    }

    private static void collectArraySchemas(JsonNode node, Map<String, ArraySchema> result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectArraySchemas(child, result));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String field = resolveField(node);
        String type = normalizeType(firstText(node, "type", "component", "componentKey"));
        if (StringUtils.hasText(field) && !field.startsWith("ref_")
                && Set.of("group", "tableform", "subform", "array").contains(type)) {
            JsonNode props = node.path("props");
            Set<String> itemFields = new HashSet<>();
            Set<String> requiredFields = new HashSet<>();
            JsonNode itemRules;
            if ("tableform".equals(type)) {
                itemRules = props.path("columns");
                if (!itemRules.isArray()) {
                    itemRules = node.path("columns");
                }
                if (itemRules.isArray()) {
                    for (JsonNode column : itemRules) {
                        collectItemFields(column.path("rule"), itemFields, requiredFields);
                    }
                }
            } else {
                itemRules = props.path("rule");
                if (!itemRules.isArray()) {
                    itemRules = node.path("rule");
                }
                if (!itemRules.isArray()) {
                    itemRules = node.path("children");
                }
                collectItemFields(itemRules, itemFields, requiredFields);
            }
            boolean allowCreate = !props.has("addable")
                    ? !props.has("button") || props.path("button").asBoolean(true)
                    : props.path("addable").asBoolean(true);
            boolean allowDelete = !props.has("deletable")
                    ? !props.has("button") || props.path("button").asBoolean(true)
                    : props.path("deletable").asBoolean(true);
            result.putIfAbsent(field, new ArraySchema(
                    field,
                    itemFields,
                    requiredFields,
                    Math.max(0, props.path("min").asInt(0)),
                    Math.max(0, props.path("max").asInt(0)),
                    allowCreate,
                    allowDelete));
            return;
        }
        node.fields().forEachRemaining(entry -> {
            if (!"props".equals(entry.getKey()) && !"_fc_drag_tag".equals(entry.getKey())) {
                collectArraySchemas(entry.getValue(), result);
            }
        });
    }

    private static void collectItemFields(JsonNode node, Set<String> fields, Set<String> requiredFields) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectItemFields(child, fields, requiredFields));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String field = resolveField(node);
        if (StringUtils.hasText(field) && !field.startsWith("ref_")) {
            fields.add(field);
            if (isRequired(node)) {
                requiredFields.add(field);
            }
        }
        node.fields().forEachRemaining(entry -> {
            if (!"props".equals(entry.getKey()) && !"_fc_drag_tag".equals(entry.getKey())) {
                collectItemFields(entry.getValue(), fields, requiredFields);
            }
        });
    }

    private static PermissionBundle parsePermissions(ObjectMapper objectMapper, String json) {
        if (!StringUtils.hasText(json)) {
            return PermissionBundle.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode fields = root.isArray() ? root : root.path("fields");
            JsonNode arrays = root.isObject() ? root.path("arrays") : null;
            Map<String, FieldPermission> main = new HashMap<>();
            Map<String, Map<String, FieldPermission>> items = new HashMap<>();
            if (fields != null && fields.isArray()) {
                for (JsonNode fieldNode : fields) {
                    String scope = normalizeType(fieldNode.path("scope").asText("main"));
                    String field = firstText(fieldNode, "itemField", "childField", "field", "fieldCode", "code");
                    if (!StringUtils.hasText(field)) {
                        continue;
                    }
                    boolean readable = readBoolean(fieldNode, "readable", readBoolean(fieldNode, "visible", true));
                    boolean writable = readable && readBoolean(fieldNode, "writable",
                            readBoolean(fieldNode, "editable", true));
                    FieldPermission permission = new FieldPermission(field, readable, writable,
                            writable && readBoolean(fieldNode, "required", false));
                    if ("array".equals(scope)) {
                        String arrayKey = fieldNode.path("arrayKey").asText("").trim();
                        if (!arrayKey.isEmpty()) {
                            items.computeIfAbsent(arrayKey, ignored -> new HashMap<>()).put(field, permission);
                        }
                    } else if (!"child".equals(scope)) {
                        main.put(field, permission);
                    }
                }
            }
            Map<String, ArrayPermission> operations = new HashMap<>();
            if (arrays != null && arrays.isArray()) {
                for (JsonNode arrayNode : arrays) {
                    String arrayKey = firstText(arrayNode, "arrayKey", "field", "key");
                    if (!StringUtils.hasText(arrayKey)) {
                        continue;
                    }
                    boolean readable = readBoolean(arrayNode, "readable", true);
                    operations.put(arrayKey, new ArrayPermission(
                            readable,
                            readable && readBoolean(arrayNode, "allowCreate", false),
                            readable && readBoolean(arrayNode, "allowUpdate", true),
                            readable && readBoolean(arrayNode, "allowDelete", false)));
                }
            }
            return new PermissionBundle(true, main, items, operations);
        } catch (Exception e) {
            throw new IllegalArgumentException("动态表单权限解析失败", e);
        }
    }

    private static List<Map<String, Object>> requireRows(String field, Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw denied(field, "必须是对象数组");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                throw denied(field, "数组元素必须是对象");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            map.forEach((key, rowValue) -> row.put(String.valueOf(key), rowValue));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> readRows(Object value) {
        if (!(value instanceof Collection<?>)) {
            return List.of();
        }
        return requireRows("array", value);
    }

    private static String resolveField(JsonNode node) {
        String field = firstText(node, "field", "fieldCode", "name");
        if (!StringUtils.hasText(field)) {
            field = firstText(node.path("props"), "field", "fieldCode");
        }
        return field;
    }

    private static boolean isRequired(JsonNode node) {
        if (node.path("required").asBoolean(false)) {
            return true;
        }
        for (String key : List.of("validate", "rules")) {
            JsonNode rules = node.path(key);
            if (rules.isArray()) {
                for (JsonNode rule : rules) {
                    if (rule.path("required").asBoolean(false)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean readBoolean(JsonNode node, String key, boolean fallback) {
        if (node == null || !node.has(key) || node.get(key).isNull()) {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() != 0;
        }
        String text = value.asText("").trim().toLowerCase(Locale.ROOT);
        if (Set.of("true", "1", "yes", "y").contains(text)) {
            return true;
        }
        if (Set.of("false", "0", "no", "n").contains(text)) {
            return false;
        }
        return fallback;
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static String normalizeType(String value) {
        return value == null ? "" : value.replaceAll("[-_\\s]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.trim().isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    private static IllegalArgumentException denied(String field, String reason) {
        return new IllegalArgumentException("动态表单数组字段 " + field + " 校验失败：" + reason);
    }

    private record ArraySchema(String field,
                               Set<String> itemFields,
                               Set<String> requiredFields,
                               int min,
                               int max,
                               boolean allowCreate,
                               boolean allowDelete) {
    }

    private record FieldPermission(String field, boolean readable, boolean writable, boolean required) {
    }

    private record ArrayPermission(boolean readable,
                                   boolean allowCreate,
                                   boolean allowUpdate,
                                   boolean allowDelete) {
    }

    private record PermissionBundle(boolean managed,
                                    Map<String, FieldPermission> mainFields,
                                    Map<String, Map<String, FieldPermission>> itemFields,
                                    Map<String, ArrayPermission> arrays) {
        private static PermissionBundle empty() {
            return new PermissionBundle(false, Map.of(), Map.of(), Map.of());
        }
    }
}
