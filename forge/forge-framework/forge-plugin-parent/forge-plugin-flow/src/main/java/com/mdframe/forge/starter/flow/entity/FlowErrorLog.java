package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程运行错误日志
 */
@Data
@TableName("sys_flow_error_log")
public class FlowErrorLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

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
     * 业务Key
     */
    private String businessKey;

    /**
     * Flowable任务ID
     */
    private String taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * BPMN节点ID
     */
    private String activityId;

    /**
     * BPMN节点名称
     */
    private String activityName;

    /**
     * 错误发生环节
     */
    private String errorStage;

    /**
     * 异常类型
     */
    private String errorType;

    /**
     * 异常摘要
     */
    private String errorMessage;

    /**
     * 异常堆栈
     */
    private String stackTrace;

    /**
     * Flowable作业ID
     */
    private String jobId;

    /**
     * Flowable作业剩余重试次数
     */
    private Integer jobRetries;

    /**
     * 状态：0-未处理/1-已重试/2-已解决/3-重试失败
     */
    private Integer status;

    /**
     * 人工重试次数
     */
    private Integer retryCount;

    /**
     * 最近重试人
     */
    private String lastRetryUserId;

    /**
     * 最近重试时间
     */
    private LocalDateTime lastRetryTime;

    /**
     * 重试说明或失败原因
     */
    private String retryMessage;

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
