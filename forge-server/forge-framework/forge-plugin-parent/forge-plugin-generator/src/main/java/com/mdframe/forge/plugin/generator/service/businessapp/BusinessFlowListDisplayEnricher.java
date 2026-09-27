package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.spi.FlowBusinessListDisplayItem;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.buildBusinessKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseBusinessKeyObjectCode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseBusinessKeyRecordId;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;

/** Batch-loads business records and projects stable display metadata onto flow list items. */
final class BusinessFlowListDisplayEnricher {

    private final Supplier<Long> tenantIdSupplier;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final BusinessFlowRuntimeContextResolver businessRuntimeContextResolver;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessCodeFormProviderRegistry codeFormProviderRegistry;
    private final BusinessObjectResolver businessObjectResolver;
    private final Function<AiBusinessObject, BusinessObjectVO> businessObjectConverter;
    private final BindingResolver bindingResolver;
    private final SummaryResolver summaryResolver;

    BusinessFlowListDisplayEnricher(
            Supplier<Long> tenantIdSupplier,
            BusinessFlowInstanceLinkMapper flowInstanceLinkMapper,
            BusinessFlowRuntimeContextResolver businessRuntimeContextResolver,
            DynamicCrudService dynamicCrudService,
            BusinessCodeFormProviderRegistry codeFormProviderRegistry,
            BusinessObjectResolver businessObjectResolver,
            Function<AiBusinessObject, BusinessObjectVO> businessObjectConverter,
            BindingResolver bindingResolver,
            SummaryResolver summaryResolver) {
        this.tenantIdSupplier = tenantIdSupplier;
        this.flowInstanceLinkMapper = flowInstanceLinkMapper;
        this.businessRuntimeContextResolver = businessRuntimeContextResolver;
        this.dynamicCrudService = dynamicCrudService;
        this.codeFormProviderRegistry = codeFormProviderRegistry;
        this.businessObjectResolver = businessObjectResolver;
        this.businessObjectConverter = businessObjectConverter;
        this.bindingResolver = bindingResolver;
        this.summaryResolver = summaryResolver;
    }

    void enrich(List<FlowBusinessListDisplayItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Long tenantId = tenantIdSupplier.get();
        Map<String, AiBusinessFlowInstanceLink> linkByBusinessKey = loadLinksByBusinessKey(tenantId, items);
        Map<String, BusinessRuntimeContext> contextCache = new HashMap<>();
        Map<String, AiBusinessObject> objectLookupCache = new HashMap<>();
        Map<String, BusinessListGroup> grouped = new LinkedHashMap<>();
        for (FlowBusinessListDisplayItem item : items) {
            if (item == null) {
                continue;
            }
            AiBusinessFlowInstanceLink link = linkByBusinessKey.get(StringUtils.trimToEmpty(item.getBusinessKey()));
            Map<String, Object> snapshotParams = readBusinessParamsSnapshot(link);
            if (!snapshotParams.isEmpty() && (item.getBusinessParams() == null || item.getBusinessParams().isEmpty())) {
                item.setBusinessParams(snapshotParams);
            }
            BusinessTaskFormContextQueryDTO itemQuery = new BusinessTaskFormContextQueryDTO();
            itemQuery.setObjectCode(item.getObjectCode());
            String snapshotConfigKey = firstNonBlankValue(
                    extractSnapshotValue(link, "configKey"),
                    extractSnapshotValue(link, "runtimeConfigKey"),
                    item.getBusinessParams() == null ? null : item.getBusinessParams().get("configKey"));
            itemQuery.setConfigKey(snapshotConfigKey);
            itemQuery.setSuiteCode(item.getBusinessParams() == null
                    ? null : textValue(item.getBusinessParams().get("suiteCode")));
            String hintedObjectCode = StringUtils.firstNonBlank(
                    link == null ? null : link.getObjectCode(),
                    StringUtils.trimToNull(item.getObjectCode()),
                    parseBusinessKeyObjectCode(item.getBusinessKey()));
            AiBusinessObject taskObject = null;
            if (StringUtils.isBlank(snapshotConfigKey) || StringUtils.isBlank(hintedObjectCode)) {
                String objectLookupKey = buildBusinessListObjectLookupKey(itemQuery, link, hintedObjectCode);
                taskObject = objectLookupCache.computeIfAbsent(objectLookupKey,
                        ignored -> businessRuntimeContextResolver.resolveTaskBusinessObject(
                                tenantId, itemQuery, link));
            }
            String objectCode = StringUtils.firstNonBlank(
                    taskObject == null ? null : taskObject.getObjectCode(),
                    hintedObjectCode);
            Long recordId = link == null || link.getRecordId() == null
                    ? item.getRecordId()
                    : link.getRecordId();
            if (recordId == null) {
                recordId = parseBusinessKeyRecordId(item.getBusinessKey());
            }
            String businessKey = StringUtils.firstNonBlank(
                    link == null ? null : link.getBusinessKey(),
                    StringUtils.trimToNull(item.getBusinessKey()),
                    objectCode != null && recordId != null ? buildBusinessKey(objectCode, recordId) : null);
            if (StringUtils.isBlank(objectCode) || recordId == null) {
                item.setProcessDefinitionName(StringUtils.firstNonBlank(
                        item.getProcessDefinitionName(), item.getProcessName(), item.getProcessDefKey()));
                continue;
            }
            String runtimeLookupKey = StringUtils.firstNonBlank(
                    taskObject == null ? null : taskObject.getConfigKey(),
                    snapshotConfigKey,
                    objectCode);
            BusinessRuntimeContext context = contextCache.computeIfAbsent(runtimeLookupKey,
                    code -> businessRuntimeContextResolver.resolve(tenantId, code));
            String canonicalObjectCode = StringUtils.firstNonBlank(context.objectCode(), objectCode);
            String groupKey = StringUtils.firstNonBlank(context.configKey(), runtimeLookupKey, canonicalObjectCode);
            grouped.computeIfAbsent(groupKey,
                            key -> new BusinessListGroup(context, new ArrayList<>()))
                    .runtimes()
                    .add(new BusinessListRuntime(item, canonicalObjectCode, recordId, businessKey));
        }
        grouped.forEach((objectCode, group) -> enrichBusinessListGroup(tenantId, group.context(), group.runtimes()));
    }

