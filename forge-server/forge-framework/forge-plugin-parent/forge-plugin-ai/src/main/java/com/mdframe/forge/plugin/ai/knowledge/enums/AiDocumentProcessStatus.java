package com.mdframe.forge.plugin.ai.knowledge.enums;

import lombok.Getter;

/**
 * 知识库文档处理状态。
 */
@Getter
public enum AiDocumentProcessStatus {

    PENDING("pending", "待处理"),
    PROCESSING("processing", "处理中"),
    SUCCESS("success", "成功"),
    FAILED("failed", "失败");

    private final String code;
    private final String label;

    AiDocumentProcessStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
