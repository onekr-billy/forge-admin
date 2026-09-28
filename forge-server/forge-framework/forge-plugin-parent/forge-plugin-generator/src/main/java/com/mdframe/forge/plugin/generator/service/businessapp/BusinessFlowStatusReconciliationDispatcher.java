package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowCallbackDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;

/** Proactively converges local low-code links when the terminal callback was lost. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "forge.business.flow-status-sync", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BusinessFlowStatusReconciliationDispatcher {

    private static final Set<String> RUNNING_REMOTE_STATUSES = Set.of(
            "draft", "running", "active", "suspended");

    private final BusinessFlowStatusReconciliationService reconciliationService;
    private final ObjectProvider<FlowClient> flowClientProvider;
    private final ObjectProvider<BusinessFlowService> businessFlowServiceProvider;

    @Value("${forge.business.flow-status-sync.batch-size:50}")
    private int batchSize = 50;

    @Scheduled(fixedDelayString = "${forge.business.flow-status-sync.scan-interval-ms:15000}")
    public void reconcile() {
        FlowClient flowClient = flowClientProvider.getIfAvailable();
        BusinessFlowService businessFlowService = businessFlowServiceProvider.getIfAvailable();
        if (flowClient == null || businessFlowService == null) {
            return;
        }
        for (AiBusinessFlowInstanceLink candidate : reconciliationService.findCandidates(
                LocalDateTime.now(), Math.max(1, batchSize))) {
            reconcileCandidate(candidate, flowClient, businessFlowService);
        }
    }

    void reconcileCandidate(AiBusinessFlowInstanceLink candidate,
                            FlowClient flowClient,
                            BusinessFlowService businessFlowService) {
        AiBusinessFlowInstanceLink claimed = reconciliationService.claim(candidate);
        if (claimed == null) {
            return;
        }
        try {
            TenantContextHolder.executeWithTenant(claimed.getTenantId(),
                    () -> reconcileClaimed(claimed, flowClient, businessFlowService));
        } catch (RuntimeException failure) {
            try {
                reconciliationService.markFailed(claimed, failure);
            } catch (RuntimeException stateFailure) {
                log.error("流程状态主动对账失败且无法更新恢复状态: linkId={}, failureType={}",
                        claimed.getId(), stateFailure.getClass().getSimpleName());
            }
            log.warn("流程状态主动对账失败: linkId={}, failureType={}",
                    claimed.getId(), failure.getClass().getSimpleName());
        }
    }

    private void reconcileClaimed(AiBusinessFlowInstanceLink claimed,
                                  FlowClient flowClient,
                                  BusinessFlowService businessFlowService) {
        String flowBusinessKey = resolveFlowBusinessKey(claimed);
        if (StringUtils.isBlank(flowBusinessKey)) {
            throw new BusinessException("流程状态对账缺少远端业务Key");
        }
        FlowResult<Map<String, Object>> response = flowClient.getProcessStatus(flowBusinessKey);
        Map<String, Object> remote = requireRemoteStatus(response);
        validateRemoteIdentity(claimed, flowBusinessKey, remote);
        String remoteStatus = normalizeRemoteStatus(remote.get("status"));
        if (RUNNING_REMOTE_STATUSES.contains(remoteStatus)) {
            reconciliationService.markRunning(claimed, remoteStatus);
            return;
        }

        BusinessDocumentFlowStatus terminalStatus = resolveTerminalStatus(remoteStatus);
        BusinessFlowCallbackDTO callback = new BusinessFlowCallbackDTO();
        callback.setTenantId(claimed.getTenantId());
        callback.setProcessInstanceId(claimed.getProcessInstanceId());
        callback.setBusinessKey(claimed.getBusinessKey());
        callback.setFlowStatus(remoteStatus);
        callback.setResult(terminalStatus.getCode());
        callback.setOperatorId(claimed.getStartUserId());
        callback.setVariables(readRemoteVariables(flowClient, flowBusinessKey));
        businessFlowService.handleFlowCallback(callback);
        reconciliationService.markCompleted(claimed, remoteStatus);
    }

    private Map<String, Object> requireRemoteStatus(FlowResult<Map<String, Object>> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException("流程状态对账未读取到可信远端状态");
        }
        return response.getData();
    }

    private Map<String, Object> readRemoteVariables(FlowClient flowClient, String flowBusinessKey) {
        FlowResult<Map<String, Object>> response = flowClient.getProcessVariables(flowBusinessKey);
        if (response == null || !response.isSuccess()) {
            throw new BusinessException("流程状态对账未读取到可信远端变量");
        }
        return response.getData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(response.getData());
    }

    private void validateRemoteIdentity(AiBusinessFlowInstanceLink link,
                                        String flowBusinessKey,
                                        Map<String, Object> remote) {
        String remoteBusinessKey = StringUtils.trimToNull(textValue(remote.get("businessKey")));
        if (remoteBusinessKey != null && !flowBusinessKey.equals(remoteBusinessKey)) {
            throw new BusinessException("流程状态对账远端业务身份不一致");
        }
        String remoteProcessInstanceId = StringUtils.trimToNull(textValue(remote.get("processInstanceId")));
        if (StringUtils.isNotBlank(link.getProcessInstanceId())
                && remoteProcessInstanceId != null
                && !link.getProcessInstanceId().equals(remoteProcessInstanceId)) {
            throw new BusinessException("流程状态对账远端实例身份不一致");
        }
        Long remoteTenantId = parseLong(remote.get("tenantId"));
        if (remoteTenantId != null && !remoteTenantId.equals(link.getTenantId())) {
            throw new BusinessException("流程状态对账远端租户身份不一致");
        }
    }

    private BusinessDocumentFlowStatus resolveTerminalStatus(String remoteStatus) {
        return switch (remoteStatus) {
            case "approved", "completed" -> BusinessDocumentFlowStatus.APPROVED;
            case "rejected" -> BusinessDocumentFlowStatus.REJECTED;
            case "canceled", "terminated" -> BusinessDocumentFlowStatus.CANCELED;
            default -> throw new BusinessException("流程状态对账读取到未知远端状态");
        };
    }

    private String normalizeRemoteStatus(Object value) {
        String status = StringUtils.trimToNull(textValue(value));
        if (status == null) {
            throw new BusinessException("流程状态对账远端状态为空");
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private String resolveFlowBusinessKey(AiBusinessFlowInstanceLink link) {
        JSONObject variables = readJsonObject(link.getVariablesSnapshot());
        return StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(variables.get("flowBusinessKey"))),
                StringUtils.trimToNull(link.getBusinessKey()));
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException invalidIdentity) {
            throw new BusinessException("流程状态对账远端租户身份无效");
        }
    }
}
