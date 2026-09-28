package com.mdframe.forge.plugin.generator.service.lowcode;

/** 在线 DDL 发布在分配版本身份前的稳定请求计划。 */
public record LowcodeOnlinePublishPlan(
        Integer protocolVersion,
        Long tenantId,
        Long configId,
        String configKey,
        Integer expectedDraftVersion,
        Integer expectedPublishedVersion,
        Long operatorId,
        LowcodeOnlinePublishConfigSnapshot configSnapshot,
        Boolean syncMenu,
        Long requestedMenuParentId,
        String businessSuiteCode,
        String businessObjectCode,
        String businessObjectName,
        String remark
) {
}
