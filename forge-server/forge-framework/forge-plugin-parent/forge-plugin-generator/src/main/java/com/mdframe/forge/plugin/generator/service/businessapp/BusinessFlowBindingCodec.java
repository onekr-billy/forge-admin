package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowBindingDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowBindingVO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNullableBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeForms;

/** 业务流程绑定的 DTO/JSON 兼容编解码与默认值合成，不读取租户数据或保存绑定。 */
final class BusinessFlowBindingCodec {

    private BusinessFlowBindingCodec() {
    }

    static BusinessFlowBindingDTO toDTO(JSONObject config) {
        JSONObject source = config == null ? new JSONObject() : config;
        BusinessFlowBindingDTO dto = new BusinessFlowBindingDTO();
        dto.setFlowModelKey(resolveFlowModelKey(source));
        dto.setFlowModelName(source.getString("flowModelName"));
        dto.setTitleTemplate(source.getString("titleTemplate"));
        dto.setStartMode(normalizeStartMode(source.getString("startMode")));
        dto.setBusinessBinding(toBusinessBindingDTO(source.getJSONObject("businessBinding")));
        dto.setVariableMapping(normalizeVariableMapping(source.getJSONArray("variableMapping")));
        dto.setNodeForms(normalizeNodeForms(readMapList(source.getJSONArray("nodeForms"))));
        dto.setConditionFlows(readMapList(source.getJSONArray("conditionFlows")));
        dto.setOptions(readOptions(source.getJSONObject("options")));
        return dto;
    }

    static JSONObject normalizeBindingConfig(BusinessFlowBindingDTO dto) {
        JSONObject config = new JSONObject();
        config.put("flowModelKey", StringUtils.trimToNull(dto.getFlowModelKey()));
        config.put("flowModelName", StringUtils.trimToNull(dto.getFlowModelName()));
        config.put("titleTemplate", StringUtils.trimToNull(dto.getTitleTemplate()));
        config.put("startMode", normalizeStartMode(dto.getStartMode()));
        JSONObject businessBinding = normalizeBusinessBinding(dto.getBusinessBinding());
        if (!businessBinding.isEmpty()) {
            config.put("businessBinding", businessBinding);
        }
        JSONArray variableMapping = new JSONArray();
        if (dto.getVariableMapping() != null) {
            for (BusinessFlowBindingDTO.VariableMappingDTO item : dto.getVariableMapping()) {
                if (item == null || StringUtils.isBlank(item.getFormField()) || StringUtils.isBlank(item.getFlowVariable())) {
                    continue;
                }
                JSONObject mapping = new JSONObject();
                mapping.put("formField", item.getFormField().trim());
                mapping.put("flowVariable", item.getFlowVariable().trim());
                mapping.put("label", StringUtils.trimToNull(item.getLabel()));
                variableMapping.add(mapping);
            }
        }
        config.put("variableMapping", variableMapping);
        config.put("nodeForms", normalizeNodeForms(dto.getNodeForms()));
        config.put("conditionFlows", dto.getConditionFlows() == null ? new ArrayList<>() : dto.getConditionFlows());
        config.put("options", dto.getOptions() == null ? new LinkedHashMap<>() : dto.getOptions());
        return config;
    }

    static void ensureBusinessBinding(JSONObject config,
                                       AiCrudConfig runtimeConfig,
                                       AiBusinessDocumentConfig documentConfig) {
        if (config == null) {
            return;
        }
        JSONObject defaults = normalizeBusinessBinding(defaultBusinessBinding(runtimeConfig, documentConfig));
        JSONObject current = config.getJSONObject("businessBinding");
        if (current == null || current.isEmpty()) {
            if (!defaults.isEmpty()) {
                config.put("businessBinding", defaults);
            }
            return;
        }
        mergeBusinessBindingDefaults(current, defaults);
        config.put("businessBinding", current);
    }

    static BusinessFlowBindingDTO.BusinessBindingDTO defaultBusinessBinding(AiCrudConfig runtimeConfig,
                                                                            AiBusinessDocumentConfig documentConfig) {
        BusinessFlowBindingDTO.BusinessBindingDTO binding = new BusinessFlowBindingDTO.BusinessBindingDTO();
        binding.setMode("LOWCODE_OBJECT");
        if (runtimeConfig != null) {
            binding.setTableName(StringUtils.firstNonBlank(runtimeConfig.getRuntimeTableName(), runtimeConfig.getTableName()));
            binding.setPrimaryKeyField(StringUtils.firstNonBlank(
                    runtimeConfig.getPrimaryKeyField(),
                    runtimeConfig.getPrimaryKeyColumn(),
                    "id"));
        } else {
            binding.setPrimaryKeyField("id");
        }
        binding.setTenantField("tenant_id");
        if (documentConfig != null) {
            binding.setStatusField(StringUtils.trimToNull(documentConfig.getStatusField()));
            binding.setOwnerField(StringUtils.trimToNull(documentConfig.getOwnerField()));
        }
        return binding;
    }

    static JSONObject normalizeBusinessBinding(BusinessFlowBindingDTO.BusinessBindingDTO binding) {
        JSONObject result = new JSONObject();
        if (binding == null) {
            return result;
        }
        putText(result, "mode", normalizeBusinessBindingMode(binding.getMode()));
        putText(result, "tableName", binding.getTableName());
        putText(result, "primaryKeyField", binding.getPrimaryKeyField());
        putText(result, "tenantField", binding.getTenantField());
        putText(result, "statusField", binding.getStatusField());
        putText(result, "titleField", binding.getTitleField());
        putText(result, "ownerField", binding.getOwnerField());
        return result;
    }

