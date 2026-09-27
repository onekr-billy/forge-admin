package com.mdframe.forge.plugin.generator.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 低代码流程远程命令执行与本地恢复状态。 */
@Getter
@RequiredArgsConstructor
public enum BusinessFlowRemoteCommandStatus {

    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    RETRY("RETRY"),
    REMOTE_SUCCEEDED("REMOTE_SUCCEEDED"),
    COMPLETED("COMPLETED"),
    DEAD("DEAD");

    private final String code;

    public boolean matches(String value) {
        return code.equals(value);
    }
}
