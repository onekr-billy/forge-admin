package com.mdframe.forge.flow.dto;

import lombok.Data;

/**
 * 流程撤回请求。
 */
@Data
public class FlowTaskWithdrawDTO {

    private String processInstanceId;

    private String userId;

    private String comment;

    /** 仅用于与服务端可信会话租户交叉校验。 */
    private Long tenantId;

    private String idempotencyKey;

    private String requestDigest;
}
