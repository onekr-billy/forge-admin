package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowCallbackDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;

/** Coordinates non-terminal Flowable task events and running document state transitions. */
@Slf4j
final class BusinessFlowTaskEventCoordinator {

    private static final Set<String> RUNNING_DOCUMENT_STATUS_KEYS = Set.of(
            "DRAFT", "SUBMITTED", "IN_PROCESS", "NEED_MODIFY");

    private final Supplier<FlowClient> flowClientSupplier;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final BusinessFlowStatusRepairService statusRepairService;
    private final Supplier<PlatformTransactionManager> transactionManagerSupplier;
    private final Supplier<Long> tenantIdSupplier;
    private final Supplier<Long> userIdSupplier;
    private final BindingResolver bindingResolver;
    private final StatusUpdater statusUpdater;

    BusinessFlowTaskEventCoordinator(
            Supplier<FlowClient> flowClientSupplier,
            BusinessFlowInstanceLinkMapper flowInstanceLinkMapper,
            BusinessDocumentConfigService documentConfigService,
            BusinessRuntimeConfigResolver runtimeConfigResolver,
            BusinessFlowStatusRepairService statusRepairService,
            Supplier<PlatformTransactionManager> transactionManagerSupplier,
            Supplier<Long> tenantIdSupplier,
            Supplier<Long> userIdSupplier,
            BindingResolver bindingResolver,
            StatusUpdater statusUpdater) {
        this.flowClientSupplier = flowClientSupplier;
        this.flowInstanceLinkMapper = flowInstanceLinkMapper;
        this.documentConfigService = documentConfigService;
        this.runtimeConfigResolver = runtimeConfigResolver;
        this.statusRepairService = statusRepairService;
        this.transactionManagerSupplier = transactionManagerSupplier;
        this.tenantIdSupplier = tenantIdSupplier;
        this.userIdSupplier = userIdSupplier;
        this.bindingResolver = bindingResolver;
        this.statusUpdater = statusUpdater;
    }

    void handleTaskEvent(FlowEventContext ctx) {
        PlatformTransactionManager transactionManager = transactionManagerSupplier.get();
        Long tenantId = ctx.getTenantId() != null ? ctx.getTenantId() : tenantIdSupplier.get();
        BusinessFlowCallbackDTO probe = new BusinessFlowCallbackDTO();
        probe.setProcessInstanceId(StringUtils.trimToNull(ctx.getProcessInstanceId()));
        probe.setBusinessKey(StringUtils.trimToNull(ctx.getBusinessKey()));
        AiBusinessFlowInstanceLink link = findCallbackLink(tenantId, probe);
        if (link == null || isEndedLink(link)) {
            return;
        }
        Long effectiveTenantId = link.getTenantId() != null ? link.getTenantId() : tenantId;
        Runnable work = () -> TenantContextHolder.executeWithTenant(effectiveTenantId, () -> {
            if (FlowCallback.ON_TASK_COMPLETED.equals(ctx.getEvent())) {
                handleTaskCompletedEvent(link, ctx);
            } else {
                handleTaskCreatedEvent(link, ctx);
            }
        });
        try {
            if (transactionManager != null) {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(status -> work.run());
            } else {
                work.run();
            }
        } catch (Exception e) {
            log.warn("[低代码流程回调] 任务事件同步单据状态失败: event={}, processInstanceId={}, taskId={}, error={}",
                    ctx.getEvent(), ctx.getProcessInstanceId(), ctx.getTaskId(), e.getMessage());
        }
    }

