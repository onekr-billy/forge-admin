package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.mergeCodeAppAssets;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.sanitizeCodeAppMetadata;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;

/** Catalog and schema resolver for low-code, application-page and code-form assets. */
@Slf4j
final class BusinessFlowFormAssetCatalog {

    private final Supplier<BusinessApplicationService> applicationServiceSupplier;
    private final Supplier<Long> tenantIdSupplier;
    private final BusinessFlowApplicationPageFormResolver applicationPageFormResolver;
    private final BusinessFlowRuntimeContextResolver runtimeContextResolver;
    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessBindingMapper bindingMapper;
    private final BusinessFlowBindingResolver bindingResolver;
    private final BusinessFieldDesignService businessFieldDesignService;
    private final BusinessFlowFormAssetAssembler formAssetAssembler;
    private final BusinessCodeFormProviderRegistry codeFormProviderRegistry;
    private final BusinessFlowTaskFormProfiler profiler;

    BusinessFlowFormAssetCatalog(
            Supplier<BusinessApplicationService> applicationServiceSupplier,
            Supplier<Long> tenantIdSupplier,
            BusinessFlowApplicationPageFormResolver applicationPageFormResolver,
            BusinessFlowRuntimeContextResolver runtimeContextResolver,
            BusinessRuntimeConfigResolver runtimeConfigResolver,
            BusinessObjectMapper businessObjectMapper,
            BusinessBindingMapper bindingMapper,
            BusinessFlowBindingResolver bindingResolver,
            BusinessFieldDesignService businessFieldDesignService,
            BusinessFlowFormAssetAssembler formAssetAssembler,
            BusinessCodeFormProviderRegistry codeFormProviderRegistry,
            BusinessFlowTaskFormProfiler profiler) {
        this.applicationServiceSupplier = applicationServiceSupplier;
        this.tenantIdSupplier = tenantIdSupplier;
        this.applicationPageFormResolver = applicationPageFormResolver;
        this.runtimeContextResolver = runtimeContextResolver;
        this.runtimeConfigResolver = runtimeConfigResolver;
        this.businessObjectMapper = businessObjectMapper;
        this.bindingMapper = bindingMapper;
        this.bindingResolver = bindingResolver;
        this.businessFieldDesignService = businessFieldDesignService;
        this.formAssetAssembler = formAssetAssembler;
        this.codeFormProviderRegistry = codeFormProviderRegistry;
        this.profiler = profiler;
    }

    Map<String, Object> getFormAssets(String objectCode, boolean includeInternal, Long applicationId) {
        Map<String, Object> result = emptyCatalog(objectCode);
        if (StringUtils.isBlank(objectCode)) {
            return result;
        }
        if (applicationId != null && applicationId > 0 && applicationServiceSupplier.get() != null) {
            return getApplicationFormAssets(result, objectCode, includeInternal, applicationId);
        }
        return getObjectFormAssets(result, objectCode, includeInternal);
    }

    Map<String, Object> getCodeAppMetadata(String objectCode) {
        if (StringUtils.isBlank(objectCode)) {
            return Map.of();
        }
        return new LinkedHashMap<>(sanitizeCodeAppMetadata(
                readCodeAppMetadata(tenantIdSupplier.get(), objectCode), true));
    }

    boolean saveCodeAppMetadata(String objectCode, Object metadata) {
        if (StringUtils.isBlank(objectCode) || !(metadata instanceof Map<?, ?> || metadata instanceof JSONObject)) {
            return false;
        }
        Long tenantId = tenantIdSupplier.get();
        AiBusinessBinding binding = bindingResolver.selectForConfig(tenantId, objectCode);
        boolean created = binding == null;
        if (created) {
            binding = new AiBusinessBinding();
            binding.setTenantId(tenantId);
            binding.setTargetType("OBJECT");
            binding.setTargetCode(objectCode);
            binding.setBindingType("FLOW");
            binding.setBindingName(objectCode + "业务表单资产配置");
            binding.setStatus(EnableStatus.ENABLED.getCode());
            binding.setSortOrder(0);
        }
        JSONObject config = readBindingConfig(binding.getBindingConfig());
        JSONObject options = readNestedObject(config.get("options"));
        options.put("codeAppMetadata", readNestedObject(metadata));
        config.put("options", options);
        binding.setBindingConfig(config.toJSONString());
        if (created) {
            bindingMapper.insert(binding);
        } else {
            bindingMapper.updateById(binding);
        }
        return true;
    }

