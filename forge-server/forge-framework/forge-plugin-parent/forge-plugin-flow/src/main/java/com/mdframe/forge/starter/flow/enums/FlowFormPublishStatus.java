package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 流程表单发布状态，对应 {@code sys_flow_form.publish_status}。
 */
@Getter
public enum FlowFormPublishStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布");

    private final int code;
    private final String label;

    FlowFormPublishStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(Integer value) {
        return value != null && this.code == value;
    }
}
