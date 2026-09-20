package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 字段值存在性状态。
 */
@Getter
public enum DataAuditValueState {

    ABSENT("ABSENT", "不存在"),
    NULL("NULL", "空值"),
    VALUE("VALUE", "有值"),
    OMITTED("OMITTED", "按安全规则不保留");

    private final String code;
    private final String label;

    DataAuditValueState(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static DataAuditValueState fromCode(String value) {
        if (value == null || value.isBlank()) {
            return NULL;
        }
        for (DataAuditValueState type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return NULL;
    }
}
