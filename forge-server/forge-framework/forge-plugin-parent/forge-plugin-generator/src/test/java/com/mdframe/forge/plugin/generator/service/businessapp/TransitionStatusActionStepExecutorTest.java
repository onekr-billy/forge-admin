package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransitionStatusActionStepExecutorTest {

    @Test
    void transitionsStatusWithExpectedValueAndAddsAuditSummary() {
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        TransitionStatusActionStepExecutor executor = new TransitionStatusActionStepExecutor(crudService);
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        AiBusinessObject object = new AiBusinessObject();
        object.setConfigKey("pre_sale_order");
        context.setBusinessObject(object);
        BusinessActionExecuteDTO request = new BusinessActionExecuteDTO();
        request.setRecordId("101");
        context.setRequest(request);
        context.setRecordData(Map.of("id", "101", "status", "DRAFT"));

        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepType("TRANSITION_STATUS");
        step.setStepConfig(Map.of(
                "targetRecordIdField", "record.id",
                "statusField", "status",
                "fromValue", "DRAFT",
                "toValue", "SUBMITTED"));

        BusinessActionStepResultVO result = executor.execute(context, step);

        verify(crudService).updateCommandFields(
                "pre_sale_order", "101", Map.of("status", "SUBMITTED"), Map.of("status", "DRAFT"));
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("STATUS_TRANSITION", context.getAuditTransitions().get(0).get("eventType"));
        assertEquals("DRAFT", context.getAuditTransitions().get(0).get("from"));
        assertEquals("SUBMITTED", context.getAuditTransitions().get(0).get("to"));
        assertEquals("SUCCESS", context.getAuditTransitions().get(0).get("outcome"));
    }

    @Test
    void recordsFailedAuditSummaryWhenConditionalUpdateConflicts() {
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        TransitionStatusActionStepExecutor executor = new TransitionStatusActionStepExecutor(crudService);
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        AiBusinessObject object = new AiBusinessObject();
        object.setConfigKey("order");
        context.setBusinessObject(object);
        context.setRecordData(Map.of("id", "101", "status", "DRAFT"));

        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepType("TRANSITION_STATUS");
        step.setStepConfig(Map.of(
                "targetRecordIdField", "record.id",
                "statusField", "status",
                "fromValue", "DRAFT",
                "toValue", "SUBMITTED"));
        doThrow(new BusinessException("记录状态已变化"))
                .when(crudService)
                .updateCommandFields(
                        "order", "101", Map.of("status", "SUBMITTED"), Map.of("status", "DRAFT"));

        assertThrows(BusinessException.class, () -> executor.execute(context, step));

        assertEquals(1, context.getAuditTransitions().size());
        assertEquals("FAILED", context.getAuditTransitions().get(0).get("outcome"));
        assertEquals("DRAFT", context.getAuditTransitions().get(0).get("from"));
        assertEquals("SUBMITTED", context.getAuditTransitions().get(0).get("to"));
    }
}
