package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 流程回调 Inbox 消费状态。 */
@Getter
@RequiredArgsConstructor
public enum BusinessFlowCallbackInboxStatus {

    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    FAILED("FAILED"),
    COMPLETED("COMPLETED"),
    DEAD("DEAD");

    private final String code;

    public boolean matches(String value) {
        return code.equals(value);
    }
}
