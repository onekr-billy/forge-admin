package com.mdframe.forge.starter.flow.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.flow.client.spi.FlowBusinessListDisplayAdapter;
import com.mdframe.forge.flow.client.spi.FlowBusinessListDisplayItem;
import com.mdframe.forge.plugin.message.service.MessageService;
import com.mdframe.forge.starter.flow.dto.FlowApprovalPointResultDTO;
import com.mdframe.forge.starter.flow.dto.ProcessDiagramInfo;
import com.mdframe.forge.starter.flow.dto.TaskFormInfo;
import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowErrorLog;
import com.mdframe.forge.starter.flow.entity.FlowModel;
import com.mdframe.forge.starter.flow.entity.FlowTask;
import com.mdframe.forge.starter.flow.enums.FlowBusinessStatus;
import com.mdframe.forge.starter.flow.enums.FlowTaskStatus;
import com.mdframe.forge.starter.flow.enums.FlowTaskSignMode;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFormInstanceMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskCandidateMapper;
import com.mdframe.forge.starter.flow.entity.FlowTaskCandidate;
import com.mdframe.forge.starter.flow.enums.FlowTaskCandidateStatus;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import com.mdframe.forge.starter.flow.service.FlowFormService;
import com.mdframe.forge.starter.flow.service.FlowModelService;
import com.mdframe.forge.starter.flow.service.FlowNodeConfigService;
import com.mdframe.forge.starter.flow.service.FlowOrgIntegrationService;
import com.mdframe.forge.starter.flow.service.FlowTaskService;
import com.mdframe.forge.starter.flow.security.FlowAccessGuard;
import com.mdframe.forge.starter.flow.security.FlowCandidateMembershipResolver;
import com.mdframe.forge.starter.flow.vo.FlowHistoryItemVO;
import com.mdframe.forge.starter.flow.vo.FlowHistoryPageVO;
import com.mdframe.forge.starter.flow.vo.FlowTaskSignRelationVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程任务服务实现
 */
@Slf4j
@Service
public class FlowTaskServiceImpl extends ServiceImpl<FlowTaskMapper, FlowTask> implements FlowTaskService {

    private static final int MAX_DYNAMIC_SIGNERS = 50;

    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_REJECT = "reject";
    private static final String ACTION_REJECT_TO_START = "rejectToStart";
    private static final String ACTION_DELEGATE = "delegate";
    private static final String ACTION_RETURN = "return";
    private static final String ACTION_TERMINATE = "terminate";
    private static final String AUTO_APPROVAL_FIRST_ONLY = "firstOnly";
    private static final String AUTO_APPROVAL_CONSECUTIVE = "consecutive";
    private static final String AUTO_APPROVAL_NONE = "none";
    private static final String RETURN_SOURCE_ACTIVITY_ID = "FLOW_RETURN_SOURCE_ACTIVITY_ID";
    private static final int MAX_DETAIL_HISTORY_ITEMS = 1000;
    private static final String RETURN_TARGET_ACTIVITY_ID = "FLOW_RETURN_TARGET_ACTIVITY_ID";
    private static final String RETURN_TO_START_PENDING = "FLOW_RETURN_TO_START_PENDING";
    private static final String DIRECT_SEND_VARIABLE = "directSend";
    private static final String COMMENT_TYPE_APPROVAL_POINTS = "approvalPoints";

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;

    /**
     * 消息服务（可选注入）
     */
    @Autowired(required = false)
    private MessageService messageService;
    
    /**
     * 组织架构集成服务（可选注入）
     */
    @Autowired(required = false)
    private FlowOrgIntegrationService flowOrgIntegrationService;

    /**
     * 流程模型服务
     */
    @Autowired
    private FlowModelService flowModelService;

    /**
     * 流程节点配置服务
     */
    @Autowired
    private FlowNodeConfigService flowNodeConfigService;

    /**
     * 流程业务Mapper
     */
    @Autowired
    private FlowBusinessMapper flowBusinessMapper;

    @Autowired
    private FlowAccessGuard flowAccessGuard;
    
    @Autowired
    private FlowErrorLogService flowErrorLogService;

    @Autowired(required = false)
    private FlowFormService flowFormService;

    @Autowired(required = false)
    private FlowFormInstanceMapper flowFormInstanceMapper;

    @Autowired(required = false)
    private FlowBusinessListDisplayAdapter flowBusinessListDisplayAdapter;

    @Autowired(required = false)
    private FlowTaskCandidateMapper flowTaskCandidateMapper;

    @Autowired
    private FlowCandidateMembershipResolver candidateMembershipResolver;

    @Override
    public IPage<FlowTask> todoTasks(Page<FlowTask> page, String userId, String title, String category, Integer status) {
        return enrichTaskPage(this.getBaseMapper().selectTodoTasks(page, userId, title, category, status,
                SessionHelper.getTenantId(), candidateMembershipResolver.resolveCurrentSessionGroups()));
    }

    @Override
    public IPage<FlowTask> doneTasks(Page<FlowTask> page, String userId, String title, String category, Integer status) {
        return enrichTaskPage(this.getBaseMapper().selectDoneTasks(page, userId, title, category, status,
                SessionHelper.getTenantId(), SessionHelper.getActiveOrgId()));
    }

    @Override
    public IPage<FlowTask> startedTasks(Page<FlowTask> page, String userId, String title, String category, Integer status) {
        return enrichTaskPage(this.getBaseMapper().selectStartedTasks(page, userId, title, category, status,
                SessionHelper.getTenantId()));
    }

    @Override
    public IPage<FlowTask> candidateTasks(Page<FlowTask> page, String userId, String groupId, String title) {
        if ((userId == null || userId.isEmpty()) && (groupId == null || groupId.isEmpty())) {
            return page;
        }
        return enrichTaskPage(this.getBaseMapper().selectCandidateTasks(
                page, userId, groupId, title, SessionHelper.getTenantId()));
    }

