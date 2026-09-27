package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.taskform.TaskFormUiDocumentCompiler;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeFieldPermissions;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeEditMode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeFormMode;

/** Builds task form contexts while reusing one Flowable form snapshot and one runtime identity. */
@Slf4j
final class BusinessFlowTaskFormContextCoordinator {

    private final Supplier<FlowClient> flowClientSupplier;
    private final BusinessFlowTaskNodeFormResolver taskNodeFormResolver;
    private final BusinessFlowRuntimeContextResolver runtimeContextResolver;
    private final BusinessFlowTaskAccessPolicy taskAccessPolicy;
    private final BusinessFlowTaskFormPolicy taskFormPolicy;
    private final BusinessFlowTaskFormSchemaAssembler taskFormSchemaAssembler;
    private final BusinessFlowTaskChildAssembler taskChildAssembler;
    private final BusinessFlowTaskChildPolicy taskChildPolicy;
    private final BusinessFlowCodeFormCoordinator codeFormCoordinator;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessFlowTaskFormProfiler profiler;
    private final Supplier<Long> tenantIdSupplier;
    private final Supplier<Long> userIdSupplier;
    private final Supplier<BusinessProcessRunMapper> processRunMapperSupplier;
    private final Supplier<BusinessApplicationObjectMapper> applicationObjectMapperSupplier;
    private final BusinessObjectLookup businessObjectLookup;
    private final Function<AiBusinessObject, BusinessObjectVO> businessObjectConverter;
    private final FormSchemaResolver formSchemaResolver;
    private final SummaryResolver summaryResolver;

    BusinessFlowTaskFormContextCoordinator(
            Supplier<FlowClient> flowClientSupplier,
            BusinessFlowTaskNodeFormResolver taskNodeFormResolver,
            BusinessFlowRuntimeContextResolver runtimeContextResolver,
            BusinessFlowTaskAccessPolicy taskAccessPolicy,
            BusinessFlowTaskFormPolicy taskFormPolicy,
            BusinessFlowTaskFormSchemaAssembler taskFormSchemaAssembler,
            BusinessFlowTaskChildAssembler taskChildAssembler,
            BusinessFlowTaskChildPolicy taskChildPolicy,
            BusinessFlowCodeFormCoordinator codeFormCoordinator,
            DynamicCrudService dynamicCrudService,
            BusinessFlowTaskFormProfiler profiler,
            Supplier<Long> tenantIdSupplier,
            Supplier<Long> userIdSupplier,
            Supplier<BusinessProcessRunMapper> processRunMapperSupplier,
            Supplier<BusinessApplicationObjectMapper> applicationObjectMapperSupplier,
            BusinessObjectLookup businessObjectLookup,
            Function<AiBusinessObject, BusinessObjectVO> businessObjectConverter,
            FormSchemaResolver formSchemaResolver,
            SummaryResolver summaryResolver) {
        this.flowClientSupplier = flowClientSupplier;
        this.taskNodeFormResolver = taskNodeFormResolver;
        this.runtimeContextResolver = runtimeContextResolver;
        this.taskAccessPolicy = taskAccessPolicy;
        this.taskFormPolicy = taskFormPolicy;
        this.taskFormSchemaAssembler = taskFormSchemaAssembler;
        this.taskChildAssembler = taskChildAssembler;
        this.taskChildPolicy = taskChildPolicy;
        this.codeFormCoordinator = codeFormCoordinator;
        this.dynamicCrudService = dynamicCrudService;
        this.profiler = profiler;
        this.tenantIdSupplier = tenantIdSupplier;
        this.userIdSupplier = userIdSupplier;
        this.processRunMapperSupplier = processRunMapperSupplier;
        this.applicationObjectMapperSupplier = applicationObjectMapperSupplier;
        this.businessObjectLookup = businessObjectLookup;
        this.businessObjectConverter = businessObjectConverter;
        this.formSchemaResolver = formSchemaResolver;
        this.summaryResolver = summaryResolver;
    }

    BusinessTaskFormContextVO getTaskFormContext(BusinessTaskFormContextQueryDTO query) {
        return getActiveTaskFormContext(query, false);
    }

    BusinessTaskFormContextVO getActionableTaskFormContext(BusinessTaskFormContextQueryDTO query) {
        return getActiveTaskFormContext(query, true);
    }

