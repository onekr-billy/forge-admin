package com.mdframe.forge.plugin.ai.agent.engine;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * ReAct 执行请求
 */
@Data
public class ReactRequest {

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 上下文变量
     */
    private Map<String, String> contextVars;

    /**
     * 图片附件（fileId列表）
     */
    private List<Long> imageFileIds;

    /**
     * 重试/重新生成目标 assistant 记录 id（阶段二·建议项3）。
     * 非空表示对该 assistant 行发起重试或重生成：软删该行后以相同用户消息重跑，
     * 仅允许对会话内「最新」assistant 行操作，服务端二次校验。
     */
    private Long retryOfRecordId;

    /**
     * 编辑重发目标 user 记录 id（阶段二·建议项2）。
     * 非空表示用户编辑了该条 user 消息并重发：软删该 user 行及其后所有行，
     * 以新的 message 内容重建一轮对话。仅允许对会话内「最新」user 轮操作。
     */
    private Long editUserRecordId;
}
