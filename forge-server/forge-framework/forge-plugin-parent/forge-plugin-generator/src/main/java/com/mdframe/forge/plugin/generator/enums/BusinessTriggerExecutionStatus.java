package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 业务触发器单次事件执行状态。
 */
@Getter
public enum BusinessTriggerExecutionStatus {

    PENDING("PENDING", "待执行"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    SKIPPED("SKIPPED", "已跳过"),
    TODO("TODO", "待人工处理"),
    DEAD("DEAD", "重试耗尽");

    private final String code;
    private final String label;

    BusinessTriggerExecutionStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }
}
