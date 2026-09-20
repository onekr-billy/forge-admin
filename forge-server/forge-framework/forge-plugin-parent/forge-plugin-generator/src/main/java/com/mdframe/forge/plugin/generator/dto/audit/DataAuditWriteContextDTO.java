package com.mdframe.forge.plugin.generator.dto.audit;

import lombok.Data;

/**
 * 客户端可提交的审计写入上下文。不接受 actor/tenant/source/beforeData。
 */
@Data
public class DataAuditWriteContextDTO {

    private String reason;

    private Long expectedRevision;
}