    private Map<String, AiBusinessFlowInstanceLink> loadLinksByBusinessKey(Long tenantId,
                                                                           List<FlowBusinessListDisplayItem> items) {
        Set<String> businessKeys = new LinkedHashSet<>();
        for (FlowBusinessListDisplayItem item : items) {
            String businessKey = item == null ? null : StringUtils.trimToNull(item.getBusinessKey());
            if (businessKey != null) {
                businessKeys.add(businessKey);
            }
        }
        if (businessKeys.isEmpty()) {
            return Map.of();
        }
        List<AiBusinessFlowInstanceLink> links = flowInstanceLinkMapper.selectLatestByBusinessKeys(tenantId, businessKeys);
        Map<String, AiBusinessFlowInstanceLink> result = new LinkedHashMap<>();
        if (links != null) {
            for (AiBusinessFlowInstanceLink link : links) {
                if (link != null && StringUtils.isNotBlank(link.getBusinessKey())) {
                    result.put(link.getBusinessKey(), link);
                }
            }
        }
        return result;
    }

    private void enrichBusinessListGroup(Long tenantId, BusinessRuntimeContext context, List<BusinessListRuntime> runtimes) {
        String objectCode = context == null ? null : context.objectCode();
        if (StringUtils.isBlank(objectCode) || runtimes == null || runtimes.isEmpty()) {
            return;
        }
        BusinessObjectVO object = context.businessObject() == null
                ? businessObjectResolver.resolve(tenantId, objectCode, context.configKey())
                : businessObjectConverter.apply(context.businessObject());
        AiCrudConfig runtimeConfig = context.runtimeConfig();
        AiBusinessDocumentConfig documentConfig = context.documentConfig();
        String configKey = context.configKey();
        AiBusinessBinding binding = bindingResolver.resolve(tenantId, objectCode);
        JSONObject bindingConfig = binding == null ? new JSONObject() : readBindingConfig(binding.getBindingConfig());
        String objectName = StringUtils.firstNonBlank(
                object == null ? null : object.getObjectName(),
                documentConfig == null ? null : documentConfig.getDocumentName(),
                runtimeConfig == null ? null : runtimeConfig.getObjectName(),
                objectCode);

        if (StringUtils.isNotBlank(configKey)) {
            enrichLowcodeBusinessListGroup(objectCode, objectName, configKey, object, bindingConfig, runtimes);
            return;
        }
        enrichCodeBusinessListGroup(objectCode, objectName, runtimes);
    }

