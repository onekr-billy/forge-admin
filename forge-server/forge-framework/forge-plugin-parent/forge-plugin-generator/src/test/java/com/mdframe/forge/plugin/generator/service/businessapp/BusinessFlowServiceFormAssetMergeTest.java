package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFieldVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("business flow form asset catalog merge")
class BusinessFlowServiceFormAssetMergeTest {

    private BusinessFlowService service;
    private BusinessFieldDesignService fieldDesignService;
    private Method appendUniqueFormAssets;
    private Method appendObjectFieldRegistryFallback;
    private Method buildObjectFieldRegistryFormSchema;
    private Method resolveRuntimeBusinessFormRef;
    private Method resolveBusinessTaskFormAsset;
    private Method mergeRequestedFlowVariables;
    private BusinessApplicationService applicationService;

    @BeforeEach
    void setUp() throws Exception {
        fieldDesignService = mock(BusinessFieldDesignService.class);
        service = new BusinessFlowService(
                mock(BusinessBindingMapper.class),
                mock(BusinessFlowInstanceLinkMapper.class),
                mock(AiCrudConfigMapper.class),
                mock(BusinessObjectMapper.class),
                mock(BusinessDocumentConfigService.class),
                mock(BusinessDocumentRuntimeService.class),
                mock(DynamicCrudService.class),
                fieldDesignService,
                mock(BusinessFlowVariableResolver.class),
                mock(BusinessCodeFormProviderRegistry.class),
                mock(ApplicationEventPublisher.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));
        applicationService = mock(BusinessApplicationService.class);
        Field applicationField = BusinessFlowService.class.getDeclaredField("businessApplicationService");
        applicationField.setAccessible(true);
        applicationField.set(service, applicationService);
        appendUniqueFormAssets = BusinessFlowService.class.getDeclaredMethod(
                "appendUniqueFormAssets", List.class, List.class);
        appendUniqueFormAssets.setAccessible(true);
        appendObjectFieldRegistryFallback = BusinessFlowService.class.getDeclaredMethod(
                "appendObjectFieldRegistryFallback", List.class, BusinessObjectVO.class);
        appendObjectFieldRegistryFallback.setAccessible(true);
        buildObjectFieldRegistryFormSchema = BusinessFlowService.class.getDeclaredMethod(
                "buildObjectFieldRegistryFormSchema", BusinessObjectVO.class, String.class);
        buildObjectFieldRegistryFormSchema.setAccessible(true);
        resolveRuntimeBusinessFormRef = BusinessFlowService.class.getDeclaredMethod(
                "resolveRuntimeBusinessFormRef", Map.class);
        resolveRuntimeBusinessFormRef.setAccessible(true);
        resolveBusinessTaskFormAsset = BusinessFlowService.class.getDeclaredMethod(
                "resolveBusinessTaskFormAsset", String.class, String.class);
        resolveBusinessTaskFormAsset.setAccessible(true);
        mergeRequestedFlowVariables = BusinessFlowService.class.getDeclaredMethod(
                "mergeRequestedFlowVariables", Map.class, Map.class);
        mergeRequestedFlowVariables.setAccessible(true);
    }

    @Test
    @DisplayName("application form assets keep the concrete page identity and schema")
    @SuppressWarnings("unchecked")
    void applicationPageFormAssetIsConcrete() {
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setId(10L);
        application.setOptions("""
                {"inAppBuilder":{
                  "nodes":[{"id":"page_page","type":"page","pageType":"form","pageName":"打卡申请",
                    "objectRef":{"objectCode":"business_object","objectName":"打卡","configKey":"business_object"}}],
                  "pages":{"page_page":{"children":[{"type":"form","props":{"formAssetId":"asset_1"}}]}},
                  "formAssets":[{"id":"asset_1","formName":"打卡申请表",
                    "formDesignerSchema":{"components":[{"type":"input","fieldBinding":{"fieldCode":"employeeName"},"props":{"label":"员工姓名"}}]}}]
                }}
                """);
        when(applicationService.detail(10L)).thenReturn(application);

        Map<String, Object> catalog = service.getFormAssets("business_object", true, 10L);
        List<Map<String, Object>> assets = (List<Map<String, Object>>) catalog.get("formAssets");

        assertEquals(1, assets.size());
        Map<String, Object> asset = assets.get(0);
        assertEquals("10", asset.get("applicationId"));
        assertEquals("page_page", asset.get("pageId"));
        assertEquals("打卡申请", asset.get("pageName"));
        assertEquals("app_10_page_page_page_form_asset_1", asset.get("formKey"));
        assertEquals(1, asset.get("fieldCount"));
        assertEquals("employeeName", ((List<Map<String, Object>>) asset.get("fields")).get(0).get("field"));
    }

