package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_flow_model_version")
public class FlowModelVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String modelId;

    private Integer version;

    private String versionName;

    private String versionTag;

    private String bpmnXml;

    private String formJson;

    private String changeDescription;

    private String deploymentId;

    private String processDefinitionId;

    private String publishBy;

    private LocalDateTime publishTime;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer delFlag;
}