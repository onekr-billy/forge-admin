package com.mdframe.forge.flow.dto;

import lombok.Data;

/**
 * 添加审批意见或流程事件请求。
 */
@Data
public class FlowCommentAddDTO {

    private String processInstanceId;

    private String processDefKey;

    private String taskId;

    private String taskName;

    private String type;

    private String message;

    private String userId;

    private String userName;
}
