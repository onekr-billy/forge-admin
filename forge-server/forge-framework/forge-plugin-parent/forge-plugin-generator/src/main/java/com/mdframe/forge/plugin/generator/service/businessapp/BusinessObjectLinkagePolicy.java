package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.LinkageSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单字段联动策略与运行时元数据翻译器。
 *
 * <p>采用 Policy + Translator 模式兼容旧 linkageSchema，并生成字段 cascade 元数据。</p>
 */
final class BusinessObjectLinkagePolicy {

    private static final String LINKAGE_SCHEMA_OPTION_KEY = "linkageSchema";
    private static final String LINKAGE_SCHEMA_MANAGED_BY = "linkageSchema";

    private final ObjectMapper objectMapper;

    BusinessObjectLinkagePolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    LinkageSchemaDTO resolveLinkageSchema(Map<String, Object> designerOptions) {
        if (designerOptions != null && designerOptions.containsKey(LINKAGE_SCHEMA_OPTION_KEY)) {
            Object value = designerOptions.get(LINKAGE_SCHEMA_OPTION_KEY);
            try {
                if (value instanceof String text && StringUtils.isNotBlank(text)) {
                    return objectMapper.readValue(text, LinkageSchemaDTO.class);
                }
                if (value != null) {
                    return objectMapper.convertValue(value, LinkageSchemaDTO.class);
                }
            } catch (Exception ignored) {
                return new LinkageSchemaDTO();
            }
        }
        return new LinkageSchemaDTO();
    }

    FormDesignerSchemaDTO hydrateFormFieldLinkages(FormDesignerSchemaDTO formSchema,
                                                            LinkageSchemaDTO legacyLinkageSchema) {
        if (formSchema == null || hasFormFieldLinkages(formSchema)
                || legacyLinkageSchema == null || legacyLinkageSchema.getRules() == null
                || legacyLinkageSchema.getRules().isEmpty()) {
            return formSchema;
        }
        Map<String, Object> settings = new LinkedHashMap<>(formSchema.getSettings() == null
                ? Map.of()
                : formSchema.getSettings());
        Map<String, Object> governance = resolveGovernanceSettings(settings);
        governance.put("fieldLinkages", copyLinkageRules(legacyLinkageSchema.getRules()));
        settings.put("governance", governance);
        formSchema.setSettings(settings);
        return formSchema;
    }

    LinkageSchemaDTO resolveUnifiedLinkageSchema(FormDesignerSchemaDTO formSchema,
                                                          LinkageSchemaDTO legacyLinkageSchema) {
        if (!hasFormFieldLinkages(formSchema)) {
            return legacyLinkageSchema;
        }
        Map<String, Object> governance = resolveGovernanceSettings(formSchema.getSettings());
        Object configuredRules = governance.get("fieldLinkages");
        LinkageSchemaDTO unified = new LinkageSchemaDTO();
        if (legacyLinkageSchema != null) {
            unified.setSchemaVersion(StringUtils.defaultIfBlank(
                    legacyLinkageSchema.getSchemaVersion(), unified.getSchemaVersion()));
            unified.setSettings(new LinkedHashMap<>(legacyLinkageSchema.getSettings() == null
                    ? Map.of()
                    : legacyLinkageSchema.getSettings()));
        }
        if (configuredRules instanceof List<?> rules) {
            unified.setRules(objectMapper.convertValue(rules,
                    new TypeReference<List<Map<String, Object>>>() { }));
        }
        return unified;
    }

    boolean hasFormFieldLinkages(FormDesignerSchemaDTO formSchema) {
        if (formSchema == null || formSchema.getSettings() == null) {
            return false;
        }
        Map<String, Object> governance = resolveGovernanceSettings(formSchema.getSettings());
        return governance.containsKey("fieldLinkages")
                && governance.get("fieldLinkages") instanceof List<?>;
    }

