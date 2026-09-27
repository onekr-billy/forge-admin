package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 低代码业务事件 Outbox 投递状态。 */
@Getter
@RequiredArgsConstructor
public enum BusinessEventOutboxStatus {

    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    DELIVERED("DELIVERED"),
    FAILED("FAILED"),
    DEAD("DEAD");

    private final String code;

    public boolean matches(String value) {
        return code.equals(value);
    }
}
