package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 动态 CRUD 与流程回调的可靠业务事件信封。 */
@Data
@TableName("ai_business_event_outbox")
public class AiBusinessEventOutbox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String eventId;
    private String eventSource;
    private Integer eventVersion;
    private String sourceDigest;
    private String eventDigest;
    private String eventType;
    private String suiteCode;
    private String objectCode;
    private String configKey;
    private String recordId;
    private String aggregateKey;
    private Long aggregateSequence;
    private String eventPayload;
    private String deliveryStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockTime;
    private String errorType;
    private LocalDateTime deliveredTime;
    private Long createBy;
    private LocalDateTime createTime;
    private Long createDept;
    private Long updateBy;
    private LocalDateTime updateTime;
}
