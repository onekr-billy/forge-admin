package com.mdframe.forge.flow.dto;

import lombok.Data;

/**
 * 流程撤回请求。
 */
@Data
public class FlowTaskWithdrawDTO {

    private String processInstanceId;

    private String userId;
}