    private Map<String, Object> resolveGovernanceSettings(Map<String, Object> settings) {
        if (settings == null) {
            return new LinkedHashMap<>();
        }
        Object governance = settings.get("governance");
        if (governance instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> copyLinkageRules(List<Map<String, Object>> rules) {
        return objectMapper.convertValue(rules, new TypeReference<List<Map<String, Object>>>() { });
    }

    void applyLinkageSchemaToModel(LowcodeModelSchema modelSchema, LinkageSchemaDTO linkageSchema) {
        if (modelSchema == null || modelSchema.getFields() == null || linkageSchema == null) {
            return;
        }
        Map<String, Map<String, Object>> rulesByTarget = new LinkedHashMap<>();
        if (linkageSchema.getRules() != null) {
            for (Map<String, Object> rule : linkageSchema.getRules()) {
                if (rule == null || isFalse(rule.get("enabled"))) {
                    continue;
                }
                String targetField = text(rule.get("targetField"));
                if (StringUtils.isNotBlank(targetField) && !rulesByTarget.containsKey(targetField)) {
                    rulesByTarget.put(targetField, rule);
                }
            }
        }
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null || StringUtils.isBlank(field.getField())) {
                continue;
            }
            Map<String, Object> basicProps = field.getBasicProps() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(field.getBasicProps());
            Map<String, Object> rule = rulesByTarget.get(field.getField());
            if (rule == null) {
                Map<String, Object> cascade = mapValue(basicProps.get("cascade"));
                if (LINKAGE_SCHEMA_MANAGED_BY.equals(text(cascade.get("managedBy")))) {
                    basicProps.remove("cascade");
                }
                field.setBasicProps(basicProps);
                continue;
            }
            Map<String, Object> dictConfig = mapValue(rule.get("dictConfig"));
            Map<String, Object> objectConfig = mapValue(rule.get("objectConfig"));
            String targetDictType = text(dictConfig.get("targetDictType"));
            if (StringUtils.isNotBlank(targetDictType)) {
                field.setDictType(targetDictType);
            }
            String targetObjectCode = text(objectConfig.get("targetObjectCode"));
            if (StringUtils.isNotBlank(targetObjectCode)) {
                field.setReferenceObjectCode(targetObjectCode);
            }
            String displayField = text(objectConfig.get("displayField"));
            if (StringUtils.isNotBlank(displayField)) {
                field.setReferenceDisplayField(displayField);
            }
            basicProps.put("cascade", buildCascadeFromLinkageRule(rule, field));
            field.setBasicProps(basicProps);
        }
    }

    private Map<String, Object> buildCascadeFromLinkageRule(Map<String, Object> rule, LowcodeFieldSchema targetField) {
        String type = StringUtils.defaultIfBlank(text(rule.get("type")), text(rule.get("matchMode")));
        String dataSourceType = StringUtils.defaultIfBlank(text(rule.get("dataSourceType")), resolveLinkageDataSourceType(type));
        Map<String, Object> dictConfig = mapValue(rule.get("dictConfig"));
        Map<String, Object> remoteConfig = mapValue(rule.get("remoteConfig"));
        Map<String, Object> objectConfig = mapValue(rule.get("objectConfig"));
        Map<String, Object> orgConfig = mapValue(rule.get("orgConfig"));
        String mode = "dict".equals(dataSourceType) ? StringUtils.defaultIfBlank(text(rule.get("matchMode")), type) : "remoteParam";
        Map<String, Object> cascade = new LinkedHashMap<>();
        cascade.put("enabled", !isFalse(rule.get("enabled")));
        cascade.put("managedBy", LINKAGE_SCHEMA_MANAGED_BY);
        cascade.put("ruleId", text(rule.get("ruleId")));
        cascade.put("sourceField", text(rule.get("sourceField")));
        cascade.put("sourceDictType", text(dictConfig.get("sourceDictType")));
        cascade.put("targetDictType", StringUtils.defaultIfBlank(text(dictConfig.get("targetDictType")),
                targetField == null ? null : targetField.getDictType()));
        cascade.put("linkedDictType", StringUtils.firstNonBlank(text(dictConfig.get("linkedDictType")),
                text(dictConfig.get("sourceDictType"))));
        cascade.put("mode", mode);
        cascade.put("matchMode", mode);
        cascade.put("paramName", StringUtils.firstNonBlank(text(remoteConfig.get("paramName")),
                text(orgConfig.get("paramName")), text(rule.get("sourceField"))));
        cascade.put("emptyStrategy", StringUtils.defaultIfBlank(text(rule.get("emptyStrategy")), "empty"));
        cascade.put("clearOnParentChange", !isFalse(rule.get("clearOnSourceChange")));
        cascade.put("clearOnSourceChange", !isFalse(rule.get("clearOnSourceChange")));
        putIfNotBlank(cascade, "url", text(remoteConfig.get("url")));
        putIfNotBlank(cascade, "method", text(remoteConfig.get("method")));
        putIfNotBlank(cascade, "targetObjectCode", StringUtils.defaultIfBlank(text(objectConfig.get("targetObjectCode")),
                targetField == null ? null : targetField.getReferenceObjectCode()));
        putIfNotBlank(cascade, "displayField", StringUtils.defaultIfBlank(text(objectConfig.get("displayField")),
                targetField == null ? null : targetField.getReferenceDisplayField()));
        return cascade;
    }

    private String resolveLinkageDataSourceType(String type) {
        if ("parentDictCode".equals(type) || "linkedDict".equals(type)) {
            return "dict";
        }
        if ("orgScope".equals(type)) {
            return "org";
        }
        if ("objectReference".equals(type)) {
            return "object";
        }
        return "remote";
    }

    private boolean isFalse(Object value) {
        return Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(text(value)) || "0".equals(text(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
