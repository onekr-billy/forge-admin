package com.mdframe.forge.business.core.purchase.enums;

import lombok.Getter;

import java.util.List;

/**
 * 采购单审批状态。
 */
@Getter
public enum SamplePurchaseOrderStatus {

    DRAFT("DRAFT", "草稿"),
    IN_PROCESS("IN_PROCESS", "审批中"),
    NEED_MODIFY("NEED_MODIFY", "待修改"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    CANCELED("CANCELED", "已撤销");

    private final String code;
    private final String label;

    SamplePurchaseOrderStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static List<String> deletableCodes() {
        return List.of(DRAFT.code, REJECTED.code, CANCELED.code);
    }
}
