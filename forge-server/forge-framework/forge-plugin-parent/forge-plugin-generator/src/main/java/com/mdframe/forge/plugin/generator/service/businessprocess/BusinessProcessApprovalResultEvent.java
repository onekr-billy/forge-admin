package com.mdframe.forge.plugin.generator.service.businessprocess;

/**
 * Flowable 审批终态通知。使用独立内部事件解耦流程回调服务与应用业务流程编排器。
 */
public record BusinessProcessApprovalResultEvent(
        Long tenantId,
        String processInstanceId,
        String result) {
}
