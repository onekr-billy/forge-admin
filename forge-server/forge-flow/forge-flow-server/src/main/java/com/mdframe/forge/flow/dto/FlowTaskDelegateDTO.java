package com.mdframe.forge.flow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务转办请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowTaskDelegateDTO extends FlowTaskActionDTO {

    private String targetUserId;
}
