package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 数据变更审计操作主体。
 */
@Getter
public enum DataAuditActorType {

    USER("USER", "用户"),
    SERVICE("SERVICE", "服务"),
    SYSTEM("SYSTEM", "系统");

    private final String code;
    private final String label;

    DataAuditActorType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static DataAuditActorType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        for (DataAuditActorType type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return USER;
    }
}
