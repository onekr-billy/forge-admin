package com.mdframe.forge.plugin.generator.service.lowcode;

/** 发布主事务提交后执行菜单与业务入口同步所需的最小不可变命令。 */
public record LowcodePublishPostCommand(
        Integer protocolVersion,
        Long tenantId,
        Long configId,
        String configKey,
        Long versionId,
        Integer versionNo,
        String operationType,
        Boolean syncMenu,
        Long menuParentId,
        String businessSuiteCode,
        String businessObjectCode,
        String businessObjectName,
        Long operatorId
) {
}
