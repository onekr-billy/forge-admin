package com.mdframe.forge.plugin.ai.chat.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话工具调用回放视图对象。
 */
@Data
public class AiToolCallVO {

    private Long id;

    /** 工具名称 */
    private String toolName;

    /** 工具入参（由 tool_args_json 解析） */
    private Object args;

    /** 工具结果（由 tool_result_json 解析） */
    private Object result;

    /** 状态：pending/running/waiting_confirm/success/error/aborted */
    private String status;

    /** 同一消息内的顺序 */
    private Integer seq;

    /** 错误信息 */
    private String error;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