    /**
     * 新待办产生。令牌回到发起人且流程上存在驳回痕迹时，视为发起人修改节点：
     * 记录待办并把单据切到待修改；否则说明已进入审批节点，纠正回流程中。
     */
    private void handleTaskCreatedEvent(AiBusinessFlowInstanceLink link, FlowEventContext ctx) {
        if (isInitiatorModifyNode(ctx) || (isInitiatorTask(link, ctx) && hasRejectEvidence(link, ctx))) {
            writeModifyTask(link, new BusinessFlowLinkRuntimeState.ModifyTask(
                    StringUtils.trimToNull(ctx.getTaskId()),
                    StringUtils.trimToNull(ctx.getTaskDefKey()),
                    StringUtils.trimToNull(ctx.getTaskName()),
                    StringUtils.trimToNull(ctx.getAssigneeId())));
            applyRunningFlowState(link, BusinessDocumentFlowStatus.NEED_MODIFY);
            log.info("[低代码流程回调] 进入发起人修改节点，单据切换为待修改: businessKey={}, taskId={}, taskDefKey={}",
                    link.getBusinessKey(), ctx.getTaskId(), ctx.getTaskDefKey());
            return;
        }
        writeModifyTask(link, null);
        applyRunningFlowState(link, BusinessDocumentFlowStatus.IN_PROCESS);
    }

    private boolean isInitiatorModifyNode(FlowEventContext ctx) {
        String taskDefKey = StringUtils.trimToNull(ctx == null ? null : ctx.getTaskDefKey());
        return "Forge_InitiatorModify".equals(taskDefKey)
                || (taskDefKey != null && taskDefKey.startsWith("Forge_InitiatorModify"));
    }

    /**
     * 待办办理完成。审批人驳回时先落待修改，紧随的任务创建事件会补齐修改待办；
     * 若 BPMN 的驳回分支直接走到结束事件，终态事件会把状态覆盖成已驳回。
     */
    private void handleTaskCompletedEvent(AiBusinessFlowInstanceLink link, FlowEventContext ctx) {
        boolean rejected = isRejectTaskAction(ctx.getVariables());
        if (isRecordedModifyTask(link, ctx.getTaskId())) {
            writeModifyTask(link, null);
            if (!rejected) {
                applyRunningFlowState(link, BusinessDocumentFlowStatus.IN_PROCESS);
            }
            return;
        }
        if (rejected) {
            applyRunningFlowState(link, BusinessDocumentFlowStatus.NEED_MODIFY);
        }
    }

