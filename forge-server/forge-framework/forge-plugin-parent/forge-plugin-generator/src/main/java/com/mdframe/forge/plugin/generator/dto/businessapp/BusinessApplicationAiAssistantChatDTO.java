package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

/** 应用门户 AI 助理对话请求。 */
@Data
public class BusinessApplicationAiAssistantChatDTO {

    /** 当前门户页面 ID，服务端会按发布快照和当前用户权限再次校验。 */
    private String pageId;

    /** query、form、analysis。 */
    private String capability;

    private String message;
}
