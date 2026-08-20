package com.mdframe.forge.plugin.ai.chat.service;

import com.alibaba.fastjson2.JSON;
import com.mdframe.forge.plugin.ai.chat.domain.AiChatMessageToolCall;
import com.mdframe.forge.plugin.ai.chat.domain.AiChatRecord;
import com.mdframe.forge.plugin.ai.chat.vo.AiChatMessagePageVO;
import com.mdframe.forge.plugin.ai.chat.vo.AiChatMessageVO;
import com.mdframe.forge.plugin.ai.chat.vo.AiToolCallVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话消息回放组装器（读侧编排层）。
 * 聚合 ai_chat_record 与 ai_chat_message_tool_call，解析结构化 JSON，产出前端可直接消费的 VO。
 * 仅依赖同域叶子数据服务，不构成 Service 互相注入的业务编排链。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatMessageAssembler {

    private final AiChatRecordService recordService;
    private final AiChatMessageToolCallService toolCallService;

    /**
     * 组装指定会话的消息列表（未删除，按时间升序），带思考过程、工具调用、附件、状态与用量。
     */
    public List<AiChatMessageVO> assembleSessionMessages(String sessionId) {
        List<AiChatRecord> records = recordService.listBySession(sessionId);
        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        // 一次性取出会话内全部工具调用，按 assistant 消息 id 分组
        Map<Long, List<AiToolCallVO>> toolCallsByRecord = new LinkedHashMap<>();
        for (AiChatMessageToolCall call : toolCallService.listBySession(sessionId)) {
            toolCallsByRecord
                    .computeIfAbsent(call.getRecordId(), k -> new ArrayList<>())
                    .add(toToolCallVO(call));
        }

        List<AiChatMessageVO> result = new ArrayList<>(records.size());
        for (AiChatRecord record : records) {
            result.add(toMessageVO(record, toolCallsByRecord.get(record.getId())));
        }
        return result;
    }

    /**
     * 分页组装会话消息（变更B#7：长会话上滑加载更早消息）。
     *
     * <p>按 record_id 向前游标：{@code beforeId} 为 null 时取最近 {@code size} 条；否则取
     * {@code id < beforeId} 的最近 {@code size} 条。均按时间升序返回，并标记是否还有更早消息。
     * 多取一条用于判断 hasMore——升序返回时「多出的一条」在最前，据此判定并剔除。</p>
     *
     * <p>工具调用沿用会话级一次性加载并按 record_id 分组（与全量组装一致：工具调用行数小、
     * 只有被本页 assistant 消息命中的分组会被使用，零风险且避免额外的 IN 查询方法）。</p>
     */
    public AiChatMessagePageVO assembleSessionMessagesPage(String sessionId, Long beforeId, int size) {
        AiChatMessagePageVO page = new AiChatMessagePageVO();
        int pageSize = size <= 0 ? 30 : Math.min(size, 100);

        List<AiChatRecord> records = (beforeId == null)
                ? recordService.listRecentBySession(sessionId, pageSize + 1)
                : recordService.listRecentBySessionBeforeRecord(sessionId, beforeId, pageSize + 1);

        boolean hasMore = records.size() > pageSize;
        if (hasMore) {
            // 升序：最前一条是「多取的」更早消息，仅用于判定 hasMore，剔除后保留最新 pageSize 条
            records = new ArrayList<>(records.subList(records.size() - pageSize, records.size()));
        }
        page.setHasMore(hasMore);

        if (records.isEmpty()) {
            page.setList(new ArrayList<>());
            return page;
        }

        Map<Long, List<AiToolCallVO>> toolCallsByRecord = new LinkedHashMap<>();
        for (AiChatMessageToolCall call : toolCallService.listBySession(sessionId)) {
            toolCallsByRecord
                    .computeIfAbsent(call.getRecordId(), k -> new ArrayList<>())
                    .add(toToolCallVO(call));
        }

        List<AiChatMessageVO> list = new ArrayList<>(records.size());
        for (AiChatRecord record : records) {
            list.add(toMessageVO(record, toolCallsByRecord.get(record.getId())));
        }
        page.setList(list);
        return page;
    }

    private AiChatMessageVO toMessageVO(AiChatRecord record, List<AiToolCallVO> toolCalls) {
        AiChatMessageVO vo = new AiChatMessageVO();
        vo.setId(record.getId());
        vo.setRole(record.getRole());
        vo.setContent(record.getContent());
        vo.setReasoning(record.getReasoning());
        vo.setAttachments(parseJson(record.getAttachmentJson()));
        vo.setUsage(parseJson(record.getUsageJson()));
        vo.setTokenUsage(record.getTokenUsage());
        vo.setFirstTokenMs(record.getFirstTokenMs());
        vo.setTotalMs(record.getTotalMs());
        vo.setStatus(StringUtils.hasText(record.getStatus()) ? record.getStatus() : "done");
        vo.setInterruptId(record.getInterruptId());
        vo.setError(record.getErrorMsg());
        vo.setToolCalls(toolCalls != null ? toolCalls : new ArrayList<>());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        return vo;
    }

    private AiToolCallVO toToolCallVO(AiChatMessageToolCall call) {
        AiToolCallVO vo = new AiToolCallVO();
        vo.setId(call.getId());
        vo.setToolName(call.getToolName());
        vo.setArgs(parseJson(call.getToolArgsJson()));
        vo.setResult(parseJson(call.getToolResultJson()));
        vo.setStatus(call.getStatus());
        vo.setSeq(call.getSeq());
        vo.setError(call.getErrorMsg());
        vo.setCreateTime(call.getCreateTime());
        return vo;
    }

    /**
     * 宽松解析 JSON 文本；非 JSON（如纯文本工具结果）原样返回字符串，空值返回 null。
     */
    private Object parseJson(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return JSON.parse(text);
        } catch (Exception e) {
            return text;
        }
    }
}
