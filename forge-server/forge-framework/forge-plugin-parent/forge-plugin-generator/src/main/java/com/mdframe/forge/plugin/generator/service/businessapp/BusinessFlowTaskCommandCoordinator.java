package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowCallbackDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowResubmitDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowWithdrawDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskActionDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormSaveDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.resolveFlowModelKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.buildBusinessKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeFieldPermissions;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeFormMode;

/**
 * Coordinates task-form writes and task commands.
 * <p>
 * The public service keeps the transaction boundary; this coordinator owns the command pipeline so every
 * action follows the same access, persistence, Flowable invocation and local-state synchronization order.
 */
@Slf4j
final class BusinessFlowTaskCommandCoordinator {

    private final Supplier<FlowClient> flowClientSupplier;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessFlowTaskNodeFormResolver taskNodeFormResolver;
    private final BusinessFlowRuntimeContextResolver runtimeContextResolver;
    private final BusinessFlowTaskFormContextCoordinator formContextCoordinator;
    private final BusinessFlowTaskEventCoordinator taskEventCoordinator;
    private final BusinessFlowCallbackCoordinator callbackCoordinator;
    private final BusinessFlowTaskAccessPolicy taskAccessPolicy;
    private final BusinessFlowTaskFormPolicy taskFormPolicy;
    private final BusinessFlowTaskChildPolicy taskChildPolicy;
    private final BusinessFlowTaskFormSchemaAssembler taskFormSchemaAssembler;
    private final BusinessFlowTaskChildAssembler taskChildAssembler;
    private final BusinessFlowCodeFormCoordinator codeFormCoordinator;
    private final Supplier<Long> tenantIdSupplier;
    private final Supplier<Long> userIdSupplier;
    private final BusinessObjectLookup businessObjectLookup;
    private final Function<AiBusinessObject, BusinessObjectVO> businessObjectConverter;
    private final FormSchemaResolver formSchemaResolver;
    private final Function<String, String> terminalResultResolver;

    BusinessFlowTaskCommandCoordinator(
            Supplier<FlowClient> flowClientSupplier,
            BusinessFlowInstanceLinkMapper flowInstanceLinkMapper,
            DynamicCrudService dynamicCrudService,
            BusinessFlowTaskNodeFormResolver taskNodeFormResolver,
            BusinessFlowRuntimeContextResolver runtimeContextResolver,
            BusinessFlowTaskFormContextCoordinator formContextCoordinator,
            BusinessFlowTaskEventCoordinator taskEventCoordinator,
            BusinessFlowCallbackCoordinator callbackCoordinator,
            BusinessFlowTaskAccessPolicy taskAccessPolicy,
            BusinessFlowTaskFormPolicy taskFormPolicy,
            BusinessFlowTaskChildPolicy taskChildPolicy,
            BusinessFlowTaskFormSchemaAssembler taskFormSchemaAssembler,
            BusinessFlowTaskChildAssembler taskChildAssembler,
            BusinessFlowCodeFormCoordinator codeFormCoordinator,
            Supplier<Long> tenantIdSupplier,
            Supplier<Long> userIdSupplier,
            BusinessObjectLookup businessObjectLookup,
            Function<AiBusinessObject, BusinessObjectVO> businessObjectConverter,
            FormSchemaResolver formSchemaResolver,
            Function<String, String> terminalResultResolver) {
        this.flowClientSupplier = flowClientSupplier;
        this.flowInstanceLinkMapper = flowInstanceLinkMapper;
        this.dynamicCrudService = dynamicCrudService;
        this.taskNodeFormResolver = taskNodeFormResolver;
        this.runtimeContextResolver = runtimeContextResolver;
        this.formContextCoordinator = formContextCoordinator;
        this.taskEventCoordinator = taskEventCoordinator;
        this.callbackCoordinator = callbackCoordinator;
        this.taskAccessPolicy = taskAccessPolicy;
        this.taskFormPolicy = taskFormPolicy;
        this.taskChildPolicy = taskChildPolicy;
        this.taskFormSchemaAssembler = taskFormSchemaAssembler;
        this.taskChildAssembler = taskChildAssembler;
        this.codeFormCoordinator = codeFormCoordinator;
        this.tenantIdSupplier = tenantIdSupplier;
        this.userIdSupplier = userIdSupplier;
        this.businessObjectLookup = businessObjectLookup;
        this.businessObjectConverter = businessObjectConverter;
        this.formSchemaResolver = formSchemaResolver;
        this.terminalResultResolver = terminalResultResolver;
    }

