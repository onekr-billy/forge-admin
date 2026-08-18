package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 业务应用平台-通用动作执行日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_business_action_execution_log")
public class AiBusinessActionExecutionLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String suiteCode;

    private String objectCode;

    private String recordId;

    private String actionCode;

    private String actionName;

    /** 不可变业务对象发布版本。 */
    private Integer actionVersion;

    /** LOCAL_TRANSACTION / ORCHESTRATION。 */
    private String executionMode;

    private String executeStatus;

    private String requestDigest;

    private String stepResult;

    private String resultMessage;

    private String errorMessage;

    private String correlationId;

    private String idempotencyKey;

    private Long durationMs;

    private String capabilityRequestId;

    private Long clientId;

    private Long serviceUserId;

    private String actorType;

    /** 结构化审计事件类型，例如 STATUS_TRANSITION。 */
    private String auditEventType;

    private String statusField;

    private String statusFrom;

    private String statusTo;

    /** 只保存字段名和状态摘要，不保存表单原值。 */
    private String changeSummary;

    /** 专用归档任务可据此执行留存治理。 */
    private LocalDateTime retentionUntil;
}
