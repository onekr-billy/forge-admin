package com.mdframe.forge.plugin.ai.chat.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 对话消息回放视图对象。
 * 聚合正文、思考过程、工具调用、附件、状态与用量，供前端历史回放使用。
 */
@Data
public class AiChatMessageVO {

    private Long id;

    /** 角色：user / assistant / system */
    private String role;

    /** 最终回复正文 */
    private String content;

    /** 思考过程（reasoning） */
    private String reasoning;

    /** 附件明细（由 attachment_json 解析，挂在用户消息上） */
    private Object attachments;

    /** 工具调用明细（由 ai_chat_message_tool_call 聚合） */
    private List<AiToolCallVO> toolCalls;

    /** Token 用量明细（由 usage_json 解析） */
    private Object usage;

    /** Token 总消耗（兼容旧字段） */
    private Integer tokenUsage;

    /** 首 token 耗时（毫秒）：从发起到首个增量 */
    private Long firstTokenMs;

    /** 生成总耗时（毫秒）：从发起到收口 */
    private Long totalMs;

    /** 消息状态：streaming/waiting_confirm/done/error/aborted */
    private String status;

    /** HITL 中断标识 */
    private String interruptId;

    /** 失败原因（前端错误块展示） */
    private String error;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
