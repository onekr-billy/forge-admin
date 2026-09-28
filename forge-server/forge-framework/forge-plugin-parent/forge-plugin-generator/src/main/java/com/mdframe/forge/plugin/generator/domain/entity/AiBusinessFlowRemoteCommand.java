package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** FlowClient 远程命令的持久化恢复记录。 */
@Data
@TableName("ai_business_flow_remote_command")
public class AiBusinessFlowRemoteCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String commandKey;
    private String commandType;
    private String requestDigest;
    private String requestPayload;
    private String objectCode;
    private Long recordId;
    private String businessKey;
    private String flowBusinessKey;
    private String flowModelKey;
    private String processInstanceId;
    private String commandStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockTime;
    private String errorType;
    private LocalDateTime remoteSucceededTime;
    private LocalDateTime completedTime;
    private Long createBy;
    private LocalDateTime createTime;
    private Long createDept;
    private Long updateBy;
    private LocalDateTime updateTime;
}