    List<Map<String, Object>> collectTaskFormAssets(String objectCode) {
        if (StringUtils.isBlank(objectCode)) {
            return List.of();
        }
        Long tenantId = tenantIdSupplier.get();
        BusinessObjectVO object = queryBusinessObject(tenantId, objectCode, null);
        List<Map<String, Object>> assets = new ArrayList<>();
        if (object != null) {
            AiCrudConfig runtimeConfig = runtimeConfigResolver.published(tenantId, StringUtils.firstNonBlank(
                    object.getConfigKey(), object.getObjectCode(), objectCode));
            JSONObject formSchema = readNestedObject(
                    readJsonObject(object.getDesignerOptions()).get("formDesignerSchema"));
            assets.addAll(formAssetAssembler.collectBusinessFormAssets(object, formSchema));
            formAssetAssembler.appendUniqueFormAssets(
                    assets, formAssetAssembler.collectRuntimeCrudFormAssets(object, runtimeConfig));
            formAssetAssembler.appendObjectFieldRegistryFallback(assets, object);
            appendCodeAssets(assets, tenantId, object.getObjectCode(), false);
        } else {
            AiCrudConfig runtimeConfig = runtimeConfigResolver.published(tenantId, objectCode);
            formAssetAssembler.appendUniqueFormAssets(
                    assets, formAssetAssembler.collectRuntimeCrudFormAssets(null, runtimeConfig));
            appendCodeAssets(assets, tenantId, objectCode, false);
        }
        formAssetAssembler.appendObjectFieldRegistryFallback(assets, object);
        return assets;
    }

    BusinessObjectVO queryBusinessObject(Long tenantId, String objectCode, String configKey) {
        if (StringUtils.isNotBlank(configKey)) {
            AiBusinessObject byConfigKey = businessObjectMapper.selectByConfigKey(tenantId, configKey);
            if (byConfigKey != null) {
                return toBusinessObjectVO(byConfigKey);
            }
        }
        if (StringUtils.isBlank(objectCode)) {
            return null;
        }
        BusinessObjectQueryDTO query = new BusinessObjectQueryDTO();
        query.setObjectCode(objectCode);
        List<BusinessObjectVO> objects = businessObjectMapper.selectObjectList(tenantId, query);
        return objects == null || objects.isEmpty() ? null : objects.get(0);
    }

    BusinessObjectVO toBusinessObjectVO(AiBusinessObject object) {
        if (object == null) {
            return null;
        }
        BusinessObjectVO vo = new BusinessObjectVO();
        vo.setId(object.getId());
        vo.setSuiteCode(object.getSuiteCode());
        vo.setObjectCode(object.getObjectCode());
        vo.setObjectName(object.getObjectName());
        vo.setObjectType(object.getObjectType());
        vo.setModelId(object.getModelId());
        vo.setModelCode(object.getModelCode());
        vo.setDisplayField(object.getDisplayField());
        vo.setIcon(object.getIcon());
        vo.setDescription(object.getDescription());
        vo.setStatus(object.getStatus());
        vo.setSortOrder(object.getSortOrder());
        vo.setOptions(object.getOptions());
        vo.setDesignStatus(object.getDesignStatus());
        vo.setConfigKey(object.getConfigKey());
        vo.setLastPublishTime(object.getLastPublishTime());
        vo.setLastPublishVersion(object.getLastPublishVersion());
        vo.setDesignerOptions(object.getDesignerOptions());
        return vo;
    }

