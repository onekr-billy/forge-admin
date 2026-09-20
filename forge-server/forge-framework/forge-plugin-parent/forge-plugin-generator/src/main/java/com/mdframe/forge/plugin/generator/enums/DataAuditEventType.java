package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 数据变更审计事件类型。
 */
@Getter
public enum DataAuditEventType {

    CREATE("CREATE", "新增"),
    UPDATE("UPDATE", "修改"),
    DELETE("DELETE", "删除");

    private final String code;
    private final String label;

    DataAuditEventType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static DataAuditEventType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DataAuditEventType type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return null;
    }
}
