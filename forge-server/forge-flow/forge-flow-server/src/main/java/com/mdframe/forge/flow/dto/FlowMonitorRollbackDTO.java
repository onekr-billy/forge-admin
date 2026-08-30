package com.mdframe.forge.flow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员回退流程请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowMonitorRollbackDTO extends FlowMonitorReasonDTO {

    private String targetActivityId;
}
