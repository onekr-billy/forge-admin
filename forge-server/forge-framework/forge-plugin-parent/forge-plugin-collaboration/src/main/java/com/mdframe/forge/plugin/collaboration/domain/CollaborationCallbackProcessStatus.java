package com.mdframe.forge.plugin.collaboration.domain;

import lombok.Getter;

/**
 * 协同回调事件处理状态。
 */
@Getter
public enum CollaborationCallbackProcessStatus {

    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    PROCESSED("PROCESSED", "已处理"),
    FAILED("FAILED", "失败"),
    DISCARDED("DISCARDED", "已丢弃");

    private final String code;
    private final String label;

    CollaborationCallbackProcessStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
