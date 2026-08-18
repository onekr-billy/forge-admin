package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdjustNumberActionStepExecutorTest {

    @Test
    void adjustsMultipleFieldsWithBoundsAndExpectedValues() {
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        AdjustNumberActionStepExecutor executor = new AdjustNumberActionStepExecutor(crudService);
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        AiBusinessObject object = new AiBusinessObject();
        object.setConfigKey("order_item");
        context.setBusinessObject(object);
        BusinessActionExecuteDTO request = new BusinessActionExecuteDTO();
        request.setRecordId("101");
        context.setRequest(request);
        context.setFormData(Map.of("quantity", new BigDecimal("3")));

        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepType("ADJUST_NUMBER");
        step.setStepConfig(Map.of(
                "adjustments", List.of(
                        Map.of("targetField", "pickedQuantity", "sourceType", "form",
                                "sourceField", "quantity", "operator", "ADD", "min", 0),
                        Map.of("targetField", "pendingQuantity", "sourceType", "form",
                                "sourceField", "quantity", "operator", "SUBTRACT", "min", 0)
                ),
                "expectedValues", Map.of("status", "ACTIVE")
        ));

        executor.execute(context, step);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, BigDecimal>> deltaCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, BigDecimal>> minCaptor = ArgumentCaptor.forClass(Map.class);
        verify(crudService).adjustCommandNumbers(eq("order_item"), eq("101"), deltaCaptor.capture(),
                minCaptor.capture(), eq(Map.of()), eq(Map.of("status", "ACTIVE")));
        assertEquals(new BigDecimal("3"), deltaCaptor.getValue().get("pickedQuantity"));
        assertEquals(new BigDecimal("-3"), deltaCaptor.getValue().get("pendingQuantity"));
        assertEquals(BigDecimal.ZERO, minCaptor.getValue().get("pendingQuantity"));
    }
}

