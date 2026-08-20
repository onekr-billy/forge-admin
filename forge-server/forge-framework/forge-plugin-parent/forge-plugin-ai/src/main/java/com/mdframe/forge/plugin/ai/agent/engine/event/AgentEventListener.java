package com.mdframe.forge.plugin.ai.agent.engine.event;

/**
 * Agent 事件监听器。
 *
 * <p>{@link AgentEventPublisher#publish} 在 loop 线程上对每个事件同步回调所有监听器，
 * 与 HTTP 客户端连接生命周期无关——客户端断连不影响监听器收到后续事件。
 * 持久化编排层（AgentChatPersister）借此可靠落库 ai_chat_record，抗断连、支持 HITL 重连与历史回放。</p>
 *
 * <p>实现方须自行保证：轻量、不抛异常穿透（publisher 已做兜底 try/catch）、不阻塞 loop 线程。</p>
 */
public interface AgentEventListener {

    /**
     * 收到一个 Agent 事件。按事件发射顺序、在 loop 线程上串行回调。
     */
    void onEvent(AgentEvent event);
}