    JSONObject resolveBusinessFormSchema(
            BusinessObjectVO object, String formKey, String configKey, AiCrudConfig preloadedRuntimeConfig) {
        AiCrudConfig runtimeConfig = preloadedRuntimeConfig != null
                ? preloadedRuntimeConfig : resolveRuntimeConfigForBusinessForm(object, configKey);
        JSONObject applicationSchema = applicationPageFormResolver.resolveApplicationPageFormSchema(formKey);
        JSONObject objectLiveSchema = resolveObjectDesignerFormSchema(object, formKey);
        if (hasRenderableFormComponents(objectLiveSchema)) {
            JSONObject result = JSON.parseObject(JSON.toJSONString(objectLiveSchema));
            if (!applicationSchema.isEmpty()) {
                result.put("formKey", StringUtils.firstNonBlank(
                        StringUtils.trimToNull(applicationSchema.getString("formKey")),
                        StringUtils.trimToNull(result.getString("formKey")),
                        StringUtils.trimToNull(formKey)));
                result.put("formName", StringUtils.firstNonBlank(
                        StringUtils.trimToNull(applicationSchema.getString("formName")),
                        StringUtils.trimToNull(result.getString("formName"))));
            }
            profiler.note("formSchema=objectDesignerLive");
            return result;
        }
        if (!applicationSchema.isEmpty()) {
            profiler.note("formSchema=applicationPage");
            return applicationSchema;
        }
        if (object == null) {
            return formAssetAssembler.buildRuntimeCrudFormSchema(null, runtimeConfig, formKey);
        }
        if (!objectLiveSchema.isEmpty()) {
            return objectLiveSchema;
        }
        JSONObject runtimeSchema = formAssetAssembler.buildRuntimeCrudFormSchema(object, runtimeConfig, formKey);
        return runtimeSchema.isEmpty() ? buildObjectFieldRegistryFormSchema(object, formKey) : runtimeSchema;
    }

    JSONObject readCodeAppMetadata(Long tenantId, String objectCode) {
        if (tenantId == null || StringUtils.isBlank(objectCode)) {
            return new JSONObject();
        }
        AiBusinessBinding binding = bindingResolver.selectForConfig(tenantId, objectCode);
        if (binding == null) {
            return new JSONObject();
        }
        JSONObject options = readNestedObject(readBindingConfig(binding.getBindingConfig()).get("options"));
        return readNestedObject(options.get("codeAppMetadata"));
    }

    private Map<String, Object> getApplicationFormAssets(
            Map<String, Object> result, String objectCode, boolean includeInternal, Long applicationId) {
        String canonicalObjectCode = runtimeContextResolver.resolveCanonicalObjectCode(
                tenantIdSupplier.get(), objectCode);
        Map<String, Object> applicationAssets = applicationPageFormResolver.collectApplicationPageFormAssets(
                applicationId, canonicalObjectCode);
        if (!applicationAssets.isEmpty()) {
            result.putAll(applicationAssets);
        } else {
            result.put("warnings", List.of("当前应用尚未配置属于该业务对象的表单页面，请先在应用中新增表单页面"));
            result.put("objectCode", canonicalObjectCode);
        }
        result.put("providerCatalog", codeFormProviderRegistry.listProviderCatalog(
                canonicalObjectCode, includeInternal));
        return result;
    }