    @Test
    @DisplayName("historical object CRUD pages use their only application form asset")
    @SuppressWarnings("unchecked")
    void historicalObjectPageUsesOnlyFormAsset() {
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setId(10L);
        application.setOptions("""
                {"inAppBuilder":{
                  "nodes":[{"id":"page_page","type":"page","title":"测试","pageType":"object",
                    "objectRef":{"objectCode":"business_object","objectName":"测试","configKey":"business_object"}}],
                  "pages":{"page_page":{"layout":{"gridLayout":{"items":[{"type":"AiCrudPage","props":{"api":"/ai/crud/business_object"}}]}}}},
                  "formAssets":[{"id":"form_form","name":"测试",
                    "formDesignerSchema":{"components":[
                      {"componentKey":"input","fieldBinding":{"fieldCode":"fieldInput"},"props":{"label":"输入框"}},
                      {"componentKey":"number","fieldBinding":{"fieldCode":"fieldNumber"},"props":{"label":"数字"}}
                    ]}}]
                }}
                """);
        when(applicationService.detail(10L)).thenReturn(application);

        Map<String, Object> catalog = service.getFormAssets("business_object", true, 10L);
        List<Map<String, Object>> assets = (List<Map<String, Object>>) catalog.get("formAssets");

        assertEquals(1, assets.size());
        assertEquals("app_10_page_page_page_form_form_form", assets.get(0).get("formKey"));
        assertEquals("page_page", assets.get(0).get("pageId"));
        assertEquals(2, assets.get(0).get("fieldCount"));
        assertEquals(List.of("fieldInput", "fieldNumber"),
                ((List<Map<String, Object>>) assets.get(0).get("fields")).stream()
                        .map(item -> item.get("field")).toList());
    }

    @Test
    @DisplayName("task runtime accepts the concrete application page selected by the outer business process")
    @SuppressWarnings("unchecked")
    void taskRuntimeReadsSelectedApplicationPageForm() throws Exception {
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setId(10L);
        application.setOptions("""
                {"inAppBuilder":{
                  "nodes":[{"id":"page_page","type":"page","pageType":"form","pageName":"打卡申请",
                    "objectRef":{"objectCode":"business_object","objectName":"打卡","configKey":"business_object"}}],
                  "pages":{"page_page":{"children":[{"type":"form","props":{"formAssetId":"asset_1"}}]}},
                  "formAssets":[{"id":"asset_1","formName":"打卡申请表",
                    "formDesignerSchema":{"components":[{"type":"input","fieldBinding":{"fieldCode":"employeeName"},"props":{"label":"员工姓名"}}]}}]
                }}
                """);
        when(applicationService.detail(10L)).thenReturn(application);
        String formKey = "app_10_page_page_page_form_asset_1";
        Map<String, Object> formInfo = Map.of("variables", Map.of(
                "formKey", formKey,
                "businessFormKey", formKey,
                "businessFormRef", Map.of(
                        "formKey", formKey,
                        "applicationId", "10",
                        "pageId", "page_page",
                        "pageName", "打卡申请")));

        Map<String, Object> runtimeRef = (Map<String, Object>) resolveRuntimeBusinessFormRef.invoke(service, formInfo);
        Map<String, Object> resolved = (Map<String, Object>) resolveBusinessTaskFormAsset.invoke(
                service, "business_object", runtimeRef.get("formKey"));

        assertEquals(formKey, runtimeRef.get("formKey"));
        assertEquals("page_page", runtimeRef.get("pageId"));
        assertEquals("打卡申请", resolved.get("pageName"));
        assertEquals(1, ((List<?>) resolved.get("fields")).size());
    }

