package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/** 流程镜像投影 Outbox 状态。 */
@Getter
public enum FlowProjectionOutboxStatus {
    PENDING(0),
    PROCESSING(1),
    APPLIED(2),
    FAILED(3),
    DEAD(4);

    private final int code;

    FlowProjectionOutboxStatus(int code) {
        this.code = code;
    }
}
