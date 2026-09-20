package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_data_audit_event")
public class AiDataAuditEvent extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long objectId;

    private String objectCode;

    private String objectName;

    private String recordId;

    private String recordLabel;

    private Long revision;

    private String eventType;

    private LocalDateTime occurredAt;

    private String operationId;

    private String parentOperationId;

    private String correlationId;

    private String sourceType;

    private String sourceEventId;

    private String sourceVersion;

    private Long sourceApplicationId;

    private Long sourcePageId;

    private String configKey;

    private Integer schemaVersion;

    private Integer policyVersion;

    private String policySnapshot;

    private String actorType;

    private String actorId;

    private String actorName;

    private String clientId;

    private String delegatedUserId;

    private String changeReason;

    private String reasonCode;

    private String flowInstanceId;

    private String taskId;

    private String actionExecutionId;

    private Integer changedFieldCount;

    private Integer changedRowCount;

    private String protectionMetadata;
}
