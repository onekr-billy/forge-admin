package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 流程业务状态，对应 {@code sys_flow_business.status}。
 */
@Getter
public enum FlowBusinessStatus {

    DRAFT("draft"),
    RUNNING("running"),
    ACTIVE("active"),
    SUSPENDED("suspended"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CANCELED("canceled"),
    TERMINATED("terminated"),
    COMPLETED("completed");

    private final String code;

    FlowBusinessStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static FlowBusinessStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (FlowBusinessStatus status : values()) {
            if (status.code.equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        return null;
    }

    public static boolean isEnded(String code) {
        FlowBusinessStatus status = of(code);
        return status == APPROVED
                || status == REJECTED
                || status == CANCELED
                || status == TERMINATED
                || status == COMPLETED;
    }

    public static boolean isReusable(String code) {
        FlowBusinessStatus status = of(code);
        return status == RUNNING || status == DRAFT || status == SUSPENDED;
    }

    public static boolean isPending(String code) {
        return RUNNING.matches(code) || ACTIVE.matches(code);
    }
}