    static BusinessFlowBindingDTO.BusinessBindingDTO toBusinessBindingDTO(JSONObject source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        BusinessFlowBindingDTO.BusinessBindingDTO binding = new BusinessFlowBindingDTO.BusinessBindingDTO();
        binding.setMode(normalizeBusinessBindingMode(source.getString("mode")));
        binding.setTableName(StringUtils.trimToNull(source.getString("tableName")));
        binding.setPrimaryKeyField(StringUtils.trimToNull(source.getString("primaryKeyField")));
        binding.setTenantField(StringUtils.trimToNull(source.getString("tenantField")));
        binding.setStatusField(StringUtils.trimToNull(source.getString("statusField")));
        binding.setTitleField(StringUtils.trimToNull(source.getString("titleField")));
        binding.setOwnerField(StringUtils.trimToNull(source.getString("ownerField")));
        return binding;
    }

    private static void mergeBusinessBindingDefaults(JSONObject current, JSONObject defaults) {
        if (current == null || defaults == null || defaults.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isBlank(current.getString(key)) && entry.getValue() != null) {
                current.put(key, entry.getValue());
            }
        }
    }

    static String normalizeBusinessBindingMode(String mode) {
        String normalized = StringUtils.defaultIfBlank(mode, "LOWCODE_OBJECT").trim().toUpperCase();
        if ("BUSINESS_TABLE".equals(normalized) || "ADAPTER".equals(normalized)) {
            return normalized;
        }
        return "LOWCODE_OBJECT";
    }

    static void putText(JSONObject target, String key, String value) {
        String text = StringUtils.trimToNull(value);
        if (text != null) {
            target.put(key, text);
        }
    }

    static void putBoolean(JSONObject target, Map<String, Object> source, String key) {
        if (target == null || source == null || !source.containsKey(key)) {
            return;
        }
        Boolean value = readNullableBooleanValue(source.get(key));
        if (value != null) {
            target.put(key, value);
        }
    }

    static String normalizeStartMode(String startMode) {
        String normalized = StringUtils.defaultIfBlank(startMode, "MANUAL").trim().toUpperCase();
        if ("MANUAL_AND_TRIGGER".equals(normalized) || "MANUAL_TRIGGER".equals(normalized) || "BOTH".equals(normalized)) {
            return "BOTH";
        }
        if ("AUTO".equals(normalized) || "AUTOMATIC".equals(normalized)) {
            return "TRIGGER";
        }
        if ("TRIGGER".equals(normalized)) {
            return "TRIGGER";
        }
        return "MANUAL";
    }

    static List<BusinessFlowBindingDTO.VariableMappingDTO> normalizeVariableMapping(JSONArray variableMapping) {
        List<BusinessFlowBindingDTO.VariableMappingDTO> result = new ArrayList<>();
        if (variableMapping == null) {
            return result;
        }
        for (int i = 0; i < variableMapping.size(); i++) {
            JSONObject mapping = variableMapping.getJSONObject(i);
            if (mapping == null) {
                continue;
            }
            String formField = StringUtils.defaultIfBlank(mapping.getString("formField"), mapping.getString("field"));
            String flowVariable = StringUtils.defaultIfBlank(mapping.getString("flowVariable"), mapping.getString("variable"));
            if (StringUtils.isBlank(formField) || StringUtils.isBlank(flowVariable)) {
                continue;
            }
            BusinessFlowBindingDTO.VariableMappingDTO item = new BusinessFlowBindingDTO.VariableMappingDTO();
            item.setFormField(formField.trim());
            item.setFlowVariable(flowVariable.trim());
            item.setLabel(StringUtils.trimToNull(mapping.getString("label")));
            result.add(item);
        }
        return result;
    }

    static JSONObject readBindingConfig(String bindingConfig) {
        if (StringUtils.isBlank(bindingConfig)) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(bindingConfig);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static String resolveFlowModelKey(JSONObject config) {
        if (config == null) {
            return null;
        }
        return StringUtils.firstNonBlank(
                config.getString("flowModelKey"),
                config.getString("flowKey"),
                config.getString("processDefinitionKey"),
                config.getString("modelKey")
        );
    }

    static String resolveBindingName(JSONObject config) {
        return StringUtils.defaultIfBlank(config.getString("flowModelName"), config.getString("flowModelKey") + " 流程");
    }

    static JSONObject toConfigJson(BusinessFlowBindingVO binding) {
        JSONObject config = new JSONObject();
        config.put("flowModelKey", binding.getFlowModelKey());
        config.put("flowModelName", binding.getFlowModelName());
        config.put("titleTemplate", binding.getTitleTemplate());
        config.put("startMode", binding.getStartMode());
        JSONObject businessBinding = normalizeBusinessBinding(binding.getBusinessBinding());
        if (!businessBinding.isEmpty()) {
            config.put("businessBinding", businessBinding);
        }
        config.put("variableMapping", binding.getVariableMapping());
        config.put("nodeForms", binding.getNodeForms());
        config.put("conditionFlows", binding.getConditionFlows());
        config.put("options", binding.getOptions());
        return config;
    }

    static Map<String, Object> readOptions(JSONObject options) {
        if (options == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(options);
    }
}