    @Test
    @DisplayName("a duplicate runtime asset enriches an empty design-time field catalog")
    void duplicateRuntimeAssetEnrichesEmptyCatalog() throws Exception {
        Map<String, Object> designAsset = asset("purchase_form", "采购申请单", List.of());
        Map<String, Object> runtimeAsset = asset("purchase_form", "运行态表单", List.of(
                field("orderNo", "采购单号"),
                field("amountCent", "采购金额"),
                field("flowStatus", "流程状态")));
        runtimeAsset.put("configKey", "purchase_runtime");
        runtimeAsset.put("supportsSave", true);
        List<Map<String, Object>> target = new ArrayList<>(List.of(designAsset));

        appendUniqueFormAssets.invoke(service, target, List.of(runtimeAsset));

        assertEquals(1, target.size());
        Map<String, Object> merged = target.get(0);
        assertEquals("采购申请单", merged.get("formName"));
        assertEquals(3, merged.get("fieldCount"));
        assertEquals(3, ((List<?>) merged.get("fieldCatalog")).size());
        assertEquals(3, ((List<?>) merged.get("fields")).size());
        assertEquals("purchase_runtime", merged.get("configKey"));
        assertEquals(true, merged.get("supportsSave"));
        assertFalse(((List<?>) merged.get("fieldPreview")).isEmpty());
    }

    @Test
    @DisplayName("different form keys remain separate assets")
    void differentFormKeysRemainSeparate() throws Exception {
        List<Map<String, Object>> target = new ArrayList<>(List.of(
                asset("purchase_form", "采购申请单", List.of(field("orderNo", "采购单号")))));

        appendUniqueFormAssets.invoke(service, target, List.of(
                asset("invoice_form", "发票表单", List.of(field("invoiceNo", "发票号")))));

        assertEquals(2, target.size());
        assertTrue(target.stream().anyMatch(item -> "purchase_form".equals(item.get("formKey"))));
        assertTrue(target.stream().anyMatch(item -> "invoice_form".equals(item.get("formKey"))));
    }

    @Test
    @DisplayName("the governed object field registry provides a real fallback form catalog")
    @SuppressWarnings("unchecked")
    void objectFieldRegistryProvidesFallbackFormCatalog() throws Exception {
        BusinessObjectVO object = new BusinessObjectVO();
        object.setId(1001L);
        object.setObjectCode("attendance");
        object.setObjectName("打卡");
        BusinessFieldVO employee = new BusinessFieldVO();
        employee.setFieldCode("employeeName");
        employee.setFieldName("员工姓名");
        employee.setComponentType("input");
        employee.setFormVisible(true);
        BusinessFieldVO flowStatus = new BusinessFieldVO();
        flowStatus.setFieldCode("flowStatus");
        flowStatus.setFieldName("流程状态");
        flowStatus.setComponentType("select");
        flowStatus.setFormVisible(true);
        flowStatus.setReadonly(true);
        org.mockito.Mockito.when(fieldDesignService.listFields(1001L))
                .thenReturn(List.of(employee, flowStatus));
        Method collectFallback = BusinessFlowService.class.getDeclaredMethod(
                "collectObjectFieldRegistryFormAssets", BusinessObjectVO.class);
        collectFallback.setAccessible(true);

        List<Map<String, Object>> assets = (List<Map<String, Object>>) collectFallback.invoke(service, object);

        assertEquals(1, assets.size());
        assertEquals("attendance", assets.get(0).get("formKey"));
        assertEquals("打卡表单", assets.get(0).get("formName"));
        assertEquals(2, assets.get(0).get("fieldCount"));
        assertEquals(2, ((List<?>) assets.get(0).get("fieldCatalog")).size());
    }

