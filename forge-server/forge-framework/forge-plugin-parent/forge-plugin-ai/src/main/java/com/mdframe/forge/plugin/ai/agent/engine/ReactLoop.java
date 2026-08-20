package com.mdframe.forge.plugin.ai.agent.engine;

import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventPublisher;
import com.mdframe.forge.plugin.ai.agent.engine.hitl.InterruptStore;
import com.mdframe.forge.plugin.ai.agent.engine.permission.PermissionDecision;
import com.mdframe.forge.plugin.ai.agent.engine.permission.PermissionEngine;
import com.mdframe.forge.plugin.ai.agent.engine.tool.AgentTool;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolContext;
import com.mdframe.forge.plugin.ai.agent.engine.tool.ToolResult;
import com.mdframe.forge.plugin.ai.agent.engine.tool.registry.AgentToolRegistry;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventType;
import com.mdframe.forge.plugin.ai.agent.domain.AiAgent;
import com.mdframe.forge.plugin.ai.agent.service.AiAgentService;
import com.mdframe.forge.plugin.ai.provider.adapter.AiProviderAdapterRegistry;
import com.mdframe.forge.plugin.ai.provider.support.AiSecretCrypto;
import com.mdframe.forge.plugin.ai.invocation.AiInvocationObservation;
import com.mdframe.forge.plugin.ai.invocation.AiInvocationOutcome;
import com.mdframe.forge.plugin.ai.invocation.AiInvocationPhase;
import com.mdframe.forge.plugin.ai.invocation.service.AiModelInvocationRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ReAct 循环核心。
 * 纯 ReAct 循环（推理→行动迭代），底层用 Spring AI ChatModel。
 * 不使用 Spring AI 的 ChatClient.tools() / ToolCallAdvisor——工具调用循环完全自控。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactLoop {

    private final AiAgentService agentService;
    private final AgentToolRegistry toolRegistry;
    private final PermissionEngine permissionEngine;
    private final AgentEventPublisher eventPublisher;
    private final InterruptStore interruptStore;
    private final AiModelInvocationRecorder invocationRecorder;

    /**
     * 执行 ReAct 循环
     */
    public Flux<AgentEvent> run(ReactContext ctx) {
        Sinks.Many<AgentEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 最小对话埋点（task10）：requestId 与起始时间在请求线程生成，终止时统一记录一条调用观测
        String requestId = UUID.randomUUID().toString();
        long startedAtMs = System.currentTimeMillis();
        LoopTelemetry tel = new LoopTelemetry();

        // 异步执行循环
        Thread t = new Thread(() -> {
            try {
                executeLoop(ctx, sink, tel);
            } catch (Exception e) {
                log.error("[ReactLoop] 循环异常: sessionId={}", ctx.getSessionId(), e);
                tel.outcome = AiInvocationOutcome.FAILED;
                tel.phase = AiInvocationPhase.STREAMING;
                AgentEvent error = AgentEvent.of(ctx.getSessionId(), ctx.getTurnIndex(),
                        AgentEventType.AGENT_END, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                // 先 publish 再 emit：确保持久化监听器（不依赖客户端订阅）能收到终止事件
                eventPublisher.publish(error);
                sink.tryEmitNext(error);
            } finally {
                // 埋点在所有退出路径统一收口（正常/错误/中断），失败不影响主流程
                recordTelemetry(ctx, requestId, startedAtMs, tel);
                sink.tryEmitComplete();
            }
        }, "react-loop-" + ctx.getSessionId());
        t.start();

        return sink.asFlux();
    }

    private void executeLoop(ReactContext ctx, Sinks.Many<AgentEvent> sink, LoopTelemetry tel) {
        // 发送 AGENT_START
        AgentEvent startEvent = AgentEvent.of(ctx.getSessionId(), 0, AgentEventType.AGENT_START,
                "{\"agentCode\":\"" + ctx.getAgentCode() + "\"}");
        eventPublisher.publish(startEvent);
        sink.tryEmitNext(startEvent);

        // 如果有图片附件但模型可能不支持视觉，发送 HINT_BLOCK
        if (ctx.getImageFileIds() != null && !ctx.getImageFileIds().isEmpty()) {
            AgentEvent visionHint = AgentEvent.of(ctx.getSessionId(), 0, AgentEventType.HINT_BLOCK,
                    "{\"hint\":\"当前对话包含图片附件，请确认模型支持视觉能力。如不支持，图片内容将被忽略。\"}");
            eventPublisher.publish(visionHint);
            sink.tryEmitNext(visionHint);
        }

        int maxIters = ctx.getMaxIters();
        int turn = 0;

        while (turn < maxIters) {
            if (ctx.isCancelled()) {
                tel.outcome = AiInvocationOutcome.CANCELLED;
                tel.phase = AiInvocationPhase.STREAMING;
                AgentEvent stopEvent = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.AGENT_END,
                        "{\"aborted\":true}");
                eventPublisher.publish(stopEvent);
                sink.tryEmitNext(stopEvent);
                return;
            }
            ctx.setTurnIndex(turn);

            if (ctx.isCancelled()) {
                tel.outcome = AiInvocationOutcome.CANCELLED;
                tel.phase = AiInvocationPhase.STREAMING;
                AgentEvent stopEvent = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.AGENT_END,
                        "{\"aborted\":true}");
                eventPublisher.publish(stopEvent);
                sink.tryEmitNext(stopEvent);
                return;
            }

            // 1. Reason: 调用 LLM（流式消费，逐块发出 TEXT/THINKING delta）
            AgentEvent modelStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.MODEL_CALL_START,
                    "{\"turn\":" + turn + "}");
            eventPublisher.publish(modelStart);
            sink.tryEmitNext(modelStart);
            tel.dispatched = true;

            ChatResponse chatResponse;
            try {
                // 正文按 TEXT_BLOCK_START/DELTA/END 拆分，思考按 THINKING_BLOCK_DELTA 逐块发出
                chatResponse = callModel(ctx, sink, turn);
            } catch (Exception e) {
                tel.outcome = AiInvocationOutcome.FAILED;
                tel.phase = AiInvocationPhase.STREAMING;
                AgentEvent error = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.MODEL_CALL_END,
                        "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                eventPublisher.publish(error);
                sink.tryEmitNext(error);
                break;
            }

            // MODEL_CALL_END 附带用量（决策19：token_usage 保留，同时补充 usage JSON）
            String usageJson = buildUsageJson(chatResponse);
            applyUsage(chatResponse, tel);
            String modelEndData = usageJson != null
                    ? "{\"turn\":" + turn + ",\"usage\":" + usageJson + "}"
                    : "{\"turn\":" + turn + "}";
            AgentEvent modelEnd = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.MODEL_CALL_END, modelEndData);
            eventPublisher.publish(modelEnd);
            sink.tryEmitNext(modelEnd);

            // 2. 解析 LLM 响应（正文/思考已在流式阶段逐块发出，这里只取 tool_call）
            if (chatResponse == null || chatResponse.getResult() == null) {
                break;
            }
            AssistantMessage assistant = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = assistant.getToolCalls();

            // 3. 无 tool_call → 循环结束
            if (toolCalls == null || toolCalls.isEmpty()) {
                break;
            }

            // 4. 执行工具调用
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.name();
                String toolArgs = toolCall.arguments();

                AgentEvent toolStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TOOL_CALL_START,
                        "{\"tool\":\"" + toolName + "\",\"args\":" + toolArgs + "}");
                eventPublisher.publish(toolStart);
                sink.tryEmitNext(toolStart);

                // 权限决策
                PermissionDecision decision = permissionEngine.decide(ctx.getAgentId(), toolName, ctx.getToolGroupMode());
                if (decision == PermissionDecision.DENY) {
                    AgentEvent denied = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.ALL_TOOLS_DENIED,
                            "{\"tool\":\"" + toolName + "\"}");
                    eventPublisher.publish(denied);
                    sink.tryEmitNext(denied);

                    // 构造拒绝结果
                    String denyResult = "工具 " + toolName + " 被拒绝执行。";
                    ctx.addToolResult(toolName, toolArgs, denyResult);
                    continue;
                }

                if (decision == PermissionDecision.ASK) {
                    // 真正中断：保存上下文到 Redis，等待用户确认/拒绝后 resume 恢复
                    String interruptId = interruptStore.save(ctx);
                    AgentEvent confirm = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.REQUIRE_USER_CONFIRM,
                            "{\"interruptId\":\"" + interruptId + "\",\"tool\":\"" + toolName + "\",\"args\":" + toolArgs + "}");
                    eventPublisher.publish(confirm);
                    sink.tryEmitNext(confirm);
                    log.info("[ReactLoop] HITL 中断等待确认: interruptId={}, tool={}, sessionId={}",
                            interruptId, toolName, ctx.getSessionId());
                    // 中断后终止当前循环，等待 resume 恢复
                    AgentEvent interrupted = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.AGENT_END,
                            "{\"interrupted\":true,\"interruptId\":\"" + interruptId + "\"}");
                    eventPublisher.publish(interrupted);
                    sink.tryEmitNext(interrupted);
                    // 埋点：中断视为本次调用取消（resume 会作为新一次调用另行记录）
                    tel.outcome = AiInvocationOutcome.CANCELLED;
                    tel.phase = AiInvocationPhase.STREAMING;
                    return;
                }

                // 执行工具
                ToolResult result = executeTool(ctx, toolName, toolArgs, turn);

                String resultType = result.getType().name().toLowerCase();
                AgentEvent toolResultStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TOOL_RESULT_START,
                        "{\"tool\":\"" + toolName + "\",\"type\":\"" + resultType + "\"}");
                eventPublisher.publish(toolResultStart);
                sink.tryEmitNext(toolResultStart);

                String resultContent = result.isSuccess() ? result.getContent() : "错误: " + result.getError();
                AgentEventType deltaType = result.getType() == ToolResult.Type.DATA
                        ? AgentEventType.TOOL_RESULT_DATA_DELTA : AgentEventType.TOOL_RESULT_TEXT_DELTA;
                AgentEvent toolResultDelta = AgentEvent.of(ctx.getSessionId(), turn, deltaType,
                        "{\"content\":\"" + escapeJson(resultContent) + "\"}");
                eventPublisher.publish(toolResultDelta);
                sink.tryEmitNext(toolResultDelta);

                AgentEvent toolResultEnd = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TOOL_RESULT_END,
                        "{\"tool\":\"" + toolName + "\"}");
                eventPublisher.publish(toolResultEnd);
                sink.tryEmitNext(toolResultEnd);

                // 将工具结果加入上下文
                ctx.addToolResult(toolName, toolArgs, resultContent);
            }

            turn++;
        }

        // 超过最大轮次
        if (turn >= maxIters) {
            AgentEvent exceed = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.EXCEED_MAX_ITERS,
                    "{\"maxIters\":" + maxIters + "}");
            eventPublisher.publish(exceed);
            sink.tryEmitNext(exceed);
            tel.outcome = AiInvocationOutcome.FAILED;
        }

        // 发送 AGENT_END
        AgentEvent endEvent = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.AGENT_END,
                "{\"turns\":" + turn + "}");
        eventPublisher.publish(endEvent);
        sink.tryEmitNext(endEvent);
    }

    /**
     * 流式调用模型：底层用 {@code chatModel.stream(prompt)}，在 loop 线程用 {@code toIterable()}
     * 阻塞拉取逐块消费——正文按 TEXT_BLOCK_START/DELTA/END 拆分发出，思考内容按 THINKING_BLOCK_DELTA 发出。
     * 消费过程中用 {@link MessageAggregator} 聚合出完整响应，供上层解析 tool_call 与用量（usage）。
     *
     * <p>所有事件仍在 loop 线程发射（toIterable 在当前线程拉取，不切线程），保持“事件发布均在
     * loop 线程”的不变量，与 HTTP 客户端连接生命周期无关。</p>
     */
    private ChatResponse callModel(ReactContext ctx, Sinks.Many<AgentEvent> sink, int turn) {
        ChatModel chatModel = ctx.getChatModel();

        // 工具声明：仅当 Agent 实际绑定了可用工具（当前=绑定知识库→rag_search）时才向模型声明工具。
        // 普通对话 Agent（无绑定）不带工具选项，行为与之前完全一致，零回归。
        // internalToolExecutionEnabled(false)：只声明不由 Spring AI 内部执行——工具执行仍由本 ReAct 循环自控。
        List<ToolCallback> toolCallbacks = resolveToolCallbacks(ctx);
        OpenAiChatOptions toolOptions = toolCallbacks.isEmpty() ? null : buildToolOptions(chatModel, toolCallbacks);
        Prompt prompt = toolOptions == null
                ? new Prompt(ctx.buildMessages())
                : new Prompt(ctx.buildMessages(), toolOptions);

        boolean[] textStarted = {false};
        boolean[] firstTokenCaptured = {false};
        AtomicReference<ChatResponse> aggregateRef = new AtomicReference<>();

        // MessageAggregator：透传原始 chunk，同时在流完成时回调聚合后的完整响应
        Flux<ChatResponse> stream = new MessageAggregator().aggregate(chatModel.stream(prompt), aggregateRef::set);
        for (ChatResponse chunk : stream.toIterable()) {
            if (ctx.isCancelled()) {
                break;
            }
            if (chunk == null || chunk.getResult() == null) {
                continue;
            }
            AssistantMessage out = chunk.getResult().getOutput();

            // 思考增量（reasoning）：来自模型 metadata（DeepSeek/DashScope 等），逐块发出
            String reasoning = extractReasoningContent(out);
            if (reasoning != null && !reasoning.isEmpty()) {
                if (!firstTokenCaptured[0]) {
                    firstTokenCaptured[0] = true;
                }
                AgentEvent thinking = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.THINKING_BLOCK_DELTA,
                        "{\"text\":\"" + escapeJson(reasoning) + "\"}");
                eventPublisher.publish(thinking);
                sink.tryEmitNext(thinking);
            }

            // 正文增量：首块发 TEXT_BLOCK_START，随后逐块 TEXT_BLOCK_DELTA
            String text = out.getText();
            if (text != null && !text.isEmpty()) {
                if (!firstTokenCaptured[0]) {
                    firstTokenCaptured[0] = true;
                }
                if (!textStarted[0]) {
                    AgentEvent textStart = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TEXT_BLOCK_START, null);
                    eventPublisher.publish(textStart);
                    sink.tryEmitNext(textStart);
                    textStarted[0] = true;
                }
                AgentEvent textDelta = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TEXT_BLOCK_DELTA,
                        "{\"text\":\"" + escapeJson(text) + "\"}");
                eventPublisher.publish(textDelta);
                sink.tryEmitNext(textDelta);
            }
        }
        // 有正文时收口 TEXT_BLOCK_END
        if (textStarted[0]) {
            AgentEvent textEnd = AgentEvent.of(ctx.getSessionId(), turn, AgentEventType.TEXT_BLOCK_END, null);
            eventPublisher.publish(textEnd);
            sink.tryEmitNext(textEnd);
        }

        return aggregateRef.get();
    }

    /**
     * 从模型 metadata 提取思考内容（reasoning）。兼容 reasoningContent / reasoning_content /
     * reasoning 三种键，与 {@code AiClientImpl} 保持一致。
     */
    private String extractReasoningContent(AssistantMessage message) {
        if (message == null || message.getMetadata() == null) {
            return null;
        }
        Map<String, Object> metadata = message.getMetadata();
        for (String key : new String[]{"reasoningContent", "reasoning_content", "reasoning"}) {
            Object v = metadata.get(key);
            if (v instanceof String s) {
                return s;
            }
        }
        return null;
    }

    /**
     * 从聚合响应提取用量 JSON（决策19）。无用量返回 null，不写入 MODEL_CALL_END。
     */
    private String buildUsageJson(ChatResponse resp) {
        try {
            if (resp == null || resp.getMetadata() == null) {
                return null;
            }
            Usage usage = resp.getMetadata().getUsage();
            if (usage == null) {
                return null;
            }
            Long prompt = toLong(usage.getPromptTokens());
            Long completion = toLong(usage.getCompletionTokens());
            Long total = toLong(usage.getTotalTokens());
            if (prompt == null && completion == null && total == null) {
                return null;
            }
            return "{\"promptTokens\":" + prompt + ",\"completionTokens\":" + completion
                    + ",\"totalTokens\":" + total + "}";
        } catch (Exception e) {
            log.debug("[ReactLoop] 用量解析跳过: {}", e.getMessage());
            return null;
        }
    }

    private Long toLong(Number number) {
        return number == null ? null : number.longValue();
    }

    /**
     * 把聚合响应的用量写入埋点累加器（决策19 + task10）。多轮时以最后一轮为准，解析异常静默跳过。
     */
    private void applyUsage(ChatResponse resp, LoopTelemetry tel) {
        try {
            if (resp == null || resp.getMetadata() == null) {
                return;
            }
            Usage usage = resp.getMetadata().getUsage();
            if (usage == null) {
                return;
            }
            tel.promptTokens = toLong(usage.getPromptTokens());
            tel.completionTokens = toLong(usage.getCompletionTokens());
            tel.totalTokens = toLong(usage.getTotalTokens());
        } catch (Exception e) {
            log.debug("[ReactLoop] 用量埋点跳过: {}", e.getMessage());
        }
    }

    /**
     * 最小对话埋点（task10）：在循环所有退出路径统一记录一条调用观测，复用 {@link AiModelInvocationRecorder}。
     * 引擎侧不解析路由/供应商/模型/定价，这些字段置空；至少覆盖发送(dispatched)、完成/错误/中断(outcome)、
     * token 用量与总耗时(latency)。埋点失败绝不影响主流程。
     */
    private void recordTelemetry(ReactContext ctx, String requestId, long startedAtMs, LoopTelemetry tel) {
        try {
            long latency = System.currentTimeMillis() - startedAtMs;
            if (latency < 0) {
                latency = 0;
            }
            Long tenantId = ctx.getTenantId() != null ? ctx.getTenantId() : 1L;
            AiInvocationObservation obs = new AiInvocationObservation(
                    requestId, tenantId, ctx.getUserId(), ctx.getAgentCode(), ctx.getSessionId(),
                    tel.phase, tel.dispatched, tel.outcome,
                    null, null, null,
                    null, null, null, null,
                    null, null, null,
                    latency, tel.promptTokens, tel.completionTokens, tel.totalTokens,
                    null, null);
            invocationRecorder.record(obs);
        } catch (Exception e) {
            log.debug("[ReactLoop] 对话埋点记录跳过: sessionId={}, {}", ctx.getSessionId(), e.getMessage());
        }
    }

    /**
     * 单次 run 的埋点累加器：全程在同一 loop 线程内读写，无需同步。
     * 默认视为成功完成；发生模型错误/超限时置 FAILED，HITL 中断置 CANCELLED。
     */
    private static class LoopTelemetry {
        private boolean dispatched = false;
        private AiInvocationOutcome outcome = AiInvocationOutcome.SUCCESS;
        private AiInvocationPhase phase = AiInvocationPhase.COMPLETED;
        private Long promptTokens;
        private Long completionTokens;
        private Long totalTokens;
    }

    private ToolResult executeTool(ReactContext ctx, String toolName, String toolArgs, int turn) {
        try {
            // 在所有工具源中查找
            AgentTool tool = null;
            for (AgentTool t : toolRegistry.getAllTools()) {
                if (t.getKey().equals(toolName)) {
                    tool = t;
                    break;
                }
            }
            if (tool == null) {
                return ToolResult.error("工具不存在: " + toolName);
            }

            // 解析参数
            Map<String, Object> args = com.alibaba.fastjson2.JSON.parseObject(toolArgs, Map.class);
            ToolContext toolContext = ToolContext.of(ctx.getSessionId(), ctx.getAgentId(), ctx.getTenantId(), turn);
            // 注入 Agent 绑定的知识库ID，供 RagSearchTool 在未显式指定 knowledge_id 时回退使用
            toolContext.setKnowledgeIds(parseKnowledgeIds(ctx.getAgent() != null ? ctx.getAgent().getKnowledgeIds() : null));
            return tool.execute(args, toolContext);
        } catch (Exception e) {
            log.error("[ReactLoop] 工具执行失败: tool={}", toolName, e);
            return ToolResult.error("工具执行失败: " + e.getMessage());
        }
    }

    /**
     * 解析 Agent 的 knowledgeIds（JSON 数组字符串，如 "[1,2]"）为 List&lt;Long&gt;；空/异常返回空列表。
     */
    private List<Long> parseKnowledgeIds(String knowledgeIdsJson) {
        if (knowledgeIdsJson == null || knowledgeIdsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Long> ids = com.alibaba.fastjson2.JSON.parseArray(knowledgeIdsJson, Long.class);
            return ids != null ? ids : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[ReactLoop] 解析 Agent 知识库ID失败: {}", knowledgeIdsJson);
            return Collections.emptyList();
        }
    }

    /**
     * 解析当前 Agent 应向模型声明的工具，封装为 Spring AI {@link ToolCallback}（仅携带定义，不承担执行）。
     * 空列表表示本次不声明任何工具（普通对话 Agent）。
     */
    private List<ToolCallback> resolveToolCallbacks(ReactContext ctx) {
        List<AgentTool> tools = resolveAdvertisedTools(ctx);
        if (tools.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolCallback> callbacks = new ArrayList<>(tools.size());
        for (AgentTool tool : tools) {
            callbacks.add(new AdvertisedToolCallback(tool));
        }
        return callbacks;
    }

    /**
     * 计算当前 Agent 需要向模型声明的工具列表。
     * <p>当前策略（保守、最小爆炸半径）：仅当 Agent 绑定了知识库（knowledgeIds 非空且 ragMode != none）时，
     * 声明内置 {@code rag_search}，使模型可自主决定何时检索知识库。其余内置工具（http/image/read_skill）
     * 目前无 Agent 级启用信号，暂不自动声明，避免影响普通对话 Agent。</p>
     */
    private List<AgentTool> resolveAdvertisedTools(ReactContext ctx) {
        List<AgentTool> tools = new ArrayList<>();
        AiAgent agent = ctx.getAgent();
        if (agent == null) {
            return tools;
        }
        // 知识库绑定 → 声明 rag_search
        List<Long> knowledgeIds = parseKnowledgeIds(agent.getKnowledgeIds());
        boolean ragEnabled = !knowledgeIds.isEmpty()
                && (agent.getRagMode() == null || !"none".equalsIgnoreCase(agent.getRagMode()));
        if (ragEnabled) {
            AgentTool rag = toolRegistry.getTool("builtin", "rag_search");
            if (rag != null) {
                tools.add(rag);
            }
        }
        return tools;
    }

    /**
     * 按 ChatModel 类型构造携带工具声明的选项。
     * <p>只为 {@link OpenAiChatModel}（含所有 OpenAI 兼容供应商）构造 {@link OpenAiChatOptions}；
     * 必须传具体的 OpenAiChatOptions（而非通用接口），否则 Spring AI 的选项合并会因 toolCallbacks 被
     * {@code @JsonIgnore} 而丢弃工具。其它模型（如 DashScope 原生）返回 null，即本次不带工具选项，
     * 行为与之前一致（该路径暂不支持工具声明，属后续可扩展项）。</p>
     */
    private OpenAiChatOptions buildToolOptions(ChatModel chatModel, List<ToolCallback> toolCallbacks) {
        if (chatModel instanceof OpenAiChatModel) {
            return OpenAiChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(false)
                    .build();
        }
        log.debug("[ReactLoop] 当前 ChatModel 非 OpenAI 兼容，跳过工具声明: {}",
                chatModel != null ? chatModel.getClass().getSimpleName() : "null");
        return null;
    }

    /**
     * 只用于「向模型声明工具」的 {@link ToolCallback} 包装：仅携带工具定义（name/description/inputSchema），
     * 不承担执行。工具执行由本 {@link ReactLoop} 自控循环负责，调用侧已设置
     * {@code internalToolExecutionEnabled(false)}，Spring AI 不会触发这里的 {@link #call}。
     */
    private static final class AdvertisedToolCallback implements ToolCallback {
        private final ToolDefinition definition;

        AdvertisedToolCallback(AgentTool tool) {
            this.definition = ToolDefinition.builder()
                    .name(tool.getKey())
                    .description(tool.getDescription())
                    .inputSchema(tool.getParametersSchema())
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            // 自控循环：工具执行由 ReactLoop 负责，Spring AI 不应内部执行（internalToolExecutionEnabled=false）
            throw new UnsupportedOperationException("Tool execution is handled by ReactLoop, not Spring AI");
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
