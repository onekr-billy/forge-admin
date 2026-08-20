package com.mdframe.forge.plugin.ai.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.ai.chat.domain.AiChatMessageToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiChatMessageToolCallMapper extends BaseMapper<AiChatMessageToolCall> {

    /**
     * 查询指定会话的全部工具调用（未删除），按消息与顺序升序。
     */
    List<AiChatMessageToolCall> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询指定 assistant 消息的工具调用（未删除），按顺序升序。
     */
    List<AiChatMessageToolCall> selectByRecordId(@Param("recordId") Long recordId);
}
