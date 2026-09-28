package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务触发器可恢复命令信封")
class BusinessTriggerExecutionEnvelopeTest {

    @Test
    @DisplayName("完整事件和触发器快照可按原配置恢复")
    void restoresImmutableExecutionSnapshot() {
        AiBusinessTrigger trigger = trigger("SEND_MESSAGE");
        BusinessEvent event = event();
        AiBusinessTriggerLog log = log(trigger, event);

        BusinessTriggerExecutionEnvelope.snapshot(log, trigger, event);
        BusinessTriggerExecutionEnvelope.ExecutionCommand restored =
                BusinessTriggerExecutionEnvelope.restore(log);

        assertEquals("old", restored.event().getPreviousData().get("status"));
        assertEquals("template-a", restored.trigger().getActionConfig());
        assertTrue(BusinessTriggerExecutionEnvelope.isReplaySafe(restored.trigger()));
    }

    @Test
    @DisplayName("快照被修改时拒绝恢复")
    void rejectsTamperedSnapshot() {
        AiBusinessTrigger trigger = trigger("START_FLOW");
        BusinessEvent event = event();
        AiBusinessTriggerLog log = log(trigger, event);
        BusinessTriggerExecutionEnvelope.snapshot(log, trigger, event);
        log.setTriggerSnapshot(log.getTriggerSnapshot().replace("START_FLOW", "CREATE_RECORD"));

        assertThrows(BusinessException.class, () -> BusinessTriggerExecutionEnvelope.restore(log));
    }

    @Test
    @DisplayName("结果不确定的创建记录和 Webhook 不自动重放")
    void identifiesUnsafeReplayActions() {
        assertFalse(BusinessTriggerExecutionEnvelope.isReplaySafe(trigger("CREATE_RECORD")));
        assertFalse(BusinessTriggerExecutionEnvelope.isReplaySafe(trigger("WEBHOOK")));
        assertTrue(BusinessTriggerExecutionEnvelope.isReplaySafe(trigger("UPDATE_FIELD")));
    }

    private AiBusinessTrigger trigger(String actionType) {
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("测试触发器");
        trigger.setActionType(actionType);
        trigger.setActionConfig("SEND_MESSAGE".equals(actionType) ? "template-a" : "{}");
        return trigger;
    }

    private BusinessEvent event() {
        return BusinessEventEnvelope.stamp(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_UPDATED)
                .objectCode("presale_order")
                .configKey("presale-config")
                .recordId("100")
                .recordData(Map.of("status", "new"))
                .previousData(Map.of("status", "old"))
                .operatorId(8L)
                .operatorName("operator")
                .tenantId(1L)
                .build(), BusinessEventEnvelope.SOURCE_DYNAMIC_CRUD, "record:100:update:1");
    }

    private AiBusinessTriggerLog log(AiBusinessTrigger trigger, BusinessEvent event) {
        AiBusinessTriggerLog log = new AiBusinessTriggerLog();
        log.setTenantId(event.getTenantId());
        log.setTriggerId(trigger.getId());
        log.setObjectCode(event.getObjectCode());
        log.setEventType(event.getEventType());
        log.setEventId(event.getEventId());
        log.setEventSource(event.getEventSource());
        log.setEventVersion(event.getEventVersion());
        log.setEventDigest(event.getEventDigest());
        log.setActionType(trigger.getActionType());
        return log;
    }
}
