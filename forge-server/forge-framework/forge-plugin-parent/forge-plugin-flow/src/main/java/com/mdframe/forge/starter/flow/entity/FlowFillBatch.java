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
 * 组织批量填报批次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_flow_fill_batch")
public class FlowFillBatch extends TenantEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private Long entryId;

    private String entryCode;

    private String batchName;

    private String periodKey;

    private String targetScope;

    private String ownerRule;

    private LocalDateTime deadlineTime;

    private Integer allowResubmit;

    /**
     * 批次状态，取值见 {@link com.mdframe.forge.starter.flow.enums.FlowFillBatchStatus}。
     */
    private String status;

    @TableLogic
    private Integer deleted;
}