    private Map<String, Object> getObjectFormAssets(
            Map<String, Object> result, String objectCode, boolean includeInternal) {
        Long tenantId = tenantIdSupplier.get();
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> providerCatalog =
                codeFormProviderRegistry.listProviderCatalog(objectCode, includeInternal);
        BusinessObjectQueryDTO query = new BusinessObjectQueryDTO();
        query.setObjectCode(objectCode);
        List<BusinessObjectVO> objects = businessObjectMapper.selectObjectList(tenantId, query);
        if (objects == null || objects.isEmpty()) {
            return getUnregisteredObjectAssets(result, tenantId, objectCode, includeInternal,
                    providerCatalog, warnings);
        }

        BusinessObjectVO object = objects.get(0);
        AiCrudConfig runtimeConfig = runtimeConfigResolver.published(tenantId, StringUtils.firstNonBlank(
                object.getConfigKey(), object.getObjectCode(), objectCode));
        JSONObject formSchema = readNestedObject(
                readJsonObject(object.getDesignerOptions()).get("formDesignerSchema"));
        List<Map<String, Object>> assets = new ArrayList<>(
                formAssetAssembler.collectBusinessFormAssets(object, formSchema));
        formAssetAssembler.appendUniqueFormAssets(
                assets, formAssetAssembler.collectRuntimeCrudFormAssets(object, runtimeConfig));
        formAssetAssembler.appendObjectFieldRegistryFallback(assets, object);
        JSONObject metadata = readCodeAppMetadata(tenantId, object.getObjectCode());
        formAssetAssembler.appendUniqueFormAssets(assets, mergeCodeAppAssets(
                object.getObjectCode(), codeFormProviderRegistry.listAssets(object.getObjectCode(), includeInternal),
                metadata, includeInternal));
        if (assets.isEmpty()) {
            warnings.add("业务对象尚未配置低代码表单资产");
        }
        result.put("objectId", object.getId());
        result.put("objectCode", object.getObjectCode());
        result.put("objectName", object.getObjectName());
        result.put("configKey", runtimeConfig == null ? object.getConfigKey() : runtimeConfig.getConfigKey());
        result.put("formAssets", assets);
        result.put("providerCatalog", providerCatalog);
        result.put("codeAppMetadata", sanitizeCodeAppMetadata(metadata, includeInternal));
        result.put("warnings", warnings);
        return result;
    }

    private Map<String, Object> getUnregisteredObjectAssets(
            Map<String, Object> result,
            Long tenantId,
            String objectCode,
            boolean includeInternal,
            List<Map<String, Object>> providerCatalog,
            List<String> warnings) {
        AiCrudConfig runtimeConfig = runtimeConfigResolver.published(tenantId, objectCode);
        JSONObject metadata = readCodeAppMetadata(tenantId, objectCode);
        List<Map<String, Object>> assets = new ArrayList<>(
                formAssetAssembler.collectRuntimeCrudFormAssets(null, runtimeConfig));
        formAssetAssembler.appendUniqueFormAssets(assets, mergeCodeAppAssets(
                objectCode, codeFormProviderRegistry.listAssets(objectCode, includeInternal), metadata,
                includeInternal));
        if (assets.isEmpty()) {
            warnings.add("业务对象不存在或无权限访问，且未找到代码表单资产: " + objectCode);
        } else if (runtimeConfig == null) {
            warnings.add("当前编码未匹配低代码业务对象，仅显示代码表单资产");
        }
        result.put("formAssets", assets);
        if (runtimeConfig != null) {
            result.put("objectCode", StringUtils.defaultIfBlank(runtimeConfig.getObjectCode(), objectCode));
            result.put("objectName", StringUtils.defaultIfBlank(
                    runtimeConfig.getObjectName(), runtimeConfig.getAppName()));
            result.put("configKey", runtimeConfig.getConfigKey());
        }
        result.put("providerCatalog", providerCatalog);
        result.put("codeAppMetadata", sanitizeCodeAppMetadata(metadata, includeInternal));
        result.put("warnings", warnings);
        return result;
    }

    private void appendCodeAssets(
            List<Map<String, Object>> assets, Long tenantId, String objectCode, boolean includeInternal) {
        JSONObject metadata = readCodeAppMetadata(tenantId, objectCode);
        formAssetAssembler.appendUniqueFormAssets(assets, mergeCodeAppAssets(
                objectCode, codeFormProviderRegistry.listAssets(objectCode, includeInternal),
                metadata, includeInternal));
    }

