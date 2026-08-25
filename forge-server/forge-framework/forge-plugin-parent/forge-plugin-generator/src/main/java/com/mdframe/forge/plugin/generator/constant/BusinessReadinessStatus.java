package com.mdframe.forge.plugin.generator.constant;

import lombok.Getter;

/**
 * 业务就绪度状态。
 */
@Getter
public enum BusinessReadinessStatus {

    REGISTERED("REGISTERED"),
    CONFIGURED("CONFIGURED"),
    RUNNABLE("RUNNABLE"),
    MISSING("MISSING"),
    ERROR("ERROR"),
    PASSED("PASSED"),
    PARTIAL("PARTIAL"),
    FAILED("FAILED");

    private final String code;

    BusinessReadinessStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static BusinessReadinessStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (BusinessReadinessStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
