package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCodeAppFormAssetMergerTest {

    @Test
    void configuredDisplayOverridesProviderAndNonPublicFieldsStayHidden() {
        List<Map<String, Object>> providerAssets = List.of(Map.of(
                "formKey", "purchase_form", "formName", "Provider 表单", "supportsSave", true,
                "fields", List.of(
                        Map.of("field", "name", "label", "原名称"),
                        Map.of("field", "secret", "label", "内部字段"))));
        JSONObject metadata = JSON.parseObject("""
                {"fields":[
                  {"field":"name","label":"显示名称"},
                  {"field":"secret","visible":false},
                  {"field":"extra","label":"附加字段"}
                ],"formAssets":[{"formKey":"purchase_form","formName":"配置表单","supportsSave":false}]}
                """);

        List<Map<String, Object>> assets = BusinessCodeAppFormAssetMerger.mergeCodeAppAssets(
                "purchase", providerAssets, metadata, false);

        assertEquals(1, assets.size());
        Map<String, Object> asset = assets.get(0);
        assertEquals("配置表单", asset.get("formName"));
        assertEquals(false, asset.get("supportsSave"));
        assertEquals("purchase", asset.get("objectCode"));
        assertEquals(true, asset.get("metadataConfigured"));
        assertEquals(List.of("显示名称", "附加字段"), asset.get("fieldPreview"));
        assertEquals(2, asset.get("fieldCount"));
        assertEquals("Provider 表单", providerAssets.get(0).get("formName"));
    }

    @Test
    void removedAssetKeysAndConfiguredOnlyAssetKeepIdentityAndOrder() {
        List<Map<String, Object>> providerAssets = List.of(
                Map.of("formKey", "removed", "fields", List.of(Map.of("field", "a"))),
                Map.of("providerKey", "provider_keep", "fields", List.of(Map.of("field", "b"))));
        JSONObject metadata = JSON.parseObject("""
                {"removedFormAssetKeys":"form:removed,provider:provider_removed",
                 "formAssets":[
                   {"formKey":"new_form","fields":[{"field":"newField","label":"新字段"}]},
                   {"providerKey":"provider_removed","fields":[{"field":"hidden"}]}
                 ]}
                """);

        List<Map<String, Object>> assets = BusinessCodeAppFormAssetMerger.mergeCodeAppAssets(
                "purchase", providerAssets, metadata, false);

        assertEquals(2, assets.size());
        assertEquals("provider_keep", assets.get(0).get("providerKey"));
        assertEquals("new_form", assets.get(1).get("formKey"));
        assertEquals(List.of("新字段"), assets.get(1).get("fieldPreview"));
        assertEquals(1, assets.get(1).get("fieldCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sanitizeMetadataUsesFirstAssetFieldsAndRespectsInternalFlag() {
        JSONObject metadata = JSON.parseObject("""
                {"formAssets":[{"formKey":"first","fields":[
                  {"fieldCode":"owner","label":"负责人"},
                  {"field":"systemValue","systemField":true}
                ]}]}
                """);

        Map<String, Object> publicMetadata = BusinessCodeAppFormAssetMerger.sanitizeCodeAppMetadata(metadata, false);
        List<?> publicFields = (List<?>) publicMetadata.get("fields");
        List<Map<String, Object>> publicAssets = (List<Map<String, Object>>) publicMetadata.get("formAssets");
        List<Map<String, Object>> publicAssetFields = (List<Map<String, Object>>) publicAssets.get(0).get("fields");
        assertEquals(1, publicFields.size());
        assertEquals("负责人", publicAssetFields.get(0).get("label"));

        Map<String, Object> internalMetadata = BusinessCodeAppFormAssetMerger.sanitizeCodeAppMetadata(metadata, true);
        List<Map<String, Object>> internalAssets = (List<Map<String, Object>>) internalMetadata.get("formAssets");
        assertEquals(2, ((List<?>) internalMetadata.get("fields")).size());
        assertEquals(2, ((List<?>) internalAssets.get(0).get("fields")).size());
    }

    @Test
    void absentMetadataPreservesProviderCatalog() {
        List<Map<String, Object>> providerAssets = List.of(Map.of("formKey", "provider_form"));

        assertEquals(providerAssets, BusinessCodeAppFormAssetMerger.mergeCodeAppAssets(
                "purchase", providerAssets, new JSONObject(), false));
        assertTrue(BusinessCodeAppFormAssetMerger.sanitizeCodeAppMetadata(new JSONObject(), false).isEmpty());
        assertFalse(BusinessCodeAppFormAssetMerger.isPublicCodeAppFormField(Map.of("formVisible", false)));
    }
}
