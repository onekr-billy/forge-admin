package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("触发器 WEBHOOK 受管调用")
class BusinessTriggerExecutorWebhookTest {

    @Test
    @DisplayName("WEBHOOK 转换为 CALL_API 步骤并记录成功日志")
    void delegatesWebhookToCallApiExecutor() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        CallApiActionStepExecutor callApiExecutor = mock(CallApiActionStepExecutor.class);
        BusinessActionStepResultVO stepResult = new BusinessActionStepResultVO();
        stepResult.setStatus("SUCCESS");
        stepResult.setMessage("外部接口调用完成");
        stepResult.getResult().put("sourceType", "EXTERNAL_API");
        stepResult.getResult().put("sourceKey", "inventory/deduct");
        stepResult.getResult().put("mappingCount", 0);
        when(callApiExecutor.execute(any(), any())).thenReturn(stepResult);
        BusinessTriggerExecutor executor = new BusinessTriggerExecutor(
                triggerService,
                mock(BusinessFlowService.class),
                mock(DynamicCrudService.class),
                mock(BusinessMessageChannelService.class),
                mock(BusinessActionExecutionService.class),
                callApiExecutor);

        executor.executeTrigger(trigger(), event());

        ArgumentCaptor<BusinessActionExecutionContext> contextCaptor = ArgumentCaptor.forClass(
                BusinessActionExecutionContext.class);
        ArgumentCaptor<BusinessActionStepDTO> stepCaptor = ArgumentCaptor.forClass(BusinessActionStepDTO.class);
        verify(callApiExecutor).execute(contextCaptor.capture(), stepCaptor.capture());
        assertEquals("CALL_API", stepCaptor.getValue().getStepType());
        assertEquals("inventory/deduct", stepCaptor.getValue().getStepConfig().get("sourceKey"));
        assertEquals("SKU-1", contextCaptor.getValue().getRecordData().get("skuCode"));

        ArgumentCaptor<AiBusinessTriggerLog> logCaptor = ArgumentCaptor.forClass(AiBusinessTriggerLog.class);
        verify(triggerService).saveExecutionLog(logCaptor.capture());
        assertEquals("SUCCESS", logCaptor.getValue().getExecuteStatus());
        verify(triggerService).incrementExecuteCount(10L);
    }

    @Test
    @DisplayName("记录后继续的外围失败保留 FAILED 触发器状态")
    void recordsContinuedFailureAsFailed() {
        BusinessTriggerService triggerService = mock(BusinessTriggerService.class);
        CallApiActionStepExecutor callApiExecutor = mock(CallApiActionStepExecutor.class);
        BusinessActionStepResultVO stepResult = new BusinessActionStepResultVO();
        stepResult.setStatus("FAILED");
        stepResult.setMessage("外部接口调用失败，已继续后续步骤");
        stepResult.setErrorMessage("外围系统不可用");
        stepResult.getResult().put("sourceType", "EXTERNAL_API");
        stepResult.getResult().put("sourceKey", "inventory/deduct");
        when(callApiExecutor.execute(any(), any())).thenReturn(stepResult);
        BusinessTriggerExecutor executor = new BusinessTriggerExecutor(
                triggerService,
                mock(BusinessFlowService.class),
                mock(DynamicCrudService.class),
                mock(BusinessMessageChannelService.class),
                mock(BusinessActionExecutionService.class),
                callApiExecutor);
        AiBusinessTrigger trigger = trigger();
        trigger.setActionConfig("""
                {"sourceType":"EXTERNAL_API","sourceKey":"inventory/deduct",
                 "failureStrategy":"LOG_AND_CONTINUE"}
                """);

        executor.executeTrigger(trigger, event());

        ArgumentCaptor<AiBusinessTriggerLog> logCaptor = ArgumentCaptor.forClass(AiBusinessTriggerLog.class);
        verify(triggerService).saveExecutionLog(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getExecuteStatus());
        assertEquals("外围系统不可用", logCaptor.getValue().getErrorMessage());
    }

    private AiBusinessTrigger trigger() {
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setId(10L);
        trigger.setTenantId(1L);
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("扣减外围库存");
        trigger.setEventType(BusinessEvent.STATUS_CHANGED);
        trigger.setActionType("WEBHOOK");
        trigger.setActionConfig("""
                {"sourceType":"EXTERNAL_API","sourceKey":"inventory/deduct",
                 "paramMappings":[{"param":"sku","sourceType":"record","sourceField":"skuCode"}],
                 "resultMappings":[],"failureStrategy":"THROW"}
                """);
        return trigger;
    }

    private BusinessEvent event() {
        return BusinessEvent.builder()
                .eventType(BusinessEvent.STATUS_CHANGED)
                .objectCode("presale_order")
                .recordId("100")
                .recordData(Map.of("skuCode", "SKU-1"))
                .operatorId(8L)
                .operatorName("operator")
                .tenantId(1L)
                .build();
    }
}