    BusinessTaskFormContextVO getTaskFormReadonlyContext(BusinessTaskFormContextQueryDTO query) {
        BusinessTaskFormContextQueryDTO effectiveQuery = effectiveQuery(query);
        long startedAt = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        profiler.begin(stages);
        try {
            long mark = System.nanoTime();
            TaskFormRuntimeContext runtime = runtimeContextResolver.resolveTask(effectiveQuery, false);
            stages.put("runtimeContextMs", elapsedMillis(mark));
            BusinessTaskFormContextVO context = attachPrintRuntimeIdentity(
                    buildTaskFormContext(effectiveQuery, runtime, Map.of(), stages, startedAt), effectiveQuery);
            taskFormPolicy.makeReadonly(context);
            return context;
        } finally {
            profiler.end();
        }
    }

    BusinessTaskFormContextVO attachPrintRuntimeIdentity(
            BusinessTaskFormContextVO context,
            BusinessTaskFormContextQueryDTO query) {
        if (context == null) {
            return null;
        }
        applyQueryIdentityFallback(context, query);
        Long tenantId = tenantIdSupplier.get();
        String processInstanceId = StringUtils.trimToNull(context.getProcessInstanceId());
        BusinessProcessRunMapper processRunMapper = processRunMapperSupplier.get();
        AiBusinessProcessRun run = processRunMapper == null || processInstanceId == null
                ? null
                : processRunMapper.selectByProcessInstanceId(tenantId, processInstanceId);
        if (run != null) {
            context.setProcessRunId(run.getId());
            if (run.getApplicationId() != null) {
                String runApplicationId = String.valueOf(run.getApplicationId());
                if (StringUtils.isNotBlank(context.getApplicationId())
                        && !StringUtils.equals(context.getApplicationId(), runApplicationId)) {
                    context.getWarnings().add("流程运行应用身份与表单页面不一致，打印将使用流程运行版本");
                }
                context.setApplicationId(runApplicationId);
            }
        }
        fillUniquePublishedApplication(context, tenantId);
        return context;
    }

    BusinessTaskFormContextVO buildTaskFormContext(
            BusinessTaskFormContextQueryDTO query,
            TaskFormRuntimeContext runtime,
            Map<String, Object> taskFormInfo) {
        return buildTaskFormContext(
                query, runtime, taskFormInfo, new LinkedHashMap<>(), System.nanoTime());
    }

