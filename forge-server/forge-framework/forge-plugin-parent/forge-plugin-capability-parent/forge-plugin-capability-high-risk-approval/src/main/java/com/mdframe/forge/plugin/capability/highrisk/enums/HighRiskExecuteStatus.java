package com.mdframe.forge.plugin.capability.highrisk.enums;

import lombok.Getter;

/**
 * 高风险动作审批执行状态。
 */
@Getter
public enum HighRiskExecuteStatus {

    RESERVED("RESERVED", "预占"),
    PENDING_APPROVAL("PENDING_APPROVAL", "待审批"),
    EXECUTING("EXECUTING", "执行中"),
    SUCCESS("SUCCESS", "成功"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已取消"),
    EXPIRED("EXPIRED", "已过期"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    HighRiskExecuteStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