    private String buildBusinessListObjectLookupKey(BusinessTaskFormContextQueryDTO query,
                                                    AiBusinessFlowInstanceLink link,
                                                    String hintedObjectCode) {
        String suiteObjectKey = query == null
                || StringUtils.isBlank(query.getSuiteCode())
                || StringUtils.isBlank(hintedObjectCode)
                ? null
                : query.getSuiteCode() + ":" + hintedObjectCode;
        return StringUtils.firstNonBlank(
                textValue(extractSnapshotValue(link, "objectId")),
                query == null ? null : query.getConfigKey(),
                suiteObjectKey,
                hintedObjectCode,
                "unknown");
    }

    private void enrichLowcodeBusinessListGroup(String objectCode,
                                                String objectName,
                                                String configKey,
                                                BusinessObjectVO object,
                                                JSONObject bindingConfig,
                                                List<BusinessListRuntime> runtimes) {
        List<Long> recordIds = runtimes.stream()
                .map(BusinessListRuntime::recordId)
                .distinct()
                .toList();
        Map<Object, Map<String, Object>> records = dynamicCrudService.selectByIds(configKey, recordIds);
        for (BusinessListRuntime runtime : runtimes) {
            FlowBusinessListDisplayItem item = runtime.item();
            Map<String, Object> recordData = findBatchRecord(records, runtime.recordId());
            TaskFormRuntimeContext taskRuntime = new TaskFormRuntimeContext(
                    objectCode, runtime.recordId(), runtime.businessKey(), configKey, bindingConfig, null, null);
            Map<String, Object> startParams = item.getBusinessParams();
            item.setObjectCode(objectCode);
            item.setRecordId(runtime.recordId());
            item.setBusinessType(StringUtils.firstNonBlank(item.getBusinessType(), objectCode));
            item.setBusinessParams(mergeBusinessListParams(startParams, recordData));
            applyStartDisplayExtensions(item, startParams);
            item.setBusinessObjectName(objectName);
            item.setBusinessSummary(StringUtils.firstNonBlank(
                    summaryResolver.resolve(object, taskRuntime, recordData),
                    item.getBusinessSummary()));
            item.setProcessDefinitionName(StringUtils.firstNonBlank(
                    item.getProcessDefinitionName(),
                    item.getProcessName(),
                    bindingConfig.getString("flowModelName"),
                    item.getProcessDefKey()));
        }
    }

    private Map<String, Object> findBatchRecord(Map<Object, Map<String, Object>> records, Long recordId) {
        if (records == null || records.isEmpty() || recordId == null) {
            return Map.of();
        }
        Map<String, Object> record = records.get(recordId);
        if (record != null) {
            return record;
        }
        record = records.get(String.valueOf(recordId));
        return record == null ? Map.of() : record;
    }

    /**
     * 流程实例关联表保存的是完整启动变量快照，而列表扩展点历史上接收的是
     * businessParams 变量本身。优先提取嵌套值；旧数据没有该变量时保留整个快照，
     * 由列表扩展点自行忽略流程上下文字段。
     */
    private Map<String, Object> readBusinessParamsSnapshot(AiBusinessFlowInstanceLink link) {
        if (link == null || StringUtils.isBlank(link.getVariablesSnapshot())) {
            return Map.of();
        }
        JSONObject snapshot = readJsonObject(link.getVariablesSnapshot());
        Object businessParams = snapshot.get("businessParams");
        if (businessParams instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        return snapshot.isEmpty() ? Map.of() : new LinkedHashMap<>(snapshot);
    }

    private String extractSnapshotValue(AiBusinessFlowInstanceLink link, String key) {
        if (link == null || StringUtils.isBlank(key)) {
            return null;
        }
        JSONObject snapshot = readJsonObject(link.getVariablesSnapshot());
        return StringUtils.trimToNull(textValue(snapshot.get(key)));
    }

    private String firstNonBlankValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = StringUtils.trimToNull(textValue(value));
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private Map<String, Object> mergeBusinessListParams(Map<String, Object> startParams,
                                                        Map<String, Object> recordData) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (startParams != null && !startParams.isEmpty()) {
            merged.putAll(startParams);
        }
        if (recordData != null && !recordData.isEmpty()) {
            merged.putAll(recordData);
        }
        return merged.isEmpty() ? startParams : merged;
    }

