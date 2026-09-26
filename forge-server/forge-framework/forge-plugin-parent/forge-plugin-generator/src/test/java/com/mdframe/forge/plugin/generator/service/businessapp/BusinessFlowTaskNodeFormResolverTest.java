package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowTaskNodeFormResolverTest {

    private FlowClient flowClient;
    private AtomicInteger assetCatalogLoads;
    private BusinessFlowTaskNodeFormResolver resolver;

    @BeforeEach
    void setUp() {
        flowClient = mock(FlowClient.class);
        assetCatalogLoads = new AtomicInteger();
        BusinessFlowApplicationPageFormResolver pageFormResolver = new BusinessFlowApplicationPageFormResolver(
                () -> null,
                mock(BusinessObjectMapper.class),
                () -> 1L,
                (key, startedAt) -> { },
                note -> { });
        resolver = new BusinessFlowTaskNodeFormResolver(
                () -> flowClient,
                pageFormResolver,
                new BusinessFlowTaskChildPolicy(),
                objectCode -> {
                    assetCatalogLoads.incrementAndGet();
                    return List.of(Map.of(
                            "formKey", "purchase-form",
                            "formName", "采购申请",
                            "formMode", "BUSINESS_OBJECT_FORM"));
                },
                (key, startedAt) -> { },
                note -> { });
    }

    @Test
    void completeTaskFormMetadataSkipsProcessFormRpc() {
        when(flowClient.getTaskFormInfo("task-1")).thenReturn(FlowResult.success(Map.of(
                "taskDefKey", "approve",
                "formKey", "purchase-form",
                "formFieldPermissions", List.of(Map.of("field", "amount", "writable", true)))));
        BusinessTaskFormContextQueryDTO query = query();

        Map<String, Object> taskFormInfo = resolver.loadTaskFormInfo(query.getTaskId());
        JSONObject nodeForm = resolver.resolveTaskNodeForm(runtime(), query, taskFormInfo);

        assertEquals("purchase-form", nodeForm.getString("formKey"));
        assertEquals("EDITABLE", nodeForm.getString("editMode"));
        assertFalse(nodeForm.getJSONArray("fieldPermissions").isEmpty());
        verify(flowClient, never()).getProcessFormInfo(any(), any(), any(), any(), any());
    }

    @Test
    void processFormIsFallbackWhenTaskMetadataIsIncomplete() {
        when(flowClient.getTaskFormInfo("task-1")).thenReturn(FlowResult.success(Map.of("taskDefKey", "approve")));
        when(flowClient.getProcessFormInfo(null, "purchase:42", "purchase-flow", "task-1", "approve"))
                .thenReturn(FlowResult.success(Map.of(
                        "taskDefKey", "approve",
                        "formKey", "purchase-form")));
        BusinessTaskFormContextQueryDTO query = query();

        JSONObject nodeForm = resolver.resolveTaskNodeForm(
                runtime(), query, resolver.loadTaskFormInfo(query.getTaskId()));

        assertEquals("purchase-form", nodeForm.getString("formKey"));
        verify(flowClient).getProcessFormInfo(null, "purchase:42", "purchase-flow", "task-1", "approve");
    }

    @Test
    void applicationFormKeyNeverFallsBackToObjectAssetScan() {
        JSONObject asset = resolver.resolveBusinessTaskFormAsset("purchase", "app_10_page_form_asset_1");

        assertTrue(asset.isEmpty());
        assertEquals(0, assetCatalogLoads.get());
    }

    @Test
    void structuredRuntimeReferenceTakesPriorityOverLegacyFormKey() {
        JSONObject reference = resolver.resolveRuntimeBusinessFormRef(Map.of("variables", Map.of(
                "formKey", "legacy-form",
                "businessFormKey", "page-form",
                "businessFormRef", Map.of("formKey", "structured-form", "pageId", "page-1"))));

        assertEquals("page-form", reference.getString("formKey"));
        assertEquals("page-1", reference.getString("pageId"));
    }

    private BusinessTaskFormContextQueryDTO query() {
        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId("task-1");
        query.setTaskDefKey("approve");
        query.setBusinessKey("purchase:42");
        query.setProcessDefKey("purchase-flow");
        return query;
    }

    private TaskFormRuntimeContext runtime() {
        JSONObject binding = new JSONObject();
        binding.put("flowModelKey", "purchase-flow");
        return new TaskFormRuntimeContext(
                "purchase", 42L, "purchase:42", "purchase", binding, null, null);
    }
}
