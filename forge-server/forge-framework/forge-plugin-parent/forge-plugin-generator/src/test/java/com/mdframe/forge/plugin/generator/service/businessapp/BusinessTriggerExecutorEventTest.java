package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("增强事件触发器")
class BusinessTriggerExecutorEventTest {

    @Test
    @DisplayName("异步匹配按事件租户恢复上下文")
    void restoresEventTenantWhenMatchingAsyncTriggers() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        when(triggerService.selectActiveByObjectAndEvent(9L, "presale_order", BusinessEvent.RECORD_CREATED))
                .thenAnswer(invocation -> {
                    assertEquals(9L, TenantContextHolder.getTenantId());
                    return java.util.List.of();
                });
        BusinessTriggerExecutor executor = new BusinessTriggerExecutor(
                triggerService,
                mock(BusinessFlowService.class),
                mock(DynamicCrudService.class),
                mock(BusinessMessageChannelService.class),
                mock(BusinessActionExecutionService.class),
                mock(CallApiActionStepExecutor.class));
        BusinessEvent event = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .tenantId(9L)
                .build());

        executor.executeTriggersAsync(event);

        assertNull(TenantContextHolder.getTenantId());
        verify(triggerService).selectActiveByObjectAndEvent(9L, "presale_order", BusinessEvent.RECORD_CREATED);
    }

    @Test
    void missingEventTenantDoesNotFallBackToDefaultTenant() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        BusinessTriggerExecutor executor = executor(triggerService);
        BusinessEvent event = BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .build();

        executor.executeTriggersAsync(event);

        verify(triggerService, never()).selectActiveByObjectAndEvent(
                org.mockito.ArgumentMatchers.anyLong(), eq("presale_order"), eq(BusinessEvent.RECORD_CREATED));
    }

    @Test
    void unknownConditionOperatorFailsClosed() {
        BusinessTriggerExecutor executor = executor(mock(BusinessTriggerService.class));
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(12L);
        trigger.setTenantId(1L);
        trigger.setEventCondition("{\"field\":\"status\",\"op\":\"execute\",\"value\":\"READY\"}");
        BusinessEvent event = BusinessEvent.builder()
                .tenantId(1L)
                .recordData(Map.of("status", "READY"))
                .build();

        assertEquals(false, executor.matchesCondition(trigger, event));
    }

    @Test
    @DisplayName("主子表新增结果使用 main 字段匹配条件并发起主流程")
    void startsMainFlowForAggregateCreateEvent() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        when(triggerService.tryClaimExecution(any())).thenReturn(true);
        BusinessFlowService flowService = mock(BusinessFlowService.class);
        BusinessFlowRuntimeVO runtime = new BusinessFlowRuntimeVO();
        runtime.setFlowModelKey("presale_approval");
        runtime.setProcessInstanceId("process-instance-1");
        runtime.setFlowStatus("RUNNING");
        when(flowService.startFlowFromTrigger(
                eq(null), eq("presale_order:100"), eq("预售申请 SKU-1"),
                eq(8L), eq("operator"), eq(1L), org.mockito.ArgumentMatchers.any(JSONObject.class)))
                .thenReturn(runtime);
        BusinessTriggerExecutor executor = new BusinessTriggerExecutor(
                triggerService,
                flowService,
                mock(DynamicCrudService.class),
                mock(BusinessMessageChannelService.class),
                mock(BusinessActionExecutionService.class),
                mock(CallApiActionStepExecutor.class));
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("新增后发起主流程");
        trigger.setEventType(BusinessEvent.RECORD_CREATED);
        trigger.setEventCondition("""
                {"rules":[{"field":"approvalStatus","op":"eq","value":"DRAFT"}],"logic":"AND"}
                """);
        trigger.setActionType("START_FLOW");
        trigger.setActionConfig("""
                {"useMainFlow":true,"titleTemplate":"预售申请 ${skuCode}",
                 "variableMapping":[{"formField":"skuCode","flowVariable":"sku"}]}
                """);
        BusinessEvent event = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .recordData(Map.of(
                        "main", Map.of("id", 100L, "approval_status", "DRAFT", "sku_code", "SKU-1"),
                        "children", Map.of()))
                .operatorId(8L)
                .operatorName("operator")
                .tenantId(1L)
                .build());

        executor.executeTrigger(trigger, event);

        ArgumentCaptor<JSONObject> variables = ArgumentCaptor.forClass(JSONObject.class);
        verify(flowService).startFlowFromTrigger(
                eq(null), eq("presale_order:100"), eq("预售申请 SKU-1"),
                eq(8L), eq("operator"), eq(1L), variables.capture());
        assertEquals("SKU-1", variables.getValue().getString("sku"));
        verify(triggerService).incrementExecuteCount(10L);
    }

    @Test
    @DisplayName("同一事件重复投递时只执行一次副作用")
    void duplicateEventExecutesSideEffectOnlyOnce() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        when(triggerService.tryClaimExecution(any())).thenReturn(true, false);
        BusinessFlowService flowService = mock(BusinessFlowService.class);
        BusinessFlowRuntimeVO runtime = new BusinessFlowRuntimeVO();
        runtime.setProcessInstanceId("process-instance-1");
        runtime.setFlowStatus("RUNNING");
        when(flowService.startFlowFromTrigger(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(runtime);
        BusinessTriggerExecutor executor = new BusinessTriggerExecutor(
                triggerService,
                flowService,
                mock(DynamicCrudService.class),
                mock(BusinessMessageChannelService.class),
                mock(BusinessActionExecutionService.class),
                mock(CallApiActionStepExecutor.class));
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("新增后发起流程");
        trigger.setEventType(BusinessEvent.RECORD_CREATED);
        trigger.setActionType("START_FLOW");
        trigger.setActionConfig("{\"flowModelKey\":\"presale_approval\"}");
        BusinessEvent event = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .recordData(Map.of("id", 100L))
                .operatorId(8L)
                .operatorName("operator")
                .tenantId(1L)
                .build());

        executor.executeTrigger(trigger, event);
        executor.executeTrigger(trigger, event);

        verify(triggerService, org.mockito.Mockito.times(2)).tryClaimExecution(any());
        verify(flowService, org.mockito.Mockito.times(1)).startFlowFromTrigger(
                any(), any(), any(), any(), any(), any(), any());
        verify(triggerService, org.mockito.Mockito.times(1)).incrementExecuteCount(10L);
    }

    @Test
    @DisplayName("事件被篡改或版本过期时拒绝执行")
    void rejectsTamperedAndOutdatedEvents() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        BusinessTriggerExecutor executor = executor(triggerService);
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        BusinessEvent tampered = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_UPDATED)
                .objectCode("presale_order")
                .recordId("100")
                .recordData(new java.util.HashMap<>(Map.of("status", "DRAFT")))
                .tenantId(1L)
                .build());
        tampered.getRecordData().put("status", "APPROVED");
        BusinessEvent outdated = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_UPDATED)
                .objectCode("presale_order")
                .recordId("100")
                .tenantId(1L)
                .build());
        outdated.setEventVersion(0);

        executor.executeTrigger(trigger, tampered);
        executor.executeTrigger(trigger, outdated);

        verify(triggerService, never()).tryClaimExecution(any());
    }

    @Test
    @DisplayName("触发器与事件租户不一致时拒绝认领")
    void rejectsCrossTenantTrigger() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        BusinessTriggerExecutor executor = executor(triggerService);
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(2L);
        BusinessEvent event = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .tenantId(1L)
                .build());

        executor.executeTrigger(trigger, event);

        verify(triggerService, never()).tryClaimExecution(any());
    }

    @Test
    @DisplayName("未知动作类型按失败记录而不是伪装成功")
    void unknownActionTypeFailsClosed() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        when(triggerService.tryClaimExecution(any())).thenReturn(true);
        BusinessTriggerExecutor executor = executor(triggerService);
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("未知动作");
        trigger.setActionType("UNSUPPORTED");
        trigger.setActionConfig("{}");
        BusinessEvent event = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .tenantId(1L)
                .build());

        executor.executeTrigger(trigger, event);

        ArgumentCaptor<AiBusinessTriggerLog> logCaptor = ArgumentCaptor.forClass(AiBusinessTriggerLog.class);
        verify(triggerService).updateExecutionLog(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getExecuteStatus());
        verify(triggerService, never()).incrementExecuteCount(10L);
    }

    @Test
    @DisplayName("消息副作用使用事件与触发器组合幂等键")
    void messageActionUsesStableIdempotencyKey() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        when(triggerService.tryClaimExecution(any())).thenReturn(true);
        BusinessMessageChannelService messageService = mock(BusinessMessageChannelService.class);
        BusinessMessageChannelStatus status = new BusinessMessageChannelStatus();
        status.setChannelCode("internal_websocket");
        status.setSendChannel("WEB");
        status.setTodo(false);
        when(messageService.resolveChannel(any())).thenReturn(status);
        SysMessage message = new SysMessage();
        message.setId(88L);
        when(messageService.sendInternalMessage(any())).thenReturn(message);
        BusinessTriggerExecutor executor = new BusinessTriggerExecutor(
                triggerService,
                mock(BusinessFlowService.class),
                mock(DynamicCrudService.class),
                messageService,
                mock(BusinessActionExecutionService.class),
                mock(CallApiActionStepExecutor.class));
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("发送消息");
        trigger.setActionType("SEND_MESSAGE");
        trigger.setActionConfig("{}");
        BusinessEvent event = trusted(BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .operatorId(8L)
                .tenantId(1L)
                .build());

        executor.executeTrigger(trigger, event);

        ArgumentCaptor<MessageSendRequestDTO> request = ArgumentCaptor.forClass(MessageSendRequestDTO.class);
        verify(messageService).sendInternalMessage(request.capture());
        assertEquals("trigger:10:" + event.getEventId() + ":message",
                request.getValue().getIdempotencyKey());
    }

    private BusinessTriggerExecutor executor(BusinessTriggerService triggerService) {
        return new BusinessTriggerExecutor(
                triggerService,
                mock(BusinessFlowService.class),
                mock(DynamicCrudService.class),
                mock(BusinessMessageChannelService.class),
                mock(BusinessActionExecutionService.class),
                mock(CallApiActionStepExecutor.class));
    }

    private BusinessEvent trusted(BusinessEvent event) {
        return BusinessEventEnvelope.stamp(event, BusinessEventEnvelope.SOURCE_DYNAMIC_CRUD);
    }
}
