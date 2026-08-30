package com.mdframe.forge.plugin.capability.flowaction.enums;

import lombok.Getter;

/**
 * 能力执行状态，对应流程动作审计日志 {@code execute_status}。
 */
@Getter
public enum CapabilityExecuteStatus {

    RUNNING("RUNNING", "执行中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    CapabilityExecuteStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
