package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentRuntimeVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单据运行态动作策略与投影器。
 *
 * <p>Policy/Specification 负责主流程、发起模式、单据状态及权限判定，Projector
 * 负责把判定结果转换成前端运行态动作，避免查询服务同时维护展示协议。</p>
 */
final class BusinessDocumentRuntimeActionPolicy {

    void fillNextAction(BusinessDocumentRuntimeVO runtime,
                        BusinessDocumentConfigVO config,
                        AiBusinessFlowInstanceLink link,
                        List<String> actions) {
        Map<String, Object> mainFlowSummary = config.getMainFlowSummary();
        if (!isMainFlowConfigured(mainFlowSummary)) {
            runtime.setNextAction("CONFIG_FLOW");
            runtime.setMessage("单据模式已启用，尚未配置默认流程");
            return;
        }
        if (link != null && isRunningFlow(link.getFlowStatus())) {
            if (runtime.getMyTask() != null) {
                boolean initiatorModify = Boolean.TRUE.equals(runtime.getMyTask().getInitiatorModify());
                runtime.setNextAction(initiatorModify ? "RESUBMIT_FLOW" : "HANDLE_TASK");
                runtime.setMessage(initiatorModify ? "已驳回，修改后可重新提交" : "有待你处理的审批节点");
                return;
            }
            runtime.setNextAction("VIEW_FLOW");
            runtime.setMessage("流程流转中");
            return;
        }
        if (hasDocumentFlowInstance(link)) {
            runtime.setNextAction("VIEW_FLOW");
            runtime.setMessage("流程已结束，可查看审批记录");
            return;
        }
        if (!isManualStartMode(text(mainFlowSummary.get("startMode")))) {
            runtime.setNextAction("CONFIG_TRIGGER");
            runtime.setMessage("当前主流程配置为触发器自动发起");
            return;
        }
        StatusPolicy statusPolicy = resolveStatusPolicy(config, runtime.getDocumentStatus());
        if (!statusPolicy.allowStartFlow()) {
            runtime.setNextAction("WAIT_STATUS");
            runtime.setMessage(StringUtils.defaultIfBlank(statusPolicy.reason(), "当前单据状态不可发起主流程"));
            return;
        }
        if (actions.contains("START_FLOW")) {
            runtime.setNextAction("START_FLOW");
            runtime.setMessage("可发起主流程");
            return;
        }
        runtime.setNextAction("REQUEST_PERMISSION");
        runtime.setMessage("缺少可执行的单据动作权限");
    }

    void fillRuntimeActions(BusinessDocumentRuntimeVO runtime,
                            AiBusinessDocumentConfig documentConfig,
                            BusinessDocumentConfigVO config,
                            AiBusinessFlowInstanceLink link,
                            List<String> actions) {
        List<BusinessDocumentRuntimeVO.RuntimeActionVO> runtimeActions = new ArrayList<>();
        addMyTaskAction(runtimeActions, runtime, documentConfig.getObjectCode());
        addWithdrawAction(runtimeActions, runtime, documentConfig.getObjectCode(), link, actions);
        Map<String, Object> mainFlowSummary = config.getMainFlowSummary();
        String startMode = mainFlowSummary == null ? "MANUAL" : text(mainFlowSummary.get("startMode"));
        if (!isManualStartMode(startMode)) {
            runtime.setRuntimeActions(runtimeActions);
            return;
        }
        Map<String, Object> options = config.getOptions();
        if (!readBoolean(options == null ? null : options.get("showStartFlowAction"), true)
                || !readBoolean(options == null ? null : options.get("showRuntimeStartFlowAction"), true)
                || readBoolean(options == null ? null : options.get("hideStartFlowAction"), false)) {
            runtime.setRuntimeActions(runtimeActions);
            return;
        }

        BusinessDocumentRuntimeVO.RuntimeActionVO action = action(
                "START_FLOW", "发起主流程", "success", documentConfig.getObjectCode(), runtime);
        if (!isMainFlowConfigured(mainFlowSummary)) {
            action.setDisabled(true);
            action.setDisabledReason("请先配置主流程");
        } else if (hasDocumentFlowInstance(link)) {
            action.setVisible(false);
        } else {
            StatusPolicy statusPolicy = resolveStatusPolicy(config, runtime.getDocumentStatus());
            if (!statusPolicy.allowStartFlow()) {
                action.setDisabled(true);
                action.setDisabledReason(StringUtils.defaultIfBlank(statusPolicy.reason(), "当前单据状态不可发起主流程"));
            } else if (actions == null || !actions.contains("START_FLOW")) {
                action.setDisabled(true);
                action.setDisabledReason("缺少发起主流程权限");
            }
        }
        runtimeActions.add(action);
        runtime.setRuntimeActions(runtimeActions);
    }

