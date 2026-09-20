package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 审计查询授权模式。客户端选定不能替代服务端授权。
 */
@Getter
public enum DataAuditAccessMode {

    RECORD("RECORD", "业务记录模式"),
    AUDIT("AUDIT", "管理员审计模式");

    private final String code;
    private final String label;

    DataAuditAccessMode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static DataAuditAccessMode fromCode(String value) {
        if (RECORD.matches(value)) {
            return RECORD;
        }
        return AUDIT;
    }
}
