package com.mdframe.forge.plugin.generator.service.lowcode;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Compiles table, detail and form actions from the merged table settings. */
final class RuntimeActionCompiler {

    private RuntimeActionCompiler() {
    }

    static List<Map<String, Object>> rowActions(Map<String, Object> tableProps, boolean treeRuntime) {
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(defaultAction("edit", "编辑", "primary"));
        actions.add(defaultAction("detail", "查看详情", "info"));
        if (treeRuntime) {
            actions.add(defaultAction("addChild", "添加下级", "success"));
        }
        actions.add(defaultAction("delete", "删除", "error"));
        List<Map<String, Object>> customActions = customActions(tableProps, "row");
        Set<String> existingKeys = actions.stream()
                .map(action -> text(action.get("key")))
                .collect(Collectors.toSet());
        customActions.stream()
                .filter(action -> !existingKeys.contains(text(action.get("key"))))
                .forEach(actions::add);
        return actions;
    }

    static List<Map<String, Object>> customActions(Map<String, Object> tableProps, String position) {
        Object value = tableProps.get("customActions");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> source)) {
                continue;
            }
            String actionPosition = StringUtils.defaultIfBlank(text(source.get("position")), "toolbar");
            if (!position.equals(actionPosition)) {
                continue;
            }
            String key = StringUtils.defaultIfBlank(text(source.get("key")), "custom_" + result.size());
            String label = StringUtils.defaultIfBlank(text(source.get("label")), "自定义按钮");
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("key", key);
            action.put("label", label);
            action.put("type", StringUtils.defaultIfBlank(text(source.get("type")), "default"));
            action.put("position", position);
            action.put("actionType", StringUtils.defaultIfBlank(text(source.get("actionType")), "route"));
            putIfNotBlank(action, "actionCode", text(source.get("actionCode")));
            putIfNotBlank(action, "suiteCode", text(source.get("suiteCode")));
            putIfNotBlank(action, "objectCode", text(source.get("objectCode")));
            putIfNotBlank(action, "businessObjectCode", text(source.get("businessObjectCode")));
            putIfNotBlank(action, "targetObjectCode", text(source.get("targetObjectCode")));
            putIfNotBlank(action, "targetEntityCode", text(source.get("targetEntityCode")));
            putIfNotBlank(action, "referenceObjectCode", text(source.get("referenceObjectCode")));
            Map<String, Object> actionConfig = mapValue(source.get("actionConfig"));
            if (!actionConfig.isEmpty()) {
                action.put("actionConfig", actionConfig);
            }
            putIfNotBlank(action, "routePath", text(source.get("routePath")));
            putIfNotBlank(action, "targetFormKey", text(source.get("targetFormKey")));
            putIfNotBlank(action, "openTarget", StringUtils.defaultIfBlank(text(source.get("openTarget")), "_self"));
            putIfNotBlank(action, "permissionCode", text(source.get("permissionCode")));
            putIfNotBlank(action, "permissionKey", StringUtils.firstNonBlank(
                    text(source.get("permissionKey")), text(source.get("permissionCode"))));
            putIfNotBlank(action, "permissionStrategy", text(source.get("permissionStrategy")));
            putIfNotBlank(action, "confirmText", text(source.get("confirmText")));
            putIfNotBlank(action, "displayCondition", text(source.get("displayCondition")));
            putIfNotBlank(action, "successMessage", text(source.get("successMessage")));
            putIfNotBlank(action, "failureMessage", text(source.get("failureMessage")));
            putIfNotBlank(action, "successBehavior", text(source.get("successBehavior")));
            List<Map<String, Object>> params = actionParams(source.get("params"));
            if (!params.isEmpty()) {
                action.put("params", params);
            }
            result.add(action);
        }
        return result;
    }

    private static Map<String, Object> defaultAction(String key, String label, String type) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("key", key);
        action.put("label", label);
        action.put("type", type);
        action.put("position", "row");
        return action;
    }

    private static List<Map<String, Object>> actionParams(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> source)) {
                continue;
            }
            String name = text(source.get("name"));
            if (StringUtils.isBlank(name)) {
                continue;
            }
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", name);
            putIfNotBlank(param, "target", text(source.get("target")));
            putIfNotBlank(param, "sourceType", text(source.get("sourceType")));
            putIfNotBlank(param, "sourceField", text(source.get("sourceField")));
            param.put("value", StringUtils.defaultString(text(source.get("value"))));
            result.add(param);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private static void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
