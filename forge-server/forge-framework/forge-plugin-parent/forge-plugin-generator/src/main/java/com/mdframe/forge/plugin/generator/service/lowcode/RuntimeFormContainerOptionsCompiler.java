package com.mdframe.forge.plugin.generator.service.lowcode;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 编译运行时表单容器的展示选项，不处理表单字段或保存行为。 */
final class RuntimeFormContainerOptionsCompiler {

    private RuntimeFormContainerOptionsCompiler() {
    }

    static String defaultModalWidth(boolean masterDetailRuntime, int editGridCols) {
        if (masterDetailRuntime) {
            return "1080px";
        }
        if (editGridCols >= 3) {
            return "1180px";
        }
        return editGridCols > 1 ? "1040px" : "800px";
    }

    static String runtimeModalWidth(Map<String, Object> editProps,
                                    Map<String, Object> crudBlockProps,
                                    String defaultWidth) {
        String editModalWidth = normalizeModalWidth(editProps.get("modalWidth"));
        if (StringUtils.isNotBlank(editModalWidth)) {
            return editModalWidth;
        }
        String crudModalWidth = normalizeModalWidth(crudBlockProps.get("modalWidth"));
        if (StringUtils.isNotBlank(crudModalWidth) && !"900px".equals(StringUtils.trimToEmpty(crudModalWidth))) {
            return crudModalWidth;
        }
        String formStyleWidth = formStyleModalWidth(editProps.get("editFormStyle"));
        if (StringUtils.isNotBlank(formStyleWidth)) {
            return formStyleWidth;
        }
        return StringUtils.defaultIfBlank(crudModalWidth, defaultWidth);
    }

    static String modalType(Object value) {
        String modalType = StringUtils.defaultIfBlank(text(value), "modal").toLowerCase(Locale.ROOT);
        return Set.of("modal", "drawer").contains(modalType) ? modalType : "modal";
    }

    static String formOpenMode(Object value) {
        String mode = StringUtils.defaultIfBlank(text(value), "modal");
        if ("tabworkspace".equalsIgnoreCase(mode)) {
            return "tabWorkspace";
        }
        String normalized = mode.toLowerCase(Locale.ROOT);
        return Set.of("modal", "drawer", "flat").contains(normalized) ? normalized : "modal";
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> tabWorkspaceOptions(Map<String, Object> editProps,
                                                   Map<String, Object> crudBlockProps) {
        Map<String, Object> source = new LinkedHashMap<>();
        Object editConfig = editProps.get("tabWorkspace");
        if (editConfig instanceof Map<?, ?> map) {
            source.putAll((Map<String, Object>) map);
        }
        Object crudConfig = crudBlockProps.get("tabWorkspace");
        if (crudConfig instanceof Map<?, ?> map) {
            source.putAll((Map<String, Object>) map);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxTabs", Math.max(1, intValue(source.get("maxTabs"), 8)));
        result.put("reuseRecordTab", booleanWithDefault(source.get("reuseRecordTab"), true));
        result.put("closeAfterSave", booleanWithDefault(source.get("closeAfterSave"), false));
        result.put("showDirtyMark", booleanWithDefault(source.get("showDirtyMark"), true));
        return result;
    }

    private static String formStyleModalWidth(Object style) {
        if (!(style instanceof Map<?, ?> styleMap)) {
            return null;
        }
        Object maxWidth = styleMap.get("maxWidth");
        Object width = StringUtils.isNotBlank(text(maxWidth)) ? maxWidth : styleMap.get("width");
        return normalizeModalWidth(width);
    }

    private static String normalizeModalWidth(Object value) {
        String width = StringUtils.trimToNull(text(value));
        if (StringUtils.isBlank(width)) {
            return null;
        }
        String normalized = width.toLowerCase(Locale.ROOT);
        if ("auto".equals(normalized) || "100%".equals(normalized)) {
            return null;
        }
        if (width.matches("\\d+")) {
            return width + "px";
        }
        return width;
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean booleanWithDefault(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
