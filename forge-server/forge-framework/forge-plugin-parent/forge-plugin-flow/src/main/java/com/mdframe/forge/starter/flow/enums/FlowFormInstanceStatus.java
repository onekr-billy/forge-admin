package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 流程表单实例状态，对应 {@code sys_flow_form_instance.status}。
 */
@Getter
public enum FlowFormInstanceStatus {

    DRAFT("DRAFT"),
    RUNNING("RUNNING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    CANCELED("CANCELED");

    private final String code;

    FlowFormInstanceStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
