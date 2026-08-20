package com.mdframe.forge.plugin.ai.agent.engine.event;

import com.mdframe.forge.plugin.ai.agent.engine.event.persistence.AgentEventPersistence;
import com.mdframe.forge.plugin.ai.agent.engine.event.sse.AgentEventWebFluxStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 事件发布器。
 * publish 后：1) 异步持久化到 ai_agent_event；2) 同步转发 WebFlux SSE；3) 同步回调事件监听器。
 * 三条路径均在 loop 线程上执行，与 HTTP 客户端连接生命周期无关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEventPublisher {

    private final AgentEventPersistence persistence;
    private final AgentEventWebFluxStream stream;
    /** 事件监听器（如对话持久化编排层），无实现时 Spring 注入空列表 */
    private final List<AgentEventListener> listeners;

    /**
     * 发布事件
     */
    public void publish(AgentEvent event) {
        // 异步持久化
        try {
            persistence.persist(event);
        } catch (Exception e) {
            log.warn("[AgentEvent] 持久化失败: sessionId={}, type={}", event.getSessionId(), event.getEventType(), e);
        }
        // 同步转发 SSE
        try {
            stream.emit(event);
        } catch (Exception e) {
            log.debug("[AgentEvent] SSE转发跳过(无订阅者): sessionId={}", event.getSessionId());
        }
        // 同步回调监听器（各监听器内部自行兜底，此处再兜底一层，互不影响）
        for (AgentEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.warn("[AgentEvent] 监听器处理失败: listener={}, sessionId={}, type={}",
                        listener.getClass().getSimpleName(), event.getSessionId(), event.getEventType(), e);
            }
        }
    }
}
