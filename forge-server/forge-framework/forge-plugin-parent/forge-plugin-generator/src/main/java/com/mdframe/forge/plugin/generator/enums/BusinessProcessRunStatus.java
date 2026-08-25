package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

import java.util.Set;

/**
 * 业务流程运行记录状态。
 */
@Getter
public enum BusinessProcessRunStatus {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "执行中"),
    WAITING("WAITING", "等待中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    public static final Set<String> ACTIVE_CODES = Set.of(PENDING.code, RUNNING.code, WAITING.code);

    private final String code;
    private final String label;

    BusinessProcessRunStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
