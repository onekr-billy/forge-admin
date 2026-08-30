package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 流程模型状态，对应 {@code sys_flow_model.status}。
 */
@Getter
public enum FlowModelStatus {

    DESIGNING(0, "设计"),
    PUBLISHED(1, "已发布"),
    SUSPENDED(2, "已挂起"),
    DISABLED(3, "已禁用");

    private final int code;
    private final String label;

    FlowModelStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(Integer value) {
        return value != null && this.code == value;
    }

    public static FlowModelStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (FlowModelStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
