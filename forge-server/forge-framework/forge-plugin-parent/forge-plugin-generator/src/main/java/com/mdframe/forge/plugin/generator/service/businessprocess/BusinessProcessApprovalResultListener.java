package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 审批终态提交后，将结果恢复到对应的应用业务流程运行。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessProcessApprovalResultListener {

    private final BusinessProcessOrchestrator orchestrator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApprovalResult(BusinessProcessApprovalResultEvent event) {
        if (event == null || event.tenantId() == null || event.tenantId() <= 0
                || StringUtils.isAnyBlank(event.processInstanceId(), event.result())) {
            return;
        }
        try {
            TenantContextHolder.executeWithTenant(event.tenantId(), () ->
                    orchestrator.resumeApprovalResult(
                            event.tenantId(), event.processInstanceId(), event.result()));
        } catch (Exception exception) {
            log.error("[业务流程审批回调] 审批终态已提交，但业务流程恢复失败: "
                            + "tenantId={}, processInstanceId={}, result={}",
                    event.tenantId(), event.processInstanceId(), event.result(), exception);
        }
    }
}
