package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AssertRecordActionStepExecutorTest {

    @Test
    void locksAndChecksTheTargetRecordWithConfiguredExpectedValues() {
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        AssertRecordActionStepExecutor executor = new AssertRecordActionStepExecutor(crudService);
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        AiBusinessObject object = new AiBusinessObject();
        object.setConfigKey("pre_sale_order");
        context.setBusinessObject(object);
        context.setRecordData(Map.of("id", "101", "status", "DRAFT"));

        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepType("ASSERT_RECORD");
        step.setStepConfig(Map.of(
                "targetRecordIdField", "record.id",
                "expectedFieldMappings", List.of(Map.of(
                        "targetField", "status",
                        "value", "DRAFT"))));

        BusinessActionStepResultVO result = executor.execute(context, step);

        verify(crudService).assertCommandRecord("pre_sale_order", "101", Map.of("status", "DRAFT"));
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("记录状态门禁通过", result.getMessage());
    }

    @Test
    void resolvesFormValueForNumericRecordConstraint() {
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        AssertRecordActionStepExecutor executor = new AssertRecordActionStepExecutor(crudService);
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        AiBusinessObject object = new AiBusinessObject();
        object.setConfigKey("pre_sale_item");
        context.setBusinessObject(object);
        context.setRecordData(Map.of("id", "201"));
        context.setFormData(Map.of("quantity", 3));

        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepType("ASSERT_RECORD");
        step.setStepConfig(Map.of(
                "targetRecordIdField", "record.id",
                "numericConstraints", List.of(Map.of(
                        "field", "pickedQuantity",
                        "operator", "gte",
                        "sourceType", "form",
                        "sourceField", "quantity"))));

        executor.execute(context, step);

        verify(crudService).assertCommandRecord(
                "pre_sale_item",
                "201",
                Map.of(),
                List.of(Map.of("field", "pickedQuantity", "operator", "gte", "value", 3)));
    }
}
