package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 流程错误日志状态，对应 {@code sys_flow_error_log.status}。
 */
@Getter
public enum FlowErrorLogStatus {

    UNRESOLVED(0, "未处理"),
    RETRIED(1, "已重试"),
    RESOLVED(2, "已解决"),
    RETRY_FAILED(3, "重试失败");

    private final int code;
    private final String label;

    FlowErrorLogStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(Integer value) {
        return value != null && this.code == value;
    }

    public static FlowErrorLogStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (FlowErrorLogStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
