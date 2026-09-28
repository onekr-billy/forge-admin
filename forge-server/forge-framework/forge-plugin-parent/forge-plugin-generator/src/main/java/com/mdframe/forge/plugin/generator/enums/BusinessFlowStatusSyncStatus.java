package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/** 低代码业务流程主动状态对账状态。 */
@Getter
public enum BusinessFlowStatusSyncStatus {

    PENDING("PENDING"),
    WAITING("WAITING"),
    PROCESSING("PROCESSING"),
    RETRY("RETRY"),
    COMPLETED("COMPLETED"),
    DEAD("DEAD");

    private final String code;

    BusinessFlowStatusSyncStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && code.equalsIgnoreCase(value.trim());
    }
}
