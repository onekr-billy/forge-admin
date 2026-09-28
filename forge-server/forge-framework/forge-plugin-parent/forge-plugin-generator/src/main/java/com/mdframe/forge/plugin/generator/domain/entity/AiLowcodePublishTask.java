package com.mdframe.forge.plugin.generator.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 低代码发布跨阶段恢复任务。 */
@Data
@TableName("ai_lowcode_publish_task")
public class AiLowcodePublishTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String requestId;
    private String operationType;
    private Long configId;
    private String configKey;
    private Long versionId;
    private Integer versionNo;
    private String schemaHash;
    private Long runtimeDatasourceId;
    private String runtimeDatasourceCode;
    private String runtimeTableName;
    private Long operatorId;
    private String commandPayload;
    private String commandDigest;
    private String currentStage;
    private String taskStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockOwner;
    private LocalDateTime lockTime;
    private String errorType;
    private LocalDateTime completedTime;
    private Long createBy;
    private LocalDateTime createTime;
    private Long createDept;
    private Long updateBy;
    private LocalDateTime updateTime;
}
