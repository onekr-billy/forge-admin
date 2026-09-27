package com.mdframe.forge.plugin.generator.service.businessapp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 可持久化、可校验的远程流程启动请求快照。 */
@Data
public class BusinessFlowRemoteStartRequest {

    private Long tenantId;
    private String objectCode;
    private String configKey;
    private Long recordId;
    private String businessKey;
    private String flowBusinessKey;
    private String flowModelKey;
    private String title;
    private Map<String, Object> variables = new LinkedHashMap<>();
    private Long starterUserId;
    private String starterUserName;
    private boolean delegated;
    private boolean allowDraftRuntime;
}
