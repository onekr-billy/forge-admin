package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;

/**
 * 业务单据流程链接/运行态状态。
 */
@Getter
public enum BusinessDocumentFlowStatus {

    NOT_STARTED("NOT_STARTED", "未发起"),
    RUNNING("RUNNING", "运行中"),
    IN_PROCESS("IN_PROCESS", "流程中"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    CANCELED("CANCELED", "已取消");

    private final String code;
    private final String label;

    BusinessDocumentFlowStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
