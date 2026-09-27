package com.mdframe.forge.plugin.generator.service.businessapp;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;

/** 审批表单控件类型的设计器优先级、弱类型推断与运行时名称兼容。 */
final class BusinessFlowTaskFormControlTypes {

    private BusinessFlowTaskFormControlTypes() {
    }

    static String resolveTaskFormControlType(Map<String, Object> field) {
        if (field == null) {
            return "input";
        }
        // 设计器以 componentKey 为控件事实来源；type 常被 editSchema/资产写成 input
        String explicit = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(field.get("componentKey"))),
                StringUtils.trimToNull(textValue(field.get("componentType"))),
                StringUtils.trimToNull(textValue(field.get("type"))));
        explicit = stripForgeComponentPrefix(StringUtils.defaultIfBlank(explicit, ""));
        if (!isWeakTaskFormControlType(explicit)) {
            return explicit;
        }
        // 弱类型时从 props 推断：避免审批端全变成输入框
        Map<String, Object> props = new LinkedHashMap<>(readNestedObject(field.get("props")));
        if (props.get("optionSource") instanceof Map<?, ?> || props.get("options") instanceof List<?>) {
            Object optionSource = props.get("optionSource");
            if (optionSource instanceof Map<?, ?> source) {
                String sourceType = StringUtils.trimToEmpty(textValue(source.get("sourceType")));
                String type = StringUtils.trimToEmpty(textValue(source.get("type")));
                if ("BUSINESS_OBJECT".equalsIgnoreCase(sourceType)
                        || "businessRecordSelector".equalsIgnoreCase(type)
                        || StringUtils.isNotBlank(textValue(source.get("objectCode")))) {
                    return "objectReference";
                }
            }
            return StringUtils.isNotBlank(textValue(field.get("dictType")))
                    || StringUtils.isNotBlank(textValue(props.get("dictType")))
                    ? "dictSelect"
                    : "select";
        }
        if (props.get("recordSelector") instanceof Map<?, ?>
                || StringUtils.isNotBlank(textValue(props.get("objectCode")))
                || StringUtils.isNotBlank(textValue(props.get("referenceObjectCode")))
                || StringUtils.isNotBlank(textValue(field.get("referenceObjectCode")))) {
            return "objectReference";
        }
        if (StringUtils.isNotBlank(textValue(field.get("dictType")))
                || StringUtils.isNotBlank(textValue(props.get("dictType")))) {
            return "dictSelect";
        }
        return StringUtils.defaultIfBlank(explicit, "input");
    }

    static boolean isWeakTaskFormControlType(String type) {
        String normalized = StringUtils.trimToEmpty(type).toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                || "input".equals(normalized)
                || "text".equals(normalized)
                || "string".equals(normalized)
                || "varchar".equals(normalized);
    }

    static String firstStrongTaskFormControlType(Map<String, Object> field) {
        for (String key : new String[]{"type", "componentType", "componentKey"}) {
            String value = stripForgeComponentPrefix(StringUtils.trimToEmpty(textValue(field.get(key))));
            if (!isWeakTaskFormControlType(value)) {
                return value;
            }
        }
        return null;
    }

    static String normalizeTaskFormFieldType(String componentType) {
        String type = stripForgeComponentPrefix(StringUtils.defaultIfBlank(componentType, "input").trim());
        return switch (type) {
            case "textarea" -> "textarea";
            case "inputNumber", "input-number", "integer", "decimal", "money", "number" -> "number";
            case "dictSelect", "forgeDictSelect" -> "dictSelect";
            case "select", "radio", "radioButton", "checkbox", "date", "datetime", "daterange", "datetimerange",
                    "month", "year", "time", "timerange", "switch", "imageUpload", "fileUpload", "slider", "rate",
                    "color", "regionTreeSelect", "treeSelect", "transfer", "customSelect", "objectReference",
                    "recordSelector", "userSelect", "orgTreeSelect", "cascader", "text", "slot" -> type;
            case "deptTreeSelect", "departmentTreeSelect", "deptSelect", "departmentSelect",
                    "orgSelect", "organizationSelect", "orgName", "deptName", "elTreeSelect" -> "orgTreeSelect";
            case "userPicker", "user", "userName", "sysUserSelect" -> "userSelect";
            case "upload" -> "fileUpload";
            default -> "input".equals(type) || StringUtils.isBlank(type) ? "input" : type;
        };
    }

    private static String stripForgeComponentPrefix(String componentType) {
        String type = StringUtils.trimToEmpty(componentType);
        if (type.startsWith("forge") && type.length() > 5 && Character.isUpperCase(type.charAt(5))) {
            return Character.toLowerCase(type.charAt(5)) + type.substring(6);
        }
        return type;
    }
}
