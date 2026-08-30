package com.mdframe.forge.starter.excel.enums;

import lombok.Getter;

/**
 * 异步导出任务状态。
 */
@Getter
public enum AsyncExportStatus {

    PROCESSING(0, "处理中"),
    COMPLETED(1, "完成"),
    FAILED(2, "失败");

    private final int code;
    private final String label;

    AsyncExportStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(Integer value) {
        return value != null && this.code == value;
    }

    public static AsyncExportStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (AsyncExportStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static String labelOf(Integer code) {
        AsyncExportStatus status = of(code);
        return status == null ? "未知" : status.label;
    }
}
