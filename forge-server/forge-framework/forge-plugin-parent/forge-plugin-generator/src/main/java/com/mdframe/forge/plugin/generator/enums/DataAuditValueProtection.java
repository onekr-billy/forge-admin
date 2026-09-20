package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 历史值保护策略。
 */
@Getter
public enum DataAuditValueProtection {

    PLAIN("PLAIN", "明文"),
    ENCRYPTED("ENCRYPTED", "加密"),
    CHANGE_ONLY("CHANGE_ONLY", "仅记录变更");

    private final String code;
    private final String label;

    DataAuditValueProtection(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static DataAuditValueProtection fromCode(String value) {
        if (value == null || value.isBlank()) {
            return PLAIN;
        }
        for (DataAuditValueProtection type : values()) {
            if (type.matches(value)) {
                return type;
            }
        }
        return PLAIN;
    }

    public boolean isStricterThan(DataAuditValueProtection other) {
        return ordinal() > (other == null ? PLAIN.ordinal() : other.ordinal());
    }
}
