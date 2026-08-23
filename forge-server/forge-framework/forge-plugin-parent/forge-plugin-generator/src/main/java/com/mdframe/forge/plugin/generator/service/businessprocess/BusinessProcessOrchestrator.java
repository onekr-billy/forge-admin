package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessEdge;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessNodeRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessManualStartDTO;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessRunQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessNodeRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessNodeResult;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunDetailVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunVO;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEvent;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 业务流程运行状态机：先落 run，再按已发布 DAG 执行到等待或结束。
 */
@Service
@RequiredArgsConstructor
public class BusinessProcessOrchestrator {

    private static final int MAX_HOPS = 32;
    private static final int MAX_RETRY = 5;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> START_TYPES = Set.of("START_MANUAL", "START_EVENT", "START_SCHEDULE");
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "RUNNING", "WAITING");

    private final BusinessApplicationMapper applicationMapper;
    private final BusinessProcessMapper processMapper;
    private final BusinessProcessVersionMapper versionMapper;
    private final BusinessProcessRunMapper runMapper;
    private final BusinessProcessNodeRunMapper nodeRunMapper;
    private final BusinessProcessSchemaValidator schemaValidator;
    private final BusinessFlowService flowService;
    private final BusinessProcessActionExecutor actionExecutor;

    public Page<BusinessProcessRunVO> page(Integer pageNum, Integer pageSize, BusinessProcessRunQueryDTO query) {
        Long tenantId = requireTenantId();
        Page<BusinessProcessRunVO> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        Page<BusinessProcessRunVO> result = runMapper.selectRunPage(page, tenantId,
                query == null ? new BusinessProcessRunQueryDTO() : query);
        enrichNodeNames(tenantId, result.getRecords());
        return result;
    }

    public BusinessProcessRunDetailVO detail(Long runId) {
        AiBusinessProcessRun run = requireRun(runId);
        BusinessProcessRunDetailVO vo = toDetail(run);
        Map<String, String> nodeNameMap = loadNodeNameMap(run.getTenantId(), run.getProcessVersionId());
        if (!nodeNameMap.isEmpty()) {
            vo.setCurrentNodeName(nodeNameMap.getOrDefault(run.getCurrentNodeId(), run.getCurrentNodeId()));
        }
        List<BusinessProcessRunDetailVO.NodeRunVO> timeline = new ArrayList<>();
        for (AiBusinessProcessNodeRun nodeRun : safeList(nodeRunMapper.selectTimeline(run.getTenantId(), run.getId()))) {
            BusinessProcessRunDetailVO.NodeRunVO nodeVo = toNodeVo(nodeRun);
            if (!nodeNameMap.isEmpty()) {
                nodeVo.setNodeName(nodeNameMap.getOrDefault(nodeRun.getNodeId(), nodeRun.getNodeId()));
            }
            timeline.add(nodeVo);
        }
        vo.setTimeline(timeline);
        return vo;
    }

    public BusinessProcessRunVO start(String applicationCode, String processCode, BusinessProcessManualStartDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getRecordId())) {
            throw new BusinessException("业务记录ID不能为空");
        }
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        AiBusinessApplication application = applicationMapper.selectEntityByCode(tenantId, StringUtils.trimToEmpty(applicationCode));
        if (application == null || !Integer.valueOf(1).equals(application.getStatus())) {
            throw new BusinessException("业务应用不存在或已停用");
        }
        AiBusinessProcess process = processMapper.selectActiveByCode(
                tenantId, application.getId(), StringUtils.trimToEmpty(processCode));
        if (process == null || !Integer.valueOf(1).equals(process.getStatus())) {
            throw new BusinessException("业务流程不存在或已停用");
        }
        if (process.getPublishedVersion() == null) {
            throw new BusinessException("业务流程尚未发布，无法启动");
        }
        AiBusinessProcessVersion version = versionMapper.selectPublishedVersion(
                tenantId, process.getId(), process.getPublishedVersion());
        if (version == null) {
            throw new BusinessException("业务流程发布版本不存在");
        }
        BusinessProcessSchema schema = normalizeSchema(version.getSchemaJson());
        BusinessProcessNode startNode = requireManualStart(schema);
        String objectCode = schema.getSubject() == null ? null : StringUtils.trimToNull(schema.getSubject().getObjectCode());
        if (StringUtils.isBlank(objectCode)) {
            throw new BusinessException("已发布流程缺少主业务对象");
        }
        if (StringUtils.isNotBlank(dto.getObjectCode()) && !objectCode.equals(dto.getObjectCode().trim())) {
            throw new BusinessException("启动对象与已发布流程主对象不一致");
        }
        String permission = firstText(text(startNode.getConfig(), "permission"), "ai:businessProcess:start");
        if (!SessionHelper.hasPermission(permission)) {
            throw new BusinessException(403, "没有权限启动该业务流程");
        }
        String recordId = dto.getRecordId().trim();
        String businessKey = objectCode + ":" + recordId;
        String idempotencyKey = "MANUAL:" + objectCode + ":" + recordId;
        AiBusinessProcessRun existing = runMapper.selectByIdempotencyKey(tenantId, version.getId(), idempotencyKey);
        if (existing != null) {
            if (ACTIVE_STATUSES.contains(existing.getStatus()) || "SUCCESS".equals(existing.getStatus())) {
                if ("PENDING".equals(existing.getStatus())) {
                    execute(existing.getId());
                    existing = requireRun(existing.getId());
                }
                return completedStart(existing, process.getProcessName());
            }
            if ("FAILED".equals(existing.getStatus())) {
                return retry(existing.getId());
            }
            return completedStart(existing, process.getProcessName());
        }
        AiBusinessProcessRun run = new AiBusinessProcessRun();
        run.setId(IdWorker.getId());
        run.setTenantId(tenantId);
        run.setApplicationId(application.getId());
        run.setProcessId(process.getId());
        run.setProcessVersionId(version.getId());
        run.setProcessCode(process.getProcessCode());
        run.setSubjectObjectCode(objectCode);
        run.setSubjectRecordId(recordId);
        run.setBusinessKey(businessKey);
        run.setTriggerType("MANUAL");
        run.setIdempotencyKey(idempotencyKey);
        run.setActorType("USER");
        run.setActorUserId(userId);
        run.setActiveOrgId(SessionHelper.getActiveOrgId());
        run.setStatus("PENDING");
        run.setContextSnapshot(safeContextSnapshot(objectCode, recordId));
        run.setRetryCount(0);
        run.setCreateBy(userId);
        run.setUpdateBy(userId);
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException duplicate) {
            AiBusinessProcessRun duplicated = runMapper.selectByIdempotencyKey(tenantId, version.getId(), idempotencyKey);
            if (duplicated == null) {
                throw duplicate;
            }
            return completedStart(duplicated, process.getProcessName());
        }
        execute(run.getId());
        return completedStart(requireRun(run.getId()), process.getProcessName());
    }

    /**
     * 由动态 CRUD 成功写入后的业务事件启动已发布的 START_EVENT 流程。
     * 事件只匹配当前主对象和发布版本，运行记录使用事件幂等键，避免
     * 控制器重试或消息重复投递重复发起流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public void startEvent(BusinessEvent event) {
        if (event == null || StringUtils.isAnyBlank(event.getEventType(), event.getObjectCode())) {
            return;
        }
        Long tenantId = event.getTenantId() == null ? 1L : event.getTenantId();
        List<AiBusinessProcessVersion> versions = versionMapper.selectCurrentPublishedBySubjectObjectCode(
                tenantId, event.getObjectCode());
        if (versions == null || versions.isEmpty()) {
            return;
        }
        for (AiBusinessProcessVersion version : versions) {
            BusinessProcessSchema schema;
            try {
                schema = normalizeSchema(version.getSchemaJson());
            } catch (Exception ignored) {
                continue;
            }
            BusinessProcessNode startNode = schema.getNodes() == null ? null : schema.getNodes().stream()
                    .filter(node -> "START_EVENT".equals(upper(node.getType())))
                    .filter(node -> event.getEventType().equalsIgnoreCase(text(node.getConfig(), "eventType")))
                    .filter(node -> matchesEventCondition(node.getConfig(), event))
                    .findFirst().orElse(null);
            if (startNode == null || schema.getSubject() == null
                    || StringUtils.isBlank(schema.getSubject().getObjectCode())) {
                continue;
            }
            String recordId = StringUtils.defaultIfBlank(event.getRecordId(), "-");
            String idempotencyKey = "EVENT:" + event.getEventType() + ":"
                    + schema.getSubject().getObjectCode() + ":" + recordId;
            AiBusinessProcessRun existing = runMapper.selectByIdempotencyKey(tenantId, version.getId(), idempotencyKey);
            if (existing != null) {
                if ("PENDING".equals(existing.getStatus())) {
                    execute(tenantId, existing.getId());
                }
                continue;
            }
            AiBusinessProcessRun run = new AiBusinessProcessRun();
            run.setId(IdWorker.getId());
            run.setTenantId(tenantId);
            run.setApplicationId(version.getApplicationId());
            run.setProcessId(version.getProcessId());
            run.setProcessVersionId(version.getId());
            run.setProcessCode(version.getProcessCode());
            run.setSubjectObjectCode(schema.getSubject().getObjectCode());
            run.setSubjectRecordId(recordId);
            run.setBusinessKey(schema.getSubject().getObjectCode() + ":" + recordId);
            run.setTriggerType("EVENT");
            run.setIdempotencyKey(idempotencyKey);
            run.setActorType("USER");
            run.setActorUserId(event.getOperatorId());
            run.setStatus("PENDING");
            run.setContextSnapshot(safeContextSnapshot(schema.getSubject().getObjectCode(), recordId));
            run.setRetryCount(0);
            run.setCreateBy(event.getOperatorId());
            run.setUpdateBy(event.getOperatorId());
            try {
                runMapper.insert(run);
            } catch (DuplicateKeyException duplicate) {
                continue;
            }
            execute(tenantId, run.getId());
        }
    }

    public void execute(Long runId) {
        execute(requireTenantId(), runId);
    }

    private void execute(Long tenantId, Long runId) {
        AiBusinessProcessRun run = requireRun(tenantId, runId);
        if (!"PENDING".equals(run.getStatus()) && !"RUNNING".equals(run.getStatus())) {
            return;
        }
        if ("PENDING".equals(run.getStatus())) {
            int claimed = runMapper.compareAndSetStatus(
                    run.getTenantId(), run.getId(), "PENDING", null, null,
                    "RUNNING", null, null, null, null, null);
            if (claimed != 1) {
                return;
            }
            run = requireRun(tenantId, runId);
        }
        AiBusinessProcessVersion version = versionMapper.selectPublishedVersionById(
                run.getTenantId(), run.getProcessVersionId());
        if (version == null) {
            failRun(run, "PROCESS_VERSION_MISSING", "业务流程版本不存在");
            return;
        }
        BusinessProcessSchema schema = normalizeSchema(version.getSchemaJson());
        String currentNodeId = StringUtils.trimToNull(run.getCurrentNodeId());
        if (currentNodeId == null) {
            BusinessProcessNode startNode = requireStart(schema);
            currentNodeId = startNode.getId();
            if (!advanceCheckpoint(run, "RUNNING", currentNodeId, null)) {
                return;
            }
            run = requireRun(tenantId, runId);
        }
        int hops = 0;
        while (hops++ < MAX_HOPS) {
            BusinessProcessNode node = requireNode(schema, currentNodeId);
            BusinessProcessNodeResult result = executeNode(run, schema, node);
            completeNodeAttempt(run, node, result);
            if (result.isFailed()) {
                failRun(run, result.getErrorCode(), result.getErrorSummary());
                return;
            }
            if (result.isWaiting()) {
                waitRun(run, node.getId(), result.getCorrelationId());
                return;
            }
            if ("END".equals(upper(node.getType()))) {
                succeedRun(run, node.getId());
                return;
            }
            String nextId = nextNodeId(schema, node.getId(), result.getOutputPort());
            if (StringUtils.isBlank(nextId)) {
                failRun(run, "GRAPH_DEAD_END", "节点没有可继续的出口: " + node.getId());
                return;
            }
            if (!advanceCheckpoint(run, "RUNNING", nextId, run.getFlowProcessInstanceId())) {
                return;
            }
            run = requireRun(tenantId, run.getId());
            currentNodeId = nextId;
        }
        failRun(run, "MAX_HOPS_EXCEEDED", "业务流程节点跳转超过上限");
    }

    /**
     * 审批流程到达终态后恢复外层业务流程。状态和关联 ID 均使用 CAS 认领，
     * 因此 Flowable 重复投递同一终态事件不会重复执行后继动作。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resumeApprovalResult(Long tenantId, String processInstanceId, String result) {
        if (tenantId == null || tenantId <= 0 || StringUtils.isAnyBlank(processInstanceId, result)) {
            return;
        }
        AiBusinessProcessRun run = runMapper.selectWaitingByProcessInstanceId(tenantId, processInstanceId);
        if (run == null || StringUtils.isBlank(run.getCurrentNodeId())) {
            return;
        }
        AiBusinessProcessVersion version = versionMapper.selectPublishedVersionById(
                tenantId, run.getProcessVersionId());
        if (version == null) {
            failRun(run, "PROCESS_VERSION_MISSING", "业务流程版本不存在");
            return;
        }
        BusinessProcessSchema schema = normalizeSchema(version.getSchemaJson());
        BusinessProcessNode approvalNode = requireNode(schema, run.getCurrentNodeId());
        if (!"APPROVAL".equals(upper(approvalNode.getType()))) {
            failRun(run, "WAITING_NODE_INVALID", "等待中的节点不是审批节点");
            return;
        }
        String outputPort = normalizeApprovalOutputPort(result);
        AiBusinessProcessNodeRun waitingAttempt = nodeRunMapper.selectWaitingByCorrelation(
                tenantId, run.getId(), approvalNode.getId(), processInstanceId);
        if (waitingAttempt == null) {
            return;
        }
        String nextId = nextNodeId(schema, approvalNode.getId(), outputPort);
        if (StringUtils.isBlank(nextId)) {
            failRun(run, "GRAPH_DEAD_END", "审批结果没有可继续的出口: " + outputPort);
            return;
        }
        int claimed = runMapper.compareAndSetStatus(
                tenantId,
                run.getId(),
                "WAITING",
                approvalNode.getId(),
                processInstanceId,
                "RUNNING",
                nextId,
                processInstanceId,
                null,
                null,
                null);
        if (claimed != 1) {
            return;
        }
        String attemptStatus = "FAILED".equals(outputPort) ? "FAILED" : "SUCCESS";
        int attemptUpdated = nodeRunMapper.completeAttempt(
                tenantId,
                waitingAttempt.getId(),
                "WAITING",
                processInstanceId,
                attemptStatus,
                processInstanceId,
                "审批结果: " + outputPort,
                "FAILED".equals(outputPort) ? "APPROVAL_FAILED" : null,
                "FAILED".equals(outputPort) ? "审批流程执行失败" : null,
                null);
        if (attemptUpdated != 1) {
            throw new BusinessException("审批节点等待状态已变化，请稍后重试");
        }
        execute(tenantId, run.getId());
    }

    public BusinessProcessRunVO retry(Long runId) {
        AiBusinessProcessRun run = requireRun(runId);
        if (!SessionHelper.hasPermission("ai:businessProcess:run:retry")) {
            throw new BusinessException(403, "没有权限重试业务流程运行");
        }
        int updated = runMapper.retryFailed(run.getTenantId(), run.getId(), MAX_RETRY, requireUserId());
        if (updated != 1) {
            throw new BusinessException("当前运行不可重试");
        }
        execute(runId);
        return completedStart(requireRun(runId), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessRunVO cancel(Long runId) {
        AiBusinessProcessRun run = requireRun(runId);
        if (!SessionHelper.hasPermission("ai:businessProcess:run:cancel")) {
            throw new BusinessException(403, "没有权限取消业务流程运行");
        }
        if (!ACTIVE_STATUSES.contains(run.getStatus())) {
            throw new BusinessException("当前运行已结束，不能取消");
        }
        int updated = runMapper.compareAndSetStatus(
                run.getTenantId(),
                run.getId(),
                run.getStatus(),
                run.getCurrentNodeId(),
                run.getFlowProcessInstanceId(),
                "CANCELED",
                run.getCurrentNodeId(),
                run.getFlowProcessInstanceId(),
                null,
                "CANCELED",
                "业务流程已取消");
        if (updated != 1) {
            throw new BusinessException("取消失败，运行状态已变化");
        }
        return toVo(requireRun(runId), null);
    }

    private BusinessProcessNodeResult executeNode(
            AiBusinessProcessRun run,
            BusinessProcessSchema schema,
            BusinessProcessNode node) {
        startNodeAttempt(run, node);
        String type = upper(node.getType());
        try {
            if (START_TYPES.contains(type)) {
                return BusinessProcessNodeResult.completed("NEXT", "开始节点已通过");
            }
            if ("END".equals(type)) {
                return BusinessProcessNodeResult.completed("NEXT", "流程已结束");
            }
            if ("CONDITION".equals(type)) {
                return BusinessProcessNodeResult.completed("OTHERWISE", "条件节点使用默认出口");
            }
            if ("APPROVAL".equals(type)) {
                return executeApproval(run, node);
            }
            if ("ACTION".equals(type)) {
                return BusinessProcessNodeResult.completed(
                        "NEXT", actionExecutor.execute(run, schema, node));
            }
            return BusinessProcessNodeResult.failed(
                    "NODE_TYPE_UNSUPPORTED",
                    "节点类型尚未接入运行时: " + node.getType());
        } catch (BusinessException exception) {
            return BusinessProcessNodeResult.failed("NODE_EXECUTE_FAILED", exception.getMessage());
        } catch (Exception exception) {
            Throwable cause = exception.getCause();
            String message = exception.getMessage();
            if (cause instanceof BusinessException businessException) {
                message = businessException.getMessage();
            }
            return BusinessProcessNodeResult.failed(
                    "NODE_EXECUTE_FAILED",
                    StringUtils.defaultIfBlank(message, "节点执行失败"));
        }
    }

    private BusinessProcessNodeResult executeApproval(AiBusinessProcessRun run, BusinessProcessNode node) {
        String flowModelKey = text(node.getConfig(), "flowModelKey");
        if (StringUtils.isBlank(flowModelKey)) {
            return BusinessProcessNodeResult.failed("APPROVAL_MODEL_MISSING", "审批节点未配置已发布流程模型");
        }
        String title = firstText(text(node.getConfig(), "titleTemplate"), node.getName(), run.getProcessCode());
        JSONObject variables = new JSONObject();
        variables.put("processCode", run.getProcessCode());
        variables.put("processRunId", String.valueOf(run.getId()));
        variables.put("nodeId", node.getId());
        Map<String, Object> businessFormRef = formAssetRef(node.getConfig());
        String formKey = text(businessFormRef, "formKey");
        if (StringUtils.isNotBlank(formKey)) {
            // formKey is retained for runs created by earlier versions. The explicit
            // businessForm* variables identify the application page selected by the
            // outer business-process node and take precedence at task-form runtime.
            variables.put("formKey", formKey);
            variables.put("businessFormKey", formKey);
            variables.put("businessFormRef", businessFormRef);
        }
        String statusField = text(node.getConfig(), "statusField");
        if (StringUtils.isNotBlank(statusField)) {
            if (!Set.of("flowStatus", "flow_status").contains(statusField)) {
                return BusinessProcessNodeResult.failed(
                        "APPROVAL_FLOW_STATUS_INVALID",
                        "审批节点只能使用独立流程状态字段 flowStatus");
            }
            variables.put("flowStatusField", statusField);
        }
        BusinessFlowRuntimeVO runtime = flowService.startFromBusinessProcess(
                flowModelKey,
                run.getBusinessKey(),
                title,
                run.getActorUserId(),
                resolveActorName(run),
                run.getTenantId(),
                variables);
        String processInstanceId = runtime == null ? null : runtime.getProcessInstanceId();
        if (StringUtils.isBlank(processInstanceId)) {
            return BusinessProcessNodeResult.failed("APPROVAL_START_FAILED", "审批流程启动失败");
        }
        return BusinessProcessNodeResult.waiting(processInstanceId, "已发起审批并等待结果");
    }

    private void startNodeAttempt(AiBusinessProcessRun run, BusinessProcessNode node) {
        int attemptNo = value(nodeRunMapper.selectMaxAttemptNo(run.getTenantId(), run.getId(), node.getId())) + 1;
        AiBusinessProcessNodeRun attempt = new AiBusinessProcessNodeRun();
        attempt.setId(IdWorker.getId());
        attempt.setTenantId(run.getTenantId());
        attempt.setRunId(run.getId());
        attempt.setNodeId(node.getId());
        attempt.setNodeType(node.getType());
        attempt.setAttemptNo(attemptNo);
        attempt.setIdempotencyKey(run.getId() + ":" + node.getId() + ":" + attemptNo);
        attempt.setCreateBy(run.getActorUserId());
        attempt.setUpdateBy(run.getActorUserId());
        nodeRunMapper.insertAttempt(attempt);
        nodeRunMapper.claimAttempt(run.getTenantId(), attempt.getId());
    }

    private void completeNodeAttempt(
            AiBusinessProcessRun run,
            BusinessProcessNode node,
            BusinessProcessNodeResult result) {
        AiBusinessProcessNodeRun latest = nodeRunMapper.selectLatestAttempt(
                run.getTenantId(), run.getId(), node.getId());
        if (latest == null) {
            return;
        }
        String nextStatus = result.isFailed() ? "FAILED" : (result.isWaiting() ? "WAITING" : "SUCCESS");
        nodeRunMapper.completeAttempt(
                run.getTenantId(),
                latest.getId(),
                "RUNNING",
                latest.getCorrelationId(),
                nextStatus,
                result.getCorrelationId(),
                truncate(result.getOutputSummary()),
                result.getErrorCode(),
                truncate(result.getErrorSummary()),
                null);
    }

    private boolean advanceCheckpoint(
            AiBusinessProcessRun run,
            String nextStatus,
            String currentNodeId,
            String processInstanceId) {
        return runMapper.compareAndSetStatus(
                run.getTenantId(),
                run.getId(),
                run.getStatus(),
                run.getCurrentNodeId(),
                run.getFlowProcessInstanceId(),
                nextStatus,
                currentNodeId,
                processInstanceId,
                null,
                null,
                null) == 1;
    }

    private void succeedRun(AiBusinessProcessRun run, String nodeId) {
        runMapper.compareAndSetStatus(
                run.getTenantId(), run.getId(), "RUNNING", run.getCurrentNodeId(), run.getFlowProcessInstanceId(),
                "SUCCESS", nodeId, run.getFlowProcessInstanceId(), null, null, null);
    }

    private void waitRun(AiBusinessProcessRun run, String nodeId, String processInstanceId) {
        runMapper.compareAndSetStatus(
                run.getTenantId(), run.getId(), "RUNNING", run.getCurrentNodeId(), run.getFlowProcessInstanceId(),
                "WAITING", nodeId, processInstanceId, null, null, null);
    }

    private void failRun(AiBusinessProcessRun run, String errorCode, String errorSummary) {
        runMapper.compareAndSetStatus(
                run.getTenantId(), run.getId(), run.getStatus(), run.getCurrentNodeId(), run.getFlowProcessInstanceId(),
                "FAILED", run.getCurrentNodeId(), run.getFlowProcessInstanceId(), null,
                errorCode, truncate(errorSummary));
    }

    private String nextNodeId(BusinessProcessSchema schema, String sourceId, String sourcePort) {
        List<BusinessProcessEdge> edges = schema.getEdges() == null ? List.of() : schema.getEdges();
        String preferredPort = StringUtils.defaultIfBlank(sourcePort, "NEXT");
        BusinessProcessEdge matched = null;
        BusinessProcessEdge fallback = null;
        for (BusinessProcessEdge edge : edges) {
            if (edge == null || !sourceId.equals(edge.getSource())) {
                continue;
            }
            if (Boolean.TRUE.equals(edge.getIsDefault()) || "OTHERWISE".equals(upper(edge.getSourcePort()))) {
                fallback = edge;
            }
            if (preferredPort.equalsIgnoreCase(StringUtils.defaultIfBlank(edge.getSourcePort(), "NEXT"))) {
                matched = edge;
                break;
            }
        }
        BusinessProcessEdge chosen = matched != null ? matched : fallback;
        return chosen == null ? null : chosen.getTarget();
    }

    private BusinessProcessNode requireManualStart(BusinessProcessSchema schema) {
        BusinessProcessNode start = requireStart(schema);
        if (!"START_MANUAL".equals(upper(start.getType()))) {
            throw new BusinessException("当前流程不是手动开始节点，不能从页面按钮启动");
        }
        return start;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesEventCondition(Map<String, Object> config, BusinessEvent event) {
        Object raw = config == null ? null : config.get("condition");
        if (!(raw instanceof Map<?, ?> condition) || condition.isEmpty()) {
            return true;
        }
        Object rules = condition.get("rules");
        if (!(rules instanceof List<?> list) || list.isEmpty()) {
            return true;
        }
        boolean any = "OR".equalsIgnoreCase(String.valueOf(condition.get("operator")))
                || "OR".equalsIgnoreCase(String.valueOf(condition.get("logic")));
        boolean result = any ? false : true;
        for (Object rawRule : list) {
            if (!(rawRule instanceof Map<?, ?> rule)) {
                continue;
            }
            String field = StringUtils.trimToEmpty(String.valueOf(rule.get("field")));
            Object operatorValue = rule.containsKey("operator") ? rule.get("operator") : rule.get("op");
            String operator = upper(String.valueOf(operatorValue));
            Object actual = event.readRecordValue(field);
            Object expected = rule.get("value");
            boolean matched = switch (operator) {
                case "EQ", "EQUALS" -> StringUtils.equals(String.valueOf(actual), String.valueOf(expected));
                case "NE", "NEQ", "NOT_EQUALS" -> !StringUtils.equals(String.valueOf(actual), String.valueOf(expected));
                case "IS_NULL" -> actual == null;
                case "NOT_NULL" -> actual != null;
                default -> true;
            };
            if (any) {
                result |= matched;
            } else {
                result &= matched;
            }
        }
        return result;
    }

    private BusinessProcessNode requireStart(BusinessProcessSchema schema) {
        return schema.getNodes().stream()
                .filter(node -> node != null && START_TYPES.contains(upper(node.getType())))
                .findFirst()
                .orElseThrow(() -> new BusinessException("已发布流程缺少开始节点"));
    }

    private BusinessProcessNode requireNode(BusinessProcessSchema schema, String nodeId) {
        return schema.getNodes().stream()
                .filter(node -> node != null && nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("流程版本不包含节点: " + nodeId));
    }

    private BusinessProcessSchema normalizeSchema(String schemaJson) {
        try {
            return schemaValidator.normalize(schemaJson);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(422, exception.getMessage(), exception);
        }
    }

    private AiBusinessProcessRun requireRun(Long runId) {
        return requireRun(requireTenantId(), runId);
    }

    private AiBusinessProcessRun requireRun(Long tenantId, Long runId) {
        if (runId == null || runId <= 0) {
            throw new BusinessException("运行记录ID不能为空");
        }
        AiBusinessProcessRun run = runMapper.selectRunById(tenantId, runId);
        if (run == null) {
            throw new BusinessException("业务流程运行记录不存在");
        }
        return run;
    }

    private String normalizeApprovalOutputPort(String result) {
        String normalized = upper(result);
        if (normalized.contains("APPROV") || normalized.contains("COMPLETED")) {
            return "APPROVED";
        }
        if (normalized.contains("REJECT")) {
            return "REJECTED";
        }
        if (normalized.contains("CANCEL") || normalized.contains("WITHDRAW")) {
            return "CANCELED";
        }
        return "FAILED";
    }

    private String resolveActorName(AiBusinessProcessRun run) {
        try {
            String username = SessionHelper.getUsername();
            if (StringUtils.isNotBlank(username)) {
                return username;
            }
        } catch (Exception ignored) {
            // Flowable 回调线程可能没有浏览器会话，使用持久化发起人标识兜底。
        }
        return run.getActorUserId() == null ? "system" : String.valueOf(run.getActorUserId());
    }

    private BusinessProcessRunVO completedStart(AiBusinessProcessRun run, String processName) {
        BusinessProcessRunVO vo = toVo(run, processName);
        if ("FAILED".equals(vo.getStatus())) {
            throw new BusinessException(StringUtils.defaultIfBlank(vo.getErrorSummary(), "业务流程执行失败"));
        }
        return vo;
    }

    private BusinessProcessRunVO toVo(AiBusinessProcessRun run, String processName) {
        BusinessProcessRunVO vo = new BusinessProcessRunVO();
        fillVo(vo, run, processName);
        return vo;
    }

    private BusinessProcessRunDetailVO toDetail(AiBusinessProcessRun run) {
        BusinessProcessRunDetailVO vo = new BusinessProcessRunDetailVO();
        fillVo(vo, run, null);
        vo.setFlowProcessInstanceId(run.getFlowProcessInstanceId());
        return vo;
    }

    private void fillVo(BusinessProcessRunVO vo, AiBusinessProcessRun run, String processName) {
        vo.setId(stringId(run.getId()));
        vo.setApplicationId(stringId(run.getApplicationId()));
        vo.setProcessId(stringId(run.getProcessId()));
        vo.setProcessVersionId(stringId(run.getProcessVersionId()));
        vo.setProcessCode(run.getProcessCode());
        vo.setProcessName(processName);
        vo.setSubjectObjectCode(run.getSubjectObjectCode());
        vo.setSubjectRecordId(run.getSubjectRecordId());
        vo.setBusinessKey(run.getBusinessKey());
        vo.setTriggerType(run.getTriggerType());
        vo.setActorType(run.getActorType());
        vo.setActorUserId(stringId(run.getActorUserId()));
        vo.setActiveOrgId(stringId(run.getActiveOrgId()));
        vo.setStatus(run.getStatus());
        vo.setCurrentNodeId(run.getCurrentNodeId());
        vo.setRetryCount(run.getRetryCount());
        vo.setErrorCode(run.getErrorCode());
        vo.setErrorSummary(run.getErrorSummary());
        vo.setStartTime(run.getStartTime());
        vo.setEndTime(run.getEndTime());
        vo.setCreateTime(run.getCreateTime());
        vo.setUpdateTime(run.getUpdateTime());
    }

    private BusinessProcessRunDetailVO.NodeRunVO toNodeVo(AiBusinessProcessNodeRun nodeRun) {
        BusinessProcessRunDetailVO.NodeRunVO vo = new BusinessProcessRunDetailVO.NodeRunVO();
        vo.setId(stringId(nodeRun.getId()));
        vo.setRunId(stringId(nodeRun.getRunId()));
        vo.setNodeId(nodeRun.getNodeId());
        vo.setNodeType(nodeRun.getNodeType());
        vo.setAttemptNo(nodeRun.getAttemptNo());
        vo.setStatus(nodeRun.getStatus());
        vo.setCorrelationId(nodeRun.getCorrelationId());
        vo.setInputSummary(nodeRun.getInputSummary());
        vo.setOutputSummary(nodeRun.getOutputSummary());
        vo.setErrorCode(nodeRun.getErrorCode());
        vo.setErrorSummary(nodeRun.getErrorSummary());
        vo.setNextRetryTime(nodeRun.getNextRetryTime());
        vo.setStartTime(nodeRun.getStartTime());
        vo.setEndTime(nodeRun.getEndTime());
        vo.setCreateTime(nodeRun.getCreateTime());
        vo.setUpdateTime(nodeRun.getUpdateTime());
        return vo;
    }

    /**
     * 按页批量解析 currentNodeName：同一版本只加载一次 schema。
     */
    private void enrichNodeNames(Long tenantId, List<BusinessProcessRunVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, Map<String, String>> cache = new HashMap<>();
        for (BusinessProcessRunVO run : records) {
            String versionId = run.getProcessVersionId();
            if (versionId == null || versionId.isBlank()) {
                continue;
            }
            Map<String, String> nodeNameMap = cache.computeIfAbsent(versionId,
                    key -> loadNodeNameMap(tenantId, parseLong(key)));
            if (!nodeNameMap.isEmpty()) {
                run.setCurrentNodeName(nodeNameMap.getOrDefault(run.getCurrentNodeId(), run.getCurrentNodeId()));
            }
        }
    }

    /**
     * 加载已发布版本的 schema，构建 nodeId → nodeName 映射。
     * 版本不存在或 schema 解析失败时返回空 Map，前端回退到显示 nodeId。
     */
    private Map<String, String> loadNodeNameMap(Long tenantId, Long processVersionId) {
        if (tenantId == null || processVersionId == null) {
            return Collections.emptyMap();
        }
        AiBusinessProcessVersion version = versionMapper.selectPublishedVersionById(tenantId, processVersionId);
        if (version == null || version.getSchemaJson() == null || version.getSchemaJson().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            BusinessProcessSchema schema = schemaValidator.normalize(version.getSchemaJson());
            Map<String, String> map = new LinkedHashMap<>();
            List<BusinessProcessNode> nodes = schema.getNodes() != null
                    ? schema.getNodes() : Collections.emptyList();
            for (BusinessProcessNode node : nodes) {
                if (node.getId() != null) {
                    map.put(node.getId(), node.getName() != null ? node.getName() : node.getId());
                }
            }
            return map;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safeContextSnapshot(String objectCode, String recordId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("objectCode", objectCode);
        snapshot.put("recordId", recordId);
        snapshot.put("triggerType", "MANUAL");
        return JSONObject.toJSONString(snapshot);
    }

    private Map<String, Object> formAssetRef(Map<String, Object> config) {
        if (config == null) {
            return Map.of();
        }
        Object formAsset = config.get("formAsset");
        if (!(formAsset instanceof Map<?, ?> map)) {
            String formKey = text(config, "formKey");
            return StringUtils.isBlank(formKey) ? Map.of() : Map.of("formKey", formKey);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of(
                "formKey", "formName", "formMode", "providerKey", "formUrl", "viewKey",
                "applicationId", "pageId", "pageCode", "pageName", "pageType", "sourceFormKey")) {
            Object value = map.get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                result.put(key, value);
            }
        }
        Object nestedRef = map.get("formRef");
        if (nestedRef instanceof Map<?, ?> nested) {
            nested.forEach((key, value) -> {
                if (key != null && value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                    result.putIfAbsent(String.valueOf(key), value);
                }
            });
        }
        return result;
    }

    private String text(Map<String, Object> config, String key) {
        if (config == null || !config.containsKey(key) || config.get(key) == null) {
            return "";
        }
        return StringUtils.trimToEmpty(String.valueOf(config.get(key)));
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String upper(String value) {
        return StringUtils.isBlank(value) ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String stringId(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private List<AiBusinessProcessNodeRun> safeList(List<AiBusinessProcessNodeRun> list) {
        return list == null ? List.of() : list;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private Long requireTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception ignored) {
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("未获取到有效租户上下文");
        }
        return tenantId;
    }

    private Long requireUserId() {
        Long userId;
        try {
            userId = SessionHelper.getUserId();
        } catch (Exception ignored) {
            userId = null;
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException("未获取到有效操作用户");
        }
        return userId;
    }
}
