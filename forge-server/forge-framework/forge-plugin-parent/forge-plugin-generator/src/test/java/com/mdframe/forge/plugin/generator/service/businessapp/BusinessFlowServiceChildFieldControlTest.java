package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFieldVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("审批子表字段控件类型补齐")
class BusinessFlowServiceChildFieldControlTest {

    private BusinessFlowService service;
    private BusinessFieldDesignService fieldDesignService;
    private BusinessObjectMapper businessObjectMapper;
    private Method mergeFormDesignerChildrenWithPublished;

    @BeforeEach
    void setUp() throws Exception {
        fieldDesignService = mock(BusinessFieldDesignService.class);
        businessObjectMapper = mock(BusinessObjectMapper.class);
        service = new BusinessFlowService(
                mock(BusinessBindingMapper.class),
                mock(BusinessFlowInstanceLinkMapper.class),
                mock(AiCrudConfigMapper.class),
                businessObjectMapper,
                mock(BusinessDocumentConfigService.class),
                mock(BusinessDocumentRuntimeService.class),
                mock(DynamicCrudService.class),
                fieldDesignService,
                mock(BusinessFlowVariableResolver.class),
                mock(BusinessCodeFormProviderRegistry.class),
                mock(ApplicationEventPublisher.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));
        mergeFormDesignerChildrenWithPublished = BusinessFlowService.class.getDeclaredMethod(
                "mergeFormDesignerChildrenWithPublished", List.class, Map.class);
        mergeFormDesignerChildrenWithPublished.setAccessible(true);

        AiBusinessObject childObject = new AiBusinessObject();
        childObject.setId(20L);
        childObject.setObjectCode("order_item");
        when(businessObjectMapper.selectFirstByObjectCode(any(), eq("order_item"))).thenReturn(childObject);
        when(fieldDesignService.listFields(20L)).thenReturn(List.of(
                registryField("status", "dictSelect", "order_item_status"),
                registryField("handler", "userSelect", null),
                registryField("remark", "input", null)));
    }

    @Test
    @DisplayName("发布态没有该子表时，按子表对象字段注册表补齐控件类型")
    void standaloneChildUsesObjectRegistryControls() throws Exception {
        Map<String, Object> formChild = designerChild(List.of(
                column("status", "状态"), column("handler", "处理人"), column("remark", "备注")));

        List<Map<String, Object>> result = merge(List.of(formChild), Map.of());

        List<Map<String, Object>> fields = fieldsOf(result.get(0));
        assertEquals(List.of("status", "handler", "remark"), fields.stream().map(f -> f.get("field")).toList());
        assertEquals("dictSelect", fields.get(0).get("type"));
        assertEquals("order_item_status", fields.get(0).get("dictType"));
        assertEquals("userSelect", fields.get(1).get("type"));
        assertEquals("input", fields.get(2).get("type"));
    }

    @Test
    @DisplayName("发布态缺列或类型为 input 时补齐，发布态已有强类型时保留")
    void publishedChildKeepsStrongTypeAndFillsWeakOnes() throws Exception {
        Map<String, Object> formChild = designerChild(List.of(
                column("status", "状态"), column("handler", "处理人"), column("remark", "备注")));
        Map<String, Object> published = new LinkedHashMap<>();
        published.put("key", "order_item");
        published.put("modelCode", "order_item");
        published.put("fields", List.of(
                publishedField("status", "select"),
                publishedField("handler", "input")));

        List<Map<String, Object>> result = merge(List.of(formChild), Map.of("order_item", published));

        List<Map<String, Object>> fields = fieldsOf(result.get(0));
        assertEquals("select", fields.get(0).get("type"));
        assertEquals("userSelect", fields.get(1).get("type"));
        assertEquals("input", fields.get(2).get("type"));
    }

    @Test
    @DisplayName("主表引用字段从对象注册表补引用配置，但不改设计器控件类型")
    void mainFieldsFillReferenceMetadataWithoutOverridingDesignerType() throws Exception {
        BusinessFieldVO customer = registryField("customerId", "objectReference", null);
        customer.setReferenceObjectCode("customer");
        customer.setReferenceDisplayField("customerName");
        when(fieldDesignService.listFields(10L)).thenReturn(List.of(
                customer, registryField("level", "dictSelect", "customer_level")));
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("field", "customerId");
        reference.put("type", "objectReference");
        reference.put("componentKey", "objectReference");
        reference.put("props", new LinkedHashMap<>(Map.of("placeholder", "请选择客户")));
        Map<String, Object> plainInput = new LinkedHashMap<>();
        plainInput.put("field", "level");
        plainInput.put("type", "input");
        plainInput.put("componentKey", "input");
        com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO object =
                new com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO();
        object.setId(10L);

        Method enrich = BusinessFlowService.class.getDeclaredMethod("enrichTaskMainFieldsFromObjectRegistry",
                List.class, com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO.class);
        enrich.setAccessible(true);
        enrich.invoke(service, List.of(reference, plainInput), object);

        assertEquals("customer", reference.get("referenceObjectCode"));
        assertEquals("customerName", reference.get("referenceDisplayField"));
        assertEquals("请选择客户", ((Map<?, ?>) reference.get("props")).get("placeholder"));
        assertEquals("input", plainInput.get("type"));
        assertEquals("input", plainInput.get("componentKey"));
        assertEquals(null, plainInput.get("dictType"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> merge(List<Map<String, Object>> formChildren,
                                            Map<String, Map<String, Object>> publishedByKey) throws Exception {
        return (List<Map<String, Object>>) mergeFormDesignerChildrenWithPublished.invoke(
                service, formChildren, publishedByKey);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fieldsOf(Map<String, Object> child) {
        return (List<Map<String, Object>>) child.get("fields");
    }

    private Map<String, Object> designerChild(List<Map<String, Object>> fields) {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("key", "order_item");
        child.put("modelCode", "order_item");
        child.put("relationKey", "order_item");
        child.put("label", "订单明细");
        child.put("fields", fields);
        return child;
    }

    private Map<String, Object> column(String code, String label) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("fieldCode", code);
        column.put("fieldLabel", label);
        column.put("field", code);
        column.put("label", label);
        return column;
    }

    private Map<String, Object> publishedField(String code, String type) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("field", code);
        field.put("label", code);
        field.put("type", type);
        return field;
    }

    private BusinessFieldVO registryField(String code, String componentType, String dictType) {
        BusinessFieldVO field = new BusinessFieldVO();
        field.setFieldCode(code);
        field.setFieldName(code);
        field.setComponentType(componentType);
        field.setDictType(dictType);
        return field;
    }
}
