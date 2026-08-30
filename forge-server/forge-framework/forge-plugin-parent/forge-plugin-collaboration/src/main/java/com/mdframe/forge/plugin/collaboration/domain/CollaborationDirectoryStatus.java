package com.mdframe.forge.plugin.collaboration.domain;

import lombok.Getter;

/**
 * 协同目录映射/标签/外部账号状态。
 */
@Getter
public enum CollaborationDirectoryStatus {

    ACTIVE("ACTIVE", "有效"),
    DISABLED("DISABLED", "停用"),
    DELETED("DELETED", "已删除"),
    INACTIVE("INACTIVE", "无效"),
    ISSUE("ISSUE", "异常");

    private final String code;
    private final String label;

    CollaborationDirectoryStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
