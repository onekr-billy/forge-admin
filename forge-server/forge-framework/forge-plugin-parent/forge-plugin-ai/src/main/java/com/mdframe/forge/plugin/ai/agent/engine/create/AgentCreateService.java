package com.mdframe.forge.plugin.ai.agent.engine.create;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.agent.domain.AiAgent;
import com.mdframe.forge.plugin.ai.agent.engine.create.domain.AiAgentGenerateRecord;
import com.mdframe.forge.plugin.ai.agent.engine.create.enums.AiAgentGenerateStatus;
import com.mdframe.forge.plugin.ai.agent.engine.create.mapper.AiAgentGenerateRecordMapper;
import com.mdframe.forge.plugin.ai.agent.service.AiAgentService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * AI 创建 Agent 服务。
 * 流式逐字段生成 + 智能推荐绑定 + 确认创建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCreateService {

    private final AgentFieldGenerator fieldGenerator;
    private final AgentBindRecommender bindRecommender;
    private final AiAgentGenerateRecordMapper recordMapper;
    private final AiAgentService agentService;

    /**
     * 流式生成 Agent 配置（SSE）
     *
     * @param description 用户需求描述
     * @return SSE 事件流：start → field_done:{name,value} × N → recommend → done
     */
    public Flux<ServerSentEvent<String>> streamCreate(String description) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 先插入记录获取ID
        AiAgentGenerateRecord genRecord = new AiAgentGenerateRecord();
        genRecord.setDescription(description);
        genRecord.setStatus(AiAgentGenerateStatus.GENERATING.getCode());
        recordMapper.insert(genRecord);
        Long recordId = genRecord.getId();

        Thread t = new Thread(() -> {
            try {
                // 1. 发送 start 事件
                emit(sink, "start", JSON.toJSONString(Map.of("recordId", recordId)));

                // 2. 生成字段
                JSONObject config = fieldGenerator.generate(description);

                // 3. 逐字段推送 field_done 事件
                for (Map.Entry<String, Object> entry : config.entrySet()) {
                    String fieldName = entry.getKey();
                    Object fieldValue = entry.getValue();
                    emit(sink, "field_done", JSON.toJSONString(Map.of(
                            "name", fieldName,
                            "value", fieldValue
                    )));
                }

                // 4. 智能推荐绑定
                List<AgentBindRecommender.Recommendation> recommendations =
                        bindRecommender.recommend(description, config);
                if (!recommendations.isEmpty()) {
                    emit(sink, "recommend", JSON.toJSONString(Map.of(
                            "items", recommendations
                    )));
                }

                // 5. 保存生成结果
                genRecord.setGeneratedConfigJson(config.toJSONString());
                genRecord.setStatus(AiAgentGenerateStatus.SUCCESS.getCode());
                recordMapper.updateById(genRecord);

                // 6. 发送 done 事件
                emit(sink, "done", JSON.toJSONString(Map.of("recordId", recordId)));

            } catch (Exception e) {
                log.error("[AgentCreate] 生成失败, recordId={}", recordId, e);
                genRecord.setStatus(AiAgentGenerateStatus.FAILED.getCode());
                genRecord.setErrorMsg(truncate(e.getMessage(), 1000));
                recordMapper.updateById(genRecord);
                emit(sink, "error", JSON.toJSONString(Map.of("message", e.getMessage())));
            } finally {
                sink.tryEmitComplete();
            }
        }, "agent-create-" + recordId);
        t.start();

        return sink.asFlux();
    }

    /**
     * 确认创建 Agent
     *
     * @param config 用户编辑后的完整配置
     * @return 创建的 Agent ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long confirmCreate(JSONObject config) {
        AiAgent agent = new AiAgent();

        // 基础字段
        agent.setAgentName(config.getString("agentName"));
        agent.setDescription(config.getString("description"));
        agent.setSystemPrompt(config.getString("instruction"));
        agent.setGreeting(config.getString("greeting"));

        // 预设问题
        JSONArray presetQuestions = config.getJSONArray("presetQuestions");
        if (presetQuestions != null) {
            agent.setPresetQuestions(presetQuestions.toJSONString());
        }

        // 默认值
        agent.setModelSelectionMode("PINNED");
        agent.setMaxIters(config.getIntValue("maxIters") > 0 ? config.getIntValue("maxIters") : 10);
        agent.setRagMode("none");
        agent.setToolGroupMode("all");
        agent.setStatus(EnableStatus.DISABLED.codeAsString());

        // 供应商和模型（从配置中获取，或使用默认）
        Long providerId = config.getLong("providerId");
        if (providerId != null) {
            agent.setProviderId(providerId);
            agent.setModelName(config.getString("modelName"));
        }

        // 知识库绑定
        JSONArray knowledgeIds = config.getJSONArray("knowledgeIds");
        if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
            agent.setKnowledgeIds(knowledgeIds.toJSONString());
            agent.setRagMode("smart");
        }

        agentService.createAgent(agent);
        return agent.getId();
    }

    private void emit(Sinks.Many<ServerSentEvent<String>> sink, String event, String data) {
        sink.tryEmitNext(ServerSentEvent.<String>builder()
                .event(event)
                .data(data != null ? data : "")
                .build());
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
