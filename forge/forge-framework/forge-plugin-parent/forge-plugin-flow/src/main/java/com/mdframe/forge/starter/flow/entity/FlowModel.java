package com.mdframe.forge.starter.flow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_flow_model")
public class FlowModel {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String modelKey;

    private String modelName;

    private String description;

    private String category;

    private String flowType;

    private String formType;

    private String formId;

    private String formJson;

    private String bpmnXml;

    private Integer version;

    private String processDefinitionId;

    private String deploymentId;

    private String deploymentKey;

    private String notifyType;

    private String webhookUrl;

    private Integer status;

    private Integer importanceLevel;

    private LocalDateTime deployTime;

    private String lastUpdateBy;

    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}