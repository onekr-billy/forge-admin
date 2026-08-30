package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 流程配置启用状态，对应分类、表单、模板、入口等 {@code status} 字段。
 */
@Getter
public enum FlowEnableStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int code;
    private final String label;

    FlowEnableStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(Integer value) {
        return value != null && this.code == value;
    }
}
