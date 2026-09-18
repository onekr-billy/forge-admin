package com.mdframe.forge.plugin.generator.vo.businessprocess;

import lombok.Data;

/**
 * 当前租户可供应用编排节点设计或发布校验引用的审批模型。
 */
@Data
public class BusinessProcessFlowModelVO {

    private String modelId;

    private String modelKey;

    private String modelName;

    private Integer status;

    private Integer version;

    private String processDefinitionId;

    private String deploymentId;

    private Boolean deployed;
}
