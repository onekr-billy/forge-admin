package com.mdframe.forge.plugin.ai.agent.engine.dto;

import lombok.Data;

/**
 * Agent 引擎停止会话请求。
 */
@Data
public class AgentEngineStopDTO {

    private String sessionId;
}
