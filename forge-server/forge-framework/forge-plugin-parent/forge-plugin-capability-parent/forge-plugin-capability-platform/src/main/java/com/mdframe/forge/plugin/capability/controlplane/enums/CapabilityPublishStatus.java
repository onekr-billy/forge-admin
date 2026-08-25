package com.mdframe.forge.plugin.capability.controlplane.enums;

import lombok.Getter;

/**
 * 能力发布状态。
 */
@Getter
public enum CapabilityPublishStatus {

    PUBLISHED("PUBLISHED", "已发布");

    private final String code;
    private final String label;

    CapabilityPublishStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
