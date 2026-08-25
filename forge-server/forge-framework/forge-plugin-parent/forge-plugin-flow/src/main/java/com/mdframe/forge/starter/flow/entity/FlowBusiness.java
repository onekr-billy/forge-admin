package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程业务关联实体
 */
@Data
@TableName("sys_flow_business")
public class FlowBusiness {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 业务Key（唯一）
     */
    private String businessKey;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 流程定义ID
     */
    private String processDefId;

    /**
     * 流程定义KEY
     */
    private String processDefKey;

    /**
     * 流程标题
     */
    private String title;

    /**
     * 业务状态，取值见 {@link com.mdframe.forge.starter.flow.enums.FlowBusinessStatus}。
     */
    private String status;

    /**
     * 申请人ID
     */
    private String applyUserId;

    /**
     * 申请人姓名
     */
    private String applyUserName;

    /**
     * 申请部门ID
     */
    private String applyDeptId;

    /**
     * 申请部门名称
     */
    private String applyDeptName;

    /**
     * 申请时间
     */
    private LocalDateTime applyTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 流程耗时（毫秒）
     */
    private Long duration;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
