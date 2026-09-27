package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.starter.flow.dto.TaskFormInfo;
import com.mdframe.forge.starter.flow.entity.FlowForm;
import com.mdframe.forge.starter.flow.entity.FlowFormInstance;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.mapper.FlowFormInstanceMapper;
import com.mdframe.forge.starter.flow.service.FlowFormService;
import com.mdframe.forge.starter.flow.service.FlowModelService;
import com.mdframe.forge.starter.flow.service.support.DynamicFormArrayPermissionValidator;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 任务表单配置解析器。
 *
 * <p>使用 Resolver + Layered Fallback 集中 BPMN 节点表单、模型表单、动态表单服务和
 * 表单实例快照的优先级，避免任务命令服务同时承担表单协议兼容。</p>
 */
@Slf4j
final class FlowTaskFormConfigurationResolver {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String FORM_TYPE_BUSINESS = "business";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final FlowModelService flowModelService;
    private final FlowFormService flowFormService;
    private final FlowFormInstanceMapper flowFormInstanceMapper;
    private final FlowTaskNodePolicy nodePolicy;
    private final BiFunction<String, String, String> processDefinitionKeyResolver;

    FlowTaskFormConfigurationResolver(RepositoryService repositoryService,
                                      TaskService taskService,
                                      FlowModelService flowModelService,
                                      FlowFormService flowFormService,
                                      FlowFormInstanceMapper flowFormInstanceMapper,
                                      FlowTaskNodePolicy nodePolicy,
                                      BiFunction<String, String, String> processDefinitionKeyResolver) {
        this.repositoryService = repositoryService;
        this.taskService = taskService;
        this.flowModelService = flowModelService;
        this.flowFormService = flowFormService;
        this.flowFormInstanceMapper = flowFormInstanceMapper;
        this.nodePolicy = nodePolicy;
        this.processDefinitionKeyResolver = processDefinitionKeyResolver;
    }

    void validateDynamicFormArrayVariables(Task task,
                                           FlowNode flowNode,
                                           Map<String, Object> submittedVariables) {
        if (task == null || submittedVariables == null || submittedVariables.isEmpty()) {
            return;
        }
        NodeFormConfig nodeForm = readNodeFormConfig(flowNode);
        String schemaJson = resolveFormJson(nodeForm.formKey, nodeForm.formJson);
        if (!looksLikeFormSchema(schemaJson)) {
            String processDefKey = processDefinitionKeyResolver.apply(task.getProcessDefinitionId(), null);
            FlowModel flowModel = !isBlank(processDefKey) ? flowModelService.getModelByKey(processDefKey) : null;
            if (flowModel != null && "dynamic".equalsIgnoreCase(flowModel.getFormType())) {
                schemaJson = resolveModelFormJson(flowModel.getFormId(), flowModel.getFormJson());
            }
        }
        if (!looksLikeFormSchema(schemaJson)) {
            return;
        }
        DynamicFormArrayPermissionValidator.validate(
                OBJECT_MAPPER,
                schemaJson,
                nodeForm.formFieldPermissions,
                taskService.getVariables(task.getId()),
                submittedVariables);
    }

    void applyFormConfiguration(TaskFormInfo formInfo, String processDefinitionId, String processDefKey,
                                String taskDefKey) {
        FlowModel flowModel = !isBlank(processDefKey) ? flowModelService.getModelByKey(processDefKey) : null;
        FlowNode flowNode = resolveFormFlowNode(processDefinitionId, taskDefKey);
        applyFormConfiguration(formInfo, flowModel, flowNode);
    }

