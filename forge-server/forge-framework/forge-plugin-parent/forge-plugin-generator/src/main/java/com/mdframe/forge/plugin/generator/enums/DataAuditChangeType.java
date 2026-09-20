package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 字段变化类型。
 */
@Getter
public enum DataAuditChangeType {

    ADD("ADD", "新增"),
    UPDATE("UPDATE", "修改"),
    REMOVE("REMOVE", "删除");

    private final String code;
    private final String label;

    DataAuditChangeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
