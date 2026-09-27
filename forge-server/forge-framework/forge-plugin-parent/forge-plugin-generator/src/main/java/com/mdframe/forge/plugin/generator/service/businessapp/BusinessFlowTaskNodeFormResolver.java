package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.putBoolean;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.putText;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.resolveFlowModelKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeFieldPermissions;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeFormMode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeTaskChildPermissions;

/**
 * Resolves the effective task form from Flowable metadata, runtime variables and
 * low-code application page assets.
 *
 * <p>The resolver implements a layered fallback strategy: task form, process
 * form, runtime business form reference, and finally the binding snapshot.</p>
 */
@Slf4j
final class BusinessFlowTaskNodeFormResolver {

    private final Supplier<FlowClient> flowClientSupplier;
    private final BusinessFlowApplicationPageFormResolver applicationPageFormResolver;
    private final BusinessFlowTaskChildPolicy taskChildPolicy;
    private final Function<String, List<Map<String, Object>>> assetCatalogLoader;
    private final BiConsumer<String, Long> stageRecorder;
    private final Consumer<String> detailRecorder;

    BusinessFlowTaskNodeFormResolver(
            Supplier<FlowClient> flowClientSupplier,
            BusinessFlowApplicationPageFormResolver applicationPageFormResolver,
            BusinessFlowTaskChildPolicy taskChildPolicy,
            Function<String, List<Map<String, Object>>> assetCatalogLoader,
            BiConsumer<String, Long> stageRecorder,
            Consumer<String> detailRecorder) {
        this.flowClientSupplier = flowClientSupplier;
        this.applicationPageFormResolver = applicationPageFormResolver;
        this.taskChildPolicy = taskChildPolicy;
        this.assetCatalogLoader = assetCatalogLoader;
        this.stageRecorder = stageRecorder;
        this.detailRecorder = detailRecorder;
    }

    private JSONObject findNodeForm(JSONObject bindingConfig, String taskDefKey) {
        JSONArray nodeForms = bindingConfig == null ? null : bindingConfig.getJSONArray("nodeForms");
        if (nodeForms == null || nodeForms.isEmpty() || StringUtils.isBlank(taskDefKey)) {
            return new JSONObject();
        }
        for (int i = 0; i < nodeForms.size(); i++) {
            JSONObject nodeForm = nodeForms.getJSONObject(i);
            if (nodeForm != null && taskDefKey.equals(nodeForm.getString("taskDefKey"))) {
                return nodeForm;
            }
        }
        return new JSONObject();
    }

    JSONObject resolveTaskNodeForm(TaskFormRuntimeContext runtime, BusinessTaskFormContextQueryDTO query) {
        return resolveTaskNodeForm(runtime, query, Map.of());
    }

    JSONObject resolveTaskNodeForm(TaskFormRuntimeContext runtime,
                                           BusinessTaskFormContextQueryDTO query,
                                           Map<String, Object> taskFormInfo) {
        JSONObject flowNodeForm = resolveFlowNodeForm(runtime, query, taskFormInfo);
        if (!flowNodeForm.isEmpty()) {
            return flowNodeForm;
        }
        return findNodeForm(runtime.bindingConfig(), query.getTaskDefKey());
    }

    private JSONObject resolveFlowNodeForm(TaskFormRuntimeContext runtime, BusinessTaskFormContextQueryDTO query) {
        return resolveFlowNodeForm(runtime, query, Map.of());
    }