    /**
     * 兜底修复发起人修改节点状态。任务事件可能因回调丢失或时序问题没落地，
     * 发起人在修改节点保存字段时补一次，避免单据一直停在流程中而拿不到重提入口。
     */
    void repairInitiatorModifyState(BusinessTaskFormContextQueryDTO query,
                                            TaskFormRuntimeContext runtime,
                                            Map<String, Object> taskFormInfo) {
        Long userId = userIdSupplier.get();
        if (userId == null) {
            return;
        }
        String businessKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getBusinessKey()),
                runtime == null ? null : StringUtils.trimToNull(runtime.businessKey()));
        AiBusinessFlowInstanceLink link = findRuntimeLink(
                tenantIdSupplier.get(), StringUtils.trimToNull(query.getProcessInstanceId()), businessKey);
        if (link == null || isEndedLink(link) || !userId.equals(link.getStartUserId())) {
            return;
        }
        if (isRecordedModifyTask(link, query.getTaskId())) {
            return;
        }
        // 发起人也可能本身就是某个审批节点的处理人，必须确认这次流转是驳回引起的。
        if (!isRejectTaskAction(readFlowProcessVariables(resolveFlowEngineBusinessKey(link)))) {
            return;
        }
        writeModifyTask(link, new BusinessFlowLinkRuntimeState.ModifyTask(
                query.getTaskId(),
                StringUtils.firstNonBlank(
                        StringUtils.trimToNull(query.getTaskDefKey()),
                        textValue(taskFormInfo == null ? null : taskFormInfo.get("taskDefKey"))),
                textValue(taskFormInfo == null ? null : taskFormInfo.get("taskName")),
                String.valueOf(userId)));
        applyRunningFlowState(link, BusinessDocumentFlowStatus.NEED_MODIFY);
        log.info("[低代码流程] 修复发起人修改节点状态: businessKey={}, taskId={}",
                link.getBusinessKey(), query.getTaskId());
    }

    private boolean isInitiatorTask(AiBusinessFlowInstanceLink link, FlowEventContext ctx) {
        String assigneeId = StringUtils.trimToNull(ctx.getAssigneeId());
        if (assigneeId == null) {
            return false;
        }
        String initiatorId = StringUtils.firstNonBlank(
                link.getStartUserId() == null ? null : String.valueOf(link.getStartUserId()),
                StringUtils.trimToNull(ctx.getStartUserId()));
        return StringUtils.isNotBlank(initiatorId) && initiatorId.equals(assigneeId);
    }

    private boolean isRecordedModifyTask(AiBusinessFlowInstanceLink link, String taskId) {
        BusinessFlowLinkRuntimeState.ModifyTask recorded =
                BusinessFlowLinkRuntimeState.readModifyTask(link.getVariablesSnapshot());
        return recorded != null && StringUtils.isNotBlank(taskId) && taskId.equals(recorded.taskId());
    }

    /**
     * 任务创建事件不携带流程变量，需要回查流程实例变量确认这次流转是驳回引起的，
     * 避免把“发起人本身就是首个审批人”误判成发起人修改节点。
     */
    private boolean hasRejectEvidence(AiBusinessFlowInstanceLink link, FlowEventContext ctx) {
        if (isRejectTaskAction(ctx.getVariables())) {
            return true;
        }
        return isRejectTaskAction(readFlowProcessVariables(resolveFlowEngineBusinessKey(link)));
    }

    private Map<String, Object> readFlowProcessVariables(String businessKey) {
        if (flowClientSupplier.get() == null || StringUtils.isBlank(businessKey)) {
            return Map.of();
        }
        try {
            FlowResult<Map<String, Object>> result = flowClientSupplier.get().getProcessVariables(businessKey);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return Map.of();
            }
            return result.getData();
        } catch (Exception e) {
            log.debug("[低代码流程回调] 读取流程变量失败: businessKey={}, error={}", businessKey, e.getMessage());
            return Map.of();
        }
    }

    private boolean isRejectTaskAction(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return false;
        }
        String approvalResult = textValue(variables.get("approvalResult"));
        if (StringUtils.isNotBlank(approvalResult) && "reject".equalsIgnoreCase(approvalResult.trim())) {
            return true;
        }
        if (readBooleanValue(variables.get("rejectToStart"), false)) {
            return true;
        }
        Object approved = variables.get("approved");
        return approved != null && !readBooleanValue(approved, true);
    }

    /**
     * 合并流程变量到关联快照。发起时写入的 {@code flowBusinessKey}、{@code statusField}
     * 是后续读取引擎状态和回写状态字段的依据，整体覆盖会导致这些键丢失。
     */
    String mergeLinkVariablesSnapshot(AiBusinessFlowInstanceLink link, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return link.getVariablesSnapshot();
        }
        JSONObject snapshot = readJsonObject(link.getVariablesSnapshot());
        snapshot.putAll(variables);
        return JSON.toJSONString(snapshot);
    }

    private void writeModifyTask(AiBusinessFlowInstanceLink link,
                                 BusinessFlowLinkRuntimeState.ModifyTask task) {
        BusinessFlowLinkRuntimeState.ModifyTask recorded =
                BusinessFlowLinkRuntimeState.readModifyTask(link.getVariablesSnapshot());
        if (task == null ? recorded == null : task.equals(recorded)) {
            // 每次任务创建都会走清除分支，没有变化时不要产生无意义的 UPDATE。
            return;
        }
        String snapshot = BusinessFlowLinkRuntimeState.writeModifyTask(link.getVariablesSnapshot(), task);
        link.setVariablesSnapshot(snapshot);
        AiBusinessFlowInstanceLink update = new AiBusinessFlowInstanceLink();
        update.setId(link.getId());
        update.setVariablesSnapshot(snapshot);
        flowInstanceLinkMapper.updateById(update);
    }

    /**
     * 写入流程运行期间的单据状态。只在单据当前仍处于运行态时翻转，
     * 已经落定为通过/驳回/取消/关闭的单据不再被任务事件改写。
     */
    private void applyRunningDocumentStatus(AiBusinessFlowInstanceLink link, String targetStatusKey) {
        if (link.getRecordId() == null || StringUtils.isBlank(targetStatusKey)) {
            return;
        }
        AiBusinessDocumentConfig documentConfig = documentConfigService.selectEnabledByObjectCode(
                link.getTenantId(), link.getObjectCode());
        AiCrudConfig runtimeConfig = documentConfig == null
                ? runtimeConfigResolver.published(link.getTenantId(), link.getObjectCode())
                : null;
        Map<String, Object> startVariables = readJsonObject(link.getVariablesSnapshot());
        AiCrudConfig statusRuntimeConfig = statusRepairService.resolveStatusWriteConfig(
                link, startVariables, runtimeConfig);
        String currentStatusKey = statusRepairService.resolveCurrentDocumentStatusKey(
                link, documentConfig, statusRuntimeConfig, startVariables);
        if (targetStatusKey.equals(currentStatusKey)) {
            return;
        }
        if (currentStatusKey != null && !RUNNING_DOCUMENT_STATUS_KEYS.contains(currentStatusKey)) {
            return;
        }
        AiBusinessBinding binding = bindingResolver.resolve(link.getTenantId(), link.getObjectCode());
        JSONObject bindingConfig = binding == null ? new JSONObject() : readBindingConfig(binding.getBindingConfig());
        BusinessFlowBindingCodec.ensureBusinessBinding(bindingConfig, runtimeConfig, documentConfig);
        if (StringUtils.isBlank(statusRepairService.configuredStatusField(startVariables))) {
            statusUpdater.update(documentConfig, runtimeConfig, bindingConfig,
                    link.getRecordId(), targetStatusKey);
        }
        statusRepairService.syncConfiguredStatusField(
                statusRuntimeConfig, link.getRecordId(), startVariables, targetStatusKey);
    }

    /**
     * 统一维护流程运行期间的双状态：低代码记录状态与流程关联状态必须一致。
     * 终态关联不接受延迟到达的任务级事件，避免已通过/已撤回后被改回流程中。
     */
    void applyRunningFlowState(AiBusinessFlowInstanceLink link,
                                       BusinessDocumentFlowStatus targetStatus) {
        if (link == null || targetStatus == null || isEndedLink(link)) {
            return;
        }
        applyRunningDocumentStatus(link, targetStatus.getCode());
        boolean changed = !targetStatus.matches(link.getFlowStatus())
                || link.getResult() != null
                || link.getEndTime() != null;
        if (!changed) {
            return;
        }
        link.setFlowStatus(targetStatus.getCode());
        link.setResult(null);
        link.setEndTime(null);
        flowInstanceLinkMapper.updateById(link);
    }

    private AiBusinessFlowInstanceLink findCallbackLink(Long tenantId, BusinessFlowCallbackDTO dto) {
        return findRuntimeLink(tenantId, dto.getProcessInstanceId(), dto.getBusinessKey());
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
                ? null
                : flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, businessKey);
    }

    private String resolveFlowEngineBusinessKey(AiBusinessFlowInstanceLink link) {
        if (link == null) {
            return null;
        }
        JSONObject variables = readJsonObject(link.getVariablesSnapshot());
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(variables.get("flowBusinessKey"))),
                StringUtils.trimToNull(link.getBusinessKey()));
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

    @FunctionalInterface
    interface BindingResolver {
        AiBusinessBinding resolve(Long tenantId, String objectCode);
    }

    @FunctionalInterface
    interface StatusUpdater {
        void update(AiBusinessDocumentConfig documentConfig,
                    AiCrudConfig runtimeConfig,
                    JSONObject bindingConfig,
                    Long recordId,
                    String statusKey);
    }
}
