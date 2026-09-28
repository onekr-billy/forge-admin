package com.mdframe.forge.plugin.generator.service.businessapp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 可持久化、可校验的远程流程任务命令快照。 */
@Data
public class BusinessFlowRemoteTaskRequest {

    private Long tenantId;
    private String commandType;
    private String taskId;
    private String processInstanceId;
    private String businessKey;
    private String objectCode;
    private Long recordId;
    private String flowModelKey;
    private Long operatorUserId;
    private String comment;
    private Map<String, Object> variables = new LinkedHashMap<>();
    private String idempotencyKey;
    private String actionRequestDigest;
}
