package com.mdframe.forge.plugin.collaboration.domain;

import lombok.Getter;

/**
 * 协同目录同步问题单处理状态。
 */
@Getter
public enum CollaborationIssueProcessStatus {

    PENDING("PENDING", "待处理"),
    RESOLVED("RESOLVED", "已处理"),
    IGNORED("IGNORED", "已忽略");

    private final String code;
    private final String label;

    CollaborationIssueProcessStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