    void applyFormConfiguration(TaskFormInfo formInfo, FlowModel flowModel, FlowNode flowNode) {
        nodePolicy.applyNodePolicy(formInfo, flowNode);
        NodeFormConfig nodeForm = readNodeFormConfig(flowNode);
        formInfo.setFormFieldPermissions(nodeForm.formFieldPermissions);

        if (isBusinessNodeForm(flowModel, nodeForm)) {
            applyBusinessNodeFormConfiguration(formInfo, flowModel, nodeForm);
            hydrateResolvedFormSchema(formInfo, nodeForm);
            if (shouldExposeAsDynamicForm(formInfo, flowModel, nodeForm)) {
                formInfo.setFormType("dynamic");
            }
        } else if (!isBlank(nodeForm.formUrl)) {
            formInfo.setFormType("external");
            formInfo.setFormUrl(nodeForm.formUrl);
            formInfo.setFormTarget(!isBlank(nodeForm.formTarget) ? nodeForm.formTarget : "modal");
            applyNodeFormReference(formInfo, nodeForm, Map.of());
        } else if (!isBlank(nodeForm.formKey) || !isBlank(nodeForm.formJson)) {
            formInfo.setFormType("dynamic");
            formInfo.setFormKey(nodeForm.formKey);
            formInfo.setFormJson(resolveFormJson(nodeForm.formKey, nodeForm.formJson));
            applyNodeFormReference(formInfo, nodeForm, Map.of());
        } else if (flowModel != null) {
            applyModelFormConfiguration(formInfo, flowModel);
            hydrateResolvedFormSchema(formInfo, nodeForm);
        } else if (!isBlank(formInfo.getFormJson()) || !isBlank(formInfo.getFormKey())) {
            formInfo.setFormType("dynamic");
            formInfo.setFormJson(resolveFormJson(formInfo.getFormKey(), formInfo.getFormJson()));
        } else {
            formInfo.setFormType("none");
        }
    }

    FlowNode resolveFormFlowNode(BpmnModel bpmnModel, String taskDefKey) {
        if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
            return null;
        }
        Process process = bpmnModel.getMainProcess();
        if (!isBlank(taskDefKey)) {
            FlowElement element = process.getFlowElement(taskDefKey);
            if (element instanceof FlowNode) {
                return (FlowNode) element;
            }
        }