    @Override
    public List<FlowTask> activeTasksByProcessInstances(Collection<String> processInstanceIds, String userId) {
        if (processInstanceIds == null || processInstanceIds.isEmpty() || isBlank(userId)) {
            return List.of();
        }
        List<String> ids = processInstanceIds.stream()
                .filter(id -> !isBlank(id))
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return List.of();
        }
        return this.getBaseMapper().selectActiveTasksByProcessInstances(
                ids, userId.trim(), requireTenantId());
    }

    private IPage<FlowTask> enrichTaskPage(IPage<FlowTask> page) {
        if (flowBusinessListDisplayAdapter == null || page == null || page.getRecords() == null
                || page.getRecords().isEmpty()) {
            return page;
        }
        List<FlowBusinessListDisplayItem> items = page.getRecords().stream()
                .map(this::toDisplayItem)
                .collect(Collectors.toList());
        try {
            flowBusinessListDisplayAdapter.enrich(items);
            for (int i = 0; i < page.getRecords().size(); i++) {
                applyDisplayItem(page.getRecords().get(i), items.get(i));
            }
        } catch (Exception e) {
            log.warn("补齐流程任务业务摘要失败，继续返回流程基础信息: {}", e.getMessage());
        }
        return page;
    }

    private FlowBusinessListDisplayItem toDisplayItem(FlowTask task) {
        FlowBusinessListDisplayItem item = new FlowBusinessListDisplayItem();
        item.setBusinessKey(task.getBusinessKey());
        item.setProcessInstanceId(task.getProcessInstanceId());
        item.setProcessDefKey(task.getProcessDefKey());
        item.setProcessName(task.getProcessName());
        item.setProcessDefinitionName(task.getProcessDefinitionName());
        item.setTaskId(task.getTaskId());
        item.setTaskName(task.getTaskName());
        item.setTitle(task.getTitle());
        item.setObjectCode(task.getObjectCode());
        item.setRecordId(task.getRecordId());
        item.setBusinessObjectName(task.getBusinessObjectName());
        item.setBusinessSummary(task.getBusinessSummary());
        item.setBusinessType(task.getBusinessType());
        item.setBusinessParams(task.getBusinessParams());
        item.setDisplayExtensions(task.getDisplayExtensions());
        return item;
    }

    private void applyDisplayItem(FlowTask task, FlowBusinessListDisplayItem item) {
        if (item == null) {
            return;
        }
        task.setObjectCode(firstNonBlank(item.getObjectCode(), task.getObjectCode()));
        task.setRecordId(item.getRecordId() != null ? item.getRecordId() : task.getRecordId());
        task.setBusinessObjectName(firstNonBlank(item.getBusinessObjectName(), task.getBusinessObjectName()));
        task.setBusinessSummary(firstNonBlank(item.getBusinessSummary(), task.getBusinessSummary()));
        task.setBusinessType(firstNonBlank(item.getBusinessType(), task.getBusinessType()));
        task.setBusinessParams(item.getBusinessParams() != null ? item.getBusinessParams() : task.getBusinessParams());
        task.setDisplayExtensions(item.getDisplayExtensions() != null ? item.getDisplayExtensions() : task.getDisplayExtensions());
        task.setProcessName(firstNonBlank(task.getProcessName(), item.getProcessName()));
        task.setProcessDefinitionName(firstNonBlank(
                task.getProcessDefinitionName(),
                item.getProcessDefinitionName(),
                task.getProcessName(),
                task.getProcessDefKey()));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimTask(String taskId, String userId) {
        if (isBlank(taskId) || isBlank(userId)) {
            throw new IllegalArgumentException("FLOW_TASK_CLAIM_CONTEXT_INVALID");
        }
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
        }
        FlowTask localTask = baseMapper.selectByTaskIdForUpdateAndTenant(taskId, tenantId);
        Task runtimeTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (localTask == null || runtimeTask == null || !tenantId.equals(localTask.getTenantId())
                || !FlowTaskStatus.PENDING.matches(localTask.getStatus())
                || !isClaimCandidate(localTask, runtimeTask, userId.trim())) {
            throw new IllegalStateException("FLOW_TASK_CLAIM_NOT_ALLOWED");
        }
        taskService.claim(taskId, userId);
        
        FlowTask task = new FlowTask();
        task.setTaskId(taskId);
        task.setAssignee(userId);
        task.setStatus(FlowTaskStatus.CLAIMED.getCode());
        task.setClaimTime(LocalDateTime.now());
        
        updateTaskByTenant(taskId, task);
        log.info("签收任务：taskId={}, userId={}", taskId, userId);
    }

    private boolean isClaimCandidate(FlowTask localTask, Task runtimeTask, String userId) {
        if (containsCsv(localTask.getCandidateUsers(), userId)) {
            return true;
        }
        if (taskService.createTaskQuery().taskId(runtimeTask.getId()).taskCandidateUser(userId).singleResult() != null) {
            return true;
        }
        Set<String> groups = candidateMembershipResolver.resolveCurrentSessionGroups();
        for (String group : splitIds(localTask.getCandidateGroups())) {
            if (groups.contains(group)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCsv(String csv, String value) {
        return splitIds(csv).contains(value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String taskId, String userId, String comment, String signature, Map<String, Object> variables) {
        approve(taskId, userId, comment, signature, variables, SessionHelper.getTenantId(), null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String taskId, String userId, String comment, String signature,
                        Map<String, Object> variables, Long tenantId,
                        String idempotencyKey, String requestDigest,
                        List<FlowApprovalPointResultDTO> approvalPointResults) {
        FlowTask storedTask = authorizeTaskAction(
                taskId, userId, tenantId, "APPROVE", idempotencyKey, requestDigest, FlowTaskStatus.APPROVED);
        if (storedTask == null) {
            return;
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        validateFlowableAssignee(task, userId);
        // 整个审批事务只解析一次 BPMN：动作校验/必填变量/审批要点与自动同意模式共用，
        // 避免 getBpmnModel（每次一条命令往返）在同一请求内重复执行
        BpmnModel actionBpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        FlowTaskNodePolicy nodePolicy = taskNodePolicy();
        FlowNode actionFlowNode = nodePolicy.resolveFlowNode(actionBpmnModel, task.getTaskDefinitionKey());
        nodePolicy.validateTaskAction(task, ACTION_APPROVE, comment, signature, actionFlowNode);
        validateDynamicFormArrayVariables(task, actionFlowNode, variables);
        nodePolicy.validateRequiredVariables(variables, actionFlowNode);
        nodePolicy.validateApprovalPoints(approvalPointResults, actionFlowNode);

        try {
            if (comment != null && !comment.isEmpty()) {
                taskService.addComment(taskId, task.getProcessInstanceId(), comment);
            }
            nodePolicy.recordApprovalPointResults(task, approvalPointResults);

            Map<String, Object> completeVariables = mergeActionVariables(variables, true);
            completeTask(task, completeVariables);
            directSendAfterReturn(task, completeVariables, userId);

            FlowTask flowTask = new FlowTask();
            flowTask.setStatus(FlowTaskStatus.APPROVED.getCode());
            flowTask.setComment(comment);
            flowTask.setSignature(signature);
            flowTask.setCompleteTime(LocalDateTime.now());
            flowTask.setActionIdempotencyKey(idempotencyKey);
            flowTask.setActionRequestDigest(requestDigest);
            flowTask.setActionType(idempotencyKey == null ? null : "APPROVE");
            updateTaskActionResultRequired(taskId, flowTask);

            log.info("审批通过：taskId={}, userId={}", taskId, userId);
            autoApproveRepeatedTasks(task.getProcessInstanceId(),
                    actionBpmnModel == null ? null : actionBpmnModel.getMainProcess());
        } catch (Exception e) {
            recordTaskError(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(),
                    task.getName(), "TASK_APPROVE", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, String userId, String comment, String signature) {
        reject(taskId, userId, comment, signature, SessionHelper.getTenantId(), null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, String userId, String comment, String signature,
                       Long tenantId, String idempotencyKey, String requestDigest) {
        rejectInternal(taskId, userId, comment, signature, tenantId, idempotencyKey, requestDigest, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectToStart(String taskId, String userId, String comment, String signature,
                              Long tenantId, String idempotencyKey, String requestDigest) {
        rejectInternal(taskId, userId, comment, signature, tenantId, idempotencyKey, requestDigest, true);
    }

    private void rejectInternal(String taskId, String userId, String comment, String signature,
                                Long tenantId, String idempotencyKey, String requestDigest,
                                boolean rejectToStart) {
        FlowTask storedTask = authorizeTaskAction(
                taskId, userId, tenantId, rejectToStart ? "REJECT_TO_START" : "REJECT",
                idempotencyKey, requestDigest, FlowTaskStatus.REJECTED);
        if (storedTask == null) {
            return;
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        validateFlowableAssignee(task, userId);
        taskNodePolicy().validateTaskAction(
                task, rejectToStart ? ACTION_REJECT_TO_START : ACTION_REJECT, comment, signature);

        try {
            if (comment != null && !comment.isEmpty()) {
                taskService.addComment(taskId, task.getProcessInstanceId(), comment);
            }

            Map<String, Object> variables = mergeActionVariables(null, false);
            if (rejectToStart) {
                variables.put("rejectToStart", true);
                // 保留原驳回节点，供发起人修改后选择直送；具体修改节点仍由业务 BPMN 回路决定。
                runtimeService.setVariable(task.getProcessInstanceId(), RETURN_SOURCE_ACTIVITY_ID,
                        task.getTaskDefinitionKey());
                runtimeService.removeVariable(task.getProcessInstanceId(), RETURN_TARGET_ACTIVITY_ID);
                runtimeService.setVariable(task.getProcessInstanceId(), RETURN_TO_START_PENDING, true);
            }
            completeTask(task, variables);

            FlowTask flowTask = new FlowTask();
            flowTask.setStatus(FlowTaskStatus.REJECTED.getCode());
            flowTask.setComment(comment);
            flowTask.setSignature(signature);
            flowTask.setCompleteTime(LocalDateTime.now());
            flowTask.setActionIdempotencyKey(idempotencyKey);
            flowTask.setActionRequestDigest(requestDigest);
            flowTask.setActionType(idempotencyKey == null ? null
                    : (rejectToStart ? "REJECT_TO_START" : "REJECT"));
            updateTaskActionResultRequired(taskId, flowTask);

            log.info("审批驳回：taskId={}, userId={}", taskId, userId);
        } catch (Exception e) {
            recordTaskError(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(),
                    task.getName(), rejectToStart ? "TASK_REJECT_TO_START" : "TASK_REJECT", e);
            throw e;
        }
    }

    /**
     * 在 Flow 服务最终副作用边界重新校验租户、签收人与任务状态。
     * 返回 null 表示命中已成功的同请求幂等结果。
     */
    private FlowTask authorizeTaskAction(String taskId, String userId, Long tenantId,
                                         String actionType, String idempotencyKey,
                                         String requestDigest, FlowTaskStatus completedStatus) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
        }
        FlowTask storedTask = baseMapper.selectByTaskIdForUpdateAndTenant(taskId, tenantId);
        if (FlowTaskActionAuthorization.authorize(
                storedTask, userId, tenantId, actionType,
                idempotencyKey, requestDigest, completedStatus)) {
            return null;
        }
        return storedTask;
    }

    private void validateFlowableAssignee(Task task, String userId) {
        if (!userId.equals(task.getAssignee())) {
            throw new RuntimeException("FLOW_TASK_ASSIGNEE_MISMATCH");
        }
    }

    private void updateTaskActionResultRequired(String taskId, FlowTask flowTask) {
        if (!updateTaskByTenant(taskId, flowTask)) {
            throw new IllegalStateException("FLOW_TASK_STATE_UPDATE_FAILED");
        }
    }

    private boolean updateTaskByTenant(String taskId, FlowTask task) {
        Long tenantId = requireTenantId();
        return baseMapper.updateByTaskIdAndTenant(taskId, tenantId, task) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegate(String taskId, String userId, String targetUserId, String comment, String signature) {
        delegate(taskId, userId, targetUserId, comment, signature,
                SessionHelper.getTenantId(), null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegate(String taskId, String userId, String targetUserId, String comment, String signature,
                         Long tenantId, String idempotencyKey, String requestDigest) {
        if (isBlank(targetUserId)) {
            throw new RuntimeException("新处理人不能为空");
        }
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
        }
        assertTaskMutationActor(taskId, userId, false);
        validateReassignTarget(targetUserId.trim());
        FlowTask storedTask = authorizeTaskAction(taskId, userId, tenantId, "DELEGATE",
                idempotencyKey, requestDigest, FlowTaskStatus.CLAIMED);
        if (storedTask == null) {
            return;
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        taskNodePolicy().validateTaskAction(task, ACTION_DELEGATE, comment, signature);

        try {
            String owner = task.getAssignee() != null && !task.getAssignee().isEmpty()
                    ? task.getAssignee()
                    : userId;
            if (owner != null && !owner.isEmpty()) {
                taskService.setOwner(taskId, owner);
            }
            taskService.setAssignee(taskId, targetUserId.trim());

            FlowTask flowTask = new FlowTask();
            // Flowable 已经设置了 assignee，镜像状态必须是已签收；写成待办会导致
            // 目标用户再次签收失败但列表仍显示“待办”的状态不一致。
            flowTask.setStatus(FlowTaskStatus.CLAIMED.getCode());
            flowTask.setComment(comment);
            flowTask.setSignature(signature);
            flowTask.setAssignee(targetUserId.trim());
            flowTask.setOwner(owner);
            flowTask.setActionIdempotencyKey(idempotencyKey);
            flowTask.setActionRequestDigest(requestDigest);
            flowTask.setActionType(idempotencyKey == null ? null : "DELEGATE");
            updateTaskActionResultRequired(taskId, flowTask);

            log.info("转办任务：taskId={}, from={}, to={}", taskId, userId, targetUserId);
        } catch (Exception e) {
            recordTaskError(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(),
                    task.getName(), "TASK_DELEGATE", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(String taskId, String userId, String comment, String signature) {
        returnTask(taskId, userId, comment, signature, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(String taskId, String userId, String comment, String signature,
                           String requestedTargetActivityId) {
        assertTaskTenantForAction(taskId);
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        validateFlowableAssignee(task, userId);
        FlowTaskNodePolicy nodePolicy = taskNodePolicy();
        nodePolicy.validateReturnAction(task, comment, signature, requestedTargetActivityId);

        try {
            String targetActivityId = nodePolicy.resolveReturnTarget(task, requestedTargetActivityId);
            if (targetActivityId == null || targetActivityId.isEmpty()) {
                throw new RuntimeException("当前任务没有可退回的上一审批节点");
            }

            if (comment != null && !comment.isEmpty()) {
                taskService.addComment(taskId, task.getProcessInstanceId(), "退回：" + comment);
            }

            // 保存“谁发起退回、退回到哪里”，供修正节点选择直送时使用。
            runtimeService.setVariable(task.getProcessInstanceId(), RETURN_SOURCE_ACTIVITY_ID,
                    task.getTaskDefinitionKey());
            runtimeService.setVariable(task.getProcessInstanceId(), RETURN_TARGET_ACTIVITY_ID, targetActivityId);
            runtimeService.removeVariable(task.getProcessInstanceId(), RETURN_TO_START_PENDING);

            List<String> currentActivityIds = runtimeService.getActiveActivityIds(task.getProcessInstanceId());
            if (currentActivityIds == null || currentActivityIds.isEmpty()) {
                currentActivityIds = Collections.singletonList(task.getTaskDefinitionKey());
            }
            if (currentActivityIds.size() != 1
                    || !Objects.equals(currentActivityIds.get(0), task.getTaskDefinitionKey())) {
                throw new RuntimeException("当前流程存在多个活动分支，不能安全退回指定节点");
            }
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(task.getProcessInstanceId())
                    .moveActivityIdTo(task.getTaskDefinitionKey(), targetActivityId)
                    .changeState();

            FlowTask flowTask = new FlowTask();
            flowTask.setStatus(FlowTaskStatus.RETURNED.getCode());
            flowTask.setComment(comment);
            flowTask.setSignature(signature);
            flowTask.setCompleteTime(LocalDateTime.now());
            updateTaskByTenant(taskId, flowTask);

            log.info("退回任务：taskId={}, userId={}, targetActivityId={}", taskId, userId, targetActivityId);
        } catch (Exception e) {
            recordTaskError(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(),
                    task.getName(), "TASK_RETURN", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reassignByInitiator(String taskId, String userId, String targetUserId, String reason) {
        if (isBlank(targetUserId)) {
            throw new RuntimeException("任务不存在或新处理人不能为空");
        }
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new RuntimeException("FLOW_TASK_TENANT_REQUIRED");
        }
        FlowTask localTask = baseMapper.selectByTaskIdForUpdateAndTenant(taskId, tenantId);
        if (localTask == null || !tenantId.equals(localTask.getTenantId())) {
            throw new RuntimeException("FLOW_TASK_TENANT_MISMATCH");
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || !Objects.equals(localTask.getProcessInstanceId(), task.getProcessInstanceId())) {
            throw new RuntimeException("FLOW_TASK_NOT_FOUND");
        }
        FlowBusiness business = flowBusinessMapper.selectByProcessInstanceIdAndTenantIdForUpdate(
                task.getProcessInstanceId(), tenantId);
        if (business == null || !Objects.equals(business.getProcessInstanceId(), task.getProcessInstanceId())) {
            throw new RuntimeException("FLOW_TASK_TENANT_MISMATCH");
        }
        boolean allowed = Objects.equals(userId, task.getAssignee())
                || Objects.equals(userId, task.getOwner())
                || (business != null && Objects.equals(userId, business.getApplyUserId()));
        if (!allowed) {
            throw new RuntimeException("仅当前处理人、任务拥有人或流程发起人可以改派");
        }
        validateReassignTarget(targetUserId.trim());
        String owner = !isBlank(task.getAssignee()) ? task.getAssignee() : userId;
        taskService.setOwner(taskId, owner);
        taskService.setAssignee(taskId, targetUserId.trim());
        if (!isBlank(reason)) {
            taskService.addComment(taskId, task.getProcessInstanceId(), "改派", reason.trim());
        }
        FlowTask flowTask = new FlowTask();
        flowTask.setAssignee(targetUserId.trim());
        flowTask.setOwner(owner);
        flowTask.setStatus(FlowTaskStatus.CLAIMED.getCode());
        flowTask.setComment(reason);
        if (!updateTaskByTenant(taskId, flowTask)) {
            throw new IllegalStateException("改派任务状态同步失败");
        }
        log.info("流程任务改派：taskId={}, from={}, to={}", taskId, userId, targetUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateTask(String taskId, String userId, String comment, String signature) {
        assertTaskMutationActor(taskId, userId, true);
        Long tenantId = requireTenantId();
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        taskNodePolicy().validateTaskAction(task, ACTION_TERMINATE, comment, signature);

        try {
            List<String> activeTaskIds = taskService.createTaskQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .list()
                    .stream()
                    .map(Task::getId)
                    .filter(Objects::nonNull)
                    .toList();
            String reason = comment != null && !comment.isBlank() ? comment : "审批人终结流程";
            taskService.addComment(taskId, task.getProcessInstanceId(), "终结流程：" + reason);
            runtimeService.deleteProcessInstance(task.getProcessInstanceId(), reason);

            if (!activeTaskIds.isEmpty()) {
                baseMapper.updateProcessTaskStatusByTaskIds(activeTaskIds, tenantId,
                        FlowTaskStatus.TERMINATED.getCode(), LocalDateTime.now());
            }

            FlowBusiness business = flowBusinessMapper.selectByProcessInstanceIdAndTenantIdForUpdate(
                    task.getProcessInstanceId(), tenantId);
            if (business != null) {
                business.setStatus(FlowBusinessStatus.TERMINATED.getCode());
                business.setEndTime(LocalDateTime.now());
                business.setUpdateTime(LocalDateTime.now());
                flowBusinessMapper.updateById(business);
            }

            FlowTask flowTask = new FlowTask();
            flowTask.setStatus(FlowTaskStatus.TERMINATED.getCode());
            flowTask.setComment(comment);
            flowTask.setSignature(signature);
            flowTask.setCompleteTime(LocalDateTime.now());
            updateTaskByTenant(taskId, flowTask);

            log.info("审批人终结流程：taskId={}, processInstanceId={}, userId={}",
                    taskId, task.getProcessInstanceId(), userId);
        } catch (Exception e) {
            recordTaskError(task.getProcessInstanceId(), taskId, task.getTaskDefinitionKey(),
                    task.getName(), "TASK_TERMINATE", e);
            throw e;
        }
    }

    private Map<String, Object> mergeActionVariables(Map<String, Object> variables, boolean approved) {
        Map<String, Object> completeVariables = variables != null ? new HashMap<>(variables) : new HashMap<>();
        completeVariables.put("approved", approved);
        completeVariables.put("approvalResult", approved ? "approve" : "reject");
        // rejectToStart 是流程实例变量。每次普通动作都显式清零，避免上一个节点
        // 的“退回发起人修改”标记残留并误命中后续专用路由。
        completeVariables.put("rejectToStart", false);
        return completeVariables;
    }

    private void validateDynamicFormArrayVariables(Task task,
                                                   FlowNode flowNode,
                                                   Map<String, Object> submittedVariables) {
        formConfigurationResolver().validateDynamicFormArrayVariables(task, flowNode, submittedVariables);
    }

    /**
     * 退回节点修正后，按用户选择将新任务直接送回原驳回节点，跳过中间节点。
     * Flowable complete 后才会创建后继任务，因此这里基于完成后的活动列表做一次状态迁移。
     */
    private void directSendAfterReturn(Task completedTask, Map<String, Object> actionVariables, String userId) {
        String processInstanceId = completedTask.getProcessInstanceId();
        if (!isProcessRunning(processInstanceId)) {
            return;
        }
        Object source;
        Object target;
        Object returnToStartPending;
        // 三枚直送标记一次全量取回，替代逐 key getVariable 的 3 次独立往返（语义一致：均为流程级变量）
        Map<String, Object> returnVariables = runtimeService.getVariables(processInstanceId);
        source = returnVariables == null ? null : returnVariables.get(RETURN_SOURCE_ACTIVITY_ID);
        target = returnVariables == null ? null : returnVariables.get(RETURN_TARGET_ACTIVITY_ID);
        returnToStartPending = returnVariables == null ? null : returnVariables.get(RETURN_TO_START_PENDING);
        boolean returnedToHistoricalNode = target != null
                && Objects.equals(String.valueOf(target), completedTask.getTaskDefinitionKey());
        if (source == null || (!returnedToHistoricalNode && !Boolean.TRUE.equals(readBoolean(returnToStartPending)))) {
            return;
        }
        boolean directSend = Boolean.TRUE.equals(readBoolean(
                actionVariables == null ? null : actionVariables.get(DIRECT_SEND_VARIABLE)));
        if (!directSend) {
            clearDirectSendMarks(processInstanceId);
            return;
        }
        if (Boolean.TRUE.equals(readBoolean(returnToStartPending))
                && !isProcessStarterTask(completedTask, userId)) {
            throw new RuntimeException("仅流程发起人可以执行驳回后的直送");
        }
        String sourceActivityId = String.valueOf(source);
        List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
        if (activeActivityIds == null || activeActivityIds.isEmpty()) {
            return;
        }
        if (activeActivityIds.contains(sourceActivityId)) {
            clearDirectSendMarks(processInstanceId);
            return;
        }
        if (activeActivityIds.size() != 1) {
            throw new RuntimeException("当前流程存在多个活动分支，不能安全直送");
        }
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(processInstanceId)
                .moveActivityIdTo(activeActivityIds.get(0), sourceActivityId)
                .changeState();
        clearDirectSendMarks(processInstanceId);
    }

    /** 三枚直送标记一次批量清除，替代逐 key removeVariable 的 3 次独立往返 */
    private void clearDirectSendMarks(String processInstanceId) {
        runtimeService.removeVariables(processInstanceId, List.of(
                RETURN_SOURCE_ACTIVITY_ID, RETURN_TARGET_ACTIVITY_ID, RETURN_TO_START_PENDING));
    }

    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    /**
     * Flowable 委派态任务不能直接 complete，需要先 resolve。
     */
    private void completeTask(Task task, Map<String, Object> variables) {
        String taskId = task.getId();
        if (DelegationState.PENDING.equals(task.getDelegationState())) {
            log.info("任务处于委派待解决状态，先 resolve 再 complete：taskId={}, assignee={}, owner={}",
                    taskId, task.getAssignee(), task.getOwner());
            taskService.resolveTask(taskId);
        }

        try {
            if (variables != null && !variables.isEmpty()) {
                // 调用方（approve/reject/autoApprove）的 task 均刚从 taskQuery 查出，
                // ACT_RU_TASK 有行则流程实例必然在运行，isProcessRunning 守卫恒真且多一次往返，
                // 直接写变量；若并发下流程恰好被终止，setVariables 与 complete 抛出的
                // FlowableObjectNotFoundException 均会转为同一提示，行为等价
                runtimeService.setVariables(task.getProcessInstanceId(), variables);
                taskService.complete(taskId, variables);
            } else {
                taskService.complete(taskId);
            }
        } catch (org.flowable.common.engine.api.FlowableObjectNotFoundException e) {
            throw new RuntimeException("任务已处理或流程已结束，请刷新后重试", e);
        }
    }

    private boolean isProcessRunning(String processInstanceId) {
        if (isBlank(processInstanceId)) {
            return false;
        }
        try {
            return runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult() != null;
        } catch (Exception e) {
            log.debug("判断流程是否仍在运行失败: processInstanceId={}", processInstanceId);
            return false;
        }
    }

    private void autoApproveRepeatedTasks(String processInstanceId, Process resolvedProcess) {
        ProcessInstance instance;
        try {
            instance = isBlank(processInstanceId)
                    ? null
                    : runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
        } catch (Exception e) {
            return;
        }
        if (instance == null) {
            return;
        }

        String mode = taskNodePolicy().resolveAutoApprovalMode(
                resolvedProcess, instance.getProcessDefinitionId());
        if (!AUTO_APPROVAL_FIRST_ONLY.equals(mode) && !AUTO_APPROVAL_CONSECUTIVE.equals(mode)) {
            return;
        }

        Set<String> completedAutomatically = new HashSet<>();
        int guard = 0;
        while (guard++ < 30) {
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            Task matchedTask = null;
            for (Task activeTask : activeTasks) {
                if (completedAutomatically.contains(activeTask.getId())) {
                    continue;
                }
                if (shouldAutoApproveTask(activeTask, mode)) {
                    matchedTask = activeTask;
                    break;
                }
            }
            if (matchedTask == null) {
                return;
            }
            autoApproveTask(matchedTask, mode);
            completedAutomatically.add(matchedTask.getId());
        }
        log.warn("重复审批自动同意达到保护上限：processInstanceId={}, mode={}", processInstanceId, mode);
    }

    private boolean shouldAutoApproveTask(Task task, String mode) {
        if (task == null || isBlank(task.getAssignee())) {
            return false;
        }
        String assignee = task.getAssignee();
        if (AUTO_APPROVAL_FIRST_ONLY.equals(mode)) {
            return hasFinishedTaskByAssignee(task.getProcessInstanceId(), assignee);
        }
        HistoricTaskInstance previousTask = findLastFinishedTask(task.getProcessInstanceId());
        return previousTask != null && Objects.equals(previousTask.getAssignee(), assignee);
    }

    private boolean hasFinishedTaskByAssignee(String processInstanceId, String assignee) {
        long count = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(assignee)
                .finished()
                .count();
        return count > 0;
    }

    private HistoricTaskInstance findLastFinishedTask(String processInstanceId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .listPage(0, 1);
        return tasks == null || tasks.isEmpty() ? null : tasks.get(0);
    }

    private void autoApproveTask(Task task, String mode) {
        String comment = "系统自动同意（重复审批人）";
        taskService.addComment(task.getId(), task.getProcessInstanceId(), comment);
        completeTask(task, mergeActionVariables(null, true));

        FlowTask flowTask = new FlowTask();
        flowTask.setStatus(FlowTaskStatus.APPROVED.getCode());
        flowTask.setComment(comment);
        flowTask.setCompleteTime(LocalDateTime.now());
        updateTaskByTenant(task.getId(), flowTask);

        log.info("重复审批自动同意：taskId={}, processInstanceId={}, assignee={}, mode={}",
                task.getId(), task.getProcessInstanceId(), task.getAssignee(), mode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(String taskId, String userId, String delegateUserId, String comment) {
        delegate(taskId, userId, delegateUserId, comment, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSign(String taskId, String userId, String targetUserId, String reason) {
        addSign(taskId, userId, targetUserId, reason, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSign(String taskId, String userId, String targetUserId, String reason, String signMode) {
        mutateCandidateSign(taskId, userId, targetUserId, reason, signMode,
                SessionHelper.getTenantId(), null, null, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSign(String taskId, String userId, String targetUserId, String reason, String signMode,
                        Long tenantId, String idempotencyKey, String requestDigest) {
        mutateCandidateSign(taskId, userId, targetUserId, reason, signMode,
                tenantId, idempotencyKey, requestDigest, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reduceSign(String taskId, String userId, String targetUserId, String reason) {
        reduceSign(taskId, userId, targetUserId, reason, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reduceSign(String taskId, String userId, String targetUserId, String reason, String signMode) {
        mutateCandidateSign(taskId, userId, targetUserId, reason, signMode,
                SessionHelper.getTenantId(), null, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reduceSign(String taskId, String userId, String targetUserId, String reason, String signMode,
                           Long tenantId, String idempotencyKey, String requestDigest) {
        mutateCandidateSign(taskId, userId, targetUserId, reason, signMode,
                tenantId, idempotencyKey, requestDigest, false);
    }

    private void mutateCandidateSign(String taskId, String userId, String targetUserId,
                                     String reason, String signMode, Long tenantId,
                                     String idempotencyKey, String requestDigest, boolean add) {
        if (isBlank(targetUserId)) {
            throw new RuntimeException("目标用户不能为空");
        }
        String normalizedSignMode = FlowTaskSignMode.fromCode(signMode).getCode();
        if (!FlowTaskSignMode.PARALLEL.getCode().equals(normalizedSignMode)) {
            throw new IllegalStateException("FLOW_TASK_SIGN_MODE_UNSUPPORTED");
        }
        assertTaskMutationActor(taskId, userId, false);
        validateReassignTarget(targetUserId.trim());
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
        }
        if ((idempotencyKey == null) != (requestDigest == null)) {
            throw new IllegalStateException("FLOW_TASK_IDEMPOTENCY_INVALID");
        }
        if (idempotencyKey != null && flowTaskCandidateMapper == null) {
            throw new IllegalStateException("FLOW_TASK_IDEMPOTENCY_UNAVAILABLE");
        }
        FlowTask localTask = baseMapper.selectByTaskIdAndTenant(taskId, tenantId);
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || localTask == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        localTask = baseMapper.selectByTaskIdForUpdateAndTenant(taskId, tenantId);
        if (localTask == null || !tenantId.equals(localTask.getTenantId())) {
            throw new RuntimeException("任务不存在或已处理");
        }
        if (Objects.equals(String.valueOf(userId), targetUserId.trim())
                || Objects.equals(String.valueOf(localTask.getAssignee()), targetUserId.trim())) {
            throw new RuntimeException("不能将当前任务办理人再次加入加签名单");
        }
        if (flowTaskCandidateMapper != null && add
                && flowTaskCandidateMapper.countActiveByTaskAndValue(
                tenantId, taskId, FlowTaskCandidate.TYPE_USER, targetUserId.trim()) > 0) {
            throw new RuntimeException("目标用户已经在加签名单中");
        }

        if (idempotencyKey != null) {
            FlowTaskCandidate previous = flowTaskCandidateMapper.selectByIdempotency(
                    tenantId, taskId, FlowTaskCandidate.TYPE_USER, idempotencyKey);
            if (previous != null) {
                if (!Objects.equals(requestDigest, previous.getRequestDigest())
                        || !Objects.equals(targetUserId.trim(), previous.getCandidateValue())
                        || !Objects.equals(add, FlowTaskCandidateStatus.ACTIVE.matches(previous.getStatus()))) {
                    throw new IllegalStateException("FLOW_TASK_IDEMPOTENCY_CONFLICT");
                }
                return;
            }
        }

        if (isMultiInstanceTask(task)) {
            mutateFlowableMultiInstanceSign(task, localTask, userId, targetUserId.trim(), reason,
                    normalizedSignMode, idempotencyKey, requestDigest, add);
            return;
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>(splitIds(localTask.getCandidateUsers()));
        boolean changed;
        if (add) {
            if (candidates.size() >= MAX_DYNAMIC_SIGNERS) {
                throw new RuntimeException("单个任务最多允许加签 " + MAX_DYNAMIC_SIGNERS + " 人");
            }
            changed = candidates.add(targetUserId.trim());
            if (changed) {
                taskService.addCandidateUser(taskId, targetUserId.trim());
                syncCandidateRelation(localTask, targetUserId.trim(), userId, reason, normalizedSignMode,
                        idempotencyKey, requestDigest, true, null, null);
            }
        } else {
            changed = candidates.remove(targetUserId.trim());
            if (changed) {
                taskService.deleteCandidateUser(taskId, targetUserId.trim());
                syncCandidateRelation(localTask, targetUserId.trim(), userId, reason, normalizedSignMode,
                        idempotencyKey, requestDigest, false, null, null);
            }
        }
        if (!changed) {
            throw new RuntimeException(add ? "目标用户已经在加签名单中" : "目标用户不在加签名单中");
        }

        FlowTask update = new FlowTask();
        update.setCandidateUsers(String.join(",", candidates));
        update.setComment(reason);
        if (!updateTaskByTenant(taskId, update)) {
            throw new IllegalStateException("加签状态同步失败");
        }
        String action = add ? "加签" : "减签";
        taskService.addComment(taskId, task.getProcessInstanceId(), action,
                isBlank(reason) ? action : reason.trim());
        log.info("流程任务{}：taskId={}, actor={}, target={}", action, taskId, userId, targetUserId);
    }

    /**
     * 对已经由 BPMN 配置为多实例的用户任务，使用 Flowable 原生多实例执行 API 创建/删除子执行。
     * 普通用户任务继续使用候选关系兼容路径，避免把一个普通任务伪装成流程子任务。
     */
    private void mutateFlowableMultiInstanceSign(Task task, FlowTask parentTask, String operatorId,
                                                  String targetUserId, String reason, String signMode,
                                                  String idempotencyKey, String requestDigest, boolean add) {
        UserTask userTask = resolveMultiInstanceUserTask(task);
        if (flowTaskCandidateMapper == null || userTask == null || userTask.getLoopCharacteristics() == null) {
            throw new IllegalStateException("FLOW_TASK_SIGN_MULTI_INSTANCE_UNAVAILABLE");
        }
        MultiInstanceLoopCharacteristics loop = userTask.getLoopCharacteristics();
        if (add) {
            String elementVariable = loop.getElementVariable();
            if (isBlank(elementVariable)) {
                elementVariable = "assignee";
            }
            Map<String, Object> variables = new HashMap<>();
            variables.put(elementVariable, targetUserId);
            Execution execution = runtimeService.addMultiInstanceExecution(
                    userTask.getId(), task.getProcessInstanceId(), variables);
            if (execution == null || isBlank(execution.getId())) {
                throw new IllegalStateException("FLOW_TASK_SIGN_CHILD_EXECUTION_CREATE_FAILED");
            }
            Task childTask = taskService.createTaskQuery().executionId(execution.getId()).singleResult();
            if (childTask == null && !loop.isSequential()) {
                throw new IllegalStateException("FLOW_TASK_SIGN_CHILD_TASK_CREATE_FAILED");
            }
            if (childTask != null && !Objects.equals(targetUserId, childTask.getAssignee())) {
                taskService.setAssignee(childTask.getId(), targetUserId);
            }
            syncCandidateRelation(parentTask, targetUserId, operatorId, reason, signMode,
                    idempotencyKey, requestDigest, true,
                    childTask == null ? null : childTask.getId(), execution.getId());
            taskService.addComment(task.getId(), task.getProcessInstanceId(), "加签",
                    isBlank(reason) ? "加签" : reason.trim());
            return;
        }

        FlowTaskCandidate relation = flowTaskCandidateMapper.selectActiveDynamicSignRelation(
                parentTask.getTenantId(), parentTask.getTaskId(), targetUserId);
        if (relation == null || isBlank(relation.getChildExecutionId())) {
            throw new RuntimeException("目标用户不存在可撤销的多实例加签");
        }
        runtimeService.deleteMultiInstanceExecution(relation.getChildExecutionId(), false);
        syncCandidateRelation(parentTask, targetUserId, operatorId, reason, signMode,
                idempotencyKey, requestDigest, false,
                relation.getChildTaskId(), relation.getChildExecutionId());
        taskService.addComment(task.getId(), task.getProcessInstanceId(), "减签",
                isBlank(reason) ? "减签" : reason.trim());
    }

    private boolean isMultiInstanceTask(Task task) {
        return resolveMultiInstanceUserTask(task) != null;
    }

    private UserTask resolveMultiInstanceUserTask(Task task) {
        if (task == null || isBlank(task.getProcessDefinitionId())
                || isBlank(task.getTaskDefinitionKey()) || repositoryService == null) {
            return null;
        }
        try {
            BpmnModel model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
            if (model == null) {
                return null;
            }
            FlowElement element = model.getFlowElement(task.getTaskDefinitionKey());
            if (element instanceof UserTask userTask && userTask.hasMultiInstanceLoopCharacteristics()) {
                return userTask;
            }
        } catch (Exception e) {
            log.warn("解析多实例任务配置失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
        return null;
    }

    private void syncCandidateRelation(FlowTask task, String candidateUserId, String operatorId,
                                       String reason, String signMode, String idempotencyKey,
                                       String requestDigest, boolean active,
                                       String childTaskId, String childExecutionId) {
        if (flowTaskCandidateMapper == null || task == null || task.getTenantId() == null
                || task.getTaskId() == null || isBlank(candidateUserId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (active) {
            FlowTaskCandidate relation = new FlowTaskCandidate();
            relation.setTenantId(task.getTenantId());
            relation.setTaskId(task.getTaskId());
            relation.setParentTaskId(task.getTaskId());
            relation.setChildTaskId(childTaskId);
            relation.setChildExecutionId(childExecutionId);
            relation.setProcessInstanceId(task.getProcessInstanceId());
            relation.setCandidateType(FlowTaskCandidate.TYPE_USER);
            relation.setCandidateValue(candidateUserId);
            relation.setSource(FlowTaskCandidate.SOURCE_DYNAMIC_SIGN);
            relation.setSignMode(signMode);
            relation.setOperatorId(operatorId);
            relation.setReason(reason);
            relation.setIdempotencyKey(idempotencyKey);
            relation.setRequestDigest(requestDigest);
            relation.setStatus(FlowTaskCandidateStatus.ACTIVE.getCode());
            relation.setCreateTime(now);
            relation.setUpdateTime(now);
            flowTaskCandidateMapper.insertIgnore(relation);
        } else {
            flowTaskCandidateMapper.deactivateWithAudit(task.getTenantId(), task.getTaskId(),
                    FlowTaskCandidate.TYPE_USER, candidateUserId, operatorId, reason,
                    idempotencyKey, requestDigest, now);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowTaskSignRelationVO> getSignRelations(String taskId, String userId) {
        if (isBlank(taskId) || isBlank(userId)) {
            throw new IllegalArgumentException("FLOW_TASK_SIGN_RELATION_REQUIRED");
        }
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
        }
        FlowTask task = flowAccessGuard.requireTaskVisible(taskId);
        if (!tenantId.equals(task.getTenantId())) {
            throw new RuntimeException("FLOW_RESOURCE_NOT_FOUND");
        }
        if (flowTaskCandidateMapper == null) {
            return List.of();
        }
        return flowTaskCandidateMapper.selectDynamicSignRelations(tenantId, task.getTaskId());
    }

    private List<String> splitIds(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(String processInstanceId, String userId) {
        try {
            assertSubmitterWithdrawAllowed(processInstanceId, userId);
            List<String> activeTaskIds = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .stream()
                    .map(Task::getId)
                    .filter(Objects::nonNull)
                    .toList();
            runtimeService.deleteProcessInstance(processInstanceId, "用户撤回");

            Long tenantId = SessionHelper.getTenantId();
            if (tenantId == null || tenantId <= 0) {
                throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
            }
            if (!activeTaskIds.isEmpty()) {
                baseMapper.updateProcessTaskStatusByTaskIds(activeTaskIds, tenantId,
                        FlowTaskStatus.WITHDRAWN.getCode(), LocalDateTime.now());
            }

            log.info("撤回流程：processInstanceId={}, userId={}", processInstanceId, userId);
        } catch (Exception e) {
            FlowErrorLog errorLog = new FlowErrorLog();
            errorLog.setProcessInstanceId(processInstanceId);
            errorLog.setErrorStage("TASK_WITHDRAW");
            flowErrorLogService.recordError(errorLog, e);
            throw e;
        }
    }

    private void assertSubmitterWithdrawAllowed(String processInstanceId, String userId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new RuntimeException("流程实例不存在或已结束");
        }

        Boolean allowed = taskNodePolicy().readBooleanProcessAttribute(
                instance.getProcessDefinitionId(), "allowSubmitterWithdraw");
        if (Boolean.FALSE.equals(allowed)) {
            throw new RuntimeException("当前流程不允许提交人撤回审批中的申请");
        }

        if (!isProcessSubmitter(processInstanceId, userId)) {
            throw new RuntimeException("只有提交人可以撤回该申请");
        }
    }

    private boolean isProcessSubmitter(String processInstanceId, String userId) {
        if (isBlank(userId)) {
            return false;
        }
        Long tenantId = SessionHelper.getTenantId();
        FlowBusiness business = tenantId == null
                ? null
                : flowBusinessMapper.selectByProcessInstanceIdAndTenantId(processInstanceId, tenantId);
        if (business != null && !isBlank(business.getApplyUserId())) {
            return Objects.equals(String.valueOf(business.getApplyUserId()), String.valueOf(userId));
        }
        Object initiator = runtimeService.getVariable(processInstanceId, "initiator");
        if (initiator != null && !isBlank(String.valueOf(initiator))) {
            return Objects.equals(String.valueOf(initiator), String.valueOf(userId));
        }
        log.warn("撤回申请未找到可信提交人信息，拒绝操作：processInstanceId={}, userId={}",
                processInstanceId, userId);
        return false;
    }

    @Override
    public FlowTask getTaskDetail(String taskId) {
        FlowTask task = flowAccessGuard.requireTaskVisible(taskId);
        if (task != null) {
            task.setProcessDefKey(resolveProcessDefinitionKey(
                    firstNonBlank(task.getProcessDefId(), task.getProcessDefKey()),
                    task.getProcessDefKey()));
        }
        return task;
    }

    @Override
    public byte[] getProcessDiagram(String processInstanceId) {
        return processDiagramService().getProcessDiagram(processInstanceId);
    }

    @Override
    public ProcessDiagramInfo getProcessDiagramInfo(String processInstanceId) {
        return processDiagramService().getProcessDiagramInfo(processInstanceId);
    }

    @Override
    public ProcessDiagramInfo getProcessDiagramInfo(String processInstanceId, boolean includeImage) {
        return processDiagramService().getProcessDiagramInfo(processInstanceId, includeImage);
    }

    private FlowProcessDiagramService processDiagramService() {
        return new FlowProcessDiagramService(
                runtimeService,
                taskService,
                repositoryService,
                historyService,
                processEngineConfiguration,
                flowOrgIntegrationService,
                flowAccessGuard
        );
    }
    @Override
    public void remind(String taskId) {
        flowAccessGuard.requireTaskVisible(taskId);
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            log.warn("催办失败：任务不存在，taskId={}", taskId);
            return;
        }
        
        log.info("催办任务：taskId={}, taskName={}", taskId, task.getName());
        
        // 发送催办消息通知
        if (messageService != null) {
            try {
                // 获取任务处理人
                String assignee = task.getAssignee();
                if (assignee == null || assignee.isEmpty()) {
                    // 如果任务未签收，尝试获取候选人
                    log.info("任务未签收，跳过消息通知：taskId={}", taskId);
                    return;
                }
                
                // 构建消息
                com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO request =
                    new com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO();
                request.setTitle("流程催办提醒");
                request.setContent(String.format(
                    "您有一个待办任务需要处理：%s，请及时处理。",
                    task.getName()
                ));
                request.setType("SYSTEM");
                request.setChannel("WEB");
                request.setSendScope("USERS");
                
                // 设置接收人
                Set<Long> userIds = new HashSet<>();
                try {
                    userIds.add(Long.parseLong(assignee));
                } catch (NumberFormatException e) {
                    log.warn("无法解析处理人ID：{}", assignee);
                    return;
                }
                request.setUserIds(userIds);
                
                // 发送消息
                messageService.send(request);
                log.info("催办消息发送成功：taskId={}, assignee={}", taskId, assignee);
                
            } catch (Exception e) {
                log.error("发送催办消息失败：taskId={}", taskId, e);
            }
        } else {
            log.warn("消息服务未启用，无法发送催办通知");
        }
    }

    private FlowTaskNodePolicy taskNodePolicy() {
        return new FlowTaskNodePolicy(
                repositoryService,
                historyService,
                taskService,
                flowModelService,
                flowNodeConfigService,
                processDefinitionId -> resolveProcessDefinitionKey(processDefinitionId, null)
        );
    }

    private FlowTaskFormConfigurationResolver formConfigurationResolver() {
        return new FlowTaskFormConfigurationResolver(
                repositoryService,
                taskService,
                flowModelService,
                flowFormService,
                flowFormInstanceMapper,
                taskNodePolicy(),
                this::resolveProcessDefinitionKey
        );
    }

    private FlowTaskFormContextCoordinator formContextCoordinator() {
        return new FlowTaskFormContextCoordinator(
                taskService,
                runtimeService,
                repositoryService,
                historyService,
                getBaseMapper(),
                flowBusinessMapper,
                flowModelService,
                flowAccessGuard,
                formConfigurationResolver(),
                taskNodePolicy(),
                this::resolveProcessDefinitionKey,
                this::resolveUserDisplayName,
                this::isProcessStarterTask
        );
    }

    private boolean isProcessStarterTask(Task task, String userId) {
        if (task == null || isBlank(userId)) {
            return false;
        }
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            return false;
        }
        FlowBusiness business = flowBusinessMapper.selectByProcessInstanceIdAndTenantId(
                task.getProcessInstanceId(), tenantId);
        return business != null
                && !isBlank(business.getApplyUserId())
                && Objects.equals(business.getApplyUserId(), userId.trim());
    }

    private void validateReassignTarget(String targetUserId) {
        if (flowOrgIntegrationService == null
                || !flowOrgIntegrationService.isUserAvailableForTenant(targetUserId, SessionHelper.getTenantId())) {
            throw new RuntimeException("新处理人不存在、已停用或不属于当前租户");
        }
    }

    /**
     * return 接口位于 @IgnoreTenant 的 Flow 服务边界，必须在本地表和流程实例
     * 两侧再次锁定并校验租户，不能只依赖调用方传入的 taskId。
     */
    private void assertTaskTenantForAction(String taskId) {
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new RuntimeException("FLOW_TASK_TENANT_REQUIRED");
        }
        FlowTask localTask = baseMapper.selectByTaskIdForUpdateAndTenant(taskId, tenantId);
        Task flowableTask = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (localTask == null || (localTask.getTenantId() != null
                && !tenantId.equals(localTask.getTenantId()))
                || flowableTask == null) {
            throw new RuntimeException("FLOW_TASK_TENANT_MISMATCH");
        }
        FlowBusiness business = flowBusinessMapper.selectByProcessInstanceIdAndTenantIdForUpdate(
                flowableTask.getProcessInstanceId(), tenantId);
        if (business == null || !Objects.equals(business.getProcessInstanceId(), flowableTask.getProcessInstanceId())) {
            throw new RuntimeException("FLOW_TASK_TENANT_MISMATCH");
        }
    }

    /**
     * 委派和任务终结属于高影响写操作，不能只依赖 Flowable taskId 存在性。
     * 先验证租户/业务归属，再验证操作者是当前处理人、拥有者或流程发起人。
     */
    private void assertTaskMutationActor(String taskId, String userId, boolean allowInitiator) {
        if (isBlank(userId)) {
            throw new RuntimeException("FLOW_TASK_ACTOR_REQUIRED");
        }
        assertTaskTenantForAction(taskId);
        Long tenantId = SessionHelper.getTenantId();
        FlowTask localTask = baseMapper.selectByTaskIdForUpdateAndTenant(taskId, tenantId);
        boolean participant = Objects.equals(userId, localTask.getAssignee())
                || Objects.equals(userId, localTask.getOwner())
                || (allowInitiator && Objects.equals(userId, localTask.getStartUserId()));
        if (!participant) {
            throw new RuntimeException("FLOW_TASK_ACTOR_MISMATCH");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String textValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long requireTenantId() {
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
        }
        return tenantId;
    }

    /**
     * 详情/审批历史仍需兼容历史业务数据中的账号或姓名；列表页已由 Mapper SQL 直接关联用户，
     * 不会走这里的逐条组织服务查询。
     */
    private String resolveUserDisplayName(String userId, String fallback,
                                          Map<String, Map<String, Object>> userInfoCache) {
        if (!isBlank(userId) && userInfoCache != null && userInfoCache.containsKey(userId.trim())) {
            Map<String, Object> userInfo = userInfoCache.get(userId.trim());
            if (userInfo != null) {
                String name = firstNonBlank(
                        textValue(userInfo.get("realName")),
                        textValue(userInfo.get("name")),
                        textValue(userInfo.get("nickname")));
                if (!isBlank(name)) {
                    return name;
                }
            }
        }
        return isBlank(fallback) ? userId : fallback.trim();
    }

    private String resolveUserDisplayName(String userId, String fallback) {
        if (!isBlank(userId) && flowOrgIntegrationService != null) {
            try {
                Map<String, Object> userInfo = flowOrgIntegrationService.getUserInfo(userId.trim());
                if (userInfo != null) {
                    String name = firstNonBlank(
                            textValue(userInfo.get("realName")),
                            textValue(userInfo.get("name")),
                            textValue(userInfo.get("nickname")));
                    if (!isBlank(name)) {
                        return name;
                    }
                }
            } catch (Exception e) {
                log.debug("反查任务用户姓名失败: userId={}", userId, e);
            }
        }
        return isBlank(fallback) ? userId : fallback.trim();
    }

    @Override
    public TaskFormInfo getTaskFormInfo(String taskId) {
        return formContextCoordinator().getTaskFormInfo(taskId);
    }

    @Override
    public TaskFormInfo getProcessFormInfo(String processInstanceId, String businessKey, String processDefKey,
                                           String taskId, String taskDefKey) {
        return formContextCoordinator().getProcessFormInfo(
                processInstanceId, businessKey, processDefKey, taskId, taskDefKey);
    }

    private String resolveProcessDefinitionKey(String processDefinitionId, String fallbackProcessDefKey) {
        String key = null;
        if (!isBlank(processDefinitionId)) {
            if (processDefinitionId.contains(":")) {
                key = extractProcessKey(processDefinitionId);
            }
            if (isBlank(key) || Objects.equals(key, processDefinitionId)) {
                try {
                    ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                            .processDefinitionId(processDefinitionId)
                            .singleResult();
                    if (definition != null) {
                        key = definition.getKey();
                    }
                } catch (Exception e) {
                    log.debug("从流程定义ID解析流程定义Key失败: processDefinitionId={}", processDefinitionId);
                }
            }
            if (isBlank(key) || Objects.equals(key, processDefinitionId)) {
                try {
                    BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                    if (bpmnModel != null && bpmnModel.getMainProcess() != null) {
                        key = bpmnModel.getMainProcess().getId();
                    }
                } catch (Exception e) {
                    log.debug("从BPMN模型解析流程定义Key失败: processDefinitionId={}", processDefinitionId);
                }
            }
        }
        if (!isBlank(key) && !Objects.equals(key, processDefinitionId)) {
            return key;
        }
        if (!isBlank(fallbackProcessDefKey) && fallbackProcessDefKey.contains(":")) {
            return extractProcessKey(fallbackProcessDefKey);
        }
        return fallbackProcessDefKey;
    }

    /**
     * 获取流程审批时间轴
     */
    @Override
    public List<Map<String, Object>> getProcessHistory(String processInstanceId) {
        FlowHistoryPageVO page = getProcessHistoryPage(processInstanceId, 1, MAX_DETAIL_HISTORY_ITEMS);
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowHistoryItemVO item : page.getRecords()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("taskId", item.getTaskId());
            node.put("taskName", item.getTaskName());
            node.put("assigneeName", item.getAssigneeName());
            node.put("assigneeId", item.getAssigneeId());
            node.put("action", item.getAction());
            node.put("comment", item.getComment());
            node.put("signature", item.getSignature());
            node.put("approvalPointResults", item.getApprovalPointResults());
            node.put("createTime", item.getCreateTime());
            node.put("completeTime", item.getCompleteTime());
            result.add(node);
        }
        return result;
    }

    @Override
    public FlowHistoryPageVO getProcessHistoryPage(String processInstanceId, Integer pageNum, Integer pageSize) {
        FlowBusiness business = flowAccessGuard.requireProcessVisible(processInstanceId);
        Long tenantId = flowAccessGuard.requireTenant();
        long safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_DETAIL_HISTORY_ITEMS);
        IPage<FlowTask> taskPage = baseMapper.selectHistoryTasks(
                new Page<>(safePageNum, safePageSize), processInstanceId, tenantId);
        List<FlowTask> tasks = taskPage.getRecords();

        // 一次批量读取审批人，避免长流程历史逐任务回查组织服务。
        Map<String, Map<String, Object>> userInfoCache = new HashMap<>();
        Set<String> userIds = new LinkedHashSet<>();
        if (business != null && !isBlank(business.getApplyUserId())) {
            userIds.add(business.getApplyUserId());
        }
        tasks.stream()
                .map(FlowTask::getAssignee)
                .filter(id -> !isBlank(id))
                .forEach(userIds::add);
        if (flowOrgIntegrationService != null && !userIds.isEmpty()) {
            Map<String, Map<String, Object>> loaded =
                    flowOrgIntegrationService.getUserInfoBatch(new ArrayList<>(userIds));
            if (loaded != null) {
                userInfoCache.putAll(loaded);
            }
            userIds.forEach(id -> userInfoCache.putIfAbsent(id, Collections.emptyMap()));
        }

        List<FlowHistoryItemVO> records = new ArrayList<>();
        if (business != null && safePageNum == 1) {
            FlowHistoryItemVO startNode = new FlowHistoryItemVO();
            startNode.setTaskName("发起流程");
            startNode.setAssigneeName(resolveUserDisplayName(
                    business.getApplyUserId(), business.getApplyUserName(), userInfoCache));
            startNode.setAssigneeId(business.getApplyUserId());
            startNode.setAction("start");
            startNode.setComment("");
            String startTime = business.getApplyTime() != null
                    ? business.getApplyTime().toString() : business.getCreateTime() != null
                    ? business.getCreateTime().toString() : null;
            startNode.setCreateTime(startTime);
            startNode.setCompleteTime(startTime);
            records.add(startNode);
        }

        // 加入每个任务节点
        for (FlowTask task : tasks) {
            FlowHistoryItemVO node = new FlowHistoryItemVO();
            node.setTaskId(task.getTaskId());
            node.setTaskName(task.getTaskName());
            String assigneeName = resolveUserDisplayName(task.getAssignee(), task.getAssigneeName(), userInfoCache);
            node.setAssigneeName(assigneeName);
            node.setAssigneeId(task.getAssignee());
            node.setAction(FlowTaskStatus.historyActionOf(task.getStatus()));
            node.setComment(task.getComment() != null ? task.getComment() : "");
            node.setSignature(task.getSignature());
            node.setApprovalPointResults(taskNodePolicy().readApprovalPointResults(task.getTaskId()));
            node.setCreateTime(task.getCreateTime() != null ? task.getCreateTime().toString() : null);
            node.setCompleteTime(task.getCompleteTime() != null ? task.getCompleteTime().toString() : null);
            records.add(node);
        }

        FlowHistoryPageVO result = new FlowHistoryPageVO();
        result.setPageNum(safePageNum);
        result.setPageSize(safePageSize);
        result.setTotal(taskPage.getTotal() + (business == null ? 0 : 1));
        // 发起节点只在第一页额外展示，不参与任务表分页游标，避免最后一页被错误标记为还有数据。
        result.setHasMore(taskPage.getCurrent() * taskPage.getSize() < taskPage.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * 从流程定义ID提取流程Key
     */
    private String extractProcessKey(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        // 格式：processKey:version:id
        String[] parts = processDefinitionId.split(":");
        return parts.length > 0 ? parts[0] : processDefinitionId;
    }

    private void recordTaskError(String processInstanceId, String taskId, String activityId,
                                  String activityName, String errorStage, Throwable e) {
        FlowErrorLog errorLog = new FlowErrorLog();
        errorLog.setProcessInstanceId(processInstanceId);
        errorLog.setTaskId(taskId);
        errorLog.setActivityId(activityId);
        errorLog.setActivityName(activityName);
        errorLog.setErrorStage(errorStage);
        flowErrorLogService.recordError(errorLog, e);
    }

}
