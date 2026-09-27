package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessOrchestrator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 将已持久化的业务事件同步交给具备自身幂等边界的触发器与流程消费者。 */
@Service
@RequiredArgsConstructor
public class BusinessEventDeliveryService {

    private final BusinessTriggerExecutor triggerExecutor;
    private final ObjectProvider<BusinessProcessOrchestrator> processOrchestratorProvider;

    public void deliver(BusinessEvent event) {
        if (!BusinessEventEnvelope.isTrusted(event) || event.getAggregateSequence() == null
                || event.getAggregateSequence() <= 0) {
            throw new BusinessException("拒绝投递缺少可信 Outbox 信封的业务事件");
        }
        TenantContextHolder.executeWithTenant(event.getTenantId(), () -> {
            triggerExecutor.executeTriggers(event);
            BusinessProcessOrchestrator orchestrator = processOrchestratorProvider.getIfAvailable();
            if (orchestrator != null) {
                orchestrator.startEvent(event);
            }
        });
    }
}