    private JSONObject resolveFlowNodeForm(TaskFormRuntimeContext runtime,
                                           BusinessTaskFormContextQueryDTO query,
                                           Map<String, Object> taskFormInfo) {
        String objectCode = StringUtils.trimToNull(runtime.objectCode());
        if (StringUtils.isBlank(objectCode)) {
            return new JSONObject();
        }
        Map<String, Object> formInfo = loadFlowNodeFormInfo(runtime, query, taskFormInfo);
        String taskDefKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(formInfo.get("taskDefKey"))),
                StringUtils.trimToNull(query.getTaskDefKey()));
        String configuredFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(formInfo.get("formKey"))),
                StringUtils.trimToNull(query.getFormKey()));
        JSONObject runtimeFormRef = resolveRuntimeBusinessFormRef(formInfo);
        String runtimeFormKey = StringUtils.trimToNull(runtimeFormRef.getString("formKey"));
        // 节点表单只需要身份元数据；整份 schema 留给后续 formSchema / uiDocument
        JSONObject runtimeAsset = applicationPageFormResolver.slimPageFormAssetMeta(
                resolveBusinessTaskFormAsset(objectCode, runtimeFormKey));
        boolean useRuntimePageForm = StringUtils.isNotBlank(runtimeFormKey) && !runtimeAsset.isEmpty();
        String formKey = useRuntimePageForm ? runtimeFormKey : configuredFormKey;
        Object rawFormPermissions = formInfo.get("formFieldPermissions");
        List<Map<String, Object>> permissions = normalizeFieldPermissions(rawFormPermissions);
        List<Map<String, Object>> childPermissions = normalizeTaskChildPermissions(rawFormPermissions);
        JSONObject flowFormRef = readNestedObject(formInfo.get("formRef"));
        if (useRuntimePageForm) {
            JSONObject effectiveRuntimeRef = new JSONObject();
            effectiveRuntimeRef.putAll(runtimeAsset);
            effectiveRuntimeRef.putAll(runtimeFormRef);
            flowFormRef = effectiveRuntimeRef;
            // 只有业务流程换了另一张页面时才丢掉节点权限。
            // 同一张表单经常一边是页面 formKey，一边是带应用前缀的 formKey，不能因此把节点上配好的权限清空。
            if (!taskChildPolicy.sameTaskFormKey(runtimeFormKey, configuredFormKey)) {
                permissions = List.of();
                childPermissions = List.of();
            }
        }
        JSONObject asset = applicationPageFormResolver.slimPageFormAssetMeta(
                resolveBusinessTaskFormAsset(objectCode, formKey));
        if (asset.isEmpty() && StringUtils.isBlank(formKey) && permissions.isEmpty()) {
            if (StringUtils.isNotBlank(runtime.configKey())) {
                JSONObject defaultNodeForm = new JSONObject();
                putText(defaultNodeForm, "taskDefKey", taskDefKey);
                putText(defaultNodeForm, "taskName", textValue(formInfo.get("taskName")));
                defaultNodeForm.put("formMode", "BUSINESS_OBJECT_FORM");
                defaultNodeForm.put("editMode", "EDITABLE");
                defaultNodeForm.put("viewKey", "default");
                putBoolean(defaultNodeForm, formInfo, "allowApprove");
                putBoolean(defaultNodeForm, formInfo, "allowDelegate");
                putBoolean(defaultNodeForm, formInfo, "allowReject");
                putBoolean(defaultNodeForm, formInfo, "allowRejectToStart");
                putBoolean(defaultNodeForm, formInfo, "allowReturn");
                putBoolean(defaultNodeForm, formInfo, "allowMultiReturn");
                putBoolean(defaultNodeForm, formInfo, "allowDirectSend");
                putText(defaultNodeForm, "returnSourceActivityId", textValue(formInfo.get("returnSourceActivityId")));
                putText(defaultNodeForm, "returnSourceActivityName", textValue(formInfo.get("returnSourceActivityName")));
                if (formInfo.get("returnTargets") instanceof List<?> targets) {
                    defaultNodeForm.put("returnTargets", targets);
                }
                putBoolean(defaultNodeForm, formInfo, "allowTerminate");
                putBoolean(defaultNodeForm, formInfo, "requireSignature");
                putBoolean(defaultNodeForm, formInfo, "requireComment");
                return defaultNodeForm;
            }
            return new JSONObject();
        }

        JSONObject nodeForm = new JSONObject();
        putText(nodeForm, "taskDefKey", taskDefKey);
        putText(nodeForm, "taskName", textValue(formInfo.get("taskName")));
        putText(nodeForm, "formKey", StringUtils.firstNonBlank(
                formKey,
                StringUtils.trimToNull(flowFormRef.getString("formKey")),
                asset.getString("formKey")));
        putText(nodeForm, "formName", StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(formInfo.get("formName"))),
                StringUtils.trimToNull(flowFormRef.getString("formName")),
                asset.getString("formName")));
        putText(nodeForm, "providerKey", StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(formInfo.get("providerKey"))),
                StringUtils.trimToNull(flowFormRef.getString("providerKey")),
                asset.getString("providerKey")));
        putText(nodeForm, "formUrl", StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(formInfo.get("formUrl"))),
                StringUtils.trimToNull(flowFormRef.getString("formUrl")),
                asset.getString("formUrl")));
        putText(nodeForm, "viewKey", StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(formInfo.get("viewKey"))),
                StringUtils.trimToNull(flowFormRef.getString("viewKey")),
                asset.getString("viewKey"),
                "default"));
        String formMode = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(formInfo.get("formMode"))),
                StringUtils.trimToNull(flowFormRef.getString("formMode")),
                StringUtils.trimToNull(flowFormRef.getString("type")),
                asset.getString("formMode"),
                runtime.configKey() == null ? "BUSINESS_CODE_FORM" : "BUSINESS_OBJECT_FORM");
        putText(nodeForm, "formMode", normalizeNodeFormMode(formMode));
        putBoolean(nodeForm, formInfo, "allowApprove");
        putBoolean(nodeForm, formInfo, "allowDelegate");
        putBoolean(nodeForm, formInfo, "allowReject");
        putBoolean(nodeForm, formInfo, "allowRejectToStart");
        putBoolean(nodeForm, formInfo, "allowReturn");
        putBoolean(nodeForm, formInfo, "allowMultiReturn");
        putBoolean(nodeForm, formInfo, "allowDirectSend");
        putText(nodeForm, "returnSourceActivityId", textValue(formInfo.get("returnSourceActivityId")));
        putText(nodeForm, "returnSourceActivityName", textValue(formInfo.get("returnSourceActivityName")));
        if (formInfo.get("returnTargets") instanceof List<?> targets) {
            nodeForm.put("returnTargets", targets);
        }
        putBoolean(nodeForm, formInfo, "allowTerminate");
        putBoolean(nodeForm, formInfo, "requireSignature");
        putBoolean(nodeForm, formInfo, "requireComment");
        boolean businessObjectDefaultWritable = permissions.isEmpty()
                && "BUSINESS_OBJECT_FORM".equals(normalizeNodeFormMode(formMode));
        nodeForm.put("editMode", permissions.stream().anyMatch(item -> readBooleanValue(item.get("writable"), false))
                || businessObjectDefaultWritable ? "EDITABLE" : "READONLY");
        JSONObject effectiveFormRef = new JSONObject();
        if (!asset.isEmpty()) {
            effectiveFormRef.putAll(asset);
        }
        if (!flowFormRef.isEmpty()) {
            effectiveFormRef.putAll(flowFormRef);
        }
        putText(effectiveFormRef, "formKey", nodeForm.getString("formKey"));
        putText(effectiveFormRef, "formMode", nodeForm.getString("formMode"));
        putText(effectiveFormRef, "type", nodeForm.getString("formMode"));
        putText(effectiveFormRef, "formName", nodeForm.getString("formName"));
        putText(effectiveFormRef, "providerKey", nodeForm.getString("providerKey"));
        putText(effectiveFormRef, "formUrl", nodeForm.getString("formUrl"));
        putText(effectiveFormRef, "viewKey", nodeForm.getString("viewKey"));
        if (!effectiveFormRef.isEmpty()) {
            nodeForm.put("formRef", effectiveFormRef);
        }
        if (!permissions.isEmpty()) {
            nodeForm.put("fieldPermissions", permissions);
        }
        if (!childPermissions.isEmpty()) {
            nodeForm.put("childPermissions", childPermissions);
        }
        return nodeForm;
    }

    /**
     * Reads the concrete page form selected by an outer application business-process
     * approval node. Earlier runs only have the compatibility variable {@code formKey};
     * newer runs also carry a structured {@code businessFormRef}.
     */
    JSONObject resolveRuntimeBusinessFormRef(Map<String, Object> formInfo) {
        JSONObject variables = readNestedObject(formInfo == null ? null : formInfo.get("variables"));
        if (variables.isEmpty()) {
            return new JSONObject();
        }
        JSONObject formRef = readNestedObject(variables.get("businessFormRef"));
        String formKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(variables.getString("businessFormKey")),
                StringUtils.trimToNull(formRef.getString("formKey")),
                StringUtils.trimToNull(variables.getString("formKey")));
        if (formKey == null) {
            return new JSONObject();
        }
        formRef.put("formKey", formKey);
        return formRef;
    }

    private Map<String, Object> loadFlowNodeFormInfo(TaskFormRuntimeContext runtime,
                                                     BusinessTaskFormContextQueryDTO query) {
        return loadFlowNodeFormInfo(runtime, query, Map.of());
    }

    private Map<String, Object> loadFlowNodeFormInfo(TaskFormRuntimeContext runtime,
                                                     BusinessTaskFormContextQueryDTO query,
                                                     Map<String, Object> preloadedTaskFormInfo) {
        Map<String, Object> taskFormInfo = preloadedTaskFormInfo == null || preloadedTaskFormInfo.isEmpty()
                ? loadTaskFormInfo(query.getTaskId())
                : preloadedTaskFormInfo;
        if (isCompleteFlowNodeFormInfo(taskFormInfo)) {
            detailRecorder.accept("processFormRpc=skip(complete)");
            return taskFormInfo;
        }
        // Flow getTaskFormInfo 常缺顶层 formKey，但前端/query 或 variables 已有页面 formKey；
        // 此时再打 processFormInfo 几乎是重复 RPC（常见 600ms+）。
        if (taskFormInfo != null && !taskFormInfo.isEmpty()
                && (StringUtils.isNotBlank(query == null ? null : query.getFormKey())
                || hasTextValue(resolveRuntimeBusinessFormRef(taskFormInfo).getString("formKey")))) {
            detailRecorder.accept("processFormRpc=skip(queryOrVarFormKey)");
            return taskFormInfo;
        }
        long mark = System.nanoTime();
        Map<String, Object> processFormInfo = loadProcessFormInfo(runtime, query);
        stageRecorder.accept("processFormRpcMs", mark);
        detailRecorder.accept("processFormRpc=hit");
        if (taskFormInfo.isEmpty()) {
            return processFormInfo;
        }
        if (processFormInfo.isEmpty()) {
            return taskFormInfo;
        }
        Map<String, Object> merged = new LinkedHashMap<>(processFormInfo);
        taskFormInfo.forEach((key, value) -> {
            if (hasTextValue(value) || value instanceof Map<?, ?> || value instanceof List<?>) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    private boolean isCompleteFlowNodeFormInfo(Map<String, Object> formInfo) {
        if (formInfo == null || formInfo.isEmpty()) {
            return false;
        }
        // 已有 formKey / 权限 / formRef 即可组装节点表单，避免再打一次 processFormInfo RPC
        if (hasTextValue(formInfo.get("formKey"))
                || hasTextValue(formInfo.get("formUrl"))
                || hasTextValue(formInfo.get("formJson"))
                || hasTextValue(formInfo.get("formFieldPermissions"))
                || formInfo.get("formRef") instanceof Map<?, ?>
                || formInfo.get("formFieldPermissions") instanceof List<?>
                || formInfo.get("formFieldPermissions") instanceof Map<?, ?>) {
            return true;
        }
        // 业务流程变量里常把页面表单挂在 businessFormRef，顶层可能没有 formKey
        JSONObject runtimeFormRef = resolveRuntimeBusinessFormRef(formInfo);
        if (hasTextValue(runtimeFormRef.getString("formKey"))
                || hasTextValue(runtimeFormRef.getString("formUrl"))) {
            return true;
        }
        Object formType = formInfo.get("formType");
        if (!hasTextValue(formType)) {
            return false;
        }
        if ("none".equalsIgnoreCase(String.valueOf(formType))) {
            return true;
        }
        return false;
    }

    Map<String, Object> loadTaskFormInfo(String taskId) {
        FlowClient flowClient = flowClientSupplier.get();
        if (flowClient == null || StringUtils.isBlank(taskId)) {
            return Map.of();
        }
        try {
            FlowResult<Map<String, Object>> result = flowClient.getTaskFormInfo(taskId);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return Map.of();
            }
            return result.getData();
        } catch (Exception e) {
            log.warn("读取流程节点表单配置失败: taskId={}, error={}", taskId, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> loadProcessFormInfo(TaskFormRuntimeContext runtime,
                                                    BusinessTaskFormContextQueryDTO query) {
        FlowClient flowClient = flowClientSupplier.get();
        if (flowClient == null || query == null) {
            return Map.of();
        }
        String processInstanceId = StringUtils.trimToNull(query.getProcessInstanceId());
        String businessKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getBusinessKey()),
                runtime == null ? null : StringUtils.trimToNull(runtime.businessKey()));
        String processDefKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getProcessDefKey()),
                runtime == null || runtime.bindingConfig() == null ? null : resolveFlowModelKey(runtime.bindingConfig()));
        String taskId = StringUtils.trimToNull(query.getTaskId());
        String taskDefKey = StringUtils.trimToNull(query.getTaskDefKey());
        if (StringUtils.isBlank(processInstanceId)
                && StringUtils.isBlank(businessKey)
                && StringUtils.isBlank(processDefKey)
                && StringUtils.isBlank(taskId)
                && StringUtils.isBlank(taskDefKey)) {
            return Map.of();
        }
        try {
            FlowResult<Map<String, Object>> result = flowClient.getProcessFormInfo(
                    processInstanceId,
                    businessKey,
                    processDefKey,
                    taskId,
                    taskDefKey);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return Map.of();
            }
            return result.getData();
        } catch (Exception e) {
            log.warn("读取流程实例表单配置失败: processInstanceId={}, businessKey={}, processDefKey={}, taskDefKey={}, error={}",
                    processInstanceId, businessKey, processDefKey, taskDefKey, e.getMessage());
            return Map.of();
        }
    }

    private boolean hasTextValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence sequence) {
            return StringUtils.isNotBlank(sequence.toString());
        }
        return true;
    }

    JSONObject resolveBusinessTaskFormAsset(String objectCode, String formKey) {
        JSONObject applicationAsset = applicationPageFormResolver.resolveApplicationPageFormAsset(formKey);
        if (!applicationAsset.isEmpty()) {
            String assetObjectCode = StringUtils.trimToNull(applicationAsset.getString("objectCode"));
            // app_ 页面 formKey 是权威身份；objectCode 与运行时别名不一致时也不要回退到 collectTaskFormAssets
            if (assetObjectCode == null
                    || StringUtils.isBlank(objectCode)
                    || StringUtils.equals(objectCode, assetObjectCode)
                    || StringUtils.startsWith(StringUtils.trimToEmpty(formKey), "app_")) {
                return applicationAsset;
            }
        }
        // app_ 页面 key 不在对象 formAssets 里；空结果时再扫对象资产只会白白多查几百毫秒
        if (StringUtils.startsWith(StringUtils.trimToEmpty(formKey), "app_")) {
            detailRecorder.accept("collectTaskFormAssets=skip(appFormKey)");
            return new JSONObject();
        }
        long mark = System.nanoTime();
        List<Map<String, Object>> assets = assetCatalogLoader.apply(objectCode);
        stageRecorder.accept("collectTaskFormAssetsMs", mark);
        detailRecorder.accept("db:collectTaskFormAssets(object/designer/config)");
        if (assets.isEmpty()) {
            return new JSONObject();
        }
        if (StringUtils.isNotBlank(formKey)) {
            for (Map<String, Object> asset : assets) {
                if (StringUtils.equals(formKey, StringUtils.trimToNull(textValue(asset.get("formKey"))))) {
                    return readNestedObject(asset);
                }
            }
        }
        return assets.size() == 1 ? readNestedObject(assets.get(0)) : new JSONObject();
    }

}
