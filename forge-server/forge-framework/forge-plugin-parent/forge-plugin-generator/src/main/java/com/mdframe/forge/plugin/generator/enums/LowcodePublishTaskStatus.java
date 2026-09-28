package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 低代码发布可靠任务状态。 */
@Getter
@RequiredArgsConstructor
public enum LowcodePublishTaskStatus {

    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    RETRY("RETRY"),
    COMPLETED("COMPLETED"),
    SUPERSEDED("SUPERSEDED"),
    DEAD("DEAD");

    private final String code;

    public boolean matches(String value) {
        return code.equals(value);
    }
}
