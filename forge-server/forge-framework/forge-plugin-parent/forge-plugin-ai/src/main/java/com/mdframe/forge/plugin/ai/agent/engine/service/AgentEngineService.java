package com.mdframe.forge.plugin.ai.agent.engine.service;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.agent.domain.AiAgent;
import com.mdframe.forge.plugin.ai.agent.engine.ReactAgent;
import com.mdframe.forge.plugin.ai.agent.engine.ReactContext;
import com.mdframe.forge.plugin.ai.agent.engine.ReactRequest;
import com.mdframe.forge.plugin.ai.agent.engine.context.ContextTrimmer;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventType;
import com.mdframe.forge.plugin.ai.agent.engine.persistence.AgentChatPersister;
import com.mdframe.forge.plugin.ai.agent.service.AiAgentService;
import com.mdframe.forge.plugin.ai.chat.domain.AiChatRecord;
import com.mdframe.forge.plugin.ai.chat.service.AiChatRecordService;
import com.mdframe.forge.plugin.ai.provider.adapter.AiModelRuntimeOptions;
import com.mdframe.forge.plugin.ai.provider.adapter.AiProviderAdapterRegistry;
import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.plugin.ai.provider.mapper.AiProviderMapper;
import com.mdframe.forge.plugin.ai.provider.support.AiSecretCrypto;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.file.core.FileManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent 引擎服务。
 * 新入口，不替代 AiClient。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEngineService {

    private final ReactAgent reactAgent;
    private final AiAgentService agentService;
    private final AiProviderAdapterRegistry providerAdapterRegistry;
    private final AiProviderMapper providerMapper;
    private final AiSecretCrypto aiSecretCrypto;
    private final FileManager fileManager;
    private final AgentChatPersister chatPersister;
    private final AiChatRecordService recordService;
    private final ContextTrimmer contextTrimmer;

    /**
     * 流式执行 Agent 对话
     */
    public Flux<ServerSentEvent<String>> stream(ReactRequest request) {
        // 1. 解析 Agent 配置
        AiAgent agent = agentService.getByCode(request.getAgentCode());
        if (agent == null) {
            throw new BusinessException("Agent不存在: " + request.getAgentCode());
        }

        // 2. 构造 ReactContext
        ReactContext ctx = buildContext(agent, request);

        // 3. 请求线程建行 + 注册流状态（须在 loop 启动前，保证事件回调能查到）
        AgentChatPersister.OpenResult open = chatPersister.openForStream(request, agent.getAgentCode());

        // 4. 执行 ReAct 循环；在流首 concat PERSIST_META，把落库 recordId 下发前端
        Flux<AgentEvent> events = reactAgent.execute(ctx);
        AgentEvent meta = buildPersistMeta(request.getSessionId(), open);
        if (meta != null) {
            events = Flux.concat(Flux.just(meta), events);
        }
        return events
                .map(event -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(event.getTimestamp()))
                        .event(event.getEventType().getCode())
                        .data(event.getData() != null ? event.getData() : "")
                        .build());
    }

    /**
     * 构造流首 PERSIST_META 事件：携带本轮落库的 user/assistant recordId。
     * 在 service 层 concat 进 SSE 流，不经过 eventPublisher，故持久化监听器不会收到。
     * 结果为空或无 assistant 行（sessionId 缺失/建行失败）时返回 {@code null}，不下发。
     */
    private AgentEvent buildPersistMeta(String sessionId, AgentChatPersister.OpenResult open) {
        if (open == null || open.assistantRecordId() == null) {
            return null;
        }
        JSONObject data = new JSONObject();
        data.put("assistantRecordId", open.assistantRecordId());
        if (open.userRecordId() != null) {
            data.put("userRecordId", open.userRecordId());
        }
        return AgentEvent.of(sessionId, 0, AgentEventType.PERSIST_META, data.toJSONString());
    }

    /**
     * HITL 恢复
     */
    public Flux<ServerSentEvent<String>> resume(String interruptId, boolean confirmed) {
        // 请求线程接回原消息（须在 loop 重跑前）
        chatPersister.openForResume(interruptId, confirmed);
        return reactAgent.resume(interruptId, confirmed)
                .map(event -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(event.getTimestamp()))
                        .event(event.getEventType().getCode())
                        .data(event.getData() != null ? event.getData() : "")
                        .build());
    }

    public void stop(String sessionId) {
        reactAgent.cancel(sessionId);
    }

    private ReactContext buildContext(AiAgent agent, ReactRequest request) {
        ReactContext ctx = new ReactContext();
        ctx.setAgentCode(agent.getAgentCode());
        ctx.setAgentId(agent.getId());
        ctx.setTenantId(agent.getTenantId());
        ctx.setUserId(SessionHelper.getUserId());
        ctx.setSessionId(request.getSessionId());
        ctx.setMaxIters(agent.getMaxIters() != null ? agent.getMaxIters() : 10);
        ctx.setToolGroupMode(agent.getToolGroupMode() != null ? agent.getToolGroupMode() : "all");
        ctx.setSystemPrompt(agent.getSystemPrompt());
        ctx.setUserMessage(resolveUserMessage(request));
        ctx.setHistory(resolveHistory(request, ctx));

        // 传递图片附件
        if (request.getImageFileIds() != null && !request.getImageFileIds().isEmpty()) {
            ctx.setImageFileIds(request.getImageFileIds());
            ctx.setFileUrlResolver(this::resolveFileUrl);
        }

        // 创建 ChatModel（复用 providerAdapterRegistry 的 createChatModel）
        ctx.setChatModel(createChatModel(agent));
        ctx.setAgent(agent);

        return ctx;
    }

    /**
     * 将 fileId 转为可访问的 URL（供多模态消息使用）
     */
    private String resolveFileUrl(Long fileId) {
        try {
            return fileManager.getAccessUrl(String.valueOf(fileId), 3600);
        } catch (Exception e) {
            log.warn("[AgentEngine] 获取文件URL失败: fileId={}", fileId, e);
            return null;
        }
    }

    private List<org.springframework.ai.chat.messages.Message> resolveHistory(ReactRequest request, ReactContext ctx) {
        Long cutoffRecordId = resolveHistoryCutoffRecordId(request);
        int fetch = contextTrimmer.getMaxHistoryMessages();
        List<AiChatRecord> records = cutoffRecordId != null
                ? recordService.listRecentBySessionBeforeRecord(request.getSessionId(), cutoffRecordId, fetch)
                : recordService.listRecentBySession(request.getSessionId(), fetch);
        List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>(records.size());
        for (AiChatRecord record : records) {
            org.springframework.ai.chat.messages.Message message = toMessage(record);
            if (message != null) {
                messages.add(message);
            }
        }
        // 长会话上下文裁剪：按 token 预算保留最近若干条，避免撑爆模型上下文窗口。
        // system + user 先占预算（此时 ctx 的 systemPrompt/userMessage 已在 buildContext 中设置）。
        int reserved = contextTrimmer.estimateTokens(ctx.getSystemPrompt())
                + contextTrimmer.estimateTokens(ctx.getUserMessage());
        return contextTrimmer.trim(messages, reserved);
    }

    private Long resolveHistoryCutoffRecordId(ReactRequest request) {
        if (request.getEditUserRecordId() != null) {
            return request.getEditUserRecordId();
        }
        if (request.getRetryOfRecordId() != null) {
            AiChatRecord latestUser = recordService.findLatestUserBySession(request.getSessionId());
            return latestUser != null ? latestUser.getId() : request.getRetryOfRecordId();
        }
        return null;
    }

    private String resolveUserMessage(ReactRequest request) {
        if (request.getMessage() != null) {
            return request.getMessage();
        }
        if (request.getRetryOfRecordId() != null) {
            AiChatRecord latestUser = recordService.findLatestUserBySession(request.getSessionId());
            return latestUser != null ? latestUser.getContent() : null;
        }
        return null;
    }

    private org.springframework.ai.chat.messages.Message toMessage(AiChatRecord record) {
        if (record == null || record.getRole() == null) {
            return null;
        }
        return switch (record.getRole()) {
            case "system" -> new org.springframework.ai.chat.messages.SystemMessage(record.getContent());
            case "user" -> new org.springframework.ai.chat.messages.UserMessage(record.getContent());
            case "assistant" -> new org.springframework.ai.chat.messages.AssistantMessage(record.getContent());
            default -> null;
        };
    }

    private ChatModel createChatModel(AiAgent agent) {
        AiProvider provider = providerMapper.selectById(agent.getProviderId());
        if (provider == null) {
            throw new BusinessException("供应商不存在: " + agent.getProviderId());
        }
        AiModelRuntimeOptions options = new AiModelRuntimeOptions(
                agent.getModelName(),
                agent.getTemperature() != null ? agent.getTemperature().doubleValue() : 0.7,
                agent.getMaxTokens()
        );
        return providerAdapterRegistry.createChatModel(provider, options);
    }
}
