package com.mdframe.forge.plugin.ai.multimodal.voice.dto;

import lombok.Data;

/**
 * 语音合成请求。
 */
@Data
public class AiVoiceSynthesizeDTO {

    private String text;

    private Long agentId;
}
