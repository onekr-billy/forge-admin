package com.mdframe.forge.plugin.generator.constant;

import lombok.Getter;

import java.util.Set;

/**
 * 应用协调发布运行与不可变版本状态。
 */
@Getter
public enum BusinessApplicationPublishStatus {

    CREATED("CREATED"),
    RUNNING("RUNNING"),
    PENDING("PENDING"),
    PARTIAL("PARTIAL"),
    FAILED("FAILED"),
    SUCCESS("SUCCESS"),
    PUBLISHED("PUBLISHED"),
    ROLLBACK("ROLLBACK");

    private static final Set<String> RUN_STATUSES = Set.of(
            CREATED.code, RUNNING.code, PARTIAL.code, FAILED.code, SUCCESS.code);
    private static final Set<String> VERSION_STATUSES = Set.of(PUBLISHED.code, ROLLBACK.code);

    private final String code;

    BusinessApplicationPublishStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static BusinessApplicationPublishStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (BusinessApplicationPublishStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }

    public static Set<String> runStatuses() {
        return RUN_STATUSES;
    }

    public static Set<String> versionStatuses() {
        return VERSION_STATUSES;
    }
}
