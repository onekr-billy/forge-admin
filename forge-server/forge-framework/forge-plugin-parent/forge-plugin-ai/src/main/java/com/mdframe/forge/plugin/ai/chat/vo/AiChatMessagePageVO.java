package com.mdframe.forge.plugin.ai.chat.vo;

import lombok.Data;

import java.util.List;

/**
 * 会话消息分页回放结果（变更B#7：长会话上滑加载更早消息）。
 *
 * <p>采用「按 record_id 向前游标」分页：初始加载取最近一页；用户上滑时用当前最早消息的
 * record_id 作为 {@code beforeId} 取更早一页。避免一次性把全部历史撑进前端 DOM。</p>
 */
@Data
public class AiChatMessagePageVO {

    /** 本页消息（按时间升序：最旧在前、最新在后，方便前端直接头插/尾插） */
    private List<AiChatMessageVO> list;

    /** 是否还有更早的消息可继续上滑加载 */
    private boolean hasMore;
}
