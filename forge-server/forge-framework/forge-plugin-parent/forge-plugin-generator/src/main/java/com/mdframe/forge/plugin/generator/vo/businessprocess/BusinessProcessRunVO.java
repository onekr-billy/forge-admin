package com.mdframe.forge.plugin.generator.vo.businessprocess;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务流程运行安全摘要。
 */
@Data
public class BusinessProcessRunVO {

    /** 雪花 ID 按字符串返回，禁止前端丢失精度。 */
    private String id;

    private String applicationId;

    private String processId;

    private String processVersionId;

    private String processCode;

    private String processName;

    private String subjectObjectCode;

    private String subjectRecordId;

    private String businessKey;

    private String triggerType;

    private String actorType;

    private String actorUserId;

    private String activeOrgId;

    private String status;

    private String currentNodeId;

    /** 从流程 schema 解析的节点名称，列表展示用。 */
    private String currentNodeName;

    private Integer retryCount;

    private String errorCode;

    private String errorSummary;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
