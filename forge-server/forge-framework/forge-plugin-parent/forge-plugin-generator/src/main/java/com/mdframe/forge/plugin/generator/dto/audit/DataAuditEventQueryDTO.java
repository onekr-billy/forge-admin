package com.mdframe.forge.plugin.generator.dto.audit;

import lombok.Data;

@Data
public class DataAuditEventQueryDTO {

    private String accessMode;

    private Long objectId;

    private String recordId;

    private String recordLabel;

    private String recordKeyword;

    private String fieldCode;

    private String fieldKeyword;

    private String actorId;

    private String eventType;

    private String sourceType;

    private String operationId;

    private String correlationId;

    private Long sourceApplicationId;

    private String flowInstanceId;

    private String taskId;

    private Boolean hasReason;

    private String startTime;

    private String endTime;

    private String configKey;
}
