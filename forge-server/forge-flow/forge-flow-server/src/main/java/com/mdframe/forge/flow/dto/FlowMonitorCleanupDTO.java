package com.mdframe.forge.flow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员按筛选条件批量删除流程数据请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowMonitorCleanupDTO extends FlowMonitorReasonDTO {

    private String confirmText;

    private String processName;

    private String initiator;

    private String status;

    private String modelKey;

    private Long startTime;

    private Long endTime;
}
