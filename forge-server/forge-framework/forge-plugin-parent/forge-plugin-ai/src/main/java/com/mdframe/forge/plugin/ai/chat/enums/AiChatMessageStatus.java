package com.mdframe.forge.plugin.ai.chat.enums;

import lombok.Getter;

/**
 * Agent 对话消息/工具调用状态。
 */
@Getter
public enum AiChatMessageStatus {

    STREAMING("streaming", "流式生成中"),
    RUNNING("running", "执行中"),
    WAITING_CONFIRM("waiting_confirm", "等待确认"),
    SUCCESS("success", "成功"),
    ABORTED("aborted", "已中止"),
    ERROR("error", "失败"),
    DONE("done", "完成");

    private final String code;
    private final String label;

    AiChatMessageStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
