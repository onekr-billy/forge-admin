package com.mdframe.forge.flow.dto;

import lombok.Data;

/**
 * 重试流程失败节点请求。
 */
@Data
public class FlowErrorLogRetryDTO {

    private String processInstanceId;

    private String activityId;

    private String reason;
}
