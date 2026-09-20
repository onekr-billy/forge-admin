package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentRuntimeVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 业务单据运行态服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDocumentRuntimeService {

    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final BusinessProcessRunMapper businessProcessRunMapper;
    private final AiCrudConfigMapper crudConfigMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessPermissionService permissionService;
    private final DynamicCrudService dynamicCrudService;

    @Autowired(required = false)
    private FlowClient flowClient;

    public BusinessDocumentRuntimeVO getRuntime(String objectCode, Long recordId) {
        BusinessDocumentRuntimeVO vo = new BusinessDocumentRuntimeVO();
        vo.setDocumentEnabled(false);
        if (StringUtils.isBlank(objectCode)) {
            vo.setMessage("业务对象编码不能为空");
            return vo;
        }
        Long tenantId = resolveTenantId();
        DocumentRuntimeContext context = resolveRuntimeContext(tenantId, objectCode);
        String canonicalObjectCode = context.objectCode();
        String businessKey = buildBusinessKey(canonicalObjectCode, recordId);
        vo.setBusinessKey(businessKey);
        vo.setActiveProcessCodes(loadActiveProcessCodes(tenantId, List.of(businessKey)).getOrDefault(
                businessKey, Collections.emptyList()));
        vo.setStartedProcessCodes(loadStartedProcessCodes(tenantId, List.of(businessKey)).getOrDefault(
                businessKey, Collections.emptyList()));
        fillDetailFlowDisplayOptions(vo, null);
        AiBusinessFlowInstanceLink link = flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, businessKey);
        if (link != null) {
            vo.setFlowStatus(link.getFlowStatus());
            vo.setProcessInstanceId(link.getProcessInstanceId());
            vo.setRoundNo(link.getRoundNo());
            fillFlowRounds(vo, tenantId, businessKey);
        } else {
            fillBusinessProcessHistory(vo, tenantId, businessKey);
        }

        AiBusinessDocumentConfig config = context.documentConfig();
        if (config == null) {
            fillApplicationFlowRuntime(
                    vo,
                    canonicalObjectCode,
                    link,
                    loadMyActiveTasks(link == null ? Collections.emptyList() : List.of(link)));
            return vo;
        }
        vo.setDocumentEnabled(true);
        BusinessDocumentConfigVO configVO = documentConfigService.toVO(config, context.runtimeConfig());
        Map<String, Object> recordData = loadRecordData(config, recordId);
        if (recordData == null) {
            vo.setMessage("记录不存在或无权限访问");
            vo.setNextAction("SAVE_RECORD");
            return vo;
        }
        fillDetailFlowDisplayOptions(vo, configVO);

        String documentStatus = text(resolveRecordField(recordData, config.getStatusField()));
        vo.setDocumentStatus(documentStatus);
        vo.setDocumentStatusLabel(resolveStatusLabel(configVO, documentStatus));

        List<String> actions = permissionService.resolveAvailableActions(canonicalObjectCode, recordId, recordData);
        vo.setAvailableActions(actions);
        fillMyTask(vo, configVO, link,
                loadMyActiveTasks(link == null ? Collections.emptyList() : List.of(link)));
        fillNextAction(vo, configVO, link, actions);
        fillRuntimeActions(vo, config, configVO, link, actions);
        return vo;
    }

    public Map<Long, BusinessDocumentRuntimeVO> getRuntimeBatch(String objectCode, List<Long> recordIds) {
        LinkedHashMap<Long, BusinessDocumentRuntimeVO> result = new LinkedHashMap<>();
        List<Long> normalizedRecordIds = normalizeRecordIds(recordIds);
        if (normalizedRecordIds.isEmpty() || StringUtils.isBlank(objectCode)) {
            return result;
        }

        Long tenantId = resolveTenantId();
        DocumentRuntimeContext context = resolveRuntimeContext(tenantId, objectCode);
        AiBusinessDocumentConfig config = context.documentConfig();
        BusinessDocumentConfigVO configVO = config == null ? null : documentConfigService.toVO(config, context.runtimeConfig());
        Map<Long, Map<String, Object>> recordDataMap = config == null
                ? Collections.emptyMap()
                : loadRecordDataBatch(config, normalizedRecordIds);
        Map<Long, AiBusinessFlowInstanceLink> linkMap = loadFlowLinks(
                tenantId, context.objectCode(), normalizedRecordIds);
        Map<String, List<String>> activeProcessCodeMap = loadActiveProcessCodes(
                tenantId,
                normalizedRecordIds.stream()
                        .map(recordId -> buildBusinessKey(context.objectCode(), recordId))
                        .toList());
        Map<String, List<String>> startedProcessCodeMap = loadStartedProcessCodes(
                tenantId,
                normalizedRecordIds.stream()
                        .map(recordId -> buildBusinessKey(context.objectCode(), recordId))
                        .toList());
        List<String> documentActions = config == null
                ? Collections.emptyList()
                : permissionService.resolveDocumentActionPermissions(context.objectCode());
        // 待办按整页流程实例一次查完，避免每行单独调用流程服务。
        Map<String, BusinessDocumentRuntimeVO.MyTaskVO> myTaskMap = loadMyActiveTasks(linkMap.values());

        for (Long recordId : normalizedRecordIds) {
            Map<String, Object> recordData = recordDataMap.get(recordId);
            List<String> actions = recordData == null ? Collections.emptyList() : documentActions;
            result.put(recordId, buildRuntimeVO(
                    context,
                    recordId,
                    config,
                    configVO,
                    recordData,
                    linkMap.get(recordId),
                    actions,
                    activeProcessCodeMap.getOrDefault(
                            buildBusinessKey(context.objectCode(), recordId), Collections.emptyList()),
                    startedProcessCodeMap.getOrDefault(
                            buildBusinessKey(context.objectCode(), recordId), Collections.emptyList()),
                    myTaskMap));
        }
        return result;
    }

    private void fillDetailFlowDisplayOptions(BusinessDocumentRuntimeVO vo, BusinessDocumentConfigVO configVO) {
        Map<String, Object> options = configVO == null ? null : configVO.getOptions();
        vo.setDetailFlowTimelineVisible(readBoolean(options == null ? null : options.get("detailFlowTimelineVisible"), true));
        vo.setDetailFlowDiagramVisible(readBoolean(options == null ? null : options.get("detailFlowDiagramVisible"), true));
    }

    private void fillBusinessProcessHistory(BusinessDocumentRuntimeVO vo,
                                            Long tenantId,
                                            String businessKey) {
        AiBusinessProcessRun run = businessProcessRunMapper.selectLatestByBusinessKey(tenantId, businessKey);
        if (run == null || StringUtils.isBlank(run.getFlowProcessInstanceId())) {
            return;
        }
        vo.setProcessInstanceId(run.getFlowProcessInstanceId());
        vo.setFlowStatus(switch (StringUtils.defaultString(run.getStatus()).toUpperCase(Locale.ROOT)) {
            case "WAITING", "RUNNING", "PENDING" -> "IN_PROCESS";
            case "SUCCESS" -> "APPROVED";
            case "CANCELED" -> "CANCELED";
            default -> run.getStatus();
        });
    }

    private void fillFlowRounds(BusinessDocumentRuntimeVO vo, Long tenantId, String businessKey) {
        if (StringUtils.isBlank(businessKey)) {
            return;
        }
        List<AiBusinessFlowInstanceLink> links = flowInstanceLinkMapper.selectByBusinessKey(tenantId, businessKey);
        if (links == null || links.isEmpty()) {
            return;
        }
        List<BusinessDocumentRuntimeVO.FlowRoundVO> rounds = new ArrayList<>();
        for (AiBusinessFlowInstanceLink item : links) {
            if (item == null || StringUtils.isBlank(item.getProcessInstanceId())) {
                continue;
            }
            BusinessDocumentRuntimeVO.FlowRoundVO round = new BusinessDocumentRuntimeVO.FlowRoundVO();
            round.setRoundNo(item.getRoundNo() == null ? rounds.size() + 1 : item.getRoundNo());
            round.setProcessInstanceId(item.getProcessInstanceId());
            round.setFlowStatus(item.getFlowStatus());
            round.setResult(item.getResult());
            rounds.add(round);
        }
        vo.setFlowRounds(rounds);
    }

    private BusinessDocumentRuntimeVO buildRuntimeVO(DocumentRuntimeContext context,
                                                     Long recordId,
                                                     AiBusinessDocumentConfig config,
                                                     BusinessDocumentConfigVO configVO,
                                                     Map<String, Object> recordData,
                                                     AiBusinessFlowInstanceLink link,
                                                     List<String> actions,
                                                     List<String> activeProcessCodes,
                                                     List<String> startedProcessCodes,
                                                     Map<String, BusinessDocumentRuntimeVO.MyTaskVO> myTaskMap) {
        BusinessDocumentRuntimeVO vo = new BusinessDocumentRuntimeVO();
        vo.setDocumentEnabled(false);
        vo.setBusinessKey(buildBusinessKey(context.objectCode(), recordId));
        vo.setActiveProcessCodes(activeProcessCodes == null ? new ArrayList<>() : new ArrayList<>(activeProcessCodes));
        vo.setStartedProcessCodes(startedProcessCodes == null ? new ArrayList<>() : new ArrayList<>(startedProcessCodes));
        fillDetailFlowDisplayOptions(vo, configVO);
        if (link != null) {
            vo.setFlowStatus(link.getFlowStatus());
            vo.setProcessInstanceId(link.getProcessInstanceId());
            vo.setRoundNo(link.getRoundNo());
        }

        if (config == null) {
            fillApplicationFlowRuntime(vo, context.objectCode(), link, myTaskMap);
            return vo;
        }
        vo.setDocumentEnabled(true);
        if (recordData == null) {
            vo.setMessage("记录不存在或无权限访问");
            vo.setNextAction("SAVE_RECORD");
            return vo;
        }

        String documentStatus = text(resolveRecordField(recordData, config.getStatusField()));
        vo.setDocumentStatus(documentStatus);
        vo.setDocumentStatusLabel(resolveStatusLabel(configVO, documentStatus));

        List<String> effectiveActions = actions == null ? Collections.emptyList() : actions;
        vo.setAvailableActions(effectiveActions);
        fillMyTask(vo, configVO, link, myTaskMap);
        fillNextAction(vo, configVO, link, effectiveActions);
        fillRuntimeActions(vo, config, configVO, link, effectiveActions);
        return vo;
    }

    /**
     * 新版应用按实际流程关联提供动作，不依赖旧版单据配置或当前用户是否有待办。
     */
    private void fillApplicationFlowRuntime(
            BusinessDocumentRuntimeVO vo,
            String objectCode,
            AiBusinessFlowInstanceLink link,
            Map<String, BusinessDocumentRuntimeVO.MyTaskVO> myTaskMap) {
        fillMyTask(vo, null, link, myTaskMap);
        List<BusinessDocumentRuntimeVO.RuntimeActionVO> runtimeActions = new ArrayList<>();
        addWithdrawAction(runtimeActions, vo, objectCode, link, List.of("WITHDRAW"));
        vo.setRuntimeActions(runtimeActions);
        if (vo.getMyTask() == null) {
            vo.setMessage(StringUtils.isBlank(vo.getProcessInstanceId())
                    ? "当前记录尚未关联审批流程" : "当前记录使用应用级审批流程");
            return;
        }
        boolean initiatorModify = Boolean.TRUE.equals(vo.getMyTask().getInitiatorModify());
        vo.setNextAction(initiatorModify ? "RESUBMIT_FLOW" : "HANDLE_TASK");
        vo.setMessage(initiatorModify ? "已驳回，修改后可重新提交" : "有待你处理的审批节点");
        addMyTaskAction(runtimeActions, vo, objectCode);
        vo.setRuntimeActions(runtimeActions);
    }

    public void validateStartAllowed(String objectCode, Long recordId, boolean checkPermission) {
        BusinessDocumentRuntimeVO runtime = getRuntime(objectCode, recordId);
        validateResolvedStartAllowed(runtime, recordId, checkPermission);
    }

    /**
     * 使用流程发起服务已经加载的单据上下文执行轻量校验。
     * <p>
     * 该入口只计算发起所需的状态策略和权限，不再构建完整详情运行态，避免重复查询
     * 业务记录、流程轮次、应用流程运行记录和当前用户待办。
     */
    public void validateStartAllowed(String objectCode,
                                     Long recordId,
                                     BusinessDocumentConfigVO configVO,
                                     Map<String, Object> recordData,
                                     AiBusinessFlowInstanceLink latestLink,
                                     boolean checkPermission) {
        BusinessDocumentRuntimeVO runtime = new BusinessDocumentRuntimeVO();
        runtime.setDocumentEnabled(configVO != null && Boolean.TRUE.equals(configVO.getDocumentEnabled()));
        runtime.setBusinessKey(buildBusinessKey(objectCode, recordId));
        if (configVO == null || !Boolean.TRUE.equals(runtime.getDocumentEnabled())) {
            runtime.setMessage("当前对象未启用单据模式");
            validateResolvedStartAllowed(runtime, recordId, checkPermission);
            return;
        }
        if (recordData == null) {
            runtime.setMessage("记录不存在或无权限访问");
            runtime.setNextAction("SAVE_RECORD");
            validateResolvedStartAllowed(runtime, recordId, checkPermission);
            return;
        }
        if (latestLink != null) {
            runtime.setFlowStatus(latestLink.getFlowStatus());
            runtime.setProcessInstanceId(latestLink.getProcessInstanceId());
            runtime.setRoundNo(latestLink.getRoundNo());
        }

        String documentStatus = text(resolveRecordField(recordData, configVO.getStatusField()));
        runtime.setDocumentStatus(documentStatus);
        List<String> actions = checkPermission
                ? permissionService.resolveAvailableActions(objectCode, recordId, recordData)
                : Collections.emptyList();
        runtime.setAvailableActions(actions);
        fillNextAction(runtime, configVO, latestLink, actions);
        validateResolvedStartAllowed(runtime, recordId, checkPermission);
    }

    private void validateResolvedStartAllowed(BusinessDocumentRuntimeVO runtime,
                                              Long recordId,
                                              boolean checkPermission) {
        if (!Boolean.TRUE.equals(runtime.getDocumentEnabled())) {
            throw new BusinessException(StringUtils.defaultIfBlank(runtime.getMessage(), "当前对象未启用单据模式"));
        }
        if (StringUtils.isBlank(runtime.getBusinessKey()) || recordId == null) {
            throw new BusinessException("请先保存记录后再发起主流程");
        }
        if (checkPermission && (runtime.getAvailableActions() == null
                || !runtime.getAvailableActions().contains("START_FLOW"))) {
            throw new BusinessException("缺少发起主流程权限");
        }
        if ("CONFIG_FLOW".equals(runtime.getNextAction())) {
            throw new BusinessException(StringUtils.defaultIfBlank(runtime.getMessage(), "请先配置主流程"));
        }
        if ("VIEW_FLOW".equals(runtime.getNextAction())) {
            throw new BusinessException("当前单据已有主流程实例，不能重复发起");
        }
        if ("RESUBMIT_FLOW".equals(runtime.getNextAction())) {
            throw new BusinessException("当前单据已驳回至你修改，请修改后重新提交，不要另起新流程");
        }
        if ("HANDLE_TASK".equals(runtime.getNextAction())) {
            throw new BusinessException("当前单据有待你处理的审批节点，请先处理待办");
        }
        if ("WAIT_STATUS".equals(runtime.getNextAction())) {
            throw new BusinessException(StringUtils.defaultIfBlank(runtime.getMessage(), "当前单据状态不可发起主流程"));
        }
        if (!checkPermission) {
            return;
        }
        BusinessDocumentRuntimeVO.RuntimeActionVO startAction = findRuntimeAction(runtime, "START_FLOW");
        if (startAction != null && Boolean.TRUE.equals(startAction.getDisabled())) {
            throw new BusinessException(StringUtils.defaultIfBlank(startAction.getDisabledReason(), "当前单据不可发起主流程"));
        }
        if (!"START_FLOW".equals(runtime.getNextAction())) {
            throw new BusinessException(StringUtils.defaultIfBlank(runtime.getMessage(), "当前单据不可发起主流程"));
        }
    }

    private Map<String, Object> loadRecordData(AiBusinessDocumentConfig config, Long recordId) {
        if (recordId == null || StringUtils.isBlank(config.getConfigKey())) {
            return null;
        }
        return dynamicCrudService.selectById(config.getConfigKey(), recordId);
    }

    private Map<Long, Map<String, Object>> loadRecordDataBatch(AiBusinessDocumentConfig config, List<Long> recordIds) {
        if (config == null || recordIds == null || recordIds.isEmpty() || StringUtils.isBlank(config.getConfigKey())) {
            return Collections.emptyMap();
        }
        Map<Object, Map<String, Object>> rawRecords = dynamicCrudService.selectByIds(config.getConfigKey(), recordIds);
        if (rawRecords.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<Long, Map<String, Object>> result = new LinkedHashMap<>();
        rawRecords.forEach((key, value) -> {
            Long recordId = toLong(key);
            if (recordId != null) {
                result.put(recordId, value);
            }
        });
        return result;
    }

    private Map<Long, AiBusinessFlowInstanceLink> loadFlowLinks(Long tenantId,
                                                                String objectCode,
                                                                List<Long> recordIds) {
        if (StringUtils.isBlank(objectCode) || recordIds == null || recordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> businessKeys = recordIds.stream()
                .filter(Objects::nonNull)
                .map(recordId -> buildBusinessKey(objectCode, recordId))
                .distinct()
                .toList();
        if (businessKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AiBusinessFlowInstanceLink> links = flowInstanceLinkMapper.selectLatestByBusinessKeys(tenantId, businessKeys);
        if (links == null || links.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<Long, AiBusinessFlowInstanceLink> result = new LinkedHashMap<>();
        for (AiBusinessFlowInstanceLink link : links) {
            Long recordId = link == null ? null : link.getRecordId();
            if (recordId == null) {
                recordId = parseRecordId(link == null ? null : link.getBusinessKey());
            }
            if (recordId != null) {
                result.put(recordId, link);
            }
        }
        return result;
    }

    private Map<String, List<String>> loadActiveProcessCodes(Long tenantId, List<String> businessKeys) {
        if (tenantId == null || businessKeys == null || businessKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = businessKeys.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AiBusinessProcessRun> runs = businessProcessRunMapper.selectActiveByBusinessKeys(tenantId, keys);
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (AiBusinessProcessRun run : runs) {
            if (run == null || StringUtils.isAnyBlank(run.getBusinessKey(), run.getProcessCode())) {
                continue;
            }
            grouped.computeIfAbsent(run.getBusinessKey(), ignored -> new LinkedHashSet<>())
                    .add(run.getProcessCode());
        }
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((key, codes) -> result.put(key, List.copyOf(codes)));
        return result;
    }

    private Map<String, List<String>> loadStartedProcessCodes(Long tenantId, List<String> businessKeys) {
        if (tenantId == null || businessKeys == null || businessKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = businessKeys.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AiBusinessProcessRun> runs = businessProcessRunMapper.selectStartedByBusinessKeys(tenantId, keys);
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
        for (AiBusinessProcessRun run : runs) {
            if (run == null || StringUtils.isAnyBlank(run.getBusinessKey(), run.getProcessCode())) {
                continue;
            }
            grouped.computeIfAbsent(run.getBusinessKey(), ignored -> new LinkedHashSet<>())
                    .add(run.getProcessCode());
        }
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((key, codes) -> result.put(key, List.copyOf(codes)));
        return result;
    }

    /**
     * 查询当前登录人在这批流程实例上的待办，按流程实例 ID 归集。
     * <p>
     * 只取仍在流转的关联，已结束的流程不会再有待办。
     */
    private Map<String, BusinessDocumentRuntimeVO.MyTaskVO> loadMyActiveTasks(
            Collection<AiBusinessFlowInstanceLink> links) {
        if (flowClient == null || links == null || links.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> processInstanceIds = links.stream()
                .filter(link -> link != null && isRunningFlow(link.getFlowStatus()))
                .map(AiBusinessFlowInstanceLink::getProcessInstanceId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (processInstanceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String userId = resolveUserId();
        if (StringUtils.isBlank(userId)) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> tasks;
        try {
            FlowResult<List<Map<String, Object>>> response =
                    flowClient.getActiveTasksByProcessInstances(processInstanceIds, userId);
            tasks = response == null || !response.isSuccess() || response.getData() == null
                    ? Collections.emptyList() : response.getData();
        } catch (Exception e) {
            log.debug("[单据运行态] 读取当前用户待办失败: processInstanceIds={}, error={}",
                    processInstanceIds, e.getMessage());
            return Collections.emptyMap();
        }
        Map<String, BusinessDocumentRuntimeVO.MyTaskVO> myTasks = new LinkedHashMap<>();
        for (Map<String, Object> task : tasks) {
            String processInstanceId = text(firstPresent(task, "processInstanceId", "process_instance_id"));
            String taskId = text(firstPresent(task, "taskId", "task_id"));
            if (StringUtils.isAnyBlank(processInstanceId, taskId)) {
                continue;
            }
            BusinessDocumentRuntimeVO.MyTaskVO myTask = new BusinessDocumentRuntimeVO.MyTaskVO();
            myTask.setTaskId(taskId);
            myTask.setTaskDefKey(text(firstPresent(task, "taskDefKey", "task_def_key")));
            myTask.setTaskName(text(firstPresent(task, "taskName", "task_name")));
            myTask.setProcessInstanceId(processInstanceId);
            // 同一实例上并行多个待办时保留最新一条，单据页只需要一个入口。
            myTasks.putIfAbsent(processInstanceId, myTask);
        }
        return myTasks;
    }

    /**
     * 加载当前用户待办并判定是否为驳回后的发起人修改节点。优先使用流程服务的
     * 实时结果，回调记录的修改待办用于处理接口短暂延迟。
     */
    private void fillMyTask(BusinessDocumentRuntimeVO vo, BusinessDocumentConfigVO configVO,
                            AiBusinessFlowInstanceLink link,
                            Map<String, BusinessDocumentRuntimeVO.MyTaskVO> myTaskMap) {
        if (link == null || StringUtils.isBlank(link.getProcessInstanceId())) {
            return;
        }
        BusinessFlowLinkRuntimeState.ModifyTask recorded =
                BusinessFlowLinkRuntimeState.readModifyTask(link.getVariablesSnapshot());
        BusinessDocumentRuntimeVO.MyTaskVO shared = myTaskMap == null
                ? null : myTaskMap.get(link.getProcessInstanceId());
        if (shared == null) {
            shared = resolveRecordedModifyTask(vo, configVO, link, recorded);
        }
        if (shared == null) {
            return;
        }
        boolean initiatorModify = isInitiatorModifyTask(shared)
                || (recorded != null && StringUtils.equals(shared.getTaskId(), recorded.taskId()));
        if (!initiatorModify) {
            initiatorModify = isFlowInitiator(link)
                    && (BusinessDocumentFlowStatus.NEED_MODIFY.matches(link.getFlowStatus())
                        || BusinessDocumentFlowStatus.NEED_MODIFY.matches(
                                resolveStandardStatusKey(configVO, vo.getDocumentStatus())));
        }
        // 批量查询的待办对象在多条记录间共享，必须复制后再写入单据级判定结果。
        BusinessDocumentRuntimeVO.MyTaskVO myTask = new BusinessDocumentRuntimeVO.MyTaskVO();
        myTask.setTaskId(shared.getTaskId());
        myTask.setTaskDefKey(shared.getTaskDefKey());
        myTask.setTaskName(shared.getTaskName());
        myTask.setProcessInstanceId(shared.getProcessInstanceId());
        myTask.setInitiatorModify(initiatorModify);
        vo.setMyTask(myTask);
    }

    /**
     * 流程服务的待办查询可能与回调写入存在短暂时序差，此时使用关联表中已记录的
     * 发起人修改任务恢复操作入口。只允许任务处理人或流程发起人读取，避免把待办
     * 暴露给同一单据的其他可见用户。
     */
    private BusinessDocumentRuntimeVO.MyTaskVO resolveRecordedModifyTask(
            BusinessDocumentRuntimeVO vo,
            BusinessDocumentConfigVO configVO,
            AiBusinessFlowInstanceLink link,
            BusinessFlowLinkRuntimeState.ModifyTask recorded) {
        if (recorded == null) {
            return null;
        }
        boolean needModify = BusinessDocumentFlowStatus.NEED_MODIFY.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.NEED_MODIFY.matches(
                        resolveStandardStatusKey(configVO, vo.getDocumentStatus()));
        if (!needModify) {
            return null;
        }
        String currentUserId = resolveUserId();
        boolean assignedToCurrentUser = StringUtils.equals(
                currentUserId, StringUtils.trimToNull(recorded.assigneeId()));
        if (!assignedToCurrentUser && !isFlowInitiator(link)) {
            return null;
        }
        BusinessDocumentRuntimeVO.MyTaskVO myTask = new BusinessDocumentRuntimeVO.MyTaskVO();
        myTask.setTaskId(recorded.taskId());
        myTask.setTaskDefKey(recorded.taskDefKey());
        myTask.setTaskName(recorded.taskName());
        myTask.setProcessInstanceId(link.getProcessInstanceId());
        return myTask;
    }

    private boolean isInitiatorModifyTask(BusinessDocumentRuntimeVO.MyTaskVO task) {
        String taskDefKey = task == null ? null : StringUtils.trimToNull(task.getTaskDefKey());
        return taskDefKey != null && taskDefKey.startsWith("Forge_InitiatorModify");
    }

    private boolean isFlowInitiator(AiBusinessFlowInstanceLink link) {
        if (link == null || link.getStartUserId() == null) {
            return false;
        }
        return String.valueOf(link.getStartUserId()).equals(resolveUserId());
    }

    /**
     * 把单据存储状态值反查成标准状态键。无法判定时返回 {@code null}。
     */
    private String resolveStandardStatusKey(BusinessDocumentConfigVO configVO, String documentStatus) {
        if (configVO == null || StringUtils.isBlank(documentStatus)) {
            return null;
        }
        if (configVO.getStatusMappingRows() != null) {
            for (BusinessDocumentConfigVO.StatusMappingRowVO row : configVO.getStatusMappingRows()) {
                if (row == null) {
                    continue;
                }
                if (documentStatus.equals(row.getStatusValue())
                        || documentStatus.equalsIgnoreCase(StringUtils.defaultString(row.getStandardStatus()))) {
                    return row.getStandardStatus();
                }
            }
        }
        return documentStatus.toUpperCase(Locale.ROOT);
    }

    private void fillNextAction(BusinessDocumentRuntimeVO vo, BusinessDocumentConfigVO configVO,
                                AiBusinessFlowInstanceLink link, List<String> actions) {
        Map<String, Object> mainFlowSummary = configVO.getMainFlowSummary();
        if (!isMainFlowConfigured(mainFlowSummary)) {
            vo.setNextAction("CONFIG_FLOW");
            vo.setMessage("单据模式已启用，尚未配置默认流程");
            return;
        }
        if (link != null && isRunningFlow(link.getFlowStatus())) {
            // 流程还在跑，但当前登录人手上有待办时，单据页要给出可操作的出口，
            // 否则驳回到发起人后发起人只能看到「流程流转中」，无路可走。
            if (vo.getMyTask() != null) {
                boolean initiatorModify = Boolean.TRUE.equals(vo.getMyTask().getInitiatorModify());
                vo.setNextAction(initiatorModify ? "RESUBMIT_FLOW" : "HANDLE_TASK");
                vo.setMessage(initiatorModify ? "已驳回，修改后可重新提交" : "有待你处理的审批节点");
                return;
            }
            vo.setNextAction("VIEW_FLOW");
            vo.setMessage("流程流转中");
            return;
        }
        if (hasDocumentFlowInstance(link)) {
            vo.setNextAction("VIEW_FLOW");
            vo.setMessage("流程已结束，可查看审批记录");
            return;
        }
        if (!isManualStartMode(text(mainFlowSummary.get("startMode")))) {
            vo.setNextAction("CONFIG_TRIGGER");
            vo.setMessage("当前主流程配置为触发器自动发起");
            return;
        }
        StatusPolicy statusPolicy = resolveStatusPolicy(configVO, vo.getDocumentStatus());
        if (!statusPolicy.allowStartFlow()) {
            vo.setNextAction("WAIT_STATUS");
            vo.setMessage(StringUtils.defaultIfBlank(statusPolicy.reason(), "当前单据状态不可发起主流程"));
            return;
        }
        if (actions.contains("START_FLOW")) {
            vo.setNextAction("START_FLOW");
            vo.setMessage("可发起主流程");
            return;
        }
        vo.setNextAction("REQUEST_PERMISSION");
        vo.setMessage("缺少可执行的单据动作权限");
    }

    private void fillRuntimeActions(BusinessDocumentRuntimeVO vo,
                                    AiBusinessDocumentConfig config,
                                    BusinessDocumentConfigVO configVO,
                                    AiBusinessFlowInstanceLink link, List<String> actions) {
        List<BusinessDocumentRuntimeVO.RuntimeActionVO> runtimeActions = new ArrayList<>();
        // 待办类动作与发起模式、发起按钮显隐无关：只要当前登录人手上有这条单据的待办就必须给出入口。
        addMyTaskAction(runtimeActions, vo, config.getObjectCode());
        addWithdrawAction(runtimeActions, vo, config.getObjectCode(), link, actions);
        Map<String, Object> mainFlowSummary = configVO.getMainFlowSummary();
        String startMode = mainFlowSummary == null ? "MANUAL" : text(mainFlowSummary.get("startMode"));
        if (!isManualStartMode(startMode)) {
            vo.setRuntimeActions(runtimeActions);
            return;
        }
        Map<String, Object> options = configVO == null ? null : configVO.getOptions();
        if (!readBoolean(options == null ? null : options.get("showStartFlowAction"), true)
                || !readBoolean(options == null ? null : options.get("showRuntimeStartFlowAction"), true)
                || readBoolean(options == null ? null : options.get("hideStartFlowAction"), false)) {
            vo.setRuntimeActions(runtimeActions);
            return;
        }

        BusinessDocumentRuntimeVO.RuntimeActionVO action = new BusinessDocumentRuntimeVO.RuntimeActionVO();
        action.setKey("START_FLOW");
        action.setLabel("发起主流程");
        action.setType("success");
        action.setActionType("START_FLOW");
        action.setVisible(true);
        action.setDisabled(false);
        action.setObjectCode(config.getObjectCode());
        action.setRecordId(readRecordId(vo));

        if (!isMainFlowConfigured(mainFlowSummary)) {
            action.setDisabled(true);
            action.setDisabledReason("请先配置主流程");
        } else if (hasDocumentFlowInstance(link)) {
            // 一个单据只允许创建一次主流程实例。运行中、待修改和终态都隐藏
            // 发起入口；后续若要重新审批，应使用独立的“重新开启”业务动作。
            action.setVisible(false);
        } else {
            StatusPolicy statusPolicy = resolveStatusPolicy(configVO, vo.getDocumentStatus());
            if (!statusPolicy.allowStartFlow()) {
                action.setDisabled(true);
                action.setDisabledReason(StringUtils.defaultIfBlank(statusPolicy.reason(), "当前单据状态不可发起主流程"));
            } else if (actions == null || !actions.contains("START_FLOW")) {
                action.setDisabled(true);
                action.setDisabledReason("缺少发起主流程权限");
            }
        }
        runtimeActions.add(action);
        vo.setRuntimeActions(runtimeActions);
    }

    /**
     * 当前登录人有待办时给出对应动作：发起人修改节点给「修改后重提」，其余节点给「去处理」。
     */
    private void addMyTaskAction(List<BusinessDocumentRuntimeVO.RuntimeActionVO> runtimeActions,
                                 BusinessDocumentRuntimeVO vo,
                                 String objectCode) {
        BusinessDocumentRuntimeVO.MyTaskVO myTask = vo.getMyTask();
        if (myTask == null) {
            return;
        }
        boolean initiatorModify = Boolean.TRUE.equals(myTask.getInitiatorModify());
        BusinessDocumentRuntimeVO.RuntimeActionVO action = new BusinessDocumentRuntimeVO.RuntimeActionVO();
        action.setKey(initiatorModify ? "RESUBMIT_FLOW" : "HANDLE_TASK");
        action.setLabel(initiatorModify ? "修改后重提" : "去处理");
        action.setType(initiatorModify ? "success" : "primary");
        action.setActionType(action.getKey());
        action.setVisible(true);
        action.setDisabled(false);
        action.setObjectCode(objectCode);
        action.setRecordId(readRecordId(vo));
        runtimeActions.add(action);
    }

    /**
     * 发起人撤回：流程运行中、当前用户是发起人、且具备 WITHDRAW 权限时展示。
     */
    private void addWithdrawAction(List<BusinessDocumentRuntimeVO.RuntimeActionVO> runtimeActions,
                                   BusinessDocumentRuntimeVO vo,
                                   String objectCode,
                                   AiBusinessFlowInstanceLink link,
                                   List<String> actions) {
        if (link == null || !isRunningFlow(link.getFlowStatus()) || !isFlowInitiator(link)) {
            return;
        }
        if (actions == null || !actions.contains("WITHDRAW")) {
            return;
        }
        // 与实际撤回接口的权限注解一致，不能仅凭对象编辑权限显示入口。
        if (!SessionHelper.hasPermission("ai:businessDocument:withdraw")) {
            return;
        }
        BusinessDocumentRuntimeVO.RuntimeActionVO action = new BusinessDocumentRuntimeVO.RuntimeActionVO();
        action.setKey("WITHDRAW_FLOW");
        action.setLabel("撤回流程");
        action.setType("warning");
        action.setActionType("WITHDRAW_FLOW");
        action.setVisible(true);
        action.setDisabled(false);
        action.setObjectCode(objectCode);
        action.setRecordId(readRecordId(vo));
        runtimeActions.add(action);
    }

    private BusinessDocumentRuntimeVO.RuntimeActionVO findRuntimeAction(BusinessDocumentRuntimeVO runtime,
                                                                        String actionKey) {
        if (runtime == null || runtime.getRuntimeActions() == null) {
            return null;
        }
        return runtime.getRuntimeActions().stream()
                .filter(action -> actionKey.equalsIgnoreCase(action.getKey()))
                .findFirst()
                .orElse(null);
    }

    private boolean isMainFlowConfigured(Map<String, Object> mainFlowSummary) {
        return mainFlowSummary != null && Boolean.TRUE.equals(mainFlowSummary.get("configured"))
                && StringUtils.isNotBlank(text(mainFlowSummary.get("flowModelKey")));
    }

    private boolean isManualStartMode(String startMode) {
        String normalized = StringUtils.defaultIfBlank(startMode, "MANUAL").trim().toUpperCase();
        return "MANUAL".equals(normalized)
                || "BOTH".equals(normalized)
                || "MANUAL_AND_TRIGGER".equals(normalized)
                || "MANUAL_TRIGGER".equals(normalized);
    }

    private StatusPolicy resolveStatusPolicy(BusinessDocumentConfigVO configVO, String documentStatus) {
        if (StringUtils.isBlank(documentStatus)) {
            return new StatusPolicy(false, "单据状态为空，不能发起主流程");
        }
        if (configVO.getStatusMappingRows() != null) {
            for (BusinessDocumentConfigVO.StatusMappingRowVO row : configVO.getStatusMappingRows()) {
                if (row == null) {
                    continue;
                }
                boolean matched = documentStatus.equals(row.getStatusValue())
                        || documentStatus.equalsIgnoreCase(StringUtils.defaultString(row.getStandardStatus()));
                if (!matched) {
                    continue;
                }
                if (Boolean.TRUE.equals(row.getAllowStartFlow())) {
                    return new StatusPolicy(true, null);
                }
                String label = StringUtils.firstNonBlank(row.getDisplayName(), row.getStandardLabel(), documentStatus);
                return new StatusPolicy(false, "当前状态「" + label + "」不可发起主流程");
            }
        }
        if ("DRAFT".equalsIgnoreCase(documentStatus) || "SUBMITTED".equalsIgnoreCase(documentStatus)) {
            return new StatusPolicy(true, null);
        }
        return new StatusPolicy(false, "当前状态「" + documentStatus + "」不可发起主流程");
    }

    private Long readRecordId(BusinessDocumentRuntimeVO vo) {
        String businessKey = vo.getBusinessKey();
        if (StringUtils.isBlank(businessKey) || !businessKey.contains(":")) {
            return null;
        }
        String idText = StringUtils.substringAfter(businessKey, ":");
        try {
            return Long.valueOf(idText);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRunningFlow(String flowStatus) {
        return BusinessDocumentFlowStatus.STARTED.matches(flowStatus)
                || BusinessDocumentFlowStatus.RUNNING.matches(flowStatus)
                || BusinessDocumentFlowStatus.IN_PROCESS.matches(flowStatus)
                || BusinessDocumentFlowStatus.NEED_MODIFY.matches(flowStatus);
    }

    private boolean hasDocumentFlowInstance(AiBusinessFlowInstanceLink link) {
        return link != null && StringUtils.isNotBlank(link.getProcessInstanceId());
    }

    private String resolveStatusLabel(BusinessDocumentConfigVO configVO, String documentStatus) {
        if (StringUtils.isBlank(documentStatus)) {
            return null;
        }
        Map<String, String> mapping = configVO == null ? Collections.emptyMap() : configVO.getStatusMapping();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (documentStatus.equals(entry.getValue())) {
                return switch (entry.getKey()) {
                    case "DRAFT" -> "草稿";
                    case "SUBMITTED" -> "已提交";
                    case "IN_PROCESS" -> "流程中";
                    case "NEED_MODIFY" -> "待修改";
                    case "APPROVED" -> "已通过";
                    case "REJECTED" -> "已驳回";
                    case "CANCELED" -> "已撤回";
                    case "CLOSED" -> "已关闭";
                    default -> entry.getKey();
                };
            }
        }
        return documentStatus;
    }

    private Object firstPresent(Map<String, Object> data, String... keys) {
        if (data == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (StringUtils.isNotBlank(key) && data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object resolveRecordField(Map<String, Object> recordData, String fieldName) {
        if (recordData == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        String[] aliases = fieldAliases(fieldName);
        Object value = firstPresent(recordData, aliases);
        if (value != null) {
            return value;
        }
        Object main = recordData.get("main");
        if (main instanceof Map<?, ?> mainMap) {
            return firstPresent((Map<String, Object>) mainMap, aliases);
        }
        return null;
    }

    private String[] fieldAliases(String fieldName) {
        String trimmed = StringUtils.trimToEmpty(fieldName);
        return new String[] {
                trimmed,
                snakeToCamel(trimmed),
                camelToSnake(trimmed)
        };
    }

    private String buildBusinessKey(String objectCode, Long recordId) {
        return objectCode + ":" + (recordId == null ? "" : recordId);
    }

    private DocumentRuntimeContext resolveRuntimeContext(Long tenantId, String objectCodeOrConfigKey) {
        String requestedObjectCode = StringUtils.trimToNull(objectCodeOrConfigKey);
        AiCrudConfig runtimeConfig = resolvePublishedRuntimeConfig(tenantId, requestedObjectCode);
        AiBusinessDocumentConfig documentConfig = resolveEnabledDocumentConfig(tenantId, requestedObjectCode, runtimeConfig);
        AiBusinessObject businessObject = resolveBusinessObject(tenantId, requestedObjectCode, runtimeConfig, documentConfig);
        String canonicalObjectCode = StringUtils.firstNonBlank(
                documentConfig == null ? null : documentConfig.getObjectCode(),
                businessObject == null ? null : businessObject.getObjectCode(),
                runtimeConfig == null ? null : runtimeConfig.getObjectCode(),
                requestedObjectCode);

        if (documentConfig == null && !StringUtils.equals(canonicalObjectCode, requestedObjectCode)) {
            documentConfig = resolveEnabledDocumentConfig(tenantId, canonicalObjectCode, runtimeConfig);
        }
        if (runtimeConfig == null) {
            runtimeConfig = resolvePublishedRuntimeConfig(tenantId, StringUtils.firstNonBlank(
                    documentConfig == null ? null : documentConfig.getConfigKey(),
                    businessObject == null ? null : businessObject.getConfigKey(),
                    canonicalObjectCode));
        }
        String configKey = StringUtils.firstNonBlank(
                documentConfig == null ? null : documentConfig.getConfigKey(),
                runtimeConfig == null ? null : runtimeConfig.getConfigKey(),
                businessObject == null ? null : businessObject.getConfigKey());
        return new DocumentRuntimeContext(requestedObjectCode, canonicalObjectCode, configKey, documentConfig, runtimeConfig);
    }

    private AiCrudConfig resolvePublishedRuntimeConfig(Long tenantId, String objectCodeOrConfigKey) {
        if (StringUtils.isBlank(objectCodeOrConfigKey)) {
            return null;
        }
        return crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(
                tenantId != null ? tenantId : resolveTenantId(), objectCodeOrConfigKey);
    }

    private AiBusinessDocumentConfig resolveEnabledDocumentConfig(Long tenantId,
                                                                  String objectCodeOrConfigKey,
                                                                  AiCrudConfig runtimeConfig) {
        Long effectiveTenantId = tenantId != null ? tenantId : resolveTenantId();
        LinkedHashSet<String> configKeys = new LinkedHashSet<>();
        if (runtimeConfig != null) {
            configKeys.add(runtimeConfig.getConfigKey());
        }
        configKeys.add(objectCodeOrConfigKey);
        for (String configKey : configKeys) {
            AiBusinessDocumentConfig config = documentConfigService.selectEnabledByConfigKey(effectiveTenantId, configKey);
            if (config != null) {
                return config;
            }
        }

        LinkedHashSet<String> objectCodes = new LinkedHashSet<>();
        if (runtimeConfig != null) {
            objectCodes.add(runtimeConfig.getObjectCode());
        }
        objectCodes.add(objectCodeOrConfigKey);
        for (String objectCode : objectCodes) {
            AiBusinessDocumentConfig config = documentConfigService.selectEnabledByObjectCode(effectiveTenantId, objectCode);
            if (config != null) {
                return config;
            }
        }
        return null;
    }

    private AiBusinessObject resolveBusinessObject(Long tenantId,
                                                   String objectCodeOrConfigKey,
                                                   AiCrudConfig runtimeConfig,
                                                   AiBusinessDocumentConfig documentConfig) {
        Long effectiveTenantId = tenantId != null ? tenantId : resolveTenantId();
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
        return object;
    }

    private List<Long> normalizeRecordIds(List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Collections.emptyList();
        }
        return recordIds.stream()
                .filter(Objects::nonNull)
                .filter(recordId -> StringUtils.isNotBlank(String.valueOf(recordId)))
                .distinct()
                .limit(200)
                .toList();
    }

    private Long parseRecordId(String businessKey) {
        if (StringUtils.isBlank(businessKey) || !businessKey.contains(":")) {
            return null;
        }
        try {
            return Long.valueOf(StringUtils.substringAfter(businessKey, ":"));
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text)
                || "1".equals(text)
                || "yes".equalsIgnoreCase(text);
    }

    private String snakeToCamel(String value) {
        if (StringUtils.isBlank(value) || !value.contains("_")) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result.toString();
    }

    private String camelToSnake(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(ch));
        }
        return result.toString();
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId != null ? tenantId : 1L;
    }

    private String resolveUserId() {
        try {
            Long userId = SessionHelper.getUserId();
            return userId == null ? null : String.valueOf(userId);
        } catch (Exception e) {
            return null;
        }
    }

    private record DocumentRuntimeContext(String requestedObjectCode,
                                          String objectCode,
                                          String configKey,
                                          AiBusinessDocumentConfig documentConfig,
                                          AiCrudConfig runtimeConfig) {
    }

    private record StatusPolicy(boolean allowStartFlow, String reason) {
    }
}