    private JSONObject resolveObjectDesignerFormSchema(BusinessObjectVO object, String formKey) {
        if (object == null) {
            return new JSONObject();
        }
        JSONObject formSchema = readNestedObject(
                readJsonObject(object.getDesignerOptions()).get("formDesignerSchema"));
        if (formSchema.isEmpty()) {
            return new JSONObject();
        }
        String targetFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(formKey),
                StringUtils.trimToNull(formSchema.getString("defaultFormKey")),
                StringUtils.trimToNull(formSchema.getString("formKey")));
        boolean appPageFormKey = StringUtils.startsWith(StringUtils.trimToEmpty(formKey), "app_");
        JSONObject byForms = formAssetAssembler.findFormSchemaInArray(
                readNestedArray(formSchema.get("forms")), targetFormKey);
        if (!byForms.isEmpty() && !collectBusinessFormFieldCatalog(byForms).isEmpty()) {
            return byForms;
        }
        JSONObject byAssets = formAssetAssembler.findFormSchemaInArray(
                readNestedArray(readNestedObject(formSchema.get("settings")).get("formAssets")), targetFormKey);
        if (!byAssets.isEmpty() && !collectBusinessFormFieldCatalog(byAssets).isEmpty()) {
            return byAssets;
        }
        String rootFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(formSchema.getString("formKey")),
                StringUtils.trimToNull(formSchema.getString("defaultFormKey")));
        return appPageFormKey || StringUtils.isBlank(targetFormKey)
                || StringUtils.equals(targetFormKey, rootFormKey) || hasRenderableFormComponents(formSchema)
                ? formSchema : new JSONObject();
    }

    private boolean hasRenderableFormComponents(JSONObject formSchema) {
        if (formSchema == null || formSchema.isEmpty()) {
            return false;
        }
        if (!readNestedArray(formSchema.get("components")).isEmpty()) {
            return true;
        }
        JSONObject settings = readNestedObject(formSchema.get("settings"));
        return !readNestedArray(settings.get("components")).isEmpty()
                || !collectBusinessFormFieldCatalog(formSchema).isEmpty();
    }

    JSONObject buildObjectFieldRegistryFormSchema(BusinessObjectVO object, String requestedFormKey) {
        if (object == null || object.getId() == null) {
            return new JSONObject();
        }
        try {
            List<Map<String, Object>> sourceFields = new ArrayList<>();
            businessFieldDesignService.listFields(object.getId()).forEach(field -> sourceFields.add(
                    new LinkedHashMap<>(JSON.parseObject(JSON.toJSONString(field), JSONObject.class))));
            List<Map<String, Object>> fields = formAssetAssembler.normalizeRuntimeCrudFormFields(sourceFields);
            if (fields.isEmpty()) {
                return new JSONObject();
            }
            String formKey = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(requestedFormKey), object.getObjectCode());
            JSONObject schema = new JSONObject();
            schema.put("formKey", formKey);
            schema.put("defaultFormKey", formKey);
            schema.put("formName", StringUtils.defaultIfBlank(
                    object.getObjectName(), object.getObjectCode()) + "表单");
            JSONArray components = new JSONArray();
            fields.forEach(field -> components.add(formAssetAssembler.toRuntimeCrudFormComponent(field)));
            schema.put("components", components);
            return schema;
        } catch (Exception e) {
            log.debug("读取业务对象字段注册表表单 schema 失败: objectId={}, error={}",
                    object.getId(), e.getMessage());
            return new JSONObject();
        }
    }

    private AiCrudConfig resolveRuntimeConfigForBusinessForm(BusinessObjectVO object, String configKey) {
        String lookupKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(configKey),
                object == null ? null : StringUtils.trimToNull(object.getConfigKey()),
                object == null ? null : StringUtils.trimToNull(object.getObjectCode()));
        return runtimeConfigResolver.published(tenantIdSupplier.get(), lookupKey);
    }

    private Map<String, Object> emptyCatalog(String objectCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectCode", objectCode);
        result.put("formAssets", List.of());
        result.put("providerCatalog", List.of());
        result.put("warnings", List.of());
        return result;
    }
}
