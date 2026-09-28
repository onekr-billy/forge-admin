package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/** 流程通知 Outbox 投递状态。 */
@Getter
public enum FlowNotifyOutboxStatus {
    PENDING(0),
    PROCESSING(1),
    DELIVERED(2),
    FAILED(3),
    DEAD(4);

    private final int code;

    FlowNotifyOutboxStatus(int code) {
        this.code = code;
    }

    public boolean matches(Integer value) {
        return value != null && value == code;
    }
}
