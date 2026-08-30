package com.mdframe.forge.plugin.ai.agent.engine.create.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.ai.agent.engine.create.AgentCreateService;
import com.mdframe.forge.plugin.ai.agent.engine.create.dto.AgentCreateStreamDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 创建 Agent 控制器
 */
@RestController
@RequestMapping("/ai/agent/ai-create")
@RequiredArgsConstructor
public class AgentCreateController {

    private final AgentCreateService agentCreateService;

    /**
     * 流式生成 Agent 配置（SSE）
     */
    @SaCheckPermission("ai:agent:ai-create")
    @PostMapping(produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> streamCreate(@RequestBody AgentCreateStreamDTO params) {
        return agentCreateService.streamCreate(params.getDescription());
    }

    /**
     * 确认创建 Agent
     */
    @SaCheckPermission("ai:agent:ai-create:confirm")
    @PostMapping("/confirm")
    public Map<String, Long> confirmCreate(@RequestBody JSONObject config) {
        Long agentId = agentCreateService.confirmCreate(config);
        return Map.of("agentId", agentId);
    }
}
