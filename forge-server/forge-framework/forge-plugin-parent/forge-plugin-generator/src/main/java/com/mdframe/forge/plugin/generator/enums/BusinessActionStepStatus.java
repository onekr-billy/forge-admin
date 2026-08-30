package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 业务动作步骤执行状态。
 */
@Getter
public enum BusinessActionStepStatus {

    PENDING("PENDING", "待执行"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    TODO("TODO", "待实现"),
    SKIPPED("SKIPPED", "已跳过");

    private final String code;
    private final String label;

    BusinessActionStepStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
