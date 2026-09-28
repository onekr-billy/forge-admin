package com.mdframe.forge.plugin.generator.service.businessapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private String signature;
    private String targetActivityId;
    private Map<String, Object> variables = new LinkedHashMap<>();
    private List<Map<String, Object>> approvalPointResults = new ArrayList<>();
    private String idempotencyKey;
    private String actionRequestDigest;
    /** 服务端对完整交互提交（含表单数据）的摘要；快照不直接保存业务表单原文。 */
    private String submissionDigest;
}
