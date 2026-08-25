package com.mdframe.forge.plugin.ai.agent.engine.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.ai.agent.engine.ReactContext;
import com.mdframe.forge.plugin.ai.agent.engine.ReactRequest;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEvent;
import com.mdframe.forge.plugin.ai.agent.engine.event.AgentEventType;
import com.mdframe.forge.plugin.ai.agent.engine.event.sse.AgentEventWebFluxStream;
import com.mdframe.forge.plugin.ai.agent.engine.service.AgentEngineService;
import com.mdframe.forge.starter.core.domain.RespInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Agent 引擎控制器（新入口，不替代 AiClientController）
 */
@RestController
@RequestMapping("/ai/engine")
@RequiredArgsConstructor
public class AgentEngineController {

    private final AgentEngineService engineService;
    private final AgentEventWebFluxStream stream;

    /**
     * Agent 引擎对话（SSE 流式）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("ai:engine:stream")
    public Flux<ServerSentEvent<String>> stream(@RequestBody ReactRequest request, HttpServletResponse response) {
        prepareSse(response);
        return engineService.stream(request);
    }

    /**
     * HITL 恢复（用户确认/拒绝后继续）
     */
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("ai:engine:resume")
    public Flux<ServerSentEvent<String>> resume(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        prepareSse(response);
        String interruptId = (String) body.get("interruptId");
        Boolean confirmed = (Boolean) body.get("confirmed");
        return engineService.resume(interruptId, confirmed != null && confirmed);
    }

    /**
     * 停止当前会话生成
     */
    @PostMapping("/stop")
    @SaCheckPermission("ai:engine:stream")
    public RespInfo<Void> stop(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        engineService.stop(sessionId);
        return RespInfo.success();
    }

    /**
     * 订阅事件流（SSE）
     */
    @GetMapping(value = "/events/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> subscribeEvents(@PathVariable String sessionId, HttpServletResponse response) {
        prepareSse(response);
        return stream.subscribe(sessionId);
    }

    /**
     * 为 SSE 响应关闭缓冲：与 {@code AiClientController#stream} 保持一致，
     * 设置 UTF-8 编码、Cache-Control:no-cache 与 X-Accel-Buffering:no，
     * 避免 nginx/代理把整段流缓冲成“一次性返回”，保证逐块下发。
     */
    private void prepareSse(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
