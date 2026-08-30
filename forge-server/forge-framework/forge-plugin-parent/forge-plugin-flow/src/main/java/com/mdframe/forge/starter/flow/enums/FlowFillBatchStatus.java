package com.mdframe.forge.starter.flow.enums;

import lombok.Getter;

/**
 * 组织填报批次状态，对应 {@code sys_flow_fill_batch.status}。
 */
@Getter
public enum FlowFillBatchStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布");

    private final String code;
    private final String label;

    FlowFillBatchStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
