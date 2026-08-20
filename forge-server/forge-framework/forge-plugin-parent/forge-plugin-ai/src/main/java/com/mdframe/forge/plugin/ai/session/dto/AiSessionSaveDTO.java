package com.mdframe.forge.plugin.ai.session.dto;

import lombok.Data;

/**
 * AI 会话创建 / 重命名入参。
 * 创建（POST /ai/session）：sessionId + agentCode（+ 可选 sessionName）。
 * 重命名（PUT /ai/session/{id}）：sessionName。
 */
@Data
public class AiSessionSaveDTO {

    /** 会话ID（前端生成的 UUID，创建时必填） */
    private String sessionId;

    /** 绑定的 Agent 编码（创建时用于绑定上下文） */
    private String agentCode;

    /** 会话标题（创建可选；重命名必填） */
    private String sessionName;
}