    BusinessTaskFormContextVO saveTaskFormContext(BusinessTaskFormSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException("业务待办表单参数不能为空");
        }
        BusinessTaskFormContextQueryDTO query = toContextQuery(dto);
        Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(query.getTaskId());
        validateTaskAccess(query, taskFormInfo);
        TaskFormRuntimeContext runtime = runtimeContextResolver.resolveTask(query, true, taskFormInfo);
        taskEventCoordinator.repairInitiatorModifyState(query, runtime, taskFormInfo);
        JSONObject nodeForm = taskNodeFormResolver.resolveTaskNodeForm(runtime, query, taskFormInfo);
        TaskFormSaveResult saveResult = persistTaskFormData(dto, query, runtime, nodeForm);
        BusinessTaskFormContextVO context = saveResult.context() != null
                ? saveResult.context()
                : formContextCoordinator.buildTaskFormContext(query, saveResult.runtime(), taskFormInfo);
        return formContextCoordinator.attachPrintRuntimeIdentity(context, query);
    }

    BusinessFlowRuntimeVO completeBusinessTask(BusinessTaskActionDTO dto) {
        if (dto == null) {
            throw new BusinessException("业务待办办理参数不能为空");
        }
        String action = normalizeAction(dto.getAction());
        FlowClient flowClient = requireFlowClient("流程服务未配置，无法办理业务待办");
        BusinessTaskFormContextQueryDTO query = toContextQuery(dto);
        Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(query.getTaskId());
        validateTaskAccess(query, taskFormInfo);
        TaskFormRuntimeContext runtime = runtimeContextResolver.resolveTask(query, true, taskFormInfo);
        if (dto.getData() != null && !dto.getData().isEmpty()) {
            JSONObject nodeForm = taskNodeFormResolver.resolveTaskNodeForm(runtime, query, taskFormInfo);
            runtime = persistTaskFormData(toTaskFormSaveDTO(dto, query), query, runtime, nodeForm).runtime();
        }

        Map<String, Object> variables = dto.getVariables() == null ? Map.of() : dto.getVariables();
        String userId = String.valueOf(userIdSupplier.get());
        FlowResult<Void> result = switch (action) {
            case "rejecttostart" -> flowClient.rejectToStart(
                    query.getTaskId(), userId, dto.getComment(), dto.getSignature(), resolveTrustedTaskTenant(dto),
                    dto.getIdempotencyKey(), dto.getRequestDigest());
            case "reject" -> flowClient.reject(
                    query.getTaskId(), userId, dto.getComment(), dto.getSignature(), resolveTrustedTaskTenant(dto),
                    dto.getIdempotencyKey(), dto.getRequestDigest());
            case "return" -> flowClient.returnTask(
                    query.getTaskId(), userId, dto.getComment(), dto.getSignature(),
                    StringUtils.trimToNull(dto.getTargetActivityId()), resolveTrustedTaskTenant(dto),
                    dto.getIdempotencyKey(), dto.getRequestDigest());
            default -> flowClient.approve(
                    query.getTaskId(), userId, dto.getComment(), dto.getSignature(), variables,
                    resolveTrustedTaskTenant(dto),
                    dto.getIdempotencyKey(), dto.getRequestDigest(), dto.getApprovalPointResults());
        };
        requireSuccess(result, "业务待办办理失败");
        return syncBusinessFlowStatusAfterTaskAction(runtime, query, action, variables);
    }

    BusinessFlowRuntimeVO recoverCapabilityTaskAction(BusinessTaskActionDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getTaskId())
                || StringUtils.isBlank(dto.getIdempotencyKey())
                || StringUtils.isBlank(dto.getRequestDigest())) {
            throw new BusinessException(409, "FLOW_RECOVERY_EVIDENCE_REQUIRED");
        }
        String action = StringUtils.defaultIfBlank(dto.getAction(), "approve").trim().toLowerCase();
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new BusinessException(409, "POLICY_MISMATCH");
        }
        FlowClient flowClient = requireFlowClient("流程服务未配置，无法恢复业务待办");
        Long tenantId = resolveTrustedTaskTenant(dto);
        String userId = String.valueOf(userIdSupplier.get());
        Map<String, Object> variables = Map.of();
        FlowResult<Void> result = "reject".equals(action)
                ? flowClient.reject(dto.getTaskId(), userId, dto.getComment(), dto.getSignature(),
                        tenantId, dto.getIdempotencyKey(), dto.getRequestDigest())
                : flowClient.approve(dto.getTaskId(), userId, dto.getComment(), dto.getSignature(), variables,
                        tenantId, dto.getIdempotencyKey(), dto.getRequestDigest());
        requireSuccess(result, "业务待办恢复失败");

        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId(dto.getTaskId());
        query.setObjectCode(dto.getObjectCode());
        query.setRecordId(dto.getRecordId());
        if (StringUtils.isNotBlank(dto.getObjectCode()) && dto.getRecordId() != null) {
            String objectCode = runtimeContextResolver.resolveCanonicalObjectCode(tenantId, dto.getObjectCode());
            query.setBusinessKey(buildBusinessKey(objectCode, dto.getRecordId()));
        }
        return syncBusinessFlowStatusAfterTaskAction(null, query, action, variables);
    }

    BusinessFlowRuntimeVO resubmit(BusinessFlowResubmitDTO dto) {
        if (dto == null) {
            throw new BusinessException("重提参数不能为空");
        }
        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId(dto.getTaskId());
        query.setBusinessKey(dto.getBusinessKey());
        query.setProcessInstanceId(dto.getProcessInstanceId());
        query.setProcessDefKey(dto.getProcessDefKey());
        query.setTaskDefKey(dto.getTaskDefKey());

        Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(query.getTaskId());
        validateTaskAccess(query, taskFormInfo);
        TaskFormRuntimeContext runtime = runtimeContextResolver.resolveTask(query, true, taskFormInfo);
        Map<String, Object> variables = dto.getVariables() == null ? Map.of() : dto.getVariables();
        FlowResult<Void> result = requireFlowClient("流程服务未配置，无法重提").approve(
                query.getTaskId(), String.valueOf(userIdSupplier.get()),
                StringUtils.defaultIfBlank(dto.getComment(), "修改后重提"), variables);
        requireSuccess(result, "重提失败");

        AiBusinessFlowInstanceLink link = findRuntimeLink(
                tenantIdSupplier.get(), query.getProcessInstanceId(), runtime.businessKey());
        if (link == null) {
            BusinessFlowRuntimeVO vo = new BusinessFlowRuntimeVO();
            vo.setObjectCode(runtime.objectCode());
            vo.setRecordId(runtime.recordId());
            vo.setBusinessKey(runtime.businessKey());
            vo.setProcessInstanceId(query.getProcessInstanceId());
            vo.setFlowStatus(BusinessDocumentFlowStatus.IN_PROCESS.getCode());
            vo.setMessage("已重提");
            return vo;
        }
        taskEventCoordinator.applyRunningFlowState(link, BusinessDocumentFlowStatus.IN_PROCESS);
        link.setVariablesSnapshot(BusinessFlowLinkRuntimeState.writeModifyTask(
                taskEventCoordinator.mergeLinkVariablesSnapshot(link, variables), null));
        flowInstanceLinkMapper.updateById(link);
        return toRuntimeVO(link, "已重提");
    }

    BusinessFlowRuntimeVO withdrawDocumentFlow(BusinessFlowWithdrawDTO dto) {
        if (dto == null) {
            throw new BusinessException("撤回参数不能为空");
        }
        Long tenantId = tenantIdSupplier.get();
        Long userId = userIdSupplier.get();
        if (userId == null) {
            throw new BusinessException("当前用户未登录，无法撤回流程");
        }
        FlowClient flowClient = requireFlowClient("流程服务未配置，无法撤回流程");
        String objectCode = StringUtils.trimToNull(dto.getObjectCode());
        if (objectCode != null) {
            objectCode = runtimeContextResolver.resolveCanonicalObjectCode(tenantId, objectCode);
        }
        String businessKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(dto.getBusinessKey()),
                objectCode != null && dto.getRecordId() != null
                        ? buildBusinessKey(objectCode, dto.getRecordId()) : null);
        AiBusinessFlowInstanceLink link = findRuntimeLink(
                tenantId, StringUtils.trimToNull(dto.getProcessInstanceId()), businessKey);
        if (link == null) {
            throw new BusinessException("未找到可撤回的流程实例");
        }
        if (isEndedLink(link) || !isRunningFlowStatus(link.getFlowStatus())) {
            throw new BusinessException("当前流程已结束，不能撤回");
        }
        if (!userId.equals(link.getStartUserId())) {
            throw new BusinessException("只有流程发起人可以撤回");
        }
        FlowResult<Void> result = flowClient.withdrawProcess(
                link.getProcessInstanceId(), String.valueOf(userId),
                StringUtils.defaultIfBlank(dto.getComment(), "申请人撤回"));
        requireSuccess(result, "撤回失败");

        BusinessFlowCallbackDTO callback = new BusinessFlowCallbackDTO();
        callback.setProcessInstanceId(link.getProcessInstanceId());
        callback.setBusinessKey(link.getBusinessKey());
        callback.setResult(BusinessDocumentFlowStatus.CANCELED.getCode());
        callback.setFlowStatus(BusinessDocumentFlowStatus.CANCELED.getCode());
        callback.setTenantId(link.getTenantId());
        callback.setOperatorId(userId);
        callbackCoordinator.handleLinkedCallback(link, callback);
        return toRuntimeVO(link, "流程已撤回");
    }

    private TaskFormSaveResult persistTaskFormData(
            BusinessTaskFormSaveDTO dto,
            BusinessTaskFormContextQueryDTO query,
            TaskFormRuntimeContext runtime,
            JSONObject nodeForm) {
        if (nodeForm == null || nodeForm.isEmpty()) {
            throw new BusinessException("当前流程节点未配置业务表单权限");
        }
        String formMode = normalizeNodeFormMode(nodeForm.getString("formMode"));
        if ("BUSINESS_CODE_FORM".equals(formMode)) {
            List<Map<String, Object>> permissions = normalizeFieldPermissions(nodeForm.get("fieldPermissions"));
            BusinessTaskFormSaveDTO filteredDto = taskFormPolicy.filterSaveData(dto, permissions);
            taskFormPolicy.validateRequiredFields(
                    permissions, filteredDto.getData(), dto.getData() == null ? Map.of() : dto.getData());
            return new TaskFormSaveResult(runtime, codeFormCoordinator.save(filteredDto, nodeForm));
        }
        if (!"BUSINESS_OBJECT_FORM".equals(formMode)) {
            throw new BusinessException("当前节点不是平台可保存的业务表单，不能通过平台保存业务字段");
        }
        List<Map<String, Object>> permissions = normalizeFieldPermissions(nodeForm.get("fieldPermissions"));
        String formKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getFormKey()),
                StringUtils.trimToNull(nodeForm.getString("formKey")));
        BusinessObjectVO object = runtime.businessObject() != null
                ? businessObjectConverter.apply(runtime.businessObject())
                : businessObjectLookup.query(tenantIdSupplier.get(), runtime.objectCode(), runtime.configKey());
        JSONObject formSchema = formSchemaResolver.resolve(
                object, formKey, runtime.configKey(), runtime.publishedConfig());
        if (permissions.isEmpty()) {
            List<Map<String, Object>> fieldCatalog = taskFormSchemaAssembler.resolveBusinessTaskCrudPageFields(
                    runtime.configKey(), formKey, formSchema);
            permissions = taskFormPolicy.normalizePermissions(fieldCatalog, permissions);
        }
        JSONObject runtimeOptions = runtime.publishedConfig() == null
                ? null : readJsonObject(runtime.publishedConfig().getOptions());
        List<Map<String, Object>> childrenConfig = taskChildAssembler.resolveBusinessTaskChildrenConfig(
                runtime.configKey(), nodeForm, runtimeOptions, formSchema);
        Map<String, DynamicCrudService.TaskChildPermission> childPermissions =
                taskChildPolicy.buildSavePermissions(childrenConfig, nodeForm);
        Set<String> writableFields = taskFormPolicy.collectPermissionFields(permissions, "writable", true);
        boolean hasWritableChildren = childPermissions.values().stream()
                .anyMatch(permission -> !permission.writableFields().isEmpty()
                        || permission.allowCreate() || permission.allowUpdate() || permission.allowDelete());
        if (writableFields.isEmpty() && !hasWritableChildren) {
            throw new BusinessException("当前节点没有可编辑业务字段");
        }

        Map<String, Object> input = dto.getData() == null ? Map.of() : dto.getData();
        Map<String, Object> mainInput = taskChildPolicy.extractMainPayload(input);
        Map<String, Object> childrenInput = taskChildPolicy.extractChildrenPayload(input);
        Map<String, Object> updateData = new LinkedHashMap<>();
        for (String field : writableFields) {
            if (mainInput.containsKey(field)) {
                updateData.put(field, mainInput.get(field));
            }
        }
        taskFormPolicy.validateRequiredFields(permissions, updateData, mainInput);
        if (updateData.isEmpty() && childrenInput.isEmpty()) {
            throw new BusinessException("未提交可编辑业务字段");
        }
        if (runtime.recordId() == null) {
            return createBusinessRecord(query, runtime, updateData, childrenInput);
        }

        Map<String, Object> taskData = new LinkedHashMap<>();
        taskData.put("main", updateData);
        if (!childrenInput.isEmpty()) {
            taskData.put("children", childrenInput);
        }
        dynamicCrudService.updateTaskEditableData(
                runtime.configKey(), runtime.recordId(), taskData, writableFields, childPermissions);
        return new TaskFormSaveResult(runtime, null);
    }

    private TaskFormSaveResult createBusinessRecord(
            BusinessTaskFormContextQueryDTO query,
            TaskFormRuntimeContext runtime,
            Map<String, Object> updateData,
            Map<String, Object> childrenInput) {
        if (!childrenInput.isEmpty()) {
            throw new BusinessException("业务待办尚未关联主记录，暂不支持新增子表明细");
        }
        if (StringUtils.isBlank(runtime.configKey())) {
            throw new BusinessException("业务对象缺少已发布运行配置，无法保存业务字段");
        }
        Map<String, Object> created = dynamicCrudService.insertInternal(runtime.configKey(), updateData);
        Long createdId = runtimeContextResolver.extractCreatedRecordId(created);
        if (createdId == null) {
            throw new BusinessException("保存业务单据失败");
        }
        ensureRuntimeLink(runtime, query, createdId);
        query.setRecordId(createdId);
        query.setObjectCode(runtime.objectCode());
        query.setBusinessKey(buildBusinessKey(runtime.objectCode(), createdId));
        TaskFormRuntimeContext createdRuntime = new TaskFormRuntimeContext(
                runtime.objectCode(), createdId, query.getBusinessKey(), runtime.configKey(),
                runtime.bindingConfig(), runtime.publishedConfig(), runtime.businessObject());
        return new TaskFormSaveResult(createdRuntime, null);
    }

    private BusinessFlowRuntimeVO syncBusinessFlowStatusAfterTaskAction(
            TaskFormRuntimeContext runtime,
            BusinessTaskFormContextQueryDTO query,
            String action,
            Map<String, Object> variables) {
        String businessKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getBusinessKey()),
                runtime == null ? null : StringUtils.trimToNull(runtime.businessKey()));
        String processInstanceId = StringUtils.trimToNull(query.getProcessInstanceId());
        AiBusinessFlowInstanceLink link = findRuntimeLink(tenantIdSupplier.get(), processInstanceId, businessKey);
        if (link == null) {
            BusinessFlowRuntimeVO vo = new BusinessFlowRuntimeVO();
            vo.setObjectCode(runtime == null ? null : runtime.objectCode());
            vo.setRecordId(runtime == null ? null : runtime.recordId());
            vo.setBusinessKey(businessKey);
            vo.setProcessInstanceId(processInstanceId);
            vo.setFlowStatus(BusinessDocumentFlowStatus.IN_PROCESS.getCode());
            vo.setMessage("业务待办已办理，未找到低代码流程实例关联");
            return vo;
        }
        String engineStatus = readFlowEngineBusinessStatus(resolveFlowEngineBusinessKey(link));
        String terminalResult = terminalResultResolver.apply(engineStatus);
        if (StringUtils.isNotBlank(terminalResult)) {
            BusinessFlowCallbackDTO callback = new BusinessFlowCallbackDTO();
            callback.setProcessInstanceId(StringUtils.firstNonBlank(processInstanceId, link.getProcessInstanceId()));
            callback.setBusinessKey(link.getBusinessKey());
            callback.setResult(terminalResult);
            callback.setFlowStatus(engineStatus);
            callback.setTenantId(link.getTenantId());
            callback.setOperatorId(userIdSupplier.get());
            callback.setVariables(variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables));
            callbackCoordinator.handleLinkedCallback(link, callback);
            return toRuntimeVO(link, "业务待办已办理，流程已结束");
        }
        BusinessDocumentFlowStatus targetStatus = "reject".equals(action) || "rejecttostart".equals(action)
                ? BusinessDocumentFlowStatus.NEED_MODIFY : BusinessDocumentFlowStatus.IN_PROCESS;
        taskEventCoordinator.applyRunningFlowState(link, targetStatus);
        return toRuntimeVO(link, "业务待办已办理，流程继续流转");
    }

    private void ensureRuntimeLink(
            TaskFormRuntimeContext runtime,
            BusinessTaskFormContextQueryDTO query,
            Long recordId) {
        if (runtime == null || query == null || recordId == null || StringUtils.isBlank(runtime.objectCode())) {
            return;
        }
        Long tenantId = tenantIdSupplier.get();
        String processInstanceId = StringUtils.trimToNull(query.getProcessInstanceId());
        String businessKey = buildBusinessKey(runtime.objectCode(), recordId);
        AiBusinessFlowInstanceLink existing = processInstanceId == null
                ? null : flowInstanceLinkMapper.selectByProcessInstanceId(tenantId, processInstanceId);
        if (existing != null) {
            existing.setObjectCode(runtime.objectCode());
            existing.setRecordId(recordId);
            existing.setBusinessKey(businessKey);
            flowInstanceLinkMapper.updateById(existing);
            return;
        }
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setTenantId(tenantId);
        link.setObjectCode(runtime.objectCode());
        link.setRecordId(recordId);
        link.setBusinessKey(businessKey);
        link.setFlowModelKey(StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getProcessDefKey()), resolveFlowModelKey(runtime.bindingConfig())));
        link.setProcessInstanceId(processInstanceId);
        link.setFlowStatus(BusinessDocumentFlowStatus.RUNNING.getCode());
        link.setStartUserId(userIdSupplier.get());
        link.setStartTime(LocalDateTime.now());
        link.setRoundNo(resolveNextRoundNo(flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, businessKey)));
        flowInstanceLinkMapper.insert(link);
    }

    private void validateTaskAccess(BusinessTaskFormContextQueryDTO query, Map<String, Object> task) {
        taskAccessPolicy.validate(
                query, true, task, flowClientSupplier.get() != null, userIdSupplier.get());
    }

    private String normalizeAction(String rawAction) {
        String action = StringUtils.defaultIfBlank(rawAction, "approve").trim().toLowerCase();
        if (!Set.of("approve", "reject", "rejecttostart", "return").contains(action)) {
            throw new BusinessException("当前业务待办仅支持同意、驳回、驳回至发起人或退回");
        }
        return action;
    }

    private Long resolveTrustedTaskTenant(BusinessTaskActionDTO dto) {
        Long currentTenantId = tenantIdSupplier.get();
        if (dto.getTenantId() != null && !dto.getTenantId().equals(currentTenantId)) {
            throw new BusinessException(403, "FLOW_TASK_TENANT_MISMATCH");
        }
        return currentTenantId;
    }

    private FlowClient requireFlowClient(String message) {
        FlowClient flowClient = flowClientSupplier.get();
        if (flowClient == null) {
            throw new BusinessException(message);
        }
        return flowClient;
    }

    private void requireSuccess(FlowResult<Void> result, String fallbackMessage) {
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(result == null
                    ? fallbackMessage : StringUtils.defaultIfBlank(result.getMsg(), fallbackMessage));
        }
    }

    private String readFlowEngineBusinessStatus(String businessKey) {
        FlowClient flowClient = flowClientSupplier.get();
        if (flowClient == null || StringUtils.isBlank(businessKey)) {
            return null;
        }
        try {
            FlowResult<Map<String, Object>> status = flowClient.getProcessStatus(businessKey);
            if (status == null || !status.isSuccess() || status.getData() == null) {
                return null;
            }
            return StringUtils.trimToNull(textValue(status.getData().get("status")));
        } catch (Exception e) {
            log.debug("[低代码流程状态] 读取 Flowable 业务状态失败: businessKey={}, error={}",
                    businessKey, e.getMessage());
            return null;
        }
    }

    private String resolveFlowEngineBusinessKey(AiBusinessFlowInstanceLink link) {
        JSONObject variables = link == null ? new JSONObject() : readJsonObject(link.getVariablesSnapshot());
        return link == null ? null : StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(variables.get("flowBusinessKey"))),
                StringUtils.trimToNull(link.getBusinessKey()));
    }

    private AiBusinessFlowInstanceLink findRuntimeLink(
            Long tenantId, String processInstanceId, String businessKey) {
        if (StringUtils.isNotBlank(processInstanceId)) {
            AiBusinessFlowInstanceLink link =
                    flowInstanceLinkMapper.selectByProcessInstanceId(tenantId, processInstanceId);
            if (link != null) {
                return link;
            }
        }
        return StringUtils.isBlank(businessKey)
                ? null : flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, businessKey);
    }

    private boolean isEndedLink(AiBusinessFlowInstanceLink link) {
        return link.getEndTime() != null
                || BusinessDocumentFlowStatus.APPROVED.matches(link.getResult())
                || BusinessDocumentFlowStatus.REJECTED.matches(link.getResult())
                || BusinessDocumentFlowStatus.CANCELED.matches(link.getResult())
                || BusinessDocumentFlowStatus.APPROVED.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.REJECTED.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.CANCELED.matches(link.getFlowStatus());
    }

    private boolean isRunningFlowStatus(String flowStatus) {
        return BusinessDocumentFlowStatus.STARTED.matches(flowStatus)
                || BusinessDocumentFlowStatus.RUNNING.matches(flowStatus)
                || BusinessDocumentFlowStatus.IN_PROCESS.matches(flowStatus)
                || BusinessDocumentFlowStatus.NEED_MODIFY.matches(flowStatus);
    }

    private int resolveNextRoundNo(AiBusinessFlowInstanceLink latest) {
        return latest == null || latest.getRoundNo() == null || latest.getRoundNo() < 1
                ? 1 : latest.getRoundNo() + 1;
    }

    private BusinessFlowRuntimeVO toRuntimeVO(AiBusinessFlowInstanceLink link, String message) {
        BusinessFlowRuntimeVO vo = new BusinessFlowRuntimeVO();
        vo.setLinkId(link.getId());
        vo.setObjectCode(link.getObjectCode());
        vo.setRecordId(link.getRecordId());
        vo.setBusinessKey(link.getBusinessKey());
        vo.setFlowModelKey(link.getFlowModelKey());
        vo.setProcessInstanceId(link.getProcessInstanceId());
        vo.setFlowStatus(link.getFlowStatus());
        vo.setResult(link.getResult());
        vo.setStartTime(link.getStartTime());
        vo.setEndTime(link.getEndTime());
        vo.setMessage(message);
        return vo;
    }

    private BusinessTaskFormContextQueryDTO toContextQuery(BusinessTaskFormSaveDTO dto) {
        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId(dto.getTaskId());
        query.setBusinessKey(dto.getBusinessKey());
        query.setProcessInstanceId(dto.getProcessInstanceId());
        query.setProcessDefKey(dto.getProcessDefKey());
        query.setTaskDefKey(dto.getTaskDefKey());
        query.setObjectCode(dto.getObjectCode());
        query.setObjectId(dto.getObjectId());
        query.setConfigKey(dto.getConfigKey());
        query.setSuiteCode(dto.getSuiteCode());
        query.setRecordId(dto.getRecordId());
        query.setFormKey(dto.getFormKey());
        return query;
    }

    private BusinessTaskFormContextQueryDTO toContextQuery(BusinessTaskActionDTO dto) {
        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId(dto.getTaskId());
        query.setBusinessKey(dto.getBusinessKey());
        query.setProcessInstanceId(dto.getProcessInstanceId());
        query.setProcessDefKey(dto.getProcessDefKey());
        query.setTaskDefKey(dto.getTaskDefKey());
        query.setObjectCode(dto.getObjectCode());
        query.setObjectId(dto.getObjectId());
        query.setConfigKey(dto.getConfigKey());
        query.setSuiteCode(dto.getSuiteCode());
        query.setRecordId(dto.getRecordId());
        query.setFormKey(dto.getFormKey());
        return query;
    }

    private BusinessTaskFormSaveDTO toTaskFormSaveDTO(
            BusinessTaskActionDTO dto, BusinessTaskFormContextQueryDTO query) {
        BusinessTaskFormSaveDTO saveDTO = new BusinessTaskFormSaveDTO();
        saveDTO.setTaskId(query.getTaskId());
        saveDTO.setBusinessKey(query.getBusinessKey());
        saveDTO.setProcessInstanceId(query.getProcessInstanceId());
        saveDTO.setProcessDefKey(query.getProcessDefKey());
        saveDTO.setTaskDefKey(query.getTaskDefKey());
        saveDTO.setObjectCode(query.getObjectCode());
        saveDTO.setObjectId(query.getObjectId());
        saveDTO.setConfigKey(query.getConfigKey());
        saveDTO.setSuiteCode(query.getSuiteCode());
        saveDTO.setRecordId(query.getRecordId());
        saveDTO.setFormKey(query.getFormKey());
        saveDTO.setData(dto.getData());
        return saveDTO;
    }

    @FunctionalInterface
    interface BusinessObjectLookup {
        BusinessObjectVO query(Long tenantId, String objectCode, String configKey);
    }

    @FunctionalInterface
    interface FormSchemaResolver {
        JSONObject resolve(BusinessObjectVO object, String formKey, String configKey, AiCrudConfig runtimeConfig);
    }

    private record TaskFormSaveResult(TaskFormRuntimeContext runtime, BusinessTaskFormContextVO context) {
    }
}
