package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.buildBusinessKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseBusinessKeyObjectCode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseBusinessKeyRecordId;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseLongValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeFormMode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskAccessPolicy.isSyntheticTestBusinessKey;

/**
 * Resolves canonical business/runtime identity for list, start and task paths.
 * Task-specific hydration is layered on top of the shared runtime lookup.
 */
final class BusinessFlowRuntimeContextResolver {

    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final BusinessFlowApplicationPageFormResolver applicationPageFormResolver;
    private final BusinessFlowTaskNodeFormResolver taskNodeFormResolver;
    private final Supplier<Long> tenantIdSupplier;
    private final BindingResolver bindingResolver;
    private final BiConsumer<String, Long> stageRecorder;
    private final Consumer<String> detailRecorder;

    BusinessFlowRuntimeContextResolver(
            BusinessRuntimeConfigResolver runtimeConfigResolver,
            BusinessDocumentConfigService documentConfigService,
            BusinessObjectMapper businessObjectMapper,
            BusinessFlowInstanceLinkMapper flowInstanceLinkMapper,
            BusinessFlowApplicationPageFormResolver applicationPageFormResolver,
            BusinessFlowTaskNodeFormResolver taskNodeFormResolver,
            Supplier<Long> tenantIdSupplier,
            BindingResolver bindingResolver,
            BiConsumer<String, Long> stageRecorder,
            Consumer<String> detailRecorder) {
        this.runtimeConfigResolver = runtimeConfigResolver;
        this.documentConfigService = documentConfigService;
        this.businessObjectMapper = businessObjectMapper;
        this.flowInstanceLinkMapper = flowInstanceLinkMapper;
        this.applicationPageFormResolver = applicationPageFormResolver;
        this.taskNodeFormResolver = taskNodeFormResolver;
        this.tenantIdSupplier = tenantIdSupplier;
        this.bindingResolver = bindingResolver;
        this.stageRecorder = stageRecorder;
        this.detailRecorder = detailRecorder;
    }

    TaskFormRuntimeContext resolveTask(BusinessTaskFormContextQueryDTO query, boolean strict) {
        return resolveTask(query, strict, Map.of());
    }

    TaskFormRuntimeContext resolveTask(BusinessTaskFormContextQueryDTO query,
                                       boolean strict,
                                       Map<String, Object> taskFormInfo) {
        Long tenantId = tenantIdSupplier.get();
        long mark = System.nanoTime();
        hydrateTaskFormQuery(query, taskFormInfo);
        stageRecorder.accept("hydrateQueryMs", mark);

        mark = System.nanoTime();
        hydrateApplicationPageFormIdentity(query);
        stageRecorder.accept("pageAssetMs", mark);

        boolean syntheticTestKey = isSyntheticTestBusinessKey(query.getBusinessKey());
        mark = System.nanoTime();
        AiBusinessFlowInstanceLink link = null;
        if (StringUtils.isNotBlank(query.getProcessInstanceId())) {
            link = flowInstanceLinkMapper.selectByProcessInstanceId(tenantId, query.getProcessInstanceId());
            detailRecorder.accept("db:flow_link_by_pi");
        }
        if (link == null && StringUtils.isNotBlank(query.getBusinessKey()) && !syntheticTestKey) {
            link = flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, query.getBusinessKey());
            detailRecorder.accept("db:flow_link_by_bk");
        }
        stageRecorder.accept("flowLinkMs", mark);

        mark = System.nanoTime();
        AiBusinessObject taskObject = resolveTaskBusinessObject(tenantId, query, link);
        stageRecorder.accept("taskObjectMs", mark);

        String objectCode = StringUtils.firstNonBlank(
                taskObject == null ? null : taskObject.getObjectCode(),
                link == null ? null : link.getObjectCode(),
                StringUtils.trimToNull(query.getObjectCode()),
                parseBusinessKeyObjectCode(query.getBusinessKey()));
        Long recordId = link == null || link.getRecordId() == null
                ? query.getRecordId()
                : link.getRecordId();
        if (recordId == null) {
            recordId = parseBusinessKeyRecordId(query.getBusinessKey());
        }
        if (syntheticTestKey && link == null) {
            recordId = null;
        }
        String businessKey = StringUtils.firstNonBlank(
                link == null ? null : link.getBusinessKey(),
                StringUtils.trimToNull(query.getBusinessKey()),
                objectCode != null && recordId != null ? buildBusinessKey(objectCode, recordId) : null);