    /**
     * 发起时传入的 displayFields 不能被业务记录覆盖。
     * 待办列表只渲染 displayExtensions，业务记录仍留在 businessParams 给自定义 SPI 使用。
     */
    private void applyStartDisplayExtensions(FlowBusinessListDisplayItem item, Map<String, Object> startParams) {
        if (item == null || (item.getDisplayExtensions() != null && !item.getDisplayExtensions().isEmpty())) {
            return;
        }
        if (startParams == null || startParams.isEmpty()) {
            return;
        }
        Object fields = startParams.get("displayFields");
        if (fields == null) {
            fields = startParams.get("fields");
        }
        if (!(fields instanceof List<?> || fields instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("fields", fields);
        item.setDisplayExtensions(extensions);
    }

    private void enrichCodeBusinessListGroup(String objectCode,
                                             String fallbackObjectName,
                                             List<BusinessListRuntime> runtimes) {
        List<Map<String, Object>> assets = codeFormProviderRegistry.listAssets(objectCode, true);
        if (assets.isEmpty()) {
            applyBusinessListFallback(objectCode, fallbackObjectName, runtimes);
            return;
        }
        Map<String, Object> asset = assets.get(0);
        String providerKey = StringUtils.trimToNull(textValue(asset.get("providerKey")));
        String objectName = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(asset.get("objectName"))),
                StringUtils.trimToNull(textValue(asset.get("businessName"))),
                StringUtils.trimToNull(textValue(asset.get("appName"))),
                fallbackObjectName,
                objectCode);
        Map<Long, String> summaries = providerKey == null
                ? Map.of()
                : codeFormProviderRegistry.find(providerKey)
                        .map(provider -> provider.buildSummaries(objectCode, collectRecordIds(runtimes)))
                        .orElse(Map.of());
        if (summaries == null) {
            summaries = Map.of();
        }
        for (BusinessListRuntime runtime : runtimes) {
            FlowBusinessListDisplayItem item = runtime.item();
            Map<String, Object> startParams = item.getBusinessParams();
            item.setObjectCode(objectCode);
            item.setRecordId(runtime.recordId());
            item.setBusinessType(StringUtils.firstNonBlank(item.getBusinessType(), objectCode));
            item.setBusinessParams(mergeBusinessListParams(startParams, Map.of(
                    "recordId", runtime.recordId(),
                    "businessKey", runtime.businessKey())));
            applyStartDisplayExtensions(item, startParams);
            item.setBusinessObjectName(objectName);
            item.setBusinessSummary(StringUtils.firstNonBlank(summaries.get(runtime.recordId()), item.getBusinessSummary()));
            item.setProcessDefinitionName(StringUtils.firstNonBlank(
                    item.getProcessDefinitionName(), item.getProcessName(), item.getProcessDefKey()));
        }
    }

    private Collection<Long> collectRecordIds(List<BusinessListRuntime> runtimes) {
        List<Long> ids = new ArrayList<>();
        for (BusinessListRuntime runtime : runtimes) {
            if (runtime.recordId() != null && !ids.contains(runtime.recordId())) {
                ids.add(runtime.recordId());
            }
        }
        return ids;
    }

    private void applyBusinessListFallback(String objectCode,
                                           String objectName,
                                           List<BusinessListRuntime> runtimes) {
        for (BusinessListRuntime runtime : runtimes) {
            FlowBusinessListDisplayItem item = runtime.item();
            item.setObjectCode(objectCode);
            item.setRecordId(runtime.recordId());
            item.setBusinessObjectName(StringUtils.firstNonBlank(objectName, item.getBusinessObjectName(), objectCode));
            item.setProcessDefinitionName(StringUtils.firstNonBlank(
                    item.getProcessDefinitionName(), item.getProcessName(), item.getProcessDefKey()));
        }
    }

    @FunctionalInterface
    interface BusinessObjectResolver {
        BusinessObjectVO resolve(Long tenantId, String objectCode, String configKey);
    }

    @FunctionalInterface
    interface BindingResolver {
        AiBusinessBinding resolve(Long tenantId, String objectCode);
    }

    @FunctionalInterface
    interface SummaryResolver {
        String resolve(BusinessObjectVO object, TaskFormRuntimeContext runtime, Map<String, Object> recordData);
    }

    private record BusinessListGroup(BusinessRuntimeContext context,
                                     List<BusinessListRuntime> runtimes) {
    }

    private record BusinessListRuntime(FlowBusinessListDisplayItem item,
                                       String objectCode,
                                       Long recordId,
                                       String businessKey) {
    }
}
