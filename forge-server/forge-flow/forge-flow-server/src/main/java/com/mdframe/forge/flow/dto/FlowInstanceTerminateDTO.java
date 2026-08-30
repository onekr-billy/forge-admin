package com.mdframe.forge.flow.dto;

import lombok.Data;

/**
 * 终止流程请求。
 */
@Data
public class FlowInstanceTerminateDTO {

    private String userId;

    private String reason;
}
