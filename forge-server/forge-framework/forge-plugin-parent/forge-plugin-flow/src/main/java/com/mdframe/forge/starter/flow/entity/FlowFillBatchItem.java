package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 组织批量填报明细。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_flow_fill_batch_item")
public class FlowFillBatchItem extends TenantEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private Long batchId;

    private String entryCode;

    private Long orgId;

    private String orgName;

    private Long ownerUserId;

    private String ownerUserName;

    private Long formInstanceId;

    private String objectCode;

    private Long recordId;

    private String processInstanceId;

    /**
     * 填报状态，取值见 {@link com.mdframe.forge.starter.flow.enums.FlowFillItemStatus}。
     */
    private String submitStatus;

    /**
     * 流程状态，取值见 {@link com.mdframe.forge.starter.flow.enums.FlowFillItemStatus}。
     */
    private String flowStatus;

    private LocalDateTime deadlineTime;

    private LocalDateTime submitTime;

    @TableLogic
    private Integer deleted;
}
