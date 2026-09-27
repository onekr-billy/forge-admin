package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeDesignerLayoutReader.extractFormRules;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeDesignerLayoutReader.text;

/** 将 form-create 字段规则映射为运行时设置，保留设计态覆盖顺序和校验语义。 */
final class RuntimeFormRuleSettingResolver {

    private RuntimeFormRuleSettingResolver() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> resolveFormRuleSetting(LowcodePageSchema pageSchema, String fieldName, IntSupplier editGridCols) {
        if (StringUtils.isBlank(fieldName)) {
            return Map.of();
        }
        for (Map<String, Object> rule : extractFormRules(pageSchema)) {
            if (!fieldName.equals(text(rule.get("field")))) {
                continue;
            }
            Map<String, Object> setting = new LinkedHashMap<>();
            Object props = rule.get("props");
            if (props instanceof Map<?, ?> propsMap) {
                setting.put("props", new LinkedHashMap<>((Map<String, Object>) propsMap));
            }
            String componentType = extractForgeComponentType(rule);
            if (StringUtils.isNotBlank(componentType)) {
                setting.put("componentType", componentType);
            }
            String dictType = text(getNestedValue(rule, "props.dictType"));
            if (StringUtils.isNotBlank(dictType)) {
                setting.put("dictType", dictType);
            }
            Object requiredSwitch = rule.get("$required");
            List<Map<String, Object>> validationRules = copyRuleList(rule.get("validate"));
            boolean requiredFromSwitch = isRequiredSwitchEnabled(requiredSwitch);
            boolean required = requiredFromSwitch
                    || validationRules.stream().anyMatch(item -> booleanWithDefault(item.get("required"), false));
            if (required) {
                setting.put("required", true);
                String requiredMessage = requiredFromSwitch && requiredSwitch instanceof String message && StringUtils.isNotBlank(message)
                        ? message
                        : validationRules.stream()
                        .filter(item -> booleanWithDefault(item.get("required"), false))
                        .map(item -> text(item.get("message")))
                        .filter(StringUtils::isNotBlank)
                        .findFirst()
                        .orElse("");
                if (StringUtils.isNotBlank(requiredMessage)) {
                    setting.put("requiredMessage", requiredMessage);
                }
                if (requiredFromSwitch && validationRules.stream().noneMatch(item -> booleanWithDefault(item.get("required"), false))) {
                    Map<String, Object> requiredRule = new LinkedHashMap<>();
                    requiredRule.put("required", true);
                    requiredRule.put("message", StringUtils.defaultIfBlank(requiredMessage, "该字段为必填项"));
                    requiredRule.put("trigger", List.of("blur", "change"));
                    validationRules.add(0, requiredRule);
                }
            } else if (requiredSwitch != null) {
                setting.put("required", false);
                validationRules.removeIf(item -> booleanWithDefault(item.get("required"), false));
            }
            if (!validationRules.isEmpty()) {
                setting.put("rules", validationRules);
            }
            Object style = rule.get("style");
            if (style != null) {
                setting.put("componentStyle", style);
            }
            Object className = firstPresent(rule.get("className"), rule.get("class"));
            if (className != null) {
                setting.put("formItemClass", className);
            }
            Object forgeLayout = getNestedValue(rule, "_forge.layout");
            if (forgeLayout instanceof Map<?, ?> layoutMap) {
                Object align = layoutMap.get("align");
                if (align != null) {
                    setting.put("align", align);
                }
                Object forgeLabelWidth = layoutMap.get("labelWidth");
                if (forgeLabelWidth != null) {
                    setting.put("labelWidth", forgeLabelWidth);
                }
            }
            Object col = rule.get("col");
            if (col instanceof Map<?, ?> colMap) {
                int gridCols = editGridCols.getAsInt();
                Integer span = integerValue(colMap.get("span"));
                if (span != null && span > 0) {
                    int gridSpan = (int) Math.ceil(gridCols * Math.min(24, span) / 24.0);
                    setting.put("span", Math.max(1, Math.min(gridCols, gridSpan)));
                }
                Object gridStyle = colMap.get("style");
                if (gridStyle != null) {
                    setting.put("gridStyle", gridStyle);
                }
            }
            Object labelWidth = rule.get("labelWidth");
            if (labelWidth != null) {
                setting.put("labelWidth", labelWidth);
            }
            return setting;
        }
        return Map.of();
    }

    private static String extractForgeComponentType(Map<String, Object> rule) {
        String componentKey = text(getNestedValue(rule, "_forge.componentKey"));
        if (StringUtils.isNotBlank(componentKey)) {
            return componentKey;
        }
        String dragTag = text(rule.get("_fc_drag_tag"));
        if (StringUtils.isBlank(dragTag)) {
            return null;
        }
        return switch (dragTag) {
            case "forgeDictSelect" -> "dictSelect";
            case "forgeRegionTreeSelect" -> "regionTreeSelect";
            case "forgeOrgTreeSelect" -> "orgTreeSelect";
            case "forgeUserSelect" -> "userSelect";
            case "forgeFileUpload" -> "fileUpload";
            case "forgeImageUpload" -> "imageUpload";
            case "forgeObjectReference" -> "objectReference";
            case "forgeRecordSelector" -> "recordSelector";
            case "forgeSubTable" -> "subTable";
            default -> null;
        };
    }

    private static Object getNestedValue(Map<String, Object> source, String path) {
        if (source == null || StringUtils.isBlank(path)) {
            return null;
        }
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    static Object firstPresent(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> copyRuleList(Object source) {
        if (!(source instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    private static boolean isRequiredSwitchEnabled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)
                && !"false".equalsIgnoreCase(text) && !"0".equals(text)) {
            return true;
        }
        return booleanWithDefault(value, false);
    }

    private static Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    static boolean booleanWithDefault(Object value, boolean defaultValue) {
        Boolean bool = booleanValue(value);
        return bool == null ? defaultValue : bool;
    }

    static Integer integerValue(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int intValue(Object value, int defaultValue) {
        Integer parsed = integerValue(value);
        return parsed == null ? defaultValue : parsed;
    }

}