        FlowNode firstUserTask = null;
        for (FlowElement element : process.getFlowElements()) {
            if (element instanceof UserTask) {
                FlowNode candidate = (FlowNode) element;
                if (firstUserTask == null) {
                    firstUserTask = candidate;
                }
                if (readNodeFormConfig(candidate).hasForm()) {
                    return candidate;
                }
            }
        }
        return firstUserTask;
    }

    void hydrateFormInstanceSnapshotIfNecessary(TaskFormInfo formInfo, String processInstanceId, Long tenantId) {
        if (formInfo == null || FORM_TYPE_BUSINESS.equalsIgnoreCase(formInfo.getFormType())) {
            return;
        }
        if (!"dynamic".equalsIgnoreCase(formInfo.getFormType())
                && isBlank(formInfo.getFormKey())
                && isBlank(formInfo.getFormJson())) {
            return;
        }
        hydrateFormInstanceSnapshot(formInfo, processInstanceId, tenantId);
    }

    private void applyModelFormConfiguration(TaskFormInfo formInfo, FlowModel flowModel) {
        if (flowModel == null) {
            formInfo.setFormType("none");
            return;
        }
        String formType = flowModel.getFormType();
        formInfo.setFormType(formType);
        if ("dynamic".equals(formType)) {
            formInfo.setFormKey(flowModel.getFormId());
            formInfo.setFormJson(resolveModelFormJson(flowModel.getFormId(), flowModel.getFormJson()));
            return;
        }
        if ("external".equals(formType)) {
            formInfo.setFormUrl(flowModel.getFormId());
            formInfo.setFormTarget("modal");
            return;
        }
        if (FORM_TYPE_BUSINESS.equals(formType)) {
            Map<String, Object> formRef = readBusinessGlobalFormRef(flowModel.getFormJson());
            formInfo.setFormType(FORM_TYPE_BUSINESS);
            formInfo.setObjectCode(textValue(formRef.get("objectCode")));
            formInfo.setFormKey(firstNonBlank(textValue(formRef.get("formKey")), flowModel.getFormId()));
            formInfo.setFormMode(firstNonBlank(
                    textValue(formRef.get("formMode")),
                    textValue(formRef.get("type"))));
            formInfo.setFormName(textValue(formRef.get("formName")));
            formInfo.setProviderKey(textValue(formRef.get("providerKey")));
            formInfo.setFormUrl(textValue(formRef.get("formUrl")));
            formInfo.setViewKey(firstNonBlank(textValue(formRef.get("viewKey")), "default"));
            formInfo.setFormRef(new LinkedHashMap<>(formRef));
            formInfo.setFormTarget("modal");
            formInfo.setFormJson(flowModel.getFormJson());
        }
    }

    private void applyBusinessNodeFormConfiguration(TaskFormInfo formInfo, FlowModel flowModel,
                                                    NodeFormConfig nodeForm) {
        formInfo.setFormType(FORM_TYPE_BUSINESS);
        Map<String, Object> formRef = flowModel == null ? Map.of() : readBusinessGlobalFormRef(flowModel.getFormJson());
        Map<String, Object> mergedFormRef = mergeFormRef(formRef, nodeForm.formRef);
        formInfo.setFormKey(firstNonBlank(
                nodeForm.formKey,
                textValue(mergedFormRef.get("formKey")),
                flowModel == null ? null : flowModel.getFormId()));
        formInfo.setFormMode(firstNonBlank(
                nodeForm.formMode,
                textValue(mergedFormRef.get("formMode")),
                textValue(mergedFormRef.get("type"))));
        formInfo.setFormName(firstNonBlank(nodeForm.formName, textValue(mergedFormRef.get("formName"))));
        formInfo.setProviderKey(firstNonBlank(nodeForm.providerKey, textValue(mergedFormRef.get("providerKey"))));
        formInfo.setFormUrl(firstNonBlank(nodeForm.formUrl, textValue(mergedFormRef.get("formUrl"))));
        formInfo.setViewKey(firstNonBlank(nodeForm.viewKey, textValue(mergedFormRef.get("viewKey")), "default"));
        putIfPresent(mergedFormRef, "formKey", formInfo.getFormKey());
        putIfPresent(mergedFormRef, "formMode", formInfo.getFormMode());
        putIfPresent(mergedFormRef, "type", formInfo.getFormMode());
        putIfPresent(mergedFormRef, "formName", formInfo.getFormName());
        putIfPresent(mergedFormRef, "providerKey", formInfo.getProviderKey());
        putIfPresent(mergedFormRef, "formUrl", formInfo.getFormUrl());
        putIfPresent(mergedFormRef, "viewKey", formInfo.getViewKey());
        formInfo.setFormRef(mergedFormRef);
        formInfo.setFormTarget("modal");
        formInfo.setFormJson(flowModel == null ? null : flowModel.getFormJson());
        if (isBlank(formInfo.getObjectCode())) {
            formInfo.setObjectCode(firstNonBlank(
                    textValue(mergedFormRef.get("objectCode")),
                    flowModel == null ? null
                            : textValue(readBusinessGlobalFormRef(flowModel.getFormJson()).get("objectCode"))));
        }
    }

    private boolean isBusinessNodeForm(FlowModel flowModel, NodeFormConfig nodeForm) {
        String normalizedMode = normalizeFormMode(nodeForm == null ? null : nodeForm.formMode);
        if ("BUSINESS_CODE_FORM".equals(normalizedMode)) {
            return true;
        }
        if ("EXTERNAL".equals(normalizedMode)) {
            return false;
        }
        if ("BUSINESS_OBJECT_FORM".equals(normalizedMode)) {
            return !isBlank(nodeForm.providerKey)
                    || (flowModel != null && FORM_TYPE_BUSINESS.equalsIgnoreCase(flowModel.getFormType()));
        }
        return flowModel != null && FORM_TYPE_BUSINESS.equalsIgnoreCase(flowModel.getFormType());
    }

    private void hydrateResolvedFormSchema(TaskFormInfo formInfo, NodeFormConfig nodeForm) {
        if (formInfo == null || looksLikeFormSchema(formInfo.getFormJson())) {
            return;
        }
        String schema = resolveFormJson(
                formInfo.getFormKey(),
                nodeForm == null ? formInfo.getFormJson()
                        : firstNonBlank(nodeForm.formJson, formInfo.getFormJson()));
        if (looksLikeFormSchema(schema)) {
            formInfo.setFormJson(schema);
        }
    }

    private boolean shouldExposeAsDynamicForm(TaskFormInfo formInfo, FlowModel flowModel, NodeFormConfig nodeForm) {
        if (!looksLikeFormSchema(formInfo.getFormJson())) {
            return false;
        }
        if (!isBlank(formInfo.getProviderKey()) || !isBlank(nodeForm == null ? null : nodeForm.providerKey)) {
            return false;
        }
        return flowModel == null || !FORM_TYPE_BUSINESS.equalsIgnoreCase(flowModel.getFormType());
    }

    private boolean looksLikeFormSchema(String formJson) {
        if (isBlank(formJson)) {
            return false;
        }
        String text = formJson.trim();
        if (text.startsWith("[")) {
            return true;
        }
        if (!text.startsWith("{")) {
            return false;
        }
        return text.contains("\"field\"") || text.contains("\"rule\"") || text.contains("\"rules\"")
                || text.contains("\"schema\"") || text.contains("\"children\"");
    }

    private String normalizeFormMode(String value) {
        String mode = textValue(value);
        return mode == null ? null : mode.toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBusinessGlobalFormRef(String formJson) {
        if (isBlank(formJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> root = OBJECT_MAPPER.readValue(formJson, Map.class);
            Object nested = root.get("formRef");
            if (nested instanceof Map<?, ?> nestedMap) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) nestedMap);
                root.forEach(merged::putIfAbsent);
                return merged;
            }
            return root;
        } catch (Exception e) {
            log.warn("解析流程全局业务表单引用失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private void applyNodeFormReference(TaskFormInfo formInfo, NodeFormConfig nodeForm,
                                        Map<String, Object> fallbackRef) {
        Map<String, Object> mergedFormRef = mergeFormRef(fallbackRef, nodeForm.formRef);
        formInfo.setFormMode(firstNonBlank(
                nodeForm.formMode, textValue(mergedFormRef.get("formMode")), textValue(mergedFormRef.get("type"))));
        formInfo.setFormName(firstNonBlank(nodeForm.formName, textValue(mergedFormRef.get("formName"))));
        formInfo.setProviderKey(firstNonBlank(nodeForm.providerKey, textValue(mergedFormRef.get("providerKey"))));
        formInfo.setViewKey(firstNonBlank(nodeForm.viewKey, textValue(mergedFormRef.get("viewKey")), "default"));
        if (!mergedFormRef.isEmpty()) {
            putIfPresent(mergedFormRef, "formKey", formInfo.getFormKey());
            putIfPresent(mergedFormRef, "formMode", formInfo.getFormMode());
            putIfPresent(mergedFormRef, "type", formInfo.getFormMode());
            putIfPresent(mergedFormRef, "formName", formInfo.getFormName());
            putIfPresent(mergedFormRef, "providerKey", formInfo.getProviderKey());
            putIfPresent(mergedFormRef, "formUrl", formInfo.getFormUrl());
            putIfPresent(mergedFormRef, "viewKey", formInfo.getViewKey());
            formInfo.setFormRef(mergedFormRef);
        }
    }

    private Map<String, Object> mergeFormRef(Map<String, Object> base, Map<String, Object> overrides) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (value != null && !isBlank(String.valueOf(value))) {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (target != null && value != null && !isBlank(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private FlowNode resolveFormFlowNode(String processDefinitionId, String taskDefKey) {
        if (isBlank(processDefinitionId)) {
            return null;
        }
        return resolveFormFlowNode(repositoryService.getBpmnModel(processDefinitionId), taskDefKey);
    }

    private NodeFormConfig readNodeFormConfig(FlowNode flowNode) {
        NodeFormConfig config = new NodeFormConfig();
        if (flowNode == null) {
            return config;
        }
        if (flowNode instanceof UserTask) {
            config.formKey = ((UserTask) flowNode).getFormKey();
        }

        config.formUrl = flowNode.getAttributeValue(FLOWABLE_NS, "formUrl");
        config.formJson = flowNode.getAttributeValue(FLOWABLE_NS, "formJson");
        config.formTarget = flowNode.getAttributeValue(FLOWABLE_NS, "formTarget");
        config.formName = flowNode.getAttributeValue(FLOWABLE_NS, "formName");
        config.providerKey = flowNode.getAttributeValue(FLOWABLE_NS, "providerKey");
        config.viewKey = flowNode.getAttributeValue(FLOWABLE_NS, "viewKey");
        config.formMode = firstNonBlank(
                flowNode.getAttributeValue(FLOWABLE_NS, "formMode"),
                flowNode.getAttributeValue(FLOWABLE_NS, "formType"),
                flowNode.getAttributeValue(FLOWABLE_NS, "type"));
        config.formRef = readBusinessGlobalFormRef(flowNode.getAttributeValue(FLOWABLE_NS, "formRef"));
        config.formFieldPermissions = flowNode.getAttributeValue(FLOWABLE_NS, "formFieldPermissions");

        Map<String, List<ExtensionElement>> extensions = flowNode.getExtensionElements();
        if (extensions == null) {
            return config;
        }
        config.formJson = extensionText(config.formJson, extensions.get("formJson"));
        config.formUrl = extensionText(config.formUrl, extensions.get("formUrl"));
        config.formTarget = extensionText(config.formTarget, extensions.get("formTarget"));
        config.formName = extensionText(config.formName, extensions.get("formName"));
        config.providerKey = extensionText(config.providerKey, extensions.get("providerKey"));
        config.viewKey = extensionText(config.viewKey, extensions.get("viewKey"));
        config.formMode = extensionText(config.formMode, extensions.get("formMode"));
        config.formMode = extensionText(config.formMode, extensions.get("formType"));
        List<ExtensionElement> formRefElements = extensions.get("formRef");
        if ((config.formRef == null || config.formRef.isEmpty())
                && formRefElements != null && !formRefElements.isEmpty()) {
            config.formRef = readBusinessGlobalFormRef(formRefElements.get(0).getElementText());
        }
        config.formFieldPermissions = extensionText(
                config.formFieldPermissions, extensions.get("formFieldPermissions"));
        return config;
    }

    private String extensionText(String currentValue, List<ExtensionElement> elements) {
        if (!isBlank(currentValue) || elements == null || elements.isEmpty()) {
            return currentValue;
        }
        return elements.get(0).getElementText();
    }

    private void hydrateFormInstanceSnapshot(TaskFormInfo formInfo, String processInstanceId, Long tenantId) {
        if (flowFormInstanceMapper == null || processInstanceId == null || processInstanceId.isEmpty()
                || tenantId == null || tenantId <= 0) {
            return;
        }
        try {
            FlowFormInstance instance = flowFormInstanceMapper.selectByProcessInstanceIdAndTenantId(
                    processInstanceId, tenantId);
            if (instance == null) {
                return;
            }
            formInfo.setFormInstanceId(instance.getId());
            formInfo.setSchemaSnapshot(instance.getSchemaSnapshot());
            formInfo.setFormData(instance.getFormData());
            formInfo.setDataMode(instance.getDataMode());
            formInfo.setObjectCode(instance.getObjectCode());
            formInfo.setRecordId(instance.getRecordId());
            if (formInfo.getFormJson() == null || formInfo.getFormJson().isEmpty()) {
                formInfo.setFormJson(instance.getSchemaSnapshot());
            }
            if (formInfo.getFormKey() == null || formInfo.getFormKey().isEmpty()) {
                formInfo.setFormKey(instance.getFormKey());
            }
        } catch (Exception e) {
            log.warn("加载流程表单实例快照失败: processInstanceId={}", processInstanceId, e);
        }
    }

    private String resolveFormJson(String formKey, String inlineFormJson) {
        if (inlineFormJson != null && !inlineFormJson.isEmpty()) {
            return inlineFormJson;
        }
        if (formKey == null || formKey.isEmpty() || flowFormService == null) {
            return inlineFormJson;
        }
        try {
            return flowFormService.getFormSchema(formKey);
        } catch (Exception e) {
            log.warn("根据 formKey 获取动态表单失败：formKey={}", formKey, e);
            return inlineFormJson;
        }
    }

    private String resolveModelFormJson(String formId, String inlineFormJson) {
        if (inlineFormJson != null && !inlineFormJson.isEmpty()) {
            return inlineFormJson;
        }
        if (formId == null || formId.isEmpty() || flowFormService == null) {
            return inlineFormJson;
        }
        try {
            FlowForm form = flowFormService.getById(Long.valueOf(formId));
            return form != null ? form.getFormSchema() : inlineFormJson;
        } catch (NumberFormatException e) {
            return resolveFormJson(formId, inlineFormJson);
        } catch (Exception e) {
            log.warn("根据 formId 获取模型动态表单失败：formId={}", formId, e);
            return inlineFormJson;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class NodeFormConfig {
        private String formKey;
        private String formJson;
        private String formUrl;
        private String formTarget;
        private String formMode;
        private String formName;
        private String providerKey;
        private String viewKey;
        private Map<String, Object> formRef = Map.of();
        private String formFieldPermissions;

        private boolean hasForm() {
            return !isBlank(formKey) || !isBlank(formJson) || !isBlank(formUrl);
        }

        private static boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
