package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 搜索与编辑字段共用的组件类型和伴随标签协议。 */
final class RuntimeFieldComponentResolver {

    private RuntimeFieldComponentResolver() {
    }

    static String resolveSearchComponentType(LowcodeFieldSchema field, String queryType) {
        return resolveSearchComponentType(field, queryType, Map.of());
    }

    static String resolveSearchComponentType(LowcodeFieldSchema field, String queryType,
                                             Map<String, Object> pageSetting) {
        if (hasRecordSelectorConfig(field, pageSetting)) {
            return "recordSelector";
        }
        // 字段自身已有明确组件类型时，不接受搜索区遗留的错误覆盖。
        String fieldComponentType = normalizeEditComponentType(StringUtils.defaultIfBlank(field.getComponentType(), ""));
        if (isPreferredSearchFieldComponent(fieldComponentType)) {
            return resolveSearchComponentTypeFromField(field, queryType, fieldComponentType);
        }
        String configuredType = StringUtils.defaultIfBlank(text(pageSetting.get("componentType")),
                text(pageSetting.get("type")));
        if (StringUtils.isNotBlank(configuredType)) {
            String normalizedConfigured = normalizeEditComponentType(configuredType);
            if (!shouldIgnoreConfiguredSearchComponent(normalizedConfigured, field)) {
                return normalizedConfigured;
            }
        }
        String componentType = StringUtils.defaultIfBlank(field.getComponentType(), field.getDataType());
        componentType = StringUtils.defaultIfBlank(componentType, "input");
        return resolveSearchComponentTypeFromField(field, queryType, normalizeEditComponentType(componentType));
    }

    static String resolveEditComponentType(LowcodeFieldSchema field) {
        if (hasRecordSelectorConfig(field, Map.of())) {
            return "recordSelector";
        }
        return normalizeEditComponentType(StringUtils.defaultIfBlank(field.getComponentType(), "input"));
    }

    static String resolveEditComponentType(LowcodeFieldSchema field, Map<String, Object> pageSetting) {
        if (hasRecordSelectorConfig(field, pageSetting)) {
            return "recordSelector";
        }
        String componentType = StringUtils.defaultIfBlank(text(pageSetting.get("componentType")),
                text(pageSetting.get("type")));
        componentType = StringUtils.defaultIfBlank(componentType, field.getComponentType());
        return normalizeEditComponentType(StringUtils.defaultIfBlank(componentType, "input"));
    }

    static String normalizeEditComponentType(String componentType) {
        return switch (StringUtils.defaultString(componentType)) {
            case "inputNumber", "input-number", "inputnumber", "integer", "money" -> "number";
            case "orgSelect", "organizationSelect", "departmentSelect", "deptSelect",
                    "departmentTreeSelect", "deptTreeSelect", "elTreeSelect", "orgName", "deptName",
                    "forgeOrgTreeSelect" -> "orgTreeSelect";
            case "userPicker", "user", "userName", "sysUserSelect", "forgeUserSelect" -> "userSelect";
            default -> componentType;
        };
    }

    static String normalizeRuntimeFormSize(String value) {
        String size = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        if ("default".equals(size) || "medium".equals(size)) {
            return "medium";
        }
        return Set.of("small", "large").contains(size) ? size : "medium";
    }

    static void applySelectionLabelProps(Map<String, Object> props, String fieldName, String componentType) {
        if (StringUtils.isBlank(fieldName)
                || (!"orgTreeSelect".equals(componentType) && !"userSelect".equals(componentType))) {
            return;
        }
        String labelField = fieldName + "Name";
        if (StringUtils.isBlank(text(props.get("labelValueField")))
                || fieldName.equals(text(props.get("labelValueField")))) {
            props.put("labelValueField", labelField);
        }
        if (StringUtils.isBlank(text(props.get("targetField")))
                || fieldName.equals(text(props.get("targetField")))) {
            props.put("targetField", labelField);
        }
    }

    static void ensureDynamicOptionSourceLabelValueField(Map<String, Object> props,
                                                          String fieldName, String componentType) {
        if (props == null || StringUtils.isBlank(fieldName)
                || StringUtils.isNotBlank(text(props.get("labelValueField")))) {
            return;
        }
        if (!Set.of("select", "radio", "radioButton", "checkbox", "cascader", "treeSelect", "transfer")
                .contains(StringUtils.defaultString(componentType))) {
            return;
        }
        Object source = props.get("optionSource");
        if (!(source instanceof Map<?, ?> map)) {
            return;
        }
        String type = text(map.get("type"));
        if (StringUtils.isBlank(type) || "STATIC".equalsIgnoreCase(type.replace('-', '_'))) {
            return;
        }
        props.put("labelValueField", fieldName + "Name");
    }

