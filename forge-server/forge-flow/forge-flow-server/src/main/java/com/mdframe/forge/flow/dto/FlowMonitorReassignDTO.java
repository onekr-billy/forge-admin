package com.mdframe.forge.flow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员转派任务请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowMonitorReassignDTO extends FlowMonitorReasonDTO {

    private String newAssignee;
}