    void addMyTaskAction(List<BusinessDocumentRuntimeVO.RuntimeActionVO> runtimeActions,
                         BusinessDocumentRuntimeVO runtime,
                         String objectCode) {
        BusinessDocumentRuntimeVO.MyTaskVO myTask = runtime.getMyTask();
        if (myTask == null) {
            return;
        }
        boolean initiatorModify = Boolean.TRUE.equals(myTask.getInitiatorModify());
        runtimeActions.add(action(
                initiatorModify ? "RESUBMIT_FLOW" : "HANDLE_TASK",
                initiatorModify ? "修改后重提" : "去处理",
                initiatorModify ? "success" : "primary",
                objectCode,
                runtime));
    }

    void addWithdrawAction(List<BusinessDocumentRuntimeVO.RuntimeActionVO> runtimeActions,
                           BusinessDocumentRuntimeVO runtime,
                           String objectCode,
                           AiBusinessFlowInstanceLink link,
                           List<String> actions) {
        if (link == null || !isRunningFlow(link.getFlowStatus()) || !isFlowInitiator(link)) {
            return;
        }
        if (actions == null || !actions.contains("WITHDRAW")) {
            return;
        }
        if (!SessionHelper.hasPermission("ai:businessDocument:withdraw")) {
            return;
        }
        runtimeActions.add(action("WITHDRAW_FLOW", "撤回流程", "warning", objectCode, runtime));
    }

    BusinessDocumentRuntimeVO.RuntimeActionVO findRuntimeAction(BusinessDocumentRuntimeVO runtime,
                                                                 String actionKey) {
        if (runtime == null || runtime.getRuntimeActions() == null) {
            return null;
        }
        return runtime.getRuntimeActions().stream()
                .filter(action -> actionKey.equalsIgnoreCase(action.getKey()))
                .findFirst()
                .orElse(null);
    }

    boolean isRunningFlow(String flowStatus) {
        return BusinessDocumentFlowStatus.STARTED.matches(flowStatus)
                || BusinessDocumentFlowStatus.RUNNING.matches(flowStatus)
                || BusinessDocumentFlowStatus.IN_PROCESS.matches(flowStatus)
                || BusinessDocumentFlowStatus.NEED_MODIFY.matches(flowStatus);
    }

    boolean isFlowInitiator(AiBusinessFlowInstanceLink link) {
        if (link == null || link.getStartUserId() == null) {
            return false;
        }
        return String.valueOf(link.getStartUserId()).equals(resolveUserId());
    }

    String resolveStatusLabel(BusinessDocumentConfigVO config, String documentStatus) {
        if (StringUtils.isBlank(documentStatus)) {
            return null;
        }
        Map<String, String> mapping = config == null ? Collections.emptyMap() : config.getStatusMapping();
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

    private BusinessDocumentRuntimeVO.RuntimeActionVO action(String key,
                                                             String label,
                                                             String type,
                                                             String objectCode,
                                                             BusinessDocumentRuntimeVO runtime) {
        BusinessDocumentRuntimeVO.RuntimeActionVO action = new BusinessDocumentRuntimeVO.RuntimeActionVO();
        action.setKey(key);
        action.setLabel(label);
        action.setType(type);
        action.setActionType(key);
        action.setVisible(true);
        action.setDisabled(false);
        action.setObjectCode(objectCode);
        action.setRecordId(readRecordId(runtime));
        return action;
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

    private StatusPolicy resolveStatusPolicy(BusinessDocumentConfigVO config, String documentStatus) {
        if (StringUtils.isBlank(documentStatus)) {
            return new StatusPolicy(false, "单据状态为空，不能发起主流程");
        }
        if (config.getStatusMappingRows() != null) {
            for (BusinessDocumentConfigVO.StatusMappingRowVO row : config.getStatusMappingRows()) {
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

    private Long readRecordId(BusinessDocumentRuntimeVO runtime) {
        String businessKey = runtime.getBusinessKey();
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

    private boolean hasDocumentFlowInstance(AiBusinessFlowInstanceLink link) {
        return link != null && StringUtils.isNotBlank(link.getProcessInstanceId());
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private String resolveUserId() {
        try {
            Long userId = SessionHelper.getUserId();
            return userId == null ? null : String.valueOf(userId);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record StatusPolicy(boolean allowStartFlow, String reason) {
    }
}
