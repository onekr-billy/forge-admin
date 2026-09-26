package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormSaveDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.isPublicCodeAppFormField;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.mergeNonNull;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.normalizeCodeAppMetadataFields;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeFieldPermissions;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeEditMode;

/**
 * 代码业务表单协调器。
 * <p>
 * 封装 Provider 选择、查询/保存调用、节点权限投影和代码应用元数据过滤；
 * 调用方仍持有事务和任务访问校验边界。
 */
final class BusinessFlowCodeFormCoordinator {

    private final BusinessCodeFormProviderRegistry providerRegistry;
    private final BusinessFlowTaskFormPolicy formPolicy;
    private final Function<String, JSONObject> metadataLoader;

    BusinessFlowCodeFormCoordinator(BusinessCodeFormProviderRegistry providerRegistry,
                                    BusinessFlowTaskFormPolicy formPolicy,
                                    Function<String, JSONObject> metadataLoader) {
        this.providerRegistry = providerRegistry;
        this.formPolicy = formPolicy;
        this.metadataLoader = metadataLoader;
    }

    BusinessTaskFormContextVO build(BusinessTaskFormContextQueryDTO query,
                                    JSONObject nodeForm,
                                    String objectCode,
                                    Long recordId,
                                    String businessKey,
                                    String configKey) {
        JSONObject formRef = readNestedObject(nodeForm.get("formRef"));
        String providerKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(nodeForm.getString("providerKey")),
                StringUtils.trimToNull(formRef.getString("providerKey")));
        List<Map<String, Object>> permissions = normalizeFieldPermissions(nodeForm.get("fieldPermissions"));
        BusinessTaskFormContextQueryDTO effectiveQuery = enrichQuery(
                query, nodeForm, formRef, objectCode, recordId, businessKey);
        BusinessTaskFormContextVO fallback = buildFallback(
                effectiveQuery, nodeForm, formRef, permissions, providerKey,
                objectCode, recordId, businessKey, configKey);
        if (StringUtils.isBlank(providerKey)) {
            fallback.getWarnings().add("当前代码表单缺少 providerKey，无法加载业务表单");
            return fallback;
        }
        return providerRegistry.find(providerKey)
                .map(provider -> formPolicy.applyFieldPermissions(
                        applyMetadata(mergeBase(
                                provider.buildContext(effectiveQuery, new LinkedHashMap<>(formRef), permissions),
                                fallback), objectCode),
                        permissions))
                .orElseGet(() -> {
                    fallback.getWarnings().add("代码表单Provider未注册: " + providerKey);
                    return fallback;
                });
    }

    BusinessTaskFormContextVO save(BusinessTaskFormSaveDTO dto, JSONObject nodeForm) {
        JSONObject formRef = readNestedObject(nodeForm.get("formRef"));
        String providerKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(nodeForm.getString("providerKey")),
                StringUtils.trimToNull(formRef.getString("providerKey")));
        if (StringUtils.isBlank(providerKey)) {
            throw new BusinessException("当前代码表单缺少 providerKey，无法保存业务字段");
        }
        List<Map<String, Object>> permissions = normalizeFieldPermissions(nodeForm.get("fieldPermissions"));
        BusinessTaskFormContextVO context = providerRegistry.require(providerKey)
                .saveContext(dto, new LinkedHashMap<>(formRef), permissions);
        return formPolicy.applyFieldPermissions(applyMetadata(context, dto.getObjectCode()), permissions);
    }

    private BusinessTaskFormContextVO buildFallback(BusinessTaskFormContextQueryDTO query,
                                                     JSONObject nodeForm,
                                                     JSONObject formRef,
                                                     List<Map<String, Object>> permissions,
                                                     String providerKey,
                                                     String objectCode,
                                                     Long recordId,
                                                     String businessKey,
                                                     String configKey) {
        BusinessTaskFormContextVO vo = new BusinessTaskFormContextVO();
        vo.setConfigured(true);
        vo.setFormType("business-code");
        vo.setTaskId(StringUtils.trimToNull(query.getTaskId()));
        vo.setBusinessKey(businessKey);
        vo.setProcessInstanceId(StringUtils.trimToNull(query.getProcessInstanceId()));
        vo.setProcessDefKey(StringUtils.trimToNull(query.getProcessDefKey()));
        vo.setTaskDefKey(StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getTaskDefKey()),
                StringUtils.trimToNull(nodeForm.getString("taskDefKey"))));
        vo.setObjectCode(objectCode);
        vo.setBusinessObjectName(StringUtils.firstNonBlank(
                StringUtils.trimToNull(nodeForm.getString("objectName")),
                StringUtils.trimToNull(formRef.getString("objectName")),
                StringUtils.trimToNull(formRef.getString("businessName")),
                objectCode));
        vo.setRecordId(recordId);
        vo.setConfigKey(configKey);
        vo.setFormKey(StringUtils.firstNonBlank(
                StringUtils.trimToNull(nodeForm.getString("formKey")),
                StringUtils.trimToNull(formRef.getString("formKey"))));
        vo.setFormName(StringUtils.trimToNull(nodeForm.getString("formName")));
        vo.setProviderKey(providerKey);
        vo.setFormUrl(StringUtils.firstNonBlank(
                StringUtils.trimToNull(nodeForm.getString("formUrl")),
                StringUtils.trimToNull(formRef.getString("formUrl"))));
        vo.setViewKey(StringUtils.defaultIfBlank(nodeForm.getString("viewKey"), "default"));
        vo.setEditMode(normalizeNodeEditMode(nodeForm.getString("editMode")));
        vo.setFormRef(new LinkedHashMap<>(formRef));
        vo.setFieldPermissions(permissions);
        formPolicy.applyApprovalPolicy(vo, nodeForm);
        return vo;
    }

    private BusinessTaskFormContextQueryDTO enrichQuery(BusinessTaskFormContextQueryDTO query,
                                                         JSONObject nodeForm,
                                                         JSONObject formRef,
                                                         String objectCode,
                                                         Long recordId,
                                                         String businessKey) {
        BusinessTaskFormContextQueryDTO source = query == null ? new BusinessTaskFormContextQueryDTO() : query;
        BusinessTaskFormContextQueryDTO result = new BusinessTaskFormContextQueryDTO();
        result.setTaskId(StringUtils.trimToNull(source.getTaskId()));
        result.setBusinessKey(StringUtils.firstNonBlank(
                StringUtils.trimToNull(source.getBusinessKey()), StringUtils.trimToNull(businessKey)));
        result.setProcessInstanceId(StringUtils.trimToNull(source.getProcessInstanceId()));
        result.setProcessDefKey(StringUtils.firstNonBlank(
                StringUtils.trimToNull(source.getProcessDefKey()),
                StringUtils.trimToNull(nodeForm.getString("processDefKey"))));
        result.setTaskDefKey(StringUtils.firstNonBlank(
                StringUtils.trimToNull(source.getTaskDefKey()),
                StringUtils.trimToNull(nodeForm.getString("taskDefKey"))));
        result.setObjectCode(StringUtils.firstNonBlank(
                StringUtils.trimToNull(source.getObjectCode()), StringUtils.trimToNull(objectCode)));
        result.setRecordId(source.getRecordId() != null ? source.getRecordId() : recordId);
        result.setFormKey(StringUtils.firstNonBlank(
                StringUtils.trimToNull(source.getFormKey()),
                StringUtils.trimToNull(nodeForm.getString("formKey")),
                StringUtils.trimToNull(formRef.getString("formKey"))));
        return result;
    }

    private BusinessTaskFormContextVO mergeBase(BusinessTaskFormContextVO source,
                                                 BusinessTaskFormContextVO fallback) {
        if (source == null) {
            return fallback;
        }
        if (source.getConfigured() == null) {
            source.setConfigured(true);
        }
        if (StringUtils.isBlank(source.getFormType())) {
            source.setFormType("business-code");
        }
        if (StringUtils.isBlank(source.getTaskId())) {
            source.setTaskId(fallback.getTaskId());
        }
        if (StringUtils.isBlank(source.getBusinessKey())) {
            source.setBusinessKey(fallback.getBusinessKey());
        }
        if (StringUtils.isBlank(source.getProcessInstanceId())) {
            source.setProcessInstanceId(fallback.getProcessInstanceId());
        }
        if (StringUtils.isBlank(source.getProcessDefKey())) {
            source.setProcessDefKey(fallback.getProcessDefKey());
        }
        if (StringUtils.isBlank(source.getTaskDefKey())) {
            source.setTaskDefKey(fallback.getTaskDefKey());
        }
        if (StringUtils.isBlank(source.getObjectCode())) {
            source.setObjectCode(fallback.getObjectCode());
        }
        if (StringUtils.isBlank(source.getBusinessObjectName())) {
            source.setBusinessObjectName(fallback.getBusinessObjectName());
        }
        if (StringUtils.isBlank(source.getBusinessSummary())) {
            source.setBusinessSummary(fallback.getBusinessSummary());
        }
        if (source.getRecordId() == null) {
            source.setRecordId(fallback.getRecordId());
        }
        if (StringUtils.isBlank(source.getConfigKey())) {
            source.setConfigKey(fallback.getConfigKey());
        }
        if (StringUtils.isBlank(source.getFormKey())) {
            source.setFormKey(fallback.getFormKey());
        }
        if (StringUtils.isBlank(source.getFormName())) {
            source.setFormName(fallback.getFormName());
        }
        if (StringUtils.isBlank(source.getProviderKey())) {
            source.setProviderKey(fallback.getProviderKey());
        }
        if (StringUtils.isBlank(source.getFormUrl())) {
            source.setFormUrl(fallback.getFormUrl());
        }
        if (StringUtils.isBlank(source.getViewKey())) {
            source.setViewKey(fallback.getViewKey());
        }
        if (StringUtils.isBlank(source.getEditMode())) {
            source.setEditMode(fallback.getEditMode());
        }
        if (source.getFormRef() == null || source.getFormRef().isEmpty()) {
            source.setFormRef(fallback.getFormRef());
        }
        if (source.getFieldPermissions() == null || source.getFieldPermissions().isEmpty()) {
            source.setFieldPermissions(fallback.getFieldPermissions());
        }
        return source;
    }

    private BusinessTaskFormContextVO applyMetadata(BusinessTaskFormContextVO context, String objectCode) {
        if (context == null) {
            return null;
        }
        String code = StringUtils.firstNonBlank(
                StringUtils.trimToNull(objectCode), StringUtils.trimToNull(context.getObjectCode()));
        JSONObject metadata = metadataLoader.apply(code);
        List<Map<String, Object>> configuredFields = normalizeCodeAppMetadataFields(
                metadata == null ? null : metadata.get("fields"), false);
        if (configuredFields.isEmpty() || context.getFields() == null || context.getFields().isEmpty()) {
            return context;
        }
        Map<String, Map<String, Object>> configuredMap = new LinkedHashMap<>();
        for (Map<String, Object> field : configuredFields) {
            String fieldCode = StringUtils.trimToNull(textValue(field.get("field")));
            if (fieldCode != null) {
                formPolicy.putPermissionAliases(configuredMap, fieldCode, field);
            }
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> source : context.getFields()) {
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(source.get("field"))),
                    StringUtils.trimToNull(textValue(source.get("fieldCode"))));
            Map<String, Object> configured = fieldCode == null ? null : configuredMap.get(fieldCode);
            if (configured == null || !isPublicCodeAppFormField(configured)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(source);
            mergeNonNull(item, configured);
            item.put("field", fieldCode);
            item.put("fieldCode", fieldCode);
            filtered.add(item);
        }
        context.setFields(filtered);
        context.setRecordData(formPolicy.filterVisibleRecordData(context.getRecordData(), filtered));
        return context;
    }
}