    BusinessTaskFormContextVO buildTaskFormContext(
            BusinessTaskFormContextQueryDTO query,
            TaskFormRuntimeContext runtime,
            Map<String, Object> taskFormInfo,
            Map<String, Long> preStages,
            long startedAt) {
        Map<String, Long> stages = preStages == null ? new LinkedHashMap<>() : preStages;
        BusinessTaskFormContextVO vo = baseContext(query, runtime, taskFormInfo);
        if (StringUtils.isBlank(runtime.objectCode())) {
            vo.getWarnings().add("未解析到业务对象");
            logTiming(query, runtime, null, stages, startedAt);
            return vo;
        }

        long mark = System.nanoTime();
        JSONObject nodeForm = taskNodeFormResolver.resolveTaskNodeForm(runtime, query, taskFormInfo);
        stages.put("nodeFormMs", elapsedMillis(mark));
        if (nodeForm == null || nodeForm.isEmpty()) {
            vo.getWarnings().add("当前节点未配置业务表单策略");
            logTiming(query, runtime, null, stages, startedAt);
            return vo;
        }
        String formMode = normalizeNodeFormMode(nodeForm.getString("formMode"));
        if (!"BUSINESS_OBJECT_FORM".equals(formMode)) {
            return buildNonObjectFormContext(query, runtime, nodeForm, formMode, vo, stages, startedAt);
        }
        if (StringUtils.isBlank(runtime.configKey())) {
            vo.getWarnings().add("业务对象缺少已发布运行配置，无法加载低代码业务表单");
            logTiming(query, runtime, null, stages, startedAt);
            return vo;
        }

        String formKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getFormKey()),
                StringUtils.trimToNull(nodeForm.getString("formKey")));
        mark = System.nanoTime();
        AiCrudConfig runtimeConfig = runtime.publishedConfig() != null
                ? runtime.publishedConfig()
                : taskFormSchemaAssembler.safeGetRuntimeConfig(runtime.configKey());
        JSONObject runtimeOptions = runtimeConfig == null
                ? new JSONObject() : readJsonObject(runtimeConfig.getOptions());
        stages.put("runtimeConfigMs", elapsedMillis(mark));

        mark = System.nanoTime();
        BusinessObjectVO object = runtime.businessObject() != null
                ? businessObjectConverter.apply(runtime.businessObject())
                : businessObjectLookup.query(tenantIdSupplier.get(), runtime.objectCode(), runtime.configKey());
        stages.put("businessObjectMs", elapsedMillis(mark));
        vo.setBusinessObjectName(object == null ? runtime.objectCode() : object.getObjectName());

        mark = System.nanoTime();
        JSONObject formSchema = formSchemaResolver.resolve(
                object, formKey, runtime.configKey(), runtimeConfig);
        stages.put("formSchemaMs", elapsedMillis(mark));
        if (formSchema.isEmpty()) {
            vo.getWarnings().add("未找到节点引用的低代码表单资产: " + formKey);
            logTiming(query, runtime, formKey, stages, startedAt);
            return vo;
        }

        mark = System.nanoTime();
        List<Map<String, Object>> fieldCatalog = taskFormSchemaAssembler.resolveBusinessTaskCrudPageFields(
                runtime.configKey(), formKey, formSchema, runtimeConfig, runtimeOptions);
        taskChildAssembler.enrichTaskMainFieldsFromObjectRegistry(fieldCatalog, object);
        List<Map<String, Object>> permissions = taskFormPolicy.normalizePermissions(
                fieldCatalog, normalizeFieldPermissions(nodeForm.get("fieldPermissions")));
        List<Map<String, Object>> fields = taskFormPolicy.buildFields(fieldCatalog, permissions);
        stages.put("fieldsMs", elapsedMillis(mark));

        mark = System.nanoTime();
        Map<String, Object> recordData = runtime.recordId() == null
                ? runtimeContextResolver.loadTaskVariablesAsRecord(query, taskFormInfo)
                : (runtimeConfig == null
                ? dynamicCrudService.selectById(runtime.configKey(), runtime.recordId())
                : dynamicCrudService.selectById(runtimeConfig, runtime.recordId()));
        Map<String, Object> visibleRecordData = taskFormPolicy.filterVisibleRecordData(recordData, fields);
        stages.put("recordMs", elapsedMillis(mark));

        mark = System.nanoTime();
        List<Map<String, Object>> childrenConfig = taskChildAssembler.resolveBusinessTaskChildrenConfig(
                runtime.configKey(), nodeForm, runtimeOptions, formSchema);
        taskChildPolicy.logChildren("raw", runtime.configKey(), runtime.recordId(), childrenConfig, visibleRecordData);
        taskChildPolicy.filterVisibleRecordChildren(visibleRecordData, childrenConfig);
        taskChildPolicy.logChildren("filtered", runtime.configKey(), runtime.recordId(), childrenConfig, visibleRecordData);
        stages.put("childrenMs", elapsedMillis(mark));

        populateBusinessObjectContext(
                vo, query, runtime, nodeForm, runtimeOptions, formSchema,
                formKey, object, recordData, visibleRecordData, permissions, fields, childrenConfig, stages);
        logTiming(query, runtime, vo.getFormKey(), stages, startedAt);
        return vo;
    }

    private BusinessTaskFormContextVO getActiveTaskFormContext(
            BusinessTaskFormContextQueryDTO query, boolean writeRequired) {
        BusinessTaskFormContextQueryDTO effectiveQuery = effectiveQuery(query);
        long startedAt = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        profiler.begin(stages);
        try {
            long mark = System.nanoTime();
            Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(effectiveQuery.getTaskId());
            stages.put("flowFormInfoMs", elapsedMillis(mark));
            mark = System.nanoTime();
            taskAccessPolicy.validate(
                    effectiveQuery, writeRequired, taskFormInfo,
                    flowClientSupplier.get() != null, userIdSupplier.get());
            stages.put("accessMs", elapsedMillis(mark));
            mark = System.nanoTime();
            TaskFormRuntimeContext runtime = runtimeContextResolver.resolveTask(
                    effectiveQuery, writeRequired, taskFormInfo);
            stages.put("runtimeContextMs", elapsedMillis(mark));
            return attachPrintRuntimeIdentity(
                    buildTaskFormContext(effectiveQuery, runtime, taskFormInfo, stages, startedAt), effectiveQuery);
        } finally {
            profiler.end();
        }
    }

    private BusinessTaskFormContextQueryDTO effectiveQuery(BusinessTaskFormContextQueryDTO query) {
        return query == null ? new BusinessTaskFormContextQueryDTO() : query;
    }

    private BusinessTaskFormContextVO baseContext(
            BusinessTaskFormContextQueryDTO query,
            TaskFormRuntimeContext runtime,
            Map<String, Object> taskFormInfo) {
        BusinessTaskFormContextVO vo = new BusinessTaskFormContextVO();
        vo.setTaskId(StringUtils.trimToNull(query.getTaskId()));
        vo.setBusinessKey(runtime.businessKey());
        vo.setProcessInstanceId(StringUtils.trimToNull(query.getProcessInstanceId()));
        vo.setProcessDefKey(StringUtils.trimToNull(query.getProcessDefKey()));
        vo.setTaskDefKey(StringUtils.trimToNull(query.getTaskDefKey()));
        vo.setObjectCode(runtime.objectCode());
        vo.setRecordId(runtime.recordId());
        vo.setConfigKey(runtime.configKey());
        vo.setFormType("none");
        if (taskFormInfo != null && !taskFormInfo.isEmpty()) {
            vo.setTaskFormInfo(new LinkedHashMap<>(taskFormInfo));
        }
        return vo;
    }

    private BusinessTaskFormContextVO buildNonObjectFormContext(
            BusinessTaskFormContextQueryDTO query,
            TaskFormRuntimeContext runtime,
            JSONObject nodeForm,
            String formMode,
            BusinessTaskFormContextVO vo,
            Map<String, Long> stages,
            long startedAt) {
        if ("BUSINESS_CODE_FORM".equals(formMode)) {
            BusinessTaskFormContextVO codeContext = codeFormCoordinator.build(
                    query, nodeForm, runtime.objectCode(), runtime.recordId(),
                    runtime.businessKey(), runtime.configKey());
            logTiming(query, runtime, codeContext.getFormKey(), stages, startedAt);
            return codeContext;
        }
        vo.setFormType(formMode);
        vo.setFormKey(StringUtils.trimToNull(nodeForm.getString("formKey")));
        vo.setFormName(StringUtils.trimToNull(nodeForm.getString("formName")));
        vo.setProviderKey(StringUtils.trimToNull(nodeForm.getString("providerKey")));
        vo.setFormUrl(StringUtils.trimToNull(nodeForm.getString("formUrl")));
        vo.setEditMode(normalizeNodeEditMode(nodeForm.getString("editMode")));
        vo.setFormRef(readNestedObject(nodeForm.get("formRef")));
        taskFormSchemaAssembler.applyPageFormIdentity(vo, vo.getFormRef());
        taskFormPolicy.applyApprovalPolicy(vo, nodeForm);
        vo.getWarnings().add("当前节点表单类型暂不由低代码业务表单渲染: " + formMode);
        logTiming(query, runtime, vo.getFormKey(), stages, startedAt);
        return vo;
    }

    private void populateBusinessObjectContext(
            BusinessTaskFormContextVO vo,
            BusinessTaskFormContextQueryDTO query,
            TaskFormRuntimeContext runtime,
            JSONObject nodeForm,
            JSONObject runtimeOptions,
            JSONObject formSchema,
            String formKey,
            BusinessObjectVO object,
            Map<String, Object> recordData,
            Map<String, Object> visibleRecordData,
            List<Map<String, Object>> permissions,
            List<Map<String, Object>> fields,
            List<Map<String, Object>> childrenConfig,
            Map<String, Long> stages) {
        vo.setBusinessSummary(summaryResolver.resolve(object, runtime, recordData));
        vo.setConfigured(true);
        vo.setFormType("business-object");
        vo.setFormKey(StringUtils.firstNonBlank(formKey, formSchema.getString("formKey")));
        vo.setFormName(StringUtils.defaultIfBlank(
                nodeForm.getString("formName"), formSchema.getString("formName")));
        vo.setViewKey(StringUtils.defaultIfBlank(nodeForm.getString("viewKey"), "default"));
        vo.setEditMode(taskFormPolicy.resolveEditMode(nodeForm, permissions));
        taskFormSchemaAssembler.applyBusinessObjectFormLayout(vo, formSchema, runtimeOptions);
        vo.setFormRef(readNestedObject(nodeForm.get("formRef")));
        taskFormSchemaAssembler.applyPageFormIdentity(vo, vo.getFormRef());
        vo.setFieldPermissions(permissions);
        vo.setFields(fields);

        long mark = System.nanoTime();
        vo.setFormAssets(taskFormSchemaAssembler.resolveBusinessTaskFormAssets(
                formSchema, runtime.configKey(), formKey, runtimeOptions));
        stages.put("formAssetsMs", elapsedMillis(mark));
        vo.setChildrenConfig(childrenConfig);
        vo.setRecordData(visibleRecordData);

        mark = System.nanoTime();
        vo.setProtocolVersion(TaskFormUiDocumentCompiler.PROTOCOL_VERSION);
        vo.setUiDocument(TaskFormUiDocumentCompiler.compile(
                formSchema, vo.getFormKey(), fields, permissions));
        stages.put("uiDocumentMs", elapsedMillis(mark));
        taskFormPolicy.applyApprovalPolicy(vo, nodeForm);
        if (fields.isEmpty()) {
            vo.getWarnings().add("当前业务表单没有可展示字段");
        }
    }

    private void applyQueryIdentityFallback(
            BusinessTaskFormContextVO context, BusinessTaskFormContextQueryDTO query) {
        if (query == null) {
            return;
        }
        if (StringUtils.isBlank(context.getProcessInstanceId())) {
            context.setProcessInstanceId(StringUtils.trimToNull(query.getProcessInstanceId()));
        }
        if (StringUtils.isBlank(context.getObjectCode())) {
            context.setObjectCode(StringUtils.trimToNull(query.getObjectCode()));
        }
        if (StringUtils.isBlank(context.getConfigKey())) {
            context.setConfigKey(StringUtils.trimToNull(query.getConfigKey()));
        }
    }

    private void fillUniquePublishedApplication(BusinessTaskFormContextVO context, Long tenantId) {
        String objectCode = StringUtils.trimToNull(context.getObjectCode());
        BusinessApplicationObjectMapper mapper = applicationObjectMapperSupplier.get();
        if (StringUtils.isNotBlank(context.getApplicationId()) || objectCode == null || mapper == null) {
            return;
        }
        List<Long> applicationIds = mapper.selectPublishedApplicationIdsByObjectIdentity(
                tenantId, objectCode, StringUtils.trimToNull(context.getConfigKey()));
        if (applicationIds != null && applicationIds.size() == 1) {
            context.setApplicationId(String.valueOf(applicationIds.get(0)));
        } else if (applicationIds != null && applicationIds.size() > 1) {
            context.getWarnings().add("业务对象归属多个已发布应用，无法确定流程打印模板范围");
        }
    }

    private void logTiming(BusinessTaskFormContextQueryDTO query,
                           TaskFormRuntimeContext runtime,
                           String formKey,
                           Map<String, Long> stages,
                           long startedAt) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info("[task-form-context] taskId={} objectCode={} recordId={} formKey={} totalMs={} stages={} notes={}",
                query == null ? null : query.getTaskId(),
                runtime == null ? null : runtime.objectCode(),
                runtime == null ? null : runtime.recordId(),
                formKey,
                elapsedMillis(startedAt),
                stages,
                profiler.notes());
    }

    private static long elapsedMillis(long startedAtNanos) {
        return BusinessFlowTaskFormProfiler.elapsedMillis(startedAtNanos);
    }

    @FunctionalInterface
    interface BusinessObjectLookup {
        BusinessObjectVO query(Long tenantId, String objectCode, String configKey);
    }

    @FunctionalInterface
    interface FormSchemaResolver {
        JSONObject resolve(BusinessObjectVO object, String formKey, String configKey, AiCrudConfig runtimeConfig);
    }

    @FunctionalInterface
    interface SummaryResolver {
        String resolve(BusinessObjectVO object, TaskFormRuntimeContext runtime, Map<String, Object> recordData);
    }
}
