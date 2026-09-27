package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("business flow application page form resolver")
class BusinessFlowApplicationPageFormResolverTest {

    private BusinessApplicationService applicationService;
    private List<String> notes;
    private BusinessFlowApplicationPageFormResolver resolver;

    @BeforeEach
    void setUp() {
        applicationService = mock(BusinessApplicationService.class);
        notes = new ArrayList<>();
        resolver = new BusinessFlowApplicationPageFormResolver(
                () -> applicationService,
                mock(BusinessObjectMapper.class),
                () -> 1L,
                (name, startedAt) -> { },
                notes::add);
    }

    @Test
    @DisplayName("collects directly referenced page form with stable identity")
    @SuppressWarnings("unchecked")
    void collectsReferencedPageForm() {
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setOptions(JSON.toJSONString(Map.of("inAppBuilder", builder())));
        when(applicationService.detail(10L)).thenReturn(application);

        Map<String, Object> result = resolver.collectApplicationPageFormAssets(10L, "purchase");
        List<Map<String, Object>> assets = (List<Map<String, Object>>) result.get("formAssets");

        assertEquals(1, assets.size());
        assertEquals("app_10_page_page_1_form_asset_1", assets.get(0).get("formKey"));
        assertEquals("purchase", assets.get(0).get("objectCode"));
        assertEquals(1, assets.get(0).get("fieldCount"));
        assertFalse(((Map<?, ?>) assets.get(0).get("schema")).isEmpty());
    }

    @Test
    @DisplayName("single application asset is the legacy CRUD page default")
    @SuppressWarnings("unchecked")
    void singleAssetBackfillsLegacyCrudPage() {
        JSONObject builder = builder();
        builder.getJSONObject("pages").getJSONObject("page_1")
                .put("children", List.of(Map.of("type", "crud", "props", Map.of())));
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setOptions(JSON.toJSONString(Map.of("inAppBuilder", builder)));
        when(applicationService.detail(10L)).thenReturn(application);

        Map<String, Object> result = resolver.collectApplicationPageFormAssets(10L, "purchase");

        assertEquals(1, ((List<Map<String, Object>>) result.get("formAssets")).size());
    }

    @Test
    @DisplayName("stable form key resolves through cache aside and returns schema")
    void stableKeyUsesBuilderCache() {
        when(applicationService.loadInAppBuilder(10L)).thenReturn(builder());
        String formKey = "app_10_page_page_1_form_asset_1";

        JSONObject first = resolver.resolveApplicationPageFormAsset(formKey);
        JSONObject second = resolver.resolveApplicationPageFormAsset(formKey);
        JSONObject schema = resolver.resolveApplicationPageFormSchema(formKey);

        assertEquals(formKey, second.getString("formKey"));
        assertNotSame(first, second);
        assertEquals(formKey, schema.getString("formKey"));
        assertTrue(notes.contains("pageAssetCache=miss"));
        assertTrue(notes.contains("pageAssetCache=hit"));
        verify(applicationService, times(1)).loadInAppBuilder(10L);
    }

    private JSONObject builder() {
        return JSON.parseObject("""
                {
                  "nodes":[{"id":"page_1","type":"page","pageType":"form","pageName":"采购申请",
                    "objectRef":{"objectCode":"purchase","objectName":"采购单","configKey":"purchase"}}],
                  "pages":{"page_1":{"children":[{"type":"form","props":{"formAssetId":"asset_1"}}]}},
                  "formAssets":[{"id":"asset_1","formKey":"purchase_form","formName":"采购表单",
                    "formDesignerSchema":{"components":[{"type":"input","fieldBinding":{"fieldCode":"orderNo"},"props":{"label":"采购单号"}}]}}]
                }
                """);
    }
}
