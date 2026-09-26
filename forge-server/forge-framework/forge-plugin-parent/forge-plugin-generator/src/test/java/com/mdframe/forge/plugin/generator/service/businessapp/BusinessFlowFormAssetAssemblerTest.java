package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFieldVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("business flow form asset assembler")
class BusinessFlowFormAssetAssemblerTest {

    private BusinessFieldDesignService fieldDesignService;
    private BusinessFlowFormAssetAssembler assembler;

    @BeforeEach
    void setUp() {
        fieldDesignService = mock(BusinessFieldDesignService.class);
        assembler = new BusinessFlowFormAssetAssembler(
                fieldDesignService,
                (fields, layout) -> fields,
                (options, fields) -> { });
    }

    @Test
    @DisplayName("runtime field catalog filters internal fields and normalizes component metadata")
    void normalizesRuntimeFields() {
        List<Map<String, Object>> result = assembler.normalizeRuntimeCrudFormFields(List.of(
                Map.of("fieldCode", "order_no", "title", "单号", "componentKey", "input", "required", true),
                Map.of("field", "tenantId", "systemField", true),
                Map.of("field", "secret", "formVisible", false)));

        assertEquals(1, result.size());
        assertEquals("order_no", result.get(0).get("field"));
        assertEquals("单号", result.get(0).get("label"));
        assertEquals("input", result.get(0).get("componentType"));
        assertEquals(true, result.get(0).get("required"));
    }

    @Test
    @DisplayName("runtime schema keeps layout settings and field components")
    void buildsRuntimeSchema() {
        AiCrudConfig config = runtimeConfig();
        config.setOptions("{\"editGridCols\":3,\"editLabelPlacement\":\"top\",\"editLabelWidth\":\"120\"}");
        config.setEditSchema("[{\"field\":\"amount\",\"label\":\"金额\",\"componentType\":\"number\",\"required\":true}]");

        JSONObject schema = assembler.buildRuntimeCrudFormSchema(null, config, "purchase_default_form");

        assertEquals("purchase_default_form", schema.getString("formKey"));
        assertEquals(3, schema.getJSONObject("settings").getJSONObject("layout").getIntValue("gridColumns"));
        assertEquals("top", schema.getJSONObject("settings").getJSONObject("layout").getString("labelPlacement"));
        assertEquals("number", schema.getJSONArray("components").getJSONObject(0).getString("componentType"));
        assertTrue(schema.getJSONArray("components").getJSONObject(0)
                .getJSONObject("validation").getBooleanValue("required"));
    }

    @Test
    @DisplayName("saved designer schema takes priority over generated runtime fallback")
    void designerSchemaTakesPriority() {
        AiCrudConfig config = runtimeConfig();
        config.setOptions("""
                {"formDesignerSchema":{"formKey":"custom_form","formName":"定制表单",
                  "components":[{"type":"input","field":"customField"}]}}
                """);
        config.setEditSchema("[{\"field\":\"fallback\",\"label\":\"回退字段\"}]");

        JSONObject schema = assembler.buildRuntimeCrudFormSchema(null, config, "custom_form");

        assertEquals("定制表单", schema.getString("formName"));
        assertEquals("customField", schema.getJSONArray("components").getJSONObject(0).getString("field"));
    }

    @Test
    @DisplayName("field registry fills an empty asset without replacing its identity")
    void fieldRegistryFallbackEnrichesAsset() {
        BusinessObjectVO object = new BusinessObjectVO();
        object.setId(10L);
        object.setObjectCode("purchase");
        object.setObjectName("采购单");
        BusinessFieldVO field = new BusinessFieldVO();
        field.setFieldCode("orderNo");
        field.setFieldName("单号");
        field.setComponentType("input");
        field.setFormVisible(true);
        when(fieldDesignService.listFields(10L)).thenReturn(List.of(field));
        Map<String, Object> existing = new java.util.LinkedHashMap<>();
        existing.put("type", "BUSINESS_OBJECT_FORM");
        existing.put("formMode", "BUSINESS_OBJECT_FORM");
        existing.put("formKey", "custom_purchase_form");
        existing.put("formName", "自定义采购表");
        existing.put("fieldCatalog", List.of());
        List<Map<String, Object>> assets = new ArrayList<>(List.of(existing));

        assembler.appendObjectFieldRegistryFallback(assets, object);

        assertEquals(1, assets.size());
        assertEquals("custom_purchase_form", assets.get(0).get("formKey"));
        assertEquals("自定义采购表", assets.get(0).get("formName"));
        assertEquals(1, assets.get(0).get("fieldCount"));
        assertFalse(((List<?>) assets.get(0).get("fieldCatalog")).isEmpty());
    }

    private AiCrudConfig runtimeConfig() {
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("purchase_runtime");
        config.setObjectCode("purchase");
        config.setObjectName("采购单");
        return config;
    }
}
