package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowBindingDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowBindingCodecTest {

    @Test
    void saveConfigTrimsFieldsFiltersInvalidMappingsAndKeepsNodeOrder() {
        BusinessFlowBindingDTO dto = new BusinessFlowBindingDTO();
        dto.setFlowModelKey("  approval  ");
        dto.setStartMode("manual_trigger");
        BusinessFlowBindingDTO.VariableMappingDTO valid = new BusinessFlowBindingDTO.VariableMappingDTO();
        valid.setFormField(" amount ");
        valid.setFlowVariable(" amountCent ");
        BusinessFlowBindingDTO.VariableMappingDTO invalid = new BusinessFlowBindingDTO.VariableMappingDTO();
        invalid.setFormField("amount");
        dto.setVariableMapping(List.of(valid, invalid));
        dto.setNodeForms(List.of(Map.of("taskDefKey", "approve", "editMode", "editable"),
                Map.of("taskDefKey", "approve", "editMode", "readonly")));

        JSONObject config = BusinessFlowBindingCodec.normalizeBindingConfig(dto);

        assertEquals("approval", config.getString("flowModelKey"));
        assertEquals("BOTH", config.getString("startMode"));
        assertEquals(1, config.getJSONArray("variableMapping").size());
        assertEquals("amount", config.getJSONArray("variableMapping").getJSONObject(0).getString("formField"));
        assertEquals(1, config.getJSONArray("nodeForms").size());
        assertEquals("EDITABLE", config.getJSONArray("nodeForms").getJSONObject(0).getString("editMode"));
    }

    @Test
    void legacyConfigKeysAndVariableAliasesNormalizeOnRead() {
        JSONObject legacy = JSONObject.parseObject("""
                {"processDefinitionKey":"legacy_approval","startMode":"AUTOMATIC",
                 "variableMapping":[{"field":"amount","variable":"amountCent"},
                                    {"field":"missing"}],
                 "businessBinding":{"mode":"adapter","primaryKeyField":" id "},
                 "options":{"source":"legacy"}}
                """);

        BusinessFlowBindingDTO dto = BusinessFlowBindingCodec.toDTO(legacy);

        assertEquals("legacy_approval", dto.getFlowModelKey());
        assertEquals("TRIGGER", dto.getStartMode());
        assertEquals("ADAPTER", dto.getBusinessBinding().getMode());
        assertEquals("id", dto.getBusinessBinding().getPrimaryKeyField());
        assertEquals(1, dto.getVariableMapping().size());
        assertEquals("amountCent", dto.getVariableMapping().get(0).getFlowVariable());
        assertEquals("legacy", dto.getOptions().get("source"));
        assertNotSame(legacy.getJSONObject("options"), dto.getOptions());
    }

    @Test
    void defaultBindingFillsMissingValuesWithoutReplacingExplicitValues() {
        AiCrudConfig runtime = new AiCrudConfig();
        runtime.setRuntimeTableName("purchase_order");
        runtime.setPrimaryKeyField("purchaseId");
        AiBusinessDocumentConfig document = new AiBusinessDocumentConfig();
        document.setStatusField("approvalStatus");
        document.setOwnerField("applicantId");
        JSONObject config = JSONObject.parseObject("""
                {"businessBinding":{"mode":"BUSINESS_TABLE","tableName":"custom_order","statusField":"customStatus"}}
                """);

        BusinessFlowBindingCodec.ensureBusinessBinding(config, runtime, document);

        JSONObject binding = config.getJSONObject("businessBinding");
        assertEquals("BUSINESS_TABLE", binding.getString("mode"));
        assertEquals("custom_order", binding.getString("tableName"));
        assertEquals("customStatus", binding.getString("statusField"));
        assertEquals("purchaseId", binding.getString("primaryKeyField"));
        assertEquals("tenant_id", binding.getString("tenantField"));
        assertEquals("applicantId", binding.getString("ownerField"));
    }

    @Test
    void emptyBindingUsesRuntimeDefaultsAndMalformedJsonFailsClosed() {
        AiCrudConfig runtime = new AiCrudConfig();
        runtime.setTableName("fallback_table");
        JSONObject config = new JSONObject();

        BusinessFlowBindingCodec.ensureBusinessBinding(config, runtime, null);

        assertEquals("fallback_table", config.getJSONObject("businessBinding").getString("tableName"));
        assertEquals("id", config.getJSONObject("businessBinding").getString("primaryKeyField"));
        assertTrue(BusinessFlowBindingCodec.readBindingConfig("not json").isEmpty());
        assertEquals("LOWCODE_OBJECT", BusinessFlowBindingCodec.normalizeBusinessBindingMode("invalid"));
    }

    @Test
    void readOptionsCopiesInputAndNullableFlagsRemainUnchanged() {
        JSONObject options = JSONObject.parseObject("{\"flag\":true}");
        Map<String, Object> copy = BusinessFlowBindingCodec.readOptions(options);
        copy.put("flag", false);
        JSONObject target = new JSONObject();

        BusinessFlowBindingCodec.putBoolean(target, Map.of("allowApprove", "yes"), "allowApprove");
        BusinessFlowBindingCodec.putBoolean(target, Map.of("allowReject", ""), "allowReject");

        assertEquals(true, options.getBoolean("flag"));
        assertEquals(true, target.getBoolean("allowApprove"));
        assertFalse(target.containsKey("allowReject"));
        assertTrue(BusinessFlowBindingCodec.normalizeVariableMapping(new JSONArray()).isEmpty());
    }
}