        if (StringUtils.isBlank(objectCode)) {
            if (strict) {
                throw new BusinessException("未解析到业务对象或记录ID");
            }
            return new TaskFormRuntimeContext(null, null, businessKey, null, null, null, null);
        }

        String runtimeLookupKey = StringUtils.firstNonBlank(
                taskObject == null ? null : taskObject.getConfigKey(),
                StringUtils.trimToNull(query.getConfigKey()),
                objectCode);
        mark = System.nanoTime();
        BusinessRuntimeContext businessContext = resolve(tenantId, runtimeLookupKey);
        stageRecorder.accept("businessContextMs", mark);
        detailRecorder.accept("db:runtime_context(config/object/document)");

        String canonicalObjectCode = StringUtils.firstNonBlank(businessContext.objectCode(), objectCode);
        String configKey = StringUtils.firstNonBlank(
                taskObject == null ? null : taskObject.getConfigKey(),
                StringUtils.trimToNull(query.getConfigKey()),
                businessContext.configKey());
        mark = System.nanoTime();
        AiBusinessBinding binding = bindingResolver.resolve(tenantId, canonicalObjectCode, objectCode);
        stageRecorder.accept("bindingMs", mark);
        detailRecorder.accept("db:flow_binding");
        JSONObject bindingConfig = binding == null ? new JSONObject() : readBindingConfig(binding.getBindingConfig());
        BusinessFlowBindingCodec.ensureBusinessBinding(
                bindingConfig, businessContext.runtimeConfig(), businessContext.documentConfig());

