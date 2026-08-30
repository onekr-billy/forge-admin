package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 组织填报明细状态，对应 {@code sys_flow_fill_batch_item.submit_status / flow_status}。
 */
@Getter
public enum FlowFillItemStatus {

    PENDING("PENDING"),
    SUBMITTED("SUBMITTED"),
    RUNNING("RUNNING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    OVERDUE("OVERDUE");

    private final String code;

    FlowFillItemStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
