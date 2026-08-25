package com.mdframe.forge.plugin.ai.agent.engine.dto;

import lombok.Data;

/**
 * Agent 引擎 HITL 恢复请求。
 */
@Data
public class AgentEngineResumeDTO {

    private String interruptId;

    private Boolean confirmed;
}
