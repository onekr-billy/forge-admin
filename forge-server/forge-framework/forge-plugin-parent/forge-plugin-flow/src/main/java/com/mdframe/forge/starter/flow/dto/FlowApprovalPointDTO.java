package com.mdframe.forge.starter.flow.dto;

import lombok.Data;

/**
 * 流程节点审批要点。
 */
@Data
public class FlowApprovalPointDTO {

    private String id;

    private String content;

    private Boolean required;

    private Integer sort;
}
