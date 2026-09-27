package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("业务触发器恢复调度")
class BusinessTriggerRecoveryDispatcherTest {

    @Test
    @DisplayName("幂等动作按原命令快照恢复并重建租户上下文")
    void replaysSafeCommandUnderEventTenant() {
        BusinessTriggerService service = mock(BusinessTriggerService.class);
        BusinessTriggerExecutor executor = mock(BusinessTriggerExecutor.class);
        BusinessTriggerRecoveryDispatcher dispatcher = new BusinessTriggerRecoveryDispatcher(service, executor);
        AiBusinessTriggerLog claimed = claimed("SEND_MESSAGE");
        when(service.claimRecovery(eq(claimed), anyString(), any(LocalDateTime.class))).thenReturn(claimed);

        dispatcher.recover(claimed);

        verify(executor).executeRecoveredTrigger(any(AiBusinessTrigger.class), any(BusinessEvent.class), eq(claimed));
        verify(service, never()).markManualReview(eq(claimed), anyString());
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("结果不确定的副作用进入人工处理而不自动重放")
    void routesUnsafeCommandToManualReview() {
        BusinessTriggerService service = mock(BusinessTriggerService.class);
        BusinessTriggerExecutor executor = mock(BusinessTriggerExecutor.class);
        BusinessTriggerRecoveryDispatcher dispatcher = new BusinessTriggerRecoveryDispatcher(service, executor);
        AiBusinessTriggerLog claimed = claimed("CREATE_RECORD");
        when(service.claimRecovery(eq(claimed), anyString(), any(LocalDateTime.class))).thenReturn(claimed);

        dispatcher.recover(claimed);

        verify(service).markManualReview(eq(claimed), anyString());
        verify(executor, never()).executeRecoveredTrigger(any(), any(), any());
    }

    @Test
    @DisplayName("摘要不一致的恢复命令进入人工处理")
    void rejectsTamperedRecoveryCommand() {
        BusinessTriggerService service = mock(BusinessTriggerService.class);
        BusinessTriggerExecutor executor = mock(BusinessTriggerExecutor.class);
        BusinessTriggerRecoveryDispatcher dispatcher = new BusinessTriggerRecoveryDispatcher(service, executor);
        AiBusinessTriggerLog claimed = claimed("UPDATE_FIELD");
        claimed.setExecutionDigest("0".repeat(64));
        when(service.claimRecovery(eq(claimed), anyString(), any(LocalDateTime.class))).thenReturn(claimed);

        dispatcher.recover(claimed);

        verify(service).markManualReview(eq(claimed), anyString());
        verify(executor, never()).executeRecoveredTrigger(any(), any(), any());
    }

    private AiBusinessTriggerLog claimed(String actionType) {
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("测试触发器");
        trigger.setActionType(actionType);
        trigger.setActionConfig("{}");
        BusinessEvent event = BusinessEventEnvelope.stamp(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_UPDATED)
                .objectCode("presale_order")
                .recordId("100")
                .recordData(Map.of("status", "READY"))
                .tenantId(1L)
                .build(), BusinessEventEnvelope.SOURCE_DYNAMIC_CRUD, "record:100:update:1");
        AiBusinessTriggerLog log = new AiBusinessTriggerLog();
        log.setId(99L);
        log.setTenantId(1L);
        log.setTriggerId(10L);
        log.setObjectCode("presale_order");
        log.setEventType(event.getEventType());
        log.setEventId(event.getEventId());
        log.setEventSource(event.getEventSource());
        log.setEventVersion(event.getEventVersion());
        log.setEventDigest(event.getEventDigest());
        log.setActionType(actionType);
        log.setRetryCount(2);
        log.setLockOwner("worker");
        BusinessTriggerExecutionEnvelope.snapshot(log, trigger, event);
        return log;
    }
}
