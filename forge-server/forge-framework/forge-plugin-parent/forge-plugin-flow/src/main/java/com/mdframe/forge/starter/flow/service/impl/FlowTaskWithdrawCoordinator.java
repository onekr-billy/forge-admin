package com.mdframe.forge.starter.flow.service.impl;

import com.mdframe.forge.starter.flow.entity.FlowBusiness;
import com.mdframe.forge.starter.flow.entity.FlowErrorLog;
import com.mdframe.forge.starter.flow.enums.FlowBusinessStatus;
import com.mdframe.forge.starter.flow.enums.FlowTaskStatus;
import com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper;
import com.mdframe.forge.starter.flow.mapper.FlowTaskMapper;
import com.mdframe.forge.starter.flow.service.FlowErrorLogService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 流程发起人撤回命令的授权、最终幂等结果与 Flowable 状态协调器。 */
@Slf4j
final class FlowTaskWithdrawCoordinator {

    private static final String ACTION_TYPE = "WITHDRAW";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final FlowTaskMapper taskMapper;
    private final FlowBusinessMapper businessMapper;
    private final FlowErrorLogService errorLogService;
    private final FlowTaskNodePolicy nodePolicy;

    FlowTaskWithdrawCoordinator(RuntimeService runtimeService, TaskService taskService,
                                FlowTaskMapper taskMapper, FlowBusinessMapper businessMapper,
                                FlowErrorLogService errorLogService, FlowTaskNodePolicy nodePolicy) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.businessMapper = businessMapper;
        this.errorLogService = errorLogService;
        this.nodePolicy = nodePolicy;
    }

    void withdraw(String processInstanceId, String userId, String comment, Long tenantId,
                  String idempotencyKey, String requestDigest) {
        try {
            FlowBusiness business = authorize(
                    processInstanceId, userId, tenantId, idempotencyKey, requestDigest);
            if (business == null) {
                return;
            }
            ProcessInstance instance = requireRuntimeProcess(processInstanceId);
            validateWithdrawPolicy(instance);

            List<String> activeTaskIds = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .stream()
                    .map(Task::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (comment != null && !comment.isBlank()) {
                for (String taskId : activeTaskIds) {
                    taskService.addComment(taskId, processInstanceId, "撤回：" + comment.trim());
                }
            }
            runtimeService.deleteProcessInstance(processInstanceId, "用户撤回");

            LocalDateTime completedTime = LocalDateTime.now();
            if (!activeTaskIds.isEmpty()) {
                taskMapper.updateProcessTaskStatusByTaskIds(
                        activeTaskIds, tenantId, FlowTaskStatus.WITHDRAWN.getCode(), completedTime);
            }
            int updatedBusiness = businessMapper.markWithdrawn(
                    tenantId, processInstanceId, FlowBusinessStatus.CANCELED.getCode(), completedTime,
                    idempotencyKey, requestDigest, idempotencyKey == null ? null : ACTION_TYPE);
            if (updatedBusiness != 1) {
                throw new IllegalStateException("FLOW_WITHDRAW_BUSINESS_MIRROR_UPDATE_FAILED");
            }
            log.info("撤回流程：processInstanceId={}, userId={}", processInstanceId, userId);
        } catch (RuntimeException failure) {
            recordFailure(processInstanceId, failure);
            throw failure;
        }
    }

    /** 返回 null 表示命中已经完成的同请求结果。 */
    private FlowBusiness authorize(String processInstanceId, String userId, Long tenantId,
                                   String idempotencyKey, String requestDigest) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("FLOW_TASK_TENANT_REQUIRED");
        }
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalStateException("FLOW_PROCESS_INSTANCE_REQUIRED");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("FLOW_TASK_ASSIGNEE_REQUIRED");
        }
        if ((idempotencyKey == null) != (requestDigest == null)) {
            throw new IllegalStateException("FLOW_TASK_IDEMPOTENCY_INVALID");
        }
        if (idempotencyKey != null && (idempotencyKey.length() > 128 || requestDigest.length() > 71)) {
            throw new IllegalStateException("FLOW_TASK_IDEMPOTENCY_INVALID");
        }

        FlowBusiness business = businessMapper.selectByProcessInstanceIdAndTenantIdForUpdate(
                processInstanceId.trim(), tenantId);
        if (business == null || !tenantId.equals(business.getTenantId())) {
            throw new IllegalStateException("FLOW_TASK_TENANT_MISMATCH");
        }
        if (business.getApplyUserId() == null
                || !userId.trim().equals(String.valueOf(business.getApplyUserId()).trim())) {
            throw new IllegalStateException("FLOW_WITHDRAW_INITIATOR_MISMATCH");
        }
        boolean sameCompletedRequest = idempotencyKey != null
                && FlowBusinessStatus.CANCELED.matches(business.getStatus())
                && idempotencyKey.equals(business.getActionIdempotencyKey())
                && requestDigest.equals(business.getActionRequestDigest())
                && ACTION_TYPE.equals(business.getActionType());
        if (sameCompletedRequest) {
            return null;
        }
        if (business.getActionIdempotencyKey() != null) {
            throw new IllegalStateException("FLOW_TASK_IDEMPOTENCY_CONFLICT");
        }
        if (FlowBusinessStatus.isEnded(business.getStatus())) {
            throw new IllegalStateException("FLOW_PROCESS_NOT_ACTIONABLE");
        }
        return business;
    }

    private ProcessInstance requireRuntimeProcess(String processInstanceId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            throw new IllegalStateException("流程实例不存在或已结束");
        }
        return instance;
    }

    private void validateWithdrawPolicy(ProcessInstance instance) {
        Boolean allowed = nodePolicy.readBooleanProcessAttribute(
                instance.getProcessDefinitionId(), "allowSubmitterWithdraw");
        if (Boolean.FALSE.equals(allowed)) {
            throw new IllegalStateException("当前流程不允许提交人撤回审批中的申请");
        }
    }

    private void recordFailure(String processInstanceId, RuntimeException failure) {
        FlowErrorLog errorLog = new FlowErrorLog();
        errorLog.setProcessInstanceId(processInstanceId);
        errorLog.setErrorStage("TASK_WITHDRAW");
        errorLogService.recordError(errorLog, failure);
    }
}
