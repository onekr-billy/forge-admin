package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.putIfText;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;

/** 节点表单资产与主/子表字段权限的兼容归一化。 */
final class BusinessFlowNodeFormNormalizer {

    private BusinessFlowNodeFormNormalizer() {
    }

    static List<Map<String, Object>> normalizeNodeForms(List<Map<String, Object>> nodeForms) {
        if (nodeForms == null || nodeForms.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> source : nodeForms) {
            if (source == null) {
                continue;
            }
            String taskDefKey = StringUtils.trimToNull(textValue(source.get("taskDefKey")));
            if (taskDefKey == null || !seen.add(taskDefKey)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            putIfText(item, "taskDefKey", taskDefKey);
            putIfText(item, "taskName", source.get("taskName"));
            String formMode = normalizeNodeFormMode(textValue(source.get("formMode")));
            putIfText(item, "formMode", formMode);
            putIfText(item, "formKey", source.get("formKey"));
            putIfText(item, "formName", source.get("formName"));
            putIfText(item, "providerKey", source.get("providerKey"));
            putIfText(item, "formUrl", source.get("formUrl"));
            putIfText(item, "viewKey", source.get("viewKey"));
            putIfText(item, "editMode", normalizeNodeEditMode(textValue(source.get("editMode"))));
            Object formRef = source.get("formRef");
            if (formRef instanceof Map<?, ?> || formRef instanceof JSONObject) {
                item.put("formRef", readNestedObject(formRef));
            }
            List<Map<String, Object>> fieldPermissions = normalizeFieldPermissions(source.get("fieldPermissions"));
            if (fieldPermissions.isEmpty()) {
                fieldPermissions = normalizeFieldSelections(source);
            }
            if (!fieldPermissions.isEmpty()) {
                item.put("fieldPermissions", fieldPermissions);
            }
            result.add(item);
        }
        return result;
    }

    static String normalizeNodeFormMode(String formMode) {
        String normalized = StringUtils.defaultIfBlank(formMode, "BUSINESS_OBJECT_FORM").trim().toUpperCase();
        if ("BUSINESS_CODE_FORM".equals(normalized) || "EXTERNAL".equals(normalized)) {
            return normalized;
        }
        return "BUSINESS_OBJECT_FORM";
    }

    static String normalizeNodeEditMode(String editMode) {
        String normalized = StringUtils.defaultIfBlank(editMode, "READONLY").trim().toUpperCase();
        if ("EDITABLE".equals(normalized) || "MODIFY_RESUBMIT".equals(normalized)) {
            return normalized;
        }
        return "READONLY";
    }

    static List<Map<String, Object>> normalizeFieldPermissions(Object permissions) {
        // 带子表时 formFieldPermissions 是 JSON 对象字符串（version/fields/children）。
        // 按数组解析会失败并丢掉 fields，暂存时子表 writableFields 为空，已提交的 fieldInput 会被拒绝。
        if (permissions instanceof String stringValue) {
            String text = StringUtils.trimToEmpty(stringValue);
            if (text.startsWith("{")) {
                return normalizeFieldPermissions(readNestedObject(text));
            }
        }
        if (permissions instanceof Map<?, ?> || permissions instanceof JSONObject) {
            JSONObject object = readNestedObject(permissions);
            JSONArray fields = readNestedArray(object.get("fields"));
            if (!fields.isEmpty()) {
                return normalizeFieldPermissions(fields);
            }
            return buildFieldPermissionsFromSelections(
                    readFieldSet(object, "visibleFields", "visible", "readableFields", "readable"),
                    readFieldSet(object, "writableFields", "writable"),
                    readFieldSet(object, "requiredFields", "required"));
        }
        JSONArray array = readNestedArray(permissions);
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject source = array.getJSONObject(i);
            if (source == null) {
                continue;
            }
            String field = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(source.getString("field")),
                    StringUtils.trimToNull(source.getString("fieldCode")),
                    StringUtils.trimToNull(source.getString("code")));
            String scope = "child".equalsIgnoreCase(source.getString("scope")) || source.getString("childKey") != null
                    ? "child" : "main";
            String childKey = StringUtils.trimToNull(source.getString("childKey"));
            String childField = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(source.getString("childField")), field);
            String permissionKey = "child".equals(scope)
                    ? "child:" + StringUtils.defaultString(childKey) + ":" + StringUtils.defaultString(childField)
                    : "main:" + StringUtils.defaultString(field);
            if (field == null || !seen.add(permissionKey)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", "child".equals(scope) ? childField : field);
            item.put("fieldCode", "child".equals(scope) ? childField : field);
            if ("child".equals(scope)) {
                item.put("scope", "child");
                item.put("childKey", childKey);
                item.put("childField", childField);
            }
            putIfText(item, "label", source.getString("label"));
            boolean readable = readBooleanValue(source.get("readable"), readBooleanValue(source.get("visible"), true));
            boolean writable = readable && readBooleanValue(source.get("writable"), readBooleanValue(source.get("editable"), true));
            item.put("visible", readable);
            item.put("editable", writable);
            item.put("readable", readable);
            item.put("writable", writable);
            item.put("required", writable && readBooleanValue(source.get("required"), false));
            result.add(item);
        }
        return result;
    }

    static List<Map<String, Object>> normalizeTaskChildPermissions(Object source) {
        JSONObject object = readNestedObject(source);
        JSONArray children = source instanceof List<?> || source instanceof JSONArray
                ? readNestedArray(source)
                : readNestedArray(object.get("children"));
        if (children.isEmpty() && source instanceof String stringSource) {
            children = readNestedArray(stringSource);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < children.size(); i++) {
            JSONObject child = children.getJSONObject(i);
            if (child == null) {
                continue;
            }
            String childKey = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(child.getString("childKey")),
                    StringUtils.trimToNull(child.getString("relationKey")),
                    StringUtils.trimToNull(child.getString("key")));
            if (childKey == null || !seen.add(childKey)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(child);
            item.put("childKey", childKey);
            item.put("readable", readBooleanValue(child.get("readable"), true));
            item.put("allowCreate", readBooleanValue(child.get("allowCreate"), false));
            item.put("allowUpdate", readBooleanValue(child.get("allowUpdate"), false));
            item.put("allowDelete", readBooleanValue(child.get("allowDelete"), false));
            result.add(item);
        }
        return result;
    }

    private static List<Map<String, Object>> normalizeFieldSelections(Map<String, Object> source) {
        return buildFieldPermissionsFromSelections(
                readFieldSet(source, "visibleFields", "visible", "readableFields", "readable"),
                readFieldSet(source, "writableFields", "writable"),
                readFieldSet(source, "requiredFields", "required"));
    }

    private static List<Map<String, Object>> buildFieldPermissionsFromSelections(Set<String> visible,
                                                                          Set<String> writable,
                                                                          Set<String> required) {
        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(visible);
        fields.addAll(writable);
        fields.addAll(required);
        if (fields.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String field : fields) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", field);
            boolean readable = visible.isEmpty() || visible.contains(field);
            boolean editable = readable && writable.contains(field);
            item.put("fieldCode", field);
            item.put("visible", readable);
            item.put("editable", editable);
            item.put("readable", readable);
            item.put("writable", editable);
            item.put("required", editable && required.contains(field));
            result.add(item);
        }
        return result;
    }

    private static Set<String> readFieldSet(Map<String, Object> source, String... keys) {
        Set<String> result = new LinkedHashSet<>();
        if (source == null || keys == null) {
            return result;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value == null) {
                continue;
            }
            JSONArray array = readNestedArray(value);
            for (int i = 0; i < array.size(); i++) {
                String field = StringUtils.trimToNull(array.getString(i));
                if (field != null) {
                    result.add(field);
                }
            }
        }
        return result;
    }

}
