package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowStatusTransitionServiceTest {

    private BusinessDocumentConfigService documentConfigService;
    private DynamicCrudService dynamicCrudService;
    private BusinessFlowStatusTransitionService service;

    @BeforeEach
    void setUp() {
        documentConfigService = mock(BusinessDocumentConfigService.class);
        dynamicCrudService = mock(DynamicCrudService.class);
        service = new BusinessFlowStatusTransitionService(documentConfigService, dynamicCrudService);
    }

    @Test
    void documentStatusUsesConfiguredMapping() {
        AiBusinessDocumentConfig document = new AiBusinessDocumentConfig();
        document.setConfigKey("purchase_runtime");
        document.setStatusField("approval_status");
        BusinessDocumentConfigVO view = new BusinessDocumentConfigVO();
        view.setStatusMapping(Map.of("APPROVED", "PASSED"));
        when(documentConfigService.toVO(document)).thenReturn(view);

        service.updateBusinessFlowStatus(document, null, null, 42L, "APPROVED");

        verify(dynamicCrudService).updateInternalFieldsById(
                "purchase_runtime", 42L, Map.of("approval_status", "PASSED"));
    }

    @Test
    void lowCodeBindingUsesBindingStatusMapping() {
        AiCrudConfig runtime = runtime("purchase_runtime", "biz_purchase");
        JSONObject binding = binding("LOWCODE_OBJECT", "biz_purchase", "flow_status");
        binding.getJSONObject("document").put("statusMapping", Map.of("REJECTED", "RETURNED"));

        service.updateBusinessFlowStatus(null, runtime, binding, 42L, "REJECTED");

        verify(dynamicCrudService).updateInternalFieldsById(
                "purchase_runtime", 42L, Map.of("flow_status", "RETURNED"));
    }

    @Test
    void adapterBindingDoesNotWritePlatformRecord() {
        AiCrudConfig runtime = runtime("purchase_runtime", "biz_purchase");

        service.updateBusinessFlowStatus(
                null, runtime, binding("ADAPTER", "external_purchase", "flow_status"), 42L, "APPROVED");

        verify(dynamicCrudService, never()).updateInternalFieldsById(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void mismatchedRuntimeTableIsRejectedBeforeWrite() {
        AiCrudConfig runtime = runtime("purchase_runtime", "biz_purchase");
        JSONObject binding = binding("BUSINESS_TABLE", "other_purchase", "flow_status");

        assertThrows(BusinessException.class,
                () -> service.updateBusinessFlowStatus(null, runtime, binding, 42L, "APPROVED"));
        verify(dynamicCrudService, never()).updateInternalFieldsById(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private AiCrudConfig runtime(String configKey, String tableName) {
        AiCrudConfig runtime = new AiCrudConfig();
        runtime.setConfigKey(configKey);
        runtime.setRuntimeTableName(tableName);
        return runtime;
    }

    private JSONObject binding(String mode, String tableName, String statusField) {
        JSONObject businessBinding = new JSONObject();
        businessBinding.put("mode", mode);
        businessBinding.put("tableName", tableName);
        businessBinding.put("statusField", statusField);
        JSONObject binding = new JSONObject();
        binding.put("businessBinding", businessBinding);
        binding.put("document", new JSONObject());
        return binding;
    }
}
