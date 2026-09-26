package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowFormFieldCatalogTest {

    @Test
    void designerComponentsKeepControlTypeRequiredAndNestedFieldOrder() {
        JSONObject schema = JSON.parseObject("""
                {"components":[
                  {"componentKey":"forgeDictSelect","type":"input","fieldBinding":{"fieldCode":"status","dataType":"string"},
                    "props":{"label":"状态","dictType":"status_dict"},"validation":{"required":true}},
                  {"componentKey":"container","children":[
                    {"type":"input","fieldBinding":{"fieldCode":"memo"},"props":{"title":"备注"}},
                    {"type":"input","fieldBinding":{"fieldCode":"status"}}]}
                ]}
                """);

        List<Map<String, Object>> fields = BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog(schema);

        assertEquals(List.of("status", "memo"), fields.stream().map(field -> field.get("field")).toList());
        assertEquals("dictSelect", fields.get(0).get("type"));
        assertEquals("status_dict", fields.get(0).get("dictType"));
        assertEquals(true, fields.get(0).get("required"));
        assertEquals("备注", fields.get(1).get("label"));
    }

    @Test
    void nestedChildTableColumnsAreDeduplicatedAndKeepRelationMetadata() {
        JSONObject schema = JSON.parseObject("""
                {"components":[{"type":"container","children":[
                  {"componentKey":"subTable","props":{"modelCode":"items","header":"明细","columns":[
                    {"fieldCode":"qty","fieldLabel":"数量"},"price",{"field":"qty"}]}}
                ]}]}
                """);

        List<Map<String, Object>> fields = BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog(schema);

        assertEquals(List.of("qty", "price"), fields.stream().map(field -> field.get("field")).toList());
        assertTrue(fields.stream().allMatch(field -> "child".equals(field.get("scope"))));
        assertEquals("items", fields.get(0).get("childKey"));
        assertEquals("明细", fields.get(0).get("childLabel"));
        assertEquals("数量", fields.get(0).get("label"));
    }

    @Test
    void fallbackFieldCatalogNormalizesAliasesAndPreviewLimit() {
        JSONObject schema = JSON.parseObject("""
                {"fieldCatalog":[
                  {"fieldBinding":{"fieldCode":"first"},"fieldName":"第一项","type":"inputNumber"},
                  {"field":"first","label":"重复项"},
                  {"fieldCode":"second","label":"第二项"}
                ]}
                """);

        List<Map<String, Object>> fields = BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog(schema);
        assertEquals(2, fields.size());
        assertEquals("first", fields.get(0).get("fieldCode"));
        assertEquals("number", fields.get(0).get("type"));
        assertEquals(List.of("第一项", "第二项"), BusinessFlowFormFieldCatalog.buildFieldPreview(fields));

        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            many.add(Map.of("field", "field" + i));
        }
        assertEquals(5, BusinessFlowFormFieldCatalog.buildFieldPreview(many).size());
    }

    @Test
    void weakControlTypeInfersReferenceOrDictionaryWithoutOverridingExplicitType() {
        assertEquals("objectReference", BusinessFlowTaskFormControlTypes.resolveTaskFormControlType(
                Map.of("type", "input", "props", Map.of("optionSource", Map.of("sourceType", "BUSINESS_OBJECT")))));
        assertEquals("dictSelect", BusinessFlowTaskFormControlTypes.resolveTaskFormControlType(
                Map.of("type", "input", "props", Map.of("options", List.of(), "dictType", "status"))));
        assertEquals("date", BusinessFlowTaskFormControlTypes.resolveTaskFormControlType(
                Map.of("componentKey", "forgeDate", "type", "input")));
        assertEquals("orgTreeSelect", BusinessFlowTaskFormControlTypes.normalizeTaskFormFieldType("forgeDeptSelect"));
        assertFalse(BusinessFlowTaskFormControlTypes.isWeakTaskFormControlType("date"));
    }
}
