package com.mdframe.forge.flow.dto;

import lombok.Data;

/**
 * 流程发起人或当前处理人改派任务请求。
 */
@Data
public class FlowTaskReassignDTO {

    private String taskId;

    private String userId;

    private String newAssignee;

    private String reason;
}