    @Test
    @DisplayName("an empty existing object form is enriched without changing its form identity")
    void emptyExistingObjectFormIsEnriched() throws Exception {
        BusinessObjectVO object = new BusinessObjectVO();
        object.setId(1001L);
        object.setObjectCode("attendance");
        object.setObjectName("打卡");
        BusinessFieldVO employee = new BusinessFieldVO();
        employee.setFieldCode("employeeName");
        employee.setFieldName("员工姓名");
        employee.setFormVisible(true);
        org.mockito.Mockito.when(fieldDesignService.listFields(1001L)).thenReturn(List.of(employee));
        Map<String, Object> existing = asset("attendance", "定制打卡表单", List.of());
        List<Map<String, Object>> assets = new ArrayList<>(List.of(existing));

        appendObjectFieldRegistryFallback.invoke(service, assets, object);

        assertEquals(1, assets.size());
        assertEquals("attendance", assets.get(0).get("formKey"));
        assertEquals("定制打卡表单", assets.get(0).get("formName"));
        assertEquals(1, assets.get(0).get("fieldCount"));
    }

    @Test
    @DisplayName("the fallback object form schema exposes registered fields to task form runtime")
    @SuppressWarnings("unchecked")
    void fallbackObjectFormSchemaExposesFields() throws Exception {
        BusinessObjectVO object = new BusinessObjectVO();
        object.setId(1001L);
        object.setObjectCode("attendance");
        object.setObjectName("打卡");
        BusinessFieldVO employee = new BusinessFieldVO();
        employee.setFieldCode("employeeName");
        employee.setFieldName("员工姓名");
        employee.setComponentType("input");
        employee.setFormVisible(true);
        org.mockito.Mockito.when(fieldDesignService.listFields(1001L)).thenReturn(List.of(employee));

        Map<String, Object> schema = (Map<String, Object>) buildObjectFieldRegistryFormSchema
                .invoke(service, object, "attendance");

        assertEquals("attendance", schema.get("formKey"));
        assertEquals(1, ((List<?>) schema.get("components")).size());
        assertEquals("employeeName", ((Map<?, ?>) ((List<?>) schema.get("components")).get(0)).get("field"));
    }

    @Test
    @DisplayName("requested start variables cannot override server-owned business context")
    void requestedVariablesCannotOverrideServerOwnedContext() {
        Map<String, Object> target = new LinkedHashMap<>(Map.of("businessKey", "order:100"));
        Map<String, Object> requested = Map.of(
                "objectCode", "forged_object",
                "businessKey", "forged:999");

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> mergeRequestedFlowVariables.invoke(service, target, requested));

        assertTrue(exception.getCause() instanceof BusinessException);
        assertEquals("启动变量不能覆盖服务端业务上下文：businessKey, objectCode",
                exception.getCause().getMessage());
        assertEquals(Map.of("businessKey", "order:100"), target);
    }

    @Test
    @DisplayName("requested start variables may include initiator-selected approvers")
    void requestedVariablesMayIncludeInitiatorSelectedApprovers() throws Exception {
        Map<String, Object> target = new LinkedHashMap<>(Map.of("businessKey", "order:100"));
        Map<String, Object> selectedApprovers = Map.of("managerApprove", List.of("101", "102"));

        mergeRequestedFlowVariables.invoke(service, target, Map.of(
                "PROCESS_START_USER", selectedApprovers,
                "urgent", true));

        assertEquals(selectedApprovers, target.get("PROCESS_START_USER"));
        assertEquals(true, target.get("urgent"));
        assertEquals("order:100", target.get("businessKey"));
    }

    private Map<String, Object> asset(String formKey, String formName, List<Map<String, Object>> fields) {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("type", "BUSINESS_OBJECT_FORM");
        asset.put("formMode", "BUSINESS_OBJECT_FORM");
        asset.put("formKey", formKey);
        asset.put("formName", formName);
        asset.put("fieldCatalog", new ArrayList<>(fields));
        asset.put("fields", new ArrayList<>(fields));
        asset.put("fieldCount", fields.size());
        asset.put("fieldPreview", fields.stream().map(item -> String.valueOf(item.get("fieldName"))).toList());
        return asset;
    }

    private Map<String, Object> field(String fieldCode, String fieldName) {
        return new LinkedHashMap<>(Map.of("fieldCode", fieldCode, "fieldName", fieldName));
    }
}
