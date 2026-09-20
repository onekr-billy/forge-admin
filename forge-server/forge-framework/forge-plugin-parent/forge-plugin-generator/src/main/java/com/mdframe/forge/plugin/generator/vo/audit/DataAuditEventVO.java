package com.mdframe.forge.plugin.generator.vo.audit;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DataAuditEventVO {

    private String id;

    private String objectId;

    private String objectCode;

    private String objectName;

    private String recordId;

    private String recordLabel;

    private Long revision;

    private String eventType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occurredAt;

    private String timeZone = "Asia/Shanghai";

    private String operationId;

    private String parentOperationId;

    private String correlationId;

    private String sourceType;

    private String actorType;

    private String actorId;

    private String actorName;

    private String changeReason;

    private String reasonCode;

    private String flowInstanceId;

    private String taskId;

    private String actionExecutionId;

    private Integer changedFieldCount;

    private Integer changedRowCount;

    private Integer visibleFieldCount;

    private Boolean canReveal;

    private Boolean recordDeleted;

    private List<String> capabilities = new ArrayList<>();
}
