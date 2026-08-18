package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Builder;
import lombok.Data;

/** 应用门户 AI 助理回复。 */
@Data
@Builder
public class BusinessApplicationAiAssistantReplyVO {

    private String pageId;

    private String capability;

    private String content;
}
