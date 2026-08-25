package com.mdframe.forge.flow.dto;

import lombok.Data;

/**
 * 流程任务办理公共请求。
 */
@Data
public class FlowTaskActionDTO {

    private String taskId;

    private String userId;

    private String comment;

    private String signature;
}
