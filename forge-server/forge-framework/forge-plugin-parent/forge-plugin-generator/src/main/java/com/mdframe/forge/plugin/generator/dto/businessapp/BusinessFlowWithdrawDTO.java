package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

/**
 * 单据页撤回主流程参数。
 */
@Data
public class BusinessFlowWithdrawDTO {

    private String objectCode;

    private Long recordId;

    private String processInstanceId;

    private String businessKey;

    private String comment;
}
