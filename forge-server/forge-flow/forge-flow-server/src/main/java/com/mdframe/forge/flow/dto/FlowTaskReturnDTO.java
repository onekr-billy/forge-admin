package com.mdframe.forge.flow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务退回请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowTaskReturnDTO extends FlowTaskActionDTO {

    /**
     * 仅用于与服务端可信会话租户交叉校验。
     */
    private Long tenantId;
}
