package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 将审批终态恢复到对应的应用业务流程运行。 */
@Component
@RequiredArgsConstructor
public class BusinessProcessApprovalResultListener {

    private final BusinessProcessOrchestrator orchestrator;

    @EventListener
    public void onApprovalResult(BusinessProcessApprovalResultEvent event) {
        if (event == null || event.tenantId() == null || event.tenantId() <= 0
                || StringUtils.isAnyBlank(event.processInstanceId(), event.result())) {
            return;
        }
        TenantContextHolder.executeWithTenant(event.tenantId(), () ->
                orchestrator.resumeApprovalResult(
                        event.tenantId(), event.processInstanceId(), event.result()));
    }
}
