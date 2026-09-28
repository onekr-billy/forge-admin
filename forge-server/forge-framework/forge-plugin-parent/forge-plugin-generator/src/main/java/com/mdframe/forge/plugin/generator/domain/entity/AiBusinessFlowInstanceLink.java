package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mdframe.forge.starter.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 业务应用平台-单据流程实例关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_business_flow_instance_link")
public class AiBusinessFlowInstanceLink extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String objectCode;

    private Long recordId;

    private String businessKey;

    private String flowModelKey;

    private String processInstanceId;

    private String flowStatus;

    private Long startUserId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String result;

    /** 同一 businessKey 下的提交轮次，从 1 开始。 */
    private Integer roundNo;

    /** 流程变量快照 JSON */
    private String variablesSnapshot;

    /** 主动状态对账状态：PENDING/WAITING/PROCESSING/RETRY/COMPLETED/DEAD。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String statusSyncStatus;

    /** 连续失败/接管尝试次数，成功读取运行态后归零。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer statusSyncRetryCount;

    /** 下次允许主动对账的时间。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime statusSyncNextTime;

    /** 当前对账租约持有者。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String statusSyncLockOwner;

    /** 当前对账租约开始时间。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime statusSyncLockTime;

    /** 最近一次从 Flow 服务读取到的规范状态。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String statusSyncRemoteStatus;

    /** 最近一次对账失败类型，不保存异常消息和远端响应。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String statusSyncErrorType;

    /** 最近一次成功读取并应用远端状态的时间。 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime statusSyncedTime;
}
