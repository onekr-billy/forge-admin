package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 低代码动态 CRUD 异步导出任务状态。
 */
@Getter
public enum DynamicCrudExportStatus {

    PENDING("PENDING", "待处理"),
    RUNNING("RUNNING", "导出中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    DynamicCrudExportStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
