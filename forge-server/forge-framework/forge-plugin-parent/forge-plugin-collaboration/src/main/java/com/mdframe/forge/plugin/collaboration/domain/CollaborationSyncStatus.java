package com.mdframe.forge.plugin.collaboration.domain;

import lombok.Getter;

/**
 * 协同目录同步批次状态。
 */
@Getter
public enum CollaborationSyncStatus {

    RUNNING("RUNNING", "同步中"),
    SUCCESS("SUCCESS", "成功"),
    PARTIAL("PARTIAL", "部分成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    CollaborationSyncStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
