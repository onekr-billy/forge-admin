package com.mdframe.forge.plugin.ai.agent.engine.create.enums;

import lombok.Getter;

/**
 * AI 创建 Agent 生成记录状态。
 */
@Getter
public enum AiAgentGenerateStatus {

    GENERATING("generating", "生成中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败");

    private final String code;
    private final String label;

    AiAgentGenerateStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
