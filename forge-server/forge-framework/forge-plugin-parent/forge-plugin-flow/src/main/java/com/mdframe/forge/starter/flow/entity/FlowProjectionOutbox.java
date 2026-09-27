package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** Flowable 事件到 Forge 本地镜像的可靠投影 Outbox。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_flow_projection_outbox")
public class FlowProjectionOutbox extends TenantEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Integer eventVersion;
    private LocalDateTime occurredAt;
    private String eventType;
    private String aggregateType;
    private String aggregateId;
    private String payload;
    private String payloadHash;
    private Integer projectionStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockTime;
    private LocalDateTime appliedTime;
    private String lastError;
}
