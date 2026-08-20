package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将已发布手动开始节点编译为页面 START_PROCESS 动作。
 */
@Component
@RequiredArgsConstructor
public class BusinessProcessRuntimeActionCompiler {

    public static final String ACTION_TYPE = "START_PROCESS";
    public static final String DEFAULT_PERMISSION = "ai:businessProcess:start";

    private static final Set<String> START_TYPES = Set.of("START_MANUAL");
    private static final List<String> DEFAULT_POSITIONS = List.of("ROW", "DETAIL");

    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> compileSnapshots(
            Collection<BusinessProcessSnapshot> snapshots,
            String applicationCode) {
        if (snapshots == null || snapshots.isEmpty() || StringUtils.isBlank(applicationCode)) {
            return List.of();
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        for (BusinessProcessSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            actions.addAll(compileActions(snapshot, applicationCode, snapshot.processCode()));
        }
        return List.copyOf(actions);
    }

    public List<Map<String, Object>> compileActions(
            BusinessProcessSnapshot snapshot,
            String applicationCode,
            String processName) {
        if (snapshot == null) {
            return List.of();
        }
        return compileSchema(
                toSchema(snapshot.businessProcessJson()),
                applicationCode,
                snapshot.processCode(),
                processName);
    }

    public List<Map<String, Object>> compileSchema(
            BusinessProcessSchema schema,
            String applicationCode,
            String processCode,
            String processName) {
        if (schema == null || StringUtils.isAnyBlank(applicationCode, processCode)) {
            return List.of();
        }
        BusinessProcessNode start = findManualStart(schema);
        if (start == null) {
            return List.of();
        }
        Map<String, Object> config = start.getConfig() == null ? Map.of() : start.getConfig();
        String objectCode = schema.getSubject() == null ? null : StringUtils.trimToNull(schema.getSubject().getObjectCode());
        String label = firstText(start.getName(), processName, processCode, "启动流程");
        String permission = firstText(text(config.get("permission")), DEFAULT_PERMISSION);
        String confirmText = text(config.get("confirmText"));
        String visibleCondition = compileDisplayCondition(config.get("visibleCondition"));
        Set<String> positions = normalizePositions(config.get("positions"));
        List<Map<String, Object>> actions = new ArrayList<>();
        if (positions.contains("ROW") || positions.contains("DETAIL")) {
            actions.add(action(
                    processCode,
                    applicationCode,
                    objectCode,
                    label,
                    "row",
                    permission,
                    confirmText,
                    visibleCondition));
        }
        if (positions.contains("TOOLBAR") || positions.contains("FORM")) {
            actions.add(action(
                    processCode + ":toolbar",
                    applicationCode,
                    objectCode,
                    label,
                    "toolbar",
                    permission,
                    confirmText,
                    visibleCondition));
        }
        return List.copyOf(actions);
    }

    private Map<String, Object> action(
            String actionKey,
            String applicationCode,
            String objectCode,
            String label,
            String position,
            String permission,
            String confirmText,
            String visibleCondition) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("key", "startProcess:" + actionKey);
        action.put("label", label);
        action.put("type", "success");
        action.put("position", position);
        action.put("actionType", ACTION_TYPE);
        action.put("applicationCode", applicationCode);
        action.put("processCode", StringUtils.substringBefore(actionKey, ":"));
        if (StringUtils.isNotBlank(objectCode)) {
            action.put("objectCode", objectCode);
        }
        action.put("permission", permission);
        action.put("permissionCode", permission);
        if (StringUtils.isNotBlank(confirmText)) {
            action.put("confirm", true);
            action.put("confirmText", confirmText);
        }
        if (StringUtils.isNotBlank(visibleCondition)) {
            action.put("displayCondition", visibleCondition);
            action.put("visibleCondition", visibleCondition);
        }
        action.put("successBehavior", "refreshList");
        return action;
    }

    public String compileDisplayCondition(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String text) {
            return text.trim();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return "";
        }
        Object rules = map.get("rules");
        if (!(rules instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> rule)) {
            return "";
        }
        String field = text(rule.get("field"));
        if (StringUtils.isBlank(field)) {
            return "";
        }
        String operator = upper(text(rule.get("operator")));
        String value = text(rule.get("value"));
        if ("NE".equals(operator)) {
            return field + " != " + value;
        }
        if ("IN".equals(operator)) {
            return field + " in " + value;
        }
        if ("NOT_EMPTY".equals(operator)) {
            return field + " != ";
        }
        return field + " = " + value;
    }

    public BusinessProcessSchema schemaFromJson(Map<String, Object> json) {
        return toSchema(json);
    }

    private BusinessProcessNode findManualStart(BusinessProcessSchema schema) {
        if (schema.getNodes() == null) {
            return null;
        }
        return schema.getNodes().stream()
                .filter(node -> node != null && START_TYPES.contains(upper(node.getType())))
                .findFirst()
                .orElse(null);
    }

    private Set<String> normalizePositions(Object raw) {
        Set<String> positions = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                String value = upper(text(item));
                if (StringUtils.isNotBlank(value)) {
                    positions.add(value);
                }
            }
        }
        else if (raw instanceof String text && StringUtils.isNotBlank(text)) {
            for (String item : text.split(",")) {
                String value = upper(item.trim());
                if (StringUtils.isNotBlank(value)) {
                    positions.add(value);
                }
            }
        }
        if (positions.isEmpty()) {
            positions.addAll(DEFAULT_POSITIONS);
        }
        return positions;
    }

    private BusinessProcessSchema toSchema(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return objectMapper.convertValue(json, BusinessProcessSchema.class);
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : StringUtils.trimToEmpty(String.valueOf(value));
    }

    private String upper(String value) {
        return StringUtils.isBlank(value) ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
