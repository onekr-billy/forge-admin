package com.mdframe.forge.starter.core.enums;

import lombok.Getter;

/**
 * 通用启用/停用状态。
 */
@Getter
public enum EnableStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int code;
    private final String label;

    EnableStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(Integer value) {
        return value != null && this.code == value;
    }

    public boolean matches(String value) {
        if (value == null) {
            return false;
        }
        return String.valueOf(this.code).equals(value.trim());
    }

    public String codeAsString() {
        return String.valueOf(this.code);
    }
}
