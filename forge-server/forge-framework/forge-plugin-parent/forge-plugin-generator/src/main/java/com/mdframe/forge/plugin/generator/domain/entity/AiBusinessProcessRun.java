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
 * 固定业务流程版本的持久化运行实例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_business_process_run")
public class AiBusinessProcessRun extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private Long processId;

    private Long processVersionId;

    private String processCode;

    private String subjectObjectCode;

    /** 业务记录主键的无损字符串形式。 */
    private String subjectRecordId;

    /** 固定为 objectCode:recordId。 */
    private String businessKey;

    private String triggerType;

    private String sourceEventId;

    private String idempotencyKey;

    private String actorType;

    private Long actorUserId;

    private Long activeOrgId;

    /** PENDING/RUNNING/WAITING/SUCCESS/FAILED/CANCELED。 */
    private String status;

    private String currentNodeId;

    private String flowProcessInstanceId;

    /** 已清洗的运行上下文，不允许保存 Token、Secret 或完整外部报文。 */
    private String contextSnapshot;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String errorCode;

    private String errorSummary;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
