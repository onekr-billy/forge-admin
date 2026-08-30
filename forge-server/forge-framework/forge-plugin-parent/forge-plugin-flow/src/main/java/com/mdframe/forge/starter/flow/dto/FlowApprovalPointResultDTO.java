package com.mdframe.forge.starter.flow.dto;

import lombok.Data;

/**
 * 审批要点勾选结果。
 */
@Data
public class FlowApprovalPointResultDTO {

    private String id;

    private String content;

    private Boolean required;

    private Boolean checked;
}
