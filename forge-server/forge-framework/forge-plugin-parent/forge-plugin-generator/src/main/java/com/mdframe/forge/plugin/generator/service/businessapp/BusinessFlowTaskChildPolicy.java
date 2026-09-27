package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeFieldPermissions;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.sameField;

/**
 * 审批子表权限策略。
 * <p>
 * 统一节点字段权限匹配、保存白名单生成和返回数据裁剪；不查询数据库，
 * 不保存记录，也不修改流程状态。
 */
@Slf4j
final class BusinessFlowTaskChildPolicy {

    List<Map<String, Object>> applyFieldPermissions(List<Map<String, Object>> fields,
                                                     JSONObject nodeForm,
                                                     String childKey) {
        Map<String, Map<String, Object>> permissions = normalizeFieldPermissionMap(nodeForm);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : fields) {
            String field = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(source.get("field"))),
                    StringUtils.trimToNull(textValue(source.get("fieldCode"))),
                    StringUtils.trimToNull(textValue(source.get("sourceField"))));
            if (field == null) {
                continue;
            }
            Map<String, Object> permission = findFieldPermission(permissions, childKey, field);
            boolean readable = permission == null || readBooleanValue(permission.get("readable"), true);
            if (!readable) {
                continue;
            }
            boolean writable = permission != null && readBooleanValue(permission.get("writable"), false);
            Map<String, Object> fieldConfig = new LinkedHashMap<>(source);
            fieldConfig.put("field", field);
            fieldConfig.put("fieldCode", field);
            fieldConfig.put("readable", true);
            fieldConfig.put("writable", writable);
            fieldConfig.put("readonly", !writable);
            fieldConfig.put("disabled", !writable);
            fieldConfig.put("required", writable && permission != null
                    && readBooleanValue(permission.get("required"), false));
            fieldConfig.put("scope", "child");
            fieldConfig.put("childKey", childKey);
            fieldConfig.put("childField", field);
            result.add(fieldConfig);
        }
        return result;
    }

    boolean hasWritableField(Map<String, Object> child, JSONObject nodeForm, String childKey) {
        Map<String, Map<String, Object>> permissions = normalizeFieldPermissionMap(nodeForm);
        for (Map<String, Object> field : readMapList(readNestedArray(child.get("fields")))) {
            String fieldName = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("field"))),
                    StringUtils.trimToNull(textValue(field.get("fieldCode"))),
                    StringUtils.trimToNull(textValue(field.get("sourceField"))));
            Map<String, Object> permission = findFieldPermission(permissions, childKey, fieldName);
            if (permission != null && readBooleanValue(permission.get("writable"), false)) {
                return true;
            }
        }
        return false;
    }

    Map<String, DynamicCrudService.TaskChildPermission> buildSavePermissions(
            List<Map<String, Object>> childrenConfig, JSONObject nodeForm) {
        Map<String, Map<String, Object>> nodeFieldPermissions = normalizeFieldPermissionMap(nodeForm);
        Map<String, DynamicCrudService.TaskChildPermission> result = new LinkedHashMap<>();
        for (Map<String, Object> child : childrenConfig) {
            String childKey = resolveChildKey(child);
            if (StringUtils.isBlank(childKey)) {
                continue;
            }
            Set<String> writableFields = new LinkedHashSet<>();
            boolean explicitFieldPermission = false;
            for (Map.Entry<String, Map<String, Object>> entry : nodeFieldPermissions.entrySet()) {
                String permissionKey = entry.getKey();
                int split = permissionKey == null ? -1 : permissionKey.lastIndexOf(':');
                if (split <= 0) {
                    continue;
                }
                String configuredChildKey = permissionKey.substring(0, split);
                String configuredField = permissionKey.substring(split + 1);
                if (!sameChildTableKey(configuredChildKey, childKey) || StringUtils.isBlank(configuredField)) {
                    continue;
                }
                explicitFieldPermission = true;
                if (readBooleanValue(entry.getValue().get("writable"), false)) {
                    writableFields.add(configuredField);
                }
            }
            if (!explicitFieldPermission) {
                readMapList(readNestedArray(child.get("fields"))).stream()
                        .filter(field -> readBooleanValue(field.get("writable"), false))
                        .flatMap(field -> java.util.stream.Stream.of(
                                textValue(field.get("field")),
                                textValue(field.get("fieldCode")),
                                textValue(field.get("sourceField"))))
                        .filter(StringUtils::isNotBlank)
                        .forEach(writableFields::add);
            }
            result.put(childKey, new DynamicCrudService.TaskChildPermission(
                    readBooleanValue(child.get("readable"), true),
                    readBooleanValue(child.get("allowCreate"), false),
                    readBooleanValue(child.get("allowUpdate"), false),
                    readBooleanValue(child.get("allowDelete"), false),
                    writableFields));
        }
        return result;
    }

    Map<String, Map<String, Object>> normalizeChildPermissionMap(JSONObject nodeForm) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Object source = nodeForm == null ? null : nodeForm.get("childPermissions");
        if (source == null && nodeForm != null) {
            source = nodeForm.get("fieldPermissions");
        }
        JSONObject object = readNestedObject(source);
        JSONArray childArray = source instanceof List<?> || source instanceof JSONArray
                ? readNestedArray(source)
                : readNestedArray(object.get("children"));
        for (Map<String, Object> item : readMapList(childArray)) {
            String childKey = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(item.get("childKey"))),
                    StringUtils.trimToNull(textValue(item.get("relationKey"))),
                    StringUtils.trimToNull(textValue(item.get("key"))));
            if (childKey != null) {
                Map<String, Object> normalized = new LinkedHashMap<>(item);
                normalized.put("childKey", childKey);
                result.put(childKey, normalized);
            }
        }
        return result;
    }

    Map<String, Map<String, Object>> normalizeFieldPermissionMap(JSONObject nodeForm) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Object source = nodeForm == null ? null : nodeForm.get("fieldPermissions");
        if (source == null && nodeForm != null) {
            source = nodeForm.get("childPermissions");
        }
        for (Map<String, Object> item : normalizeFieldPermissions(source)) {
            if (!"child".equalsIgnoreCase(textValue(item.get("scope")))) {
                continue;
            }
            String childKey = StringUtils.trimToNull(textValue(item.get("childKey")));
            String childField = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(item.get("childField"))),
                    StringUtils.trimToNull(textValue(item.get("field"))));
            if (childKey != null && childField != null) {
                result.put(permissionKey(childKey, childField), item);
            }
        }
        return result;
    }

    boolean sameTaskFormKey(String configuredFormKey, String runtimeFormKey) {
        if (StringUtils.isAnyBlank(configuredFormKey, runtimeFormKey)) {
            return true;
        }
        if (StringUtils.equals(configuredFormKey, runtimeFormKey)) {
            return true;
        }
        return configuredFormKey.endsWith("_" + runtimeFormKey)
                || runtimeFormKey.endsWith("_" + configuredFormKey)
                || configuredFormKey.contains("_form_" + runtimeFormKey)
                || runtimeFormKey.contains("_form_" + configuredFormKey);
    }

    boolean sameFieldName(String left, String right) {
        if (StringUtils.equals(left, right)) {
            return true;
        }
        return StringUtils.isNotBlank(left) && StringUtils.isNotBlank(right) && sameField(left, right);
    }

    boolean sameChildTableKey(String left, String right) {
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return false;
        }
        if (StringUtils.equals(left, right)) {
            return true;
        }
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() <= right.length() ? right : left;
        return longer.endsWith("_" + shorter);
    }

    Map<String, Object> findChildPermission(Map<String, Map<String, Object>> permissions, String childKey) {
        if (permissions == null || permissions.isEmpty() || StringUtils.isBlank(childKey)) {
            return null;
        }
        Map<String, Object> direct = permissions.get(childKey);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Map<String, Object>> entry : permissions.entrySet()) {
            if (sameChildTableKey(entry.getKey(), childKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    Map<String, Object> extractMainPayload(Map<String, Object> data) {
        if (data != null && data.get("main") instanceof Map<?, ?> main) {
            return castMap(main);
        }
        if (data == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(data);
        result.remove("children");
        return result;
    }

    Map<String, Object> extractChildrenPayload(Map<String, Object> data) {
        if (data != null && data.get("children") instanceof Map<?, ?> children) {
            return castMap(children);
        }
        return Map.of();
    }

    boolean isDetailChild(Map<String, Object> child) {
        if (child == null || child.isEmpty() || Boolean.FALSE.equals(child.get("showInDetail"))) {
            return false;
        }
        if (readMapList(readNestedArray(child.get("fields"))).isEmpty()) {
            return false;
        }
        String relationType = StringUtils.defaultIfBlank(textValue(child.get("relationType")), "ONE_TO_MANY")
                .trim()
                .toUpperCase(Locale.ROOT);
        return !Set.of("REFERENCE", "LOOKUP", "OBJECT_REFERENCE", "OBJECTREFERENCE", "MANY_TO_ONE", "ONE_TO_ONE")
                .contains(relationType);
    }

    void filterVisibleRecordChildren(Map<String, Object> recordData, List<Map<String, Object>> childrenConfig) {
        if (recordData == null || !recordData.containsKey("children")) {
            return;
        }
        if (childrenConfig == null || childrenConfig.isEmpty()) {
            recordData.remove("children");
            return;
        }
        Object childrenValue = recordData.get("children");
        if (!(childrenValue instanceof Map<?, ?> children)) {
            return;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map<String, Object> childConfig : childrenConfig) {
            String key = resolveChildKey(childConfig);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Object value = findChildRowsByAlias(children, childConfig);
            if (!(value instanceof List<?> rows)) {
                filtered.put(key, List.of());
                continue;
            }
            Set<String> visibleFields = readMapList(readNestedArray(childConfig.get("fields"))).stream()
                    .map(field -> StringUtils.firstNonBlank(
                            StringUtils.trimToNull(textValue(field.get("field"))),
                            StringUtils.trimToNull(textValue(field.get("fieldCode")))))
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<Map<String, Object>> visibleRows = new ArrayList<>();
            for (Object rowValue : rows) {
                if (!(rowValue instanceof Map<?, ?> row)) {
                    continue;
                }
                Map<String, Object> visibleRow = new LinkedHashMap<>();
                row.forEach((rowKey, rowItem) -> {
                    String field = String.valueOf(rowKey);
                    if (isVisibleRowField(field, visibleFields)) {
                        visibleRow.put(field, rowItem);
                    }
                });
                visibleRows.add(visibleRow);
            }
            filtered.put(key, visibleRows);
            String sourceKey = resolveMatchedDataKey(children, childConfig);
            if (StringUtils.isNotBlank(sourceKey) && !StringUtils.equals(sourceKey, key)) {
                filtered.put(sourceKey, visibleRows);
            }
        }
        recordData.put("children", filtered);
    }

    void logChildren(String stage,
                     String configKey,
                     Object recordId,
                     List<Map<String, Object>> childrenConfig,
                     Map<String, Object> recordData) {
        log.info("[审批表单子表] stage={}, configKey={}, recordId={}, childrenConfig={}, children={}",
                stage, configKey, recordId, summarizeConfig(childrenConfig),
                summarizeData(recordData == null ? null : recordData.get("children")));
    }

    List<String> childKeyCandidates(Map<String, Object> child) {
        if (child == null) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String field : new String[]{"modelCode", "relationKey", "key", "tableName"}) {
            String trimmed = StringUtils.trimToNull(textValue(child.get(field)));
            if (trimmed != null) {
                keys.add(trimmed);
            }
        }
        return List.copyOf(keys);
    }

    String resolveChildKey(Map<String, Object> child) {
        if (child == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(child.get("modelCode"))),
                StringUtils.trimToNull(textValue(child.get("relationKey"))),
                StringUtils.trimToNull(textValue(child.get("key"))),
                StringUtils.trimToNull(textValue(child.get("tableName"))),
                "children");
    }

    private Map<String, Object> findFieldPermission(Map<String, Map<String, Object>> permissions,
                                                    String childKey,
                                                    String field) {
        if (permissions == null || permissions.isEmpty() || StringUtils.isBlank(field)) {
            return null;
        }
        Map<String, Object> direct = permissions.get(permissionKey(childKey, field));
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Map<String, Object>> entry : permissions.entrySet()) {
            String key = entry.getKey();
            int split = key == null ? -1 : key.lastIndexOf(':');
            if (split <= 0) {
                continue;
            }
            String configuredChildKey = key.substring(0, split);
            String configuredField = key.substring(split + 1);
            if (sameFieldName(configuredField, field) && sameChildTableKey(configuredChildKey, childKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String permissionKey(String childKey, String field) {
        return StringUtils.defaultString(childKey) + ":" + StringUtils.defaultString(field);
    }

    private boolean isVisibleRowField(String field, Set<String> visibleFields) {
        if (StringUtils.isBlank(field)) {
            return false;
        }
        if ("id".equalsIgnoreCase(field) || "_deleted".equalsIgnoreCase(field) || "__deleted".equalsIgnoreCase(field)) {
            return true;
        }
        if (visibleFields == null || visibleFields.isEmpty() || visibleFields.contains(field)) {
            return true;
        }
        for (String visibleField : visibleFields) {
            if (sameFieldName(visibleField, field)
                    || StringUtils.equalsIgnoreCase(field, visibleField + "Name")) {
                return true;
            }
        }
        if (field.endsWith("Name") && field.length() > 4) {
            String base = field.substring(0, field.length() - 4);
            return visibleFields.stream().anyMatch(visibleField -> sameFieldName(visibleField, base));
        }
        return false;
    }

    private Object findChildRowsByAlias(Map<?, ?> children, Map<String, Object> childConfig) {
        if (children == null || children.isEmpty() || childConfig == null) {
            return null;
        }
        for (String candidate : childKeyCandidates(childConfig)) {
            Object value = children.get(candidate);
            if (value instanceof List<?>) {
                return value;
            }
        }
        String bestKey = resolveMatchedDataKey(children, childConfig);
        return bestKey == null ? null : children.get(bestKey);
    }

    private String resolveMatchedDataKey(Map<?, ?> children, Map<String, Object> childConfig) {
        if (children == null || children.isEmpty() || childConfig == null) {
            return null;
        }
        String bestKey = null;
        int bestDelta = Integer.MAX_VALUE;
        for (String candidate : childKeyCandidates(childConfig)) {
            for (Object rawKey : children.keySet()) {
                String dataKey = rawKey == null ? null : StringUtils.trimToNull(String.valueOf(rawKey));
                if (dataKey == null || !(children.get(rawKey) instanceof List<?>)) {
                    continue;
                }
                if (StringUtils.equals(dataKey, candidate)) {
                    return dataKey;
                }
                if (!sameChildTableKey(dataKey, candidate)) {
                    continue;
                }
                int delta = Math.abs(dataKey.length() - candidate.length());
                if (delta < bestDelta) {
                    bestDelta = delta;
                    bestKey = dataKey;
                }
            }
        }
        return bestKey;
    }

    private List<Map<String, Object>> summarizeConfig(List<Map<String, Object>> childrenConfig) {
        if (childrenConfig == null || childrenConfig.isEmpty()) {
            return List.of();
        }
        return childrenConfig.stream()
                .map(child -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", resolveChildKey(child));
                    item.put("modelCode", textValue(child.get("modelCode")));
                    item.put("tableName", textValue(child.get("tableName")));
                    item.put("relationType", textValue(child.get("relationType")));
                    item.put("sourceField", textValue(child.get("sourceField")));
                    item.put("targetField", textValue(child.get("targetField")));
                    item.put("fieldCount", readMapList(readNestedArray(child.get("fields"))).size());
                    return item;
                })
                .toList();
    }

    private Map<String, Object> summarizeData(Object childrenValue) {
        if (!(childrenValue instanceof Map<?, ?> children) || children.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : children.entrySet()) {
            Object value = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            if (value instanceof List<?> list) {
                item.put("rows", list.size());
                item.put("rowIds", list.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .limit(5)
                        .map(row -> ((Map<?, ?>) row).get("id"))
                        .toList());
                item.put("firstFields", list.stream()
                        .filter(Map.class::isInstance)
                        .map(Map.class::cast)
                        .findFirst()
                        .map(row -> ((Map<?, ?>) row).keySet().stream().limit(12).toList())
                        .orElse(List.of()));
            } else {
                item.put("type", value == null ? "null" : value.getClass().getSimpleName());
            }
            result.put(String.valueOf(entry.getKey()), item);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }
}
