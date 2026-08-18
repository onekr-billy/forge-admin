package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceExecuteDTO;
import com.mdframe.forge.plugin.generator.service.lowcode.query.LowcodeQuerySourceService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CALL_API 业务动作步骤")
class CallApiActionStepExecutorTest {

    private LowcodeQuerySourceService querySourceService;
    private CallApiActionStepExecutor executor;

    @BeforeEach
    void setUp() {
        querySourceService = mock(LowcodeQuerySourceService.class);
        executor = new CallApiActionStepExecutor(querySourceService);
    }

    @Test
    @DisplayName("从受控动作上下文构建参数并写入声明的结果目标")
    void mapsControlledParamsAndResults() {
        BusinessActionExecutionContext context = context();
        BusinessActionStepDTO step = step(Map.of(
                "sourceType", "EXTERNAL_API",
                "sourceKey", "inventory/deduct",
                "paramMappings", List.of(
                        Map.of("param", "sku", "sourceType", "record", "sourceField", "skuCode"),
                        Map.of("param", "quantity", "sourceType", "form", "sourceField", "quantity"),
                        Map.of("param", "scene", "sourceType", "context", "sourceField", "routeQuery.scene"),
                        Map.of("param", "tenant", "sourceType", "system", "sourceField", "tenantId")),
                "resultMode", "ROOT",
                "resultMappings", List.of(
                        Map.of("from", "resultCode", "to", "inventoryResult", "target", "STEP_CONTEXT"),
                        Map.of("from", "remaining", "to", "remainingQuantity", "target", "FORM_DATA")),
                "failureStrategy", "THROW"));
        when(querySourceService.execute(any())).thenReturn(LowcodeQuerySourceResultVO.builder()
                .sourceType("EXTERNAL_API")
                .sourceKey("inventory/deduct")
                .sourceId(12L)
                .data(Map.of("resultCode", "OK", "remaining", 7))
                .fields(List.of())
                .build());

        BusinessActionStepResultVO result = executor.execute(context, step);

        ArgumentCaptor<LowcodeQuerySourceExecuteDTO> captor = ArgumentCaptor.forClass(
                LowcodeQuerySourceExecuteDTO.class);
        verify(querySourceService).execute(captor.capture());
        assertEquals(Map.of("sku", "SKU-1", "quantity", 3, "scene", "pickup", "tenant", 1L),
                captor.getValue().getParams());
        assertEquals("OK", ((Map<?, ?>) context.getScopedVariables().get("call_inventory"))
                .get("inventoryResult"));
        assertEquals(7, context.getFormData().get("remainingQuantity"));
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(2, result.getResult().get("mappingCount"));
    }

    @Test
    @DisplayName("结果缺失时按 CLEAR 写空值，KEEP 保留原值")
    void appliesControlledMissingStrategies() {
        BusinessActionExecutionContext context = context();
        context.getFormData().put("keepValue", "old");
        BusinessActionStepDTO step = step(Map.of(
                "sourceType", "EXTERNAL_API",
                "sourceKey", "inventory/query",
                "resultMappings", List.of(
                        Map.of("from", "missing", "to", "clearedValue", "target", "FORM_DATA",
                                "whenMissing", "CLEAR"),
                        Map.of("from", "missing", "to", "keepValue", "target", "FORM_DATA",
                                "whenMissing", "KEEP"))));
        when(querySourceService.execute(any())).thenReturn(LowcodeQuerySourceResultVO.builder()
                .sourceType("EXTERNAL_API")
                .sourceKey("inventory/query")
                .data(Map.of())
                .fields(List.of())
                .build());

        executor.execute(context, step);

        assertNull(context.getFormData().get("clearedValue"));
        assertEquals("old", context.getFormData().get("keepValue"));
    }

    @Test
    @DisplayName("拒绝数据集和任意非外部接口查询源")
    void rejectsNonExternalSources() {
        assertThrows(BusinessException.class, () -> executor.execute(context(), step(Map.of(
                "sourceType", "DATASET",
                "sourceKey", "inventory_dataset"))));
    }

    @Test
    @DisplayName("抛异常策略终止步骤，记录后继续策略返回失败结果")
    void appliesFailureStrategy() {
        when(querySourceService.execute(any())).thenThrow(new BusinessException("外围系统不可用"));

        BusinessException thrown = assertThrows(BusinessException.class, () -> executor.execute(
                context(), step(Map.of(
                        "sourceType", "EXTERNAL_API",
                        "sourceKey", "inventory/deduct",
                        "failureStrategy", "THROW"))));
        assertEquals("外围系统不可用", thrown.getMessage());

        BusinessActionStepResultVO continued = executor.execute(context(), step(Map.of(
                "sourceType", "EXTERNAL_API",
                "sourceKey", "inventory/deduct",
                "failureStrategy", "LOG_AND_CONTINUE")));
        assertEquals("FAILED", continued.getStatus());
        assertEquals("外部接口调用失败，已继续后续步骤", continued.getMessage());
        assertEquals("外围系统不可用", continued.getErrorMessage());
    }

    private BusinessActionExecutionContext context() {
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        context.setTenantId(1L);
        context.setCorrelationId("corr-1");
        context.setRecordData(new LinkedHashMap<>(Map.of("skuCode", "SKU-1")));
        context.setFormData(new LinkedHashMap<>(Map.of("quantity", 3)));
        context.setExtraContext(new LinkedHashMap<>(Map.of("routeQuery", Map.of("scene", "pickup"))));
        context.setSystemContext(new LinkedHashMap<>(Map.of("tenantId", 1L)));
        return context;
    }

    private BusinessActionStepDTO step(Map<String, Object> config) {
        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepCode("call_inventory");
        step.setStepName("调用库存接口");
        step.setStepType("CALL_API");
        step.setRollbackOnFailure(!"LOG_AND_CONTINUE".equals(config.get("failureStrategy")));
        step.setStepConfig(new LinkedHashMap<>(config));
        return step;
    }
}
