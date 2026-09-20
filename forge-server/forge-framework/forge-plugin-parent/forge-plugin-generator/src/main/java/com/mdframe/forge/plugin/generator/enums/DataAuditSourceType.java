package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 数据变更审计来源。
 */
@Getter
public enum DataAuditSourceType {

    FORM("FORM", "表单"),
    FLOW_FORM("FLOW_FORM", "流程表单"),
    FLOW_CALLBACK("FLOW_CALLBACK", "流程回写"),
    BUSINESS_ACTION("BUSINESS_ACTION", "业务动作"),
    AUTOMATION("AUTOMATION", "自动化"),
    IMPORT("IMPORT", "导入"),
    FORMULA("FORMULA", "公式"),
    EXTENSION("EXTENSION", "扩展");

    private final String code;
    private final String label;

    DataAuditSourceType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static DataAuditSourceType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DataAuditSourceType type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return null;
    }
}
