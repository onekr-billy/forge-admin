package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        BusinessEvent event = BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .tenantId(9L)
                .build();

        executor.executeTriggersAsync(event);

        assertNull(TenantContextHolder.getTenantId());
        verify(triggerService).selectActiveByObjectAndEvent(9L, "presale_order", BusinessEvent.RECORD_CREATED);
    }

    @Test
    @DisplayName("主子表新增结果使用 main 字段匹配条件并发起主流程")
    void startsMainFlowForAggregateCreateEvent() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
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
        BusinessEvent event = BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("presale_order")
                .recordId("100")
                .recordData(Map.of(
                        "main", Map.of("id", 100L, "approval_status", "DRAFT", "sku_code", "SKU-1"),
                        "children", Map.of()))
                .operatorId(8L)
                .operatorName("operator")
                .tenantId(1L)
                .build();

        executor.executeTrigger(trigger, event);

        ArgumentCaptor<JSONObject> variables = ArgumentCaptor.forClass(JSONObject.class);
        verify(flowService).startFlowFromTrigger(
                eq(null), eq("presale_order:100"), eq("预售申请 SKU-1"),
                eq(8L), eq("operator"), eq(1L), variables.capture());
        assertEquals("SKU-1", variables.getValue().getString("sku"));
        verify(triggerService).incrementExecuteCount(10L);
    }
}
