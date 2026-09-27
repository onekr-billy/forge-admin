package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowBind;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowCallbackInbox;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/** Flow 事件入口：先可靠入箱，再在租约与原租户上下文中调用业务状态机。 */
@Slf4j
@Component
@RequiredArgsConstructor
@FlowBind(modelKey = "*", businessType = "lowcode-business")
public class BusinessFlowEngineEventConsumer {

    private final BusinessFlowCallbackInboxService inboxService;
    private final BusinessFlowService businessFlowService;
    private final String workerId = UUID.randomUUID().toString();

    @FlowCallback(on = {
            FlowCallback.ON_TASK_CREATED,
            FlowCallback.ON_TASK_COMPLETED,
            FlowCallback.ON_COMPLETED,
            FlowCallback.ON_REJECTED,
            FlowCallback.ON_CANCELED
    })
    public void onFlowEvent(FlowEventContext event) {
        if (!BusinessFlowCallbackInboxEnvelope.hasReliableIdentity(event)) {
            log.warn("Flow 回调缺少 eventId/version/sequence，按兼容路径直接消费: event={}, processInstanceId={}",
                    event == null ? null : event.getEvent(),
                    event == null ? null : event.getProcessInstanceId());
            businessFlowService.handleFlowEngineEvent(event);
            return;
        }
        process(inboxService.enqueue(event));
    }

    void process(AiBusinessFlowCallbackInbox candidate) {
        AiBusinessFlowCallbackInbox claimed = inboxService.claim(candidate, workerId, LocalDateTime.now());
        if (claimed == null) {
            return;
        }
        try {
            FlowEventContext event = inboxService.restore(claimed);
            if (!inboxService.isStale(claimed)) {
                TenantContextHolder.executeWithTenant(event.getTenantId(),
                        () -> businessFlowService.handleFlowEngineEvent(event));
            }
            inboxService.markCompleted(claimed);
        } catch (RuntimeException failure) {
            try {
                inboxService.markFailed(claimed, failure);
            } catch (RuntimeException stateFailure) {
                log.error("流程回调消费失败且无法更新 Inbox 状态: inboxId={}, failureType={}",
                        claimed.getId(), stateFailure.getClass().getSimpleName());
            }
            log.warn("流程回调消费失败: inboxId={}, eventId={}, failureType={}",
                    claimed.getId(), claimed.getEventId(), failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
