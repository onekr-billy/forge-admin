package com.mdframe.forge.plugin.generator.service.lowcode;

/** 在线 DDL 发布的不可变跨阶段命令。 */
public record LowcodeOnlinePublishCommand(
        Integer protocolVersion,
        Long tenantId,
        Long configId,
        String configKey,
        Integer expectedDraftVersion,
        Integer expectedPublishedVersion,
        Long versionId,
        Integer versionNo,
        Long operatorId,
        String requestDigest,
        LowcodeOnlinePublishConfigSnapshot configSnapshot,
        Boolean syncMenu,
        Long requestedMenuParentId,
        String businessSuiteCode,
        String businessObjectCode,
        String businessObjectName,
        String remark
) {

    public LowcodePublishPostCommand toPostCommand() {
        return new LowcodePublishPostCommand(
                LowcodePublishTaskService.COMMAND_PROTOCOL_VERSION,
                tenantId,
                configId,
                configKey,
                versionId,
                versionNo,
                "PUBLISH",
                syncMenu,
                requestedMenuParentId,
                businessSuiteCode,
                businessObjectCode,
                businessObjectName,
                operatorId);
    }
}