        if (StringUtils.isBlank(configKey) && strict
                && !isBusinessCodeTaskForm(canonicalObjectCode, bindingConfig, query, taskFormInfo)) {
            throw new BusinessException("业务对象缺少已发布运行配置，无法保存待办业务字段");
        }
        AiBusinessObject reusedObject = businessContext.businessObject() != null
                ? businessContext.businessObject()
                : taskObject;
        return new TaskFormRuntimeContext(
                canonicalObjectCode, recordId, businessKey, configKey, bindingConfig,
                businessContext.runtimeConfig(), reusedObject);
    }

    BusinessRuntimeContext resolve(Long tenantId, String objectCodeOrConfigKey) {
        return resolve(tenantId, objectCodeOrConfigKey, false);
    }

    BusinessRuntimeContext resolve(Long tenantId, String objectCodeOrConfigKey, boolean allowDraftRuntime) {
        String requestedObjectCode = StringUtils.trimToNull(objectCodeOrConfigKey);
        if (requestedObjectCode == null) {
            return new BusinessRuntimeContext(null, null, null, null, null, null);
        }
        AiCrudConfig runtimeConfig = runtimeConfigResolver.published(tenantId, requestedObjectCode);
        AiBusinessDocumentConfig documentConfig = resolveEnabledDocumentConfig(
                tenantId, requestedObjectCode, runtimeConfig);
        AiBusinessObject businessObject = resolveBusinessObject(
                tenantId, requestedObjectCode, runtimeConfig, documentConfig);
        String canonicalObjectCode = StringUtils.firstNonBlank(
                documentConfig == null ? null : documentConfig.getObjectCode(),
                businessObject == null ? null : businessObject.getObjectCode(),
                runtimeConfig == null ? null : runtimeConfig.getObjectCode(),
                requestedObjectCode);

        if (documentConfig == null && !StringUtils.equals(canonicalObjectCode, requestedObjectCode)) {
            documentConfig = resolveEnabledDocumentConfig(tenantId, canonicalObjectCode, runtimeConfig);
        }
        if (runtimeConfig == null) {
            runtimeConfig = runtimeConfigResolver.published(tenantId, StringUtils.firstNonBlank(
                    documentConfig == null ? null : documentConfig.getConfigKey(),
                    businessObject == null ? null : businessObject.getConfigKey(),
                    canonicalObjectCode));
        }
        if (runtimeConfig == null && allowDraftRuntime) {
            runtimeConfig = runtimeConfigResolver.runtime(tenantId, StringUtils.firstNonBlank(
                    documentConfig == null ? null : documentConfig.getConfigKey(),
                    businessObject == null ? null : businessObject.getConfigKey(),
                    canonicalObjectCode,
                    requestedObjectCode));
        }
        if (businessObject == null && !StringUtils.equals(canonicalObjectCode, requestedObjectCode)) {
            businessObject = resolveBusinessObject(tenantId, canonicalObjectCode, runtimeConfig, documentConfig);
        }
        String configKey = StringUtils.firstNonBlank(
                documentConfig == null ? null : documentConfig.getConfigKey(),
                runtimeConfig == null ? null : runtimeConfig.getConfigKey(),
                businessObject == null ? null : businessObject.getConfigKey());
        return new BusinessRuntimeContext(
                requestedObjectCode, canonicalObjectCode, configKey,
                documentConfig, runtimeConfig, businessObject);
    }

    String resolveCanonicalObjectCode(Long tenantId, String objectCodeOrConfigKey) {
        if (StringUtils.isBlank(objectCodeOrConfigKey)) {
            return objectCodeOrConfigKey;
        }
        BusinessRuntimeContext context = resolve(tenantId, objectCodeOrConfigKey);
        return StringUtils.firstNonBlank(context.objectCode(), StringUtils.trimToNull(objectCodeOrConfigKey));
    }

    Map<String, Object> loadTaskVariablesAsRecord(BusinessTaskFormContextQueryDTO query) {
        return loadTaskVariablesAsRecord(query, Map.of());
    }

    Map<String, Object> loadTaskVariablesAsRecord(BusinessTaskFormContextQueryDTO query,
                                                   Map<String, Object> preloadedTaskFormInfo) {
        Map<String, Object> formInfo = preloadedTaskFormInfo == null || preloadedTaskFormInfo.isEmpty()
                ? taskNodeFormResolver.loadTaskFormInfo(query == null ? null : query.getTaskId())
                : preloadedTaskFormInfo;
        Object variables = formInfo.get("variables");
        if (!(variables instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    Long extractCreatedRecordId(Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            return null;
        }
        Object id = record.get("id");
        if (id == null) {
            id = record.get("ID");
        }
        return parseLongValue(textValue(id));
    }

    void hydrateTaskFormQuery(BusinessTaskFormContextQueryDTO query, Map<String, Object> preloadedTaskFormInfo) {
        if (query == null || StringUtils.isBlank(query.getTaskId())) {
            return;
        }
        boolean missingIdentity = StringUtils.isBlank(query.getProcessInstanceId())
                || StringUtils.isBlank(query.getBusinessKey())
                || StringUtils.isBlank(query.getObjectCode())
                || query.getObjectId() == null
                || StringUtils.isBlank(query.getConfigKey())
                || query.getRecordId() == null;
        if (!missingIdentity) {
            return;
        }
        Map<String, Object> formInfo = preloadedTaskFormInfo == null || preloadedTaskFormInfo.isEmpty()
                ? taskNodeFormResolver.loadTaskFormInfo(query.getTaskId())
                : preloadedTaskFormInfo;
        if (formInfo == null || formInfo.isEmpty()) {
            return;
        }
        Map<String, Object> formRef = readNestedObject(formInfo.get("formRef"));
        JSONObject variables = readNestedObject(formInfo.get("variables"));
        JSONObject variableFormRef = readNestedObject(variables.get("businessFormRef"));
        if (StringUtils.isBlank(query.getProcessInstanceId())) {
            query.setProcessInstanceId(StringUtils.trimToNull(textValue(formInfo.get("processInstanceId"))));
        }
        if (StringUtils.isBlank(query.getBusinessKey())) {
            query.setBusinessKey(StringUtils.trimToNull(textValue(formInfo.get("businessKey"))));
        }
        if (StringUtils.isBlank(query.getProcessDefKey())) {
            query.setProcessDefKey(StringUtils.trimToNull(textValue(formInfo.get("processDefKey"))));
        }
        if (StringUtils.isBlank(query.getTaskDefKey())) {
            query.setTaskDefKey(StringUtils.trimToNull(textValue(formInfo.get("taskDefKey"))));
        }
        if (StringUtils.isBlank(query.getFormKey())) {
            query.setFormKey(StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(formInfo.get("formKey"))),
                    StringUtils.trimToNull(textValue(formRef.get("formKey")))));
        }
        if (StringUtils.isBlank(query.getObjectCode())) {
            query.setObjectCode(StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(formInfo.get("objectCode"))),
                    StringUtils.trimToNull(textValue(formRef.get("objectCode"))),
                    StringUtils.trimToNull(textValue(variables.get("objectCode"))),
                    StringUtils.trimToNull(textValue(variableFormRef.get("objectCode")))));
        }
        if (query.getObjectId() == null) {
            query.setObjectId(firstLongValue(
                    formInfo.get("objectId"), formInfo.get("businessObjectId"), formRef.get("objectId"),
                    variables.get("objectId"), variables.get("businessObjectId"), variableFormRef.get("objectId")));
        }
        if (StringUtils.isBlank(query.getConfigKey())) {
            query.setConfigKey(StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(formInfo.get("configKey"))),
                    StringUtils.trimToNull(textValue(formRef.get("configKey"))),
                    StringUtils.trimToNull(textValue(variables.get("configKey"))),
                    StringUtils.trimToNull(textValue(variableFormRef.get("configKey")))));
        }
        if (StringUtils.isBlank(query.getSuiteCode())) {
            query.setSuiteCode(StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(formInfo.get("suiteCode"))),
                    StringUtils.trimToNull(textValue(formRef.get("suiteCode"))),
                    StringUtils.trimToNull(textValue(variables.get("suiteCode"))),
                    StringUtils.trimToNull(textValue(variableFormRef.get("suiteCode")))));
        }
        if (query.getRecordId() == null) {
            query.setRecordId(parseLongValue(textValue(formInfo.get("recordId"))));
            if (query.getRecordId() == null) {
                query.setRecordId(parseLongValue(textValue(formRef.get("recordId"))));
            }
        }
    }

    private void hydrateApplicationPageFormIdentity(BusinessTaskFormContextQueryDTO query) {
        if (query == null || StringUtils.isBlank(query.getFormKey())) {
            return;
        }
        JSONObject asset = applicationPageFormResolver.resolveApplicationPageFormAsset(query.getFormKey());
        if (asset == null || asset.isEmpty()) {
            return;
        }
        Long objectId = parseLongValue(asset.getString("objectId"));
        if (query.getObjectId() == null && objectId != null) {
            query.setObjectId(objectId);
        }
        String configKey = StringUtils.trimToNull(asset.getString("configKey"));
        if (StringUtils.isBlank(query.getConfigKey()) && configKey != null) {
            query.setConfigKey(configKey);
        }
        String objectCode = StringUtils.trimToNull(asset.getString("objectCode"));
        if (objectCode != null && objectId != null) {
            query.setObjectCode(objectCode);
        }
    }

    AiBusinessObject resolveTaskBusinessObject(Long tenantId,
                                               BusinessTaskFormContextQueryDTO query,
                                               AiBusinessFlowInstanceLink link) {
        Map<String, Object> snapshot = link == null ? Map.of() : readJsonObject(link.getVariablesSnapshot());
        JSONObject snapshotFormRef = readNestedObject(snapshot.get("businessFormRef"));
        Long objectId = firstLongValue(
                query == null ? null : query.getObjectId(),
                snapshot.get("objectId"), snapshot.get("businessObjectId"), snapshot.get("targetObjectId"),
                snapshotFormRef.get("objectId"));
        if (objectId != null) {
            AiBusinessObject object = businessObjectMapper.selectByIdForTenant(tenantId, objectId);
            if (object != null) {
                return object;
            }
        }
        String configKey = StringUtils.firstNonBlank(
                query == null ? null : query.getConfigKey(),
                textValue(snapshot.get("configKey")),
                textValue(snapshot.get("runtimeConfigKey")),
                textValue(snapshotFormRef.get("configKey")));
        if (StringUtils.isNotBlank(configKey)) {
            AiBusinessObject object = businessObjectMapper.selectByConfigKey(tenantId, configKey);
            if (object != null) {
                return object;
            }
        }
        String objectCode = StringUtils.firstNonBlank(
                query == null ? null : query.getObjectCode(),
                textValue(snapshot.get("objectCode")),
                textValue(snapshotFormRef.get("objectCode")));
        String suiteCode = StringUtils.firstNonBlank(
                query == null ? null : query.getSuiteCode(),
                textValue(snapshot.get("suiteCode")),
                textValue(snapshotFormRef.get("suiteCode")));
        if (StringUtils.isNotBlank(objectCode) && StringUtils.isNotBlank(suiteCode)) {
            AiBusinessObject object = businessObjectMapper.selectByObjectCode(tenantId, suiteCode, objectCode);
            if (object != null) {
                return object;
            }
        }
        return StringUtils.isBlank(objectCode)
                ? null
                : businessObjectMapper.selectFirstByObjectCode(tenantId, objectCode);
    }

    AiBusinessDocumentConfig resolveEnabledDocumentConfig(Long tenantId,
                                                          String objectCodeOrConfigKey,
                                                          AiCrudConfig runtimeConfig) {
        Long effectiveTenantId = tenantId != null ? tenantId : tenantIdSupplier.get();
        AiBusinessDocumentConfig config = documentConfigService.selectEnabledByObjectCode(
                effectiveTenantId, objectCodeOrConfigKey);
        if (config != null) {
            return config;
        }
        config = documentConfigService.selectEnabledByConfigKey(effectiveTenantId, objectCodeOrConfigKey);
        if (config != null || runtimeConfig == null) {
            return config;
        }
        config = documentConfigService.selectEnabledByConfigKey(effectiveTenantId, runtimeConfig.getConfigKey());
        if (config != null) {
            return config;
        }
        return documentConfigService.selectEnabledByObjectCode(effectiveTenantId, runtimeConfig.getObjectCode());
    }

    private AiBusinessObject resolveBusinessObject(Long tenantId,
                                                   String objectCodeOrConfigKey,
                                                   AiCrudConfig runtimeConfig,
                                                   AiBusinessDocumentConfig documentConfig) {
        Long effectiveTenantId = tenantId != null ? tenantId : tenantIdSupplier.get();
        AiBusinessObject object = null;
        if (documentConfig != null && StringUtils.isNotBlank(documentConfig.getConfigKey())) {
            object = businessObjectMapper.selectByConfigKey(effectiveTenantId, documentConfig.getConfigKey());
        }
        if (object == null && runtimeConfig != null && StringUtils.isNotBlank(runtimeConfig.getConfigKey())) {
            object = businessObjectMapper.selectByConfigKey(effectiveTenantId, runtimeConfig.getConfigKey());
        }
        if (object == null && StringUtils.isNotBlank(objectCodeOrConfigKey)) {
            object = businessObjectMapper.selectByConfigKey(effectiveTenantId, objectCodeOrConfigKey);
        }
        if (object == null && StringUtils.isNotBlank(objectCodeOrConfigKey)) {
            object = businessObjectMapper.selectFirstByObjectCode(effectiveTenantId, objectCodeOrConfigKey);
        }
        return object;
    }

    private Long firstLongValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Long parsed = value instanceof Number number
                    ? number.longValue()
                    : parseLongValue(textValue(value));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private boolean isBusinessCodeTaskForm(String objectCode,
                                           JSONObject bindingConfig,
                                           BusinessTaskFormContextQueryDTO query,
                                           Map<String, Object> taskFormInfo) {
        JSONObject nodeForm = taskNodeFormResolver.resolveTaskNodeForm(
                new TaskFormRuntimeContext(objectCode, null, null, null, bindingConfig, null, null),
                query, taskFormInfo);
        return nodeForm != null
                && "BUSINESS_CODE_FORM".equals(normalizeNodeFormMode(nodeForm.getString("formMode")));
    }

    @FunctionalInterface
    interface BindingResolver {
        AiBusinessBinding resolve(Long tenantId, String objectCode, String... fallbackCodes);
    }
}