    static boolean isBusinessSelectComponent(String componentType) {
        return "dictSelect".equals(componentType)
                || "treeSelect".equals(componentType)
                || "orgTreeSelect".equals(componentType)
                || "userSelect".equals(componentType)
                || "regionTreeSelect".equals(componentType)
                || "cascader".equals(componentType)
                || "objectReference".equals(componentType)
                || "recordSelector".equals(componentType);
    }

    static String buildPlaceholder(String componentType, String label) {
        if ("select".equals(componentType) || "radio".equals(componentType) || "radioButton".equals(componentType)
                || "checkbox".equals(componentType) || "transfer".equals(componentType)
                || "customSelect".equals(componentType) || "date".equals(componentType)
                || "datetime".equals(componentType) || "time".equals(componentType)
                || "daterange".equals(componentType) || "datetimerange".equals(componentType)
                || "timerange".equals(componentType) || "dictSelect".equals(componentType)
                || "treeSelect".equals(componentType) || "orgTreeSelect".equals(componentType)
                || "userSelect".equals(componentType) || "regionTreeSelect".equals(componentType)
                || "cascader".equals(componentType) || "objectReference".equals(componentType)
                || "recordSelector".equals(componentType) || "fileUpload".equals(componentType)
                || "imageUpload".equals(componentType) || "upload".equals(componentType)) {
            return "请选择" + label;
        }
        return "请输入" + label;
    }

    static boolean isTextComponent(String componentType) {
        return "input".equals(componentType) || "textarea".equals(componentType);
    }

    private static String resolveSearchComponentTypeFromField(
            LowcodeFieldSchema field, String queryType, String componentType) {
        if (isBusinessSelectComponent(componentType)) {
            return componentType;
        }
        if (StringUtils.isNotBlank(field.getDictType())) {
            return "select";
        }
        if ("between".equals(queryType)) {
            if ("datetime".equals(componentType)) {
                return "datetimerange";
            }
            if ("date".equals(componentType)) {
                return "daterange";
            }
            if ("time".equals(componentType)) {
                return "timerange";
            }
        }
        if (LowcodeComponentCatalog.isFieldComponent(componentType)) {
            return componentType;
        }
        if ("number".equals(componentType) || "date".equals(componentType)
                || "datetime".equals(componentType) || "time".equals(componentType)
                || "treeSelect".equals(componentType) || "cascader".equals(componentType)) {
            return componentType;
        }
        return "input";
    }

    private static boolean isPreferredSearchFieldComponent(String componentType) {
        return Set.of("treeSelect", "orgTreeSelect", "regionTreeSelect", "cascader", "userSelect",
                "select", "dictSelect", "radio", "checkbox", "switch", "date", "datetime", "time",
                "textarea", "number", "input").contains(componentType);
    }

    private static boolean shouldIgnoreConfiguredSearchComponent(
            String configuredType, LowcodeFieldSchema field) {
        if (field == null || StringUtils.isBlank(configuredType)) {
            return false;
        }
        String fieldType = normalizeEditComponentType(StringUtils.defaultIfBlank(field.getComponentType(), ""));
        if (!Set.of("treeSelect", "orgTreeSelect", "regionTreeSelect", "cascader", "userSelect")
                .contains(fieldType)) {
            return false;
        }
        return Set.of("input", "number", "textarea").contains(configuredType);
    }

    private static boolean hasRecordSelectorConfig(LowcodeFieldSchema field, Map<String, Object> pageSetting) {
        // objectReference 的 basicProps.recordSelector 只是下拉高级配置，不触发弹窗。
        if (field != null && field.getBasicProps() != null && !"objectReference".equals(field.getComponentType())) {
            Object selector = firstPresent(field.getBasicProps().get("recordSelector"),
                    field.getBasicProps().get("recordSelectorConfig"), field.getBasicProps().get("selector"),
                    field.getBasicProps().get("selectorConfig"));
            if (selector instanceof Map<?, ?> map && !map.isEmpty()) {
                return true;
            }
        }
        Object props = pageSetting == null ? null : pageSetting.get("props");
        if (props instanceof Map<?, ?> propsMap) {
            Object selector = firstPresent(propsMap.get("recordSelector"), propsMap.get("recordSelectorConfig"),
                    propsMap.get("selector"), propsMap.get("selectorConfig"));
            return selector instanceof Map<?, ?> map && !map.isEmpty();
        }
        return false;
    }

    private static Object firstPresent(Object... values) {
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

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
