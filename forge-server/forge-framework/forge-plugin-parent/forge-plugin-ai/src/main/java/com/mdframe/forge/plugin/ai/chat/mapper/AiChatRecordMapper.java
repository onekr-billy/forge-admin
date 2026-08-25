package com.mdframe.forge.plugin.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.ai.chat.domain.AiChatRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiChatRecordMapper extends BaseMapper<AiChatRecord> {

    /**
     * 按 HITL 中断标识查询等待确认的 assistant 消息（未删除），供 resume 接回原消息。
     */
    AiChatRecord selectByInterruptId(@Param("interruptId") String interruptId);

    /**
     * 查询会话内指定角色的最新一条未删除消息。
     */
    AiChatRecord selectLatestBySessionAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

    /**
     * 查询会话内最近 N 条未删除消息（倒序）。
     */
    List<AiChatRecord> selectRecentBySession(@Param("sessionId") String sessionId, @Param("limit") int limit);

    /**
     * 查询会话内某条消息之前的最近 N 条未删除消息（倒序）。
     */
    List<AiChatRecord> selectRecentBySessionBeforeRecord(@Param("sessionId") String sessionId,
                                                         @Param("beforeRecordId") Long beforeRecordId,
                                                         @Param("limit") int limit);
}

