package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务应用平台-触发器执行日志实体。
 */
@Data
@TableName("ai_business_trigger_log")
public class AiBusinessTriggerLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long triggerId;

    private String triggerName;

    private String suiteCode;

    private String objectCode;

    private String recordId;

    private String eventType;

    private String eventId;

    private String eventSource;

    private Integer eventVersion;

    private String eventDigest;

    /** 事件数据快照JSON */
    private String eventData;

    /** 触发器配置快照JSON，恢复时不得读取已变更的当前配置。 */
    private String triggerSnapshot;

    /** 事件与触发器快照的联合 SHA-256 摘要。 */
    private String executionDigest;

    private String actionType;

    /** 执行结果JSON */
    private String actionResult;

    /** PENDING/SUCCESS/FAILED/SKIPPED/TODO/DEAD */
    private String executeStatus;

    private String errorMessage;

    private String todoCode;

    private String correlationId;

    private LocalDateTime executeTime;

    private Long durationMs;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String lockOwner;

    private LocalDateTime lockTime;

    private LocalDateTime createTime;
}
