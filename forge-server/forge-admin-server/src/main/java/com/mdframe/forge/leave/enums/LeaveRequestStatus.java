package com.mdframe.forge.leave.enums;

import lombok.Getter;

/**
 * 请假申请状态，对应 {@code leave_request.status}。
 */
@Getter
public enum LeaveRequestStatus {

    DRAFT("draft", "草稿"),
    PENDING("pending", "审批中"),
    APPROVED("approved", "已通过"),
    REJECTED("rejected", "已驳回"),
    CANCELED("canceled", "已撤销");

    private final String code;
    private final String label;

    LeaveRequestStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
