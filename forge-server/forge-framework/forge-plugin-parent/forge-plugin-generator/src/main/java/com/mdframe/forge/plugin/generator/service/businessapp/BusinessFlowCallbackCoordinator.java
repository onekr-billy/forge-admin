package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowCallbackDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessApprovalResultEvent;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseLongValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;

/** Coordinates terminal flow callbacks, status persistence, actions and domain events. */
@Slf4j
final class BusinessFlowCallbackCoordinator {

    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final BusinessDocumentConfigService documentConfigService;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final BusinessFlowStatusRepairService statusRepairService;
    private final BusinessFlowStatusTransitionService statusTransitionService;
    private final BusinessFlowTaskEventCoordinator taskEventCoordinator;
    private final ObjectProvider<BusinessActionExecutionService> actionExecutionServiceProvider;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Supplier<Long> tenantIdSupplier;
    private final Supplier<String> usernameSupplier;
    private final BindingResolver bindingResolver;
    private final Function<String, String> terminalResultResolver;

    BusinessFlowCallbackCoordinator(
            BusinessFlowInstanceLinkMapper flowInstanceLinkMapper,
            BusinessDocumentConfigService documentConfigService,
            DynamicCrudService dynamicCrudService,
            BusinessRuntimeConfigResolver runtimeConfigResolver,
            BusinessFlowStatusRepairService statusRepairService,
            BusinessFlowStatusTransitionService statusTransitionService,
            BusinessFlowTaskEventCoordinator taskEventCoordinator,
            ObjectProvider<BusinessActionExecutionService> actionExecutionServiceProvider,
            ApplicationEventPublisher applicationEventPublisher,
            Supplier<Long> tenantIdSupplier,
            Supplier<String> usernameSupplier,
            BindingResolver bindingResolver,
            Function<String, String> terminalResultResolver) {
        this.flowInstanceLinkMapper = flowInstanceLinkMapper;
        this.documentConfigService = documentConfigService;
        this.dynamicCrudService = dynamicCrudService;
        this.runtimeConfigResolver = runtimeConfigResolver;
        this.statusRepairService = statusRepairService;
        this.statusTransitionService = statusTransitionService;
        this.taskEventCoordinator = taskEventCoordinator;
        this.actionExecutionServiceProvider = actionExecutionServiceProvider;
        this.applicationEventPublisher = applicationEventPublisher;
        this.tenantIdSupplier = tenantIdSupplier;
        this.usernameSupplier = usernameSupplier;
        this.bindingResolver = bindingResolver;
        this.terminalResultResolver = terminalResultResolver;
    }

    void handleCallback(BusinessFlowCallbackDTO dto) {
        if (dto == null || (StringUtils.isBlank(dto.getProcessInstanceId())
                && StringUtils.isBlank(dto.getBusinessKey()))) {
            throw new BusinessException("流程回调缺少流程实例ID或业务Key");
        }
        Long tenantId = dto.getTenantId() != null ? dto.getTenantId() : tenantIdSupplier.get();
        AiBusinessFlowInstanceLink link = findCallbackLink(tenantId, dto);
        if (link == null) {
            throw new BusinessException("未找到流程实例关联");
        }
        Long effectiveTenantId = link.getTenantId() != null ? link.getTenantId() : tenantId;
        TenantContextHolder.executeWithTenant(effectiveTenantId, () -> handleInternal(link, dto));
    }

    void handleLinkedCallback(AiBusinessFlowInstanceLink link, BusinessFlowCallbackDTO dto) {
        if (link == null) {
            throw new BusinessException("未找到流程实例关联");
        }
        handleInternal(link, dto);
    }

    void handleEngineEvent(FlowEventContext ctx) {
        BusinessFlowCallbackDTO dto = new BusinessFlowCallbackDTO();
        dto.setProcessInstanceId(StringUtils.trimToNull(ctx.getProcessInstanceId()));
        dto.setBusinessKey(StringUtils.trimToNull(ctx.getBusinessKey()));
        dto.setFlowStatus(ctx.getEvent());
        dto.setResult(resolveFlowEventResult(ctx.getEvent()));
        dto.setTenantId(ctx.getTenantId());
        dto.setNodeKey(ctx.getTaskDefKey());
        dto.setNodeName(ctx.getTaskName());
        dto.setOperatorId(parseLongValue(ctx.getAssigneeId()));
        dto.setVariables(ctx.getVariables() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(ctx.getVariables()));
        Long tenantId = dto.getTenantId() != null ? dto.getTenantId() : tenantIdSupplier.get();
        AiBusinessFlowInstanceLink link = findCallbackLink(tenantId, dto);
        if (link == null) {
            log.debug("[低代码流程回调] 忽略未绑定业务对象的流程事件: event={}, processInstanceId={}, businessKey={}",
                    ctx.getEvent(), ctx.getProcessInstanceId(), ctx.getBusinessKey());
            return;
        }
        Long effectiveTenantId = link.getTenantId() != null ? link.getTenantId() : tenantId;
        try {
            TenantContextHolder.executeWithTenant(effectiveTenantId, () -> handleInternal(link, dto));
        } catch (Exception e) {
            log.warn("[低代码流程回调] 处理流程事件失败: event={}, processInstanceId={}, businessKey={}, error={}",
                    ctx.getEvent(), ctx.getProcessInstanceId(), ctx.getBusinessKey(), e.getMessage(), e);
            throw e;
        }
    }

    private void handleInternal(AiBusinessFlowInstanceLink link, BusinessFlowCallbackDTO dto) {
        if (isEndedLink(link)) {
            String result = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(link.getResult()),
                    terminalResultResolver.apply(link.getFlowStatus()),
                    normalizeCallbackResult(dto));
            reconcileRecordFlowStatus(link, result);
            log.info("流程回调已处理，跳过重复回调: processInstanceId={}, result={}",
                    link.getProcessInstanceId(), result);
            publishBusinessProcessApprovalResult(link, result);
            return;
        }
        AiBusinessDocumentConfig documentConfig = documentConfigService.selectEnabledByObjectCode(
                link.getTenantId(), link.getObjectCode());
        AiCrudConfig runtimeConfig = documentConfig == null
                ? runtimeConfigResolver.published(link.getTenantId(), link.getObjectCode())
                : null;
        AiBusinessBinding binding = bindingResolver.resolve(link.getTenantId(), link.getObjectCode());
        JSONObject bindingConfig = binding == null ? new JSONObject() : readBindingConfig(binding.getBindingConfig());
        AiCrudConfig bindingRuntimeConfig = runtimeConfig != null
                ? runtimeConfig
                : runtimeConfigResolver.published(link.getTenantId(), link.getObjectCode());
        BusinessFlowBindingCodec.ensureBusinessBinding(bindingConfig, bindingRuntimeConfig, documentConfig);
        String configKey = documentConfig != null
                ? documentConfig.getConfigKey()
                : runtimeConfig == null ? null : runtimeConfig.getConfigKey();
        Map<String, Object> previousData = StringUtils.isBlank(configKey)
                ? null
                : dynamicCrudService.selectById(configKey, link.getRecordId());
        String result = normalizeCallbackResult(dto);
        Map<String, Object> startVariables = readJsonObject(link.getVariablesSnapshot());
        AiCrudConfig statusRuntimeConfig = statusRepairService.resolveStatusWriteConfig(
                link, startVariables, runtimeConfig);
        if (StringUtils.isBlank(statusRepairService.configuredStatusField(startVariables))) {
            statusTransitionService.updateBusinessFlowStatus(
                    documentConfig, runtimeConfig, bindingConfig, link.getRecordId(), result);
        }
        statusRepairService.syncConfiguredStatusField(
                statusRuntimeConfig, link.getRecordId(), startVariables, result);

        link.setFlowStatus(result);
        link.setResult(result);
        link.setEndTime(LocalDateTime.now());
        link.setVariablesSnapshot(BusinessFlowLinkRuntimeState.writeModifyTask(
                taskEventCoordinator.mergeLinkVariablesSnapshot(link, dto.getVariables()), null));
        flowInstanceLinkMapper.updateById(link);

        Map<String, Object> currentData = StringUtils.isBlank(configKey)
                ? null
                : dynamicCrudService.selectById(configKey, link.getRecordId());
        executeFlowCallbackAction(link, bindingConfig, result, dto);
        if (StringUtils.isNotBlank(configKey)) {
            currentData = dynamicCrudService.selectById(configKey, link.getRecordId());
        }
        if (documentConfig != null) {
            publishFlowResultEvent(link, documentConfig, result, previousData, currentData, dto);
        } else if (runtimeConfig != null) {
            publishFlowResultEvent(link, runtimeConfig, result, previousData, currentData, dto);
        }
        publishBusinessProcessApprovalResult(link, result);
    }

    private String resolveFlowEventResult(String event) {
        if (FlowCallback.ON_REJECTED.equals(event)) {
            return "REJECTED";
        }
        if (FlowCallback.ON_CANCELED.equals(event)) {
            return "CANCELED";
        }
        if (FlowCallback.ON_COMPLETED.equals(event)) {
            return "APPROVED";
        }
        return event;
    }

    private String normalizeCallbackResult(BusinessFlowCallbackDTO dto) {
        String value = StringUtils.firstNonBlank(dto.getResult(), dto.getFlowStatus());
        if (StringUtils.isBlank(value)) {
            throw new BusinessException("流程回调缺少结果状态");
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.contains("COMPLETED") || normalized.contains("APPROVED") || "APPROVE".equals(normalized)) {
            return "APPROVED";
        }
        if (normalized.contains("REJECT")) {
            return "REJECTED";
        }
        if (normalized.contains("CANCEL") || normalized.contains("WITHDRAW") || normalized.contains("TERMINAT")) {
            return "CANCELED";
        }
        throw new BusinessException("不支持的流程回调结果: " + value);
    }

    private AiBusinessFlowInstanceLink findCallbackLink(Long tenantId, BusinessFlowCallbackDTO dto) {
        if (StringUtils.isNotBlank(dto.getProcessInstanceId())) {
            AiBusinessFlowInstanceLink link = flowInstanceLinkMapper.selectByProcessInstanceId(
                    tenantId, dto.getProcessInstanceId());
            if (link != null) {
                return link;
            }
        }
        return StringUtils.isBlank(dto.getBusinessKey())
                ? null
                : flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, dto.getBusinessKey());
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

    private void reconcileRecordFlowStatus(AiBusinessFlowInstanceLink link, String result) {
        if (link == null || link.getRecordId() == null || StringUtils.isBlank(result)) {
            return;
        }
        Map<String, Object> startVariables = readJsonObject(link.getVariablesSnapshot());
        AiCrudConfig statusRuntimeConfig = statusRepairService.resolveStatusWriteConfig(link, startVariables, null);
        statusRepairService.syncConfiguredStatusField(
                statusRuntimeConfig, link.getRecordId(), startVariables, result);
    }

    private void executeFlowCallbackAction(AiBusinessFlowInstanceLink link,
                                           JSONObject bindingConfig,
                                           String result,
                                           BusinessFlowCallbackDTO dto) {
        String actionCode = resolveFlowCallbackActionCode(bindingConfig, result);
        if (StringUtils.isBlank(actionCode)) {
            return;
        }
        BusinessActionExecutionService actionExecutionService = actionExecutionServiceProvider.getIfAvailable();
        if (actionExecutionService == null) {
            throw new BusinessException("动作执行服务未启用，无法执行流程回调动作");
        }
        BusinessActionExecuteDTO request = new BusinessActionExecuteDTO();
        request.setObjectCode(link.getObjectCode());
        request.setRecordId(link.getRecordId() == null ? null : String.valueOf(link.getRecordId()));
        request.setActionCode(actionCode);
        request.setIdempotencyKey(buildFlowCallbackActionIdempotencyKey(link, result, actionCode));
        request.setContext(buildFlowCallbackActionContext(link, result, dto));
        try {
            actionExecutionService.execute(request);
        } catch (BusinessException e) {
            log.warn("[低代码流程回调] 动作执行失败: objectCode={}, recordId={}, result={}, actionCode={}, error={}",
                    link.getObjectCode(), link.getRecordId(), result, actionCode, e.getMessage());
            throw new BusinessException("流程回调动作执行失败: " + e.getMessage());
        }
    }

    private String resolveFlowCallbackActionCode(JSONObject bindingConfig, String result) {
        if (bindingConfig == null || StringUtils.isBlank(result)) {
            return null;
        }
        JSONObject options = bindingConfig.getJSONObject("options");
        JSONObject callbackActions = options == null ? null : options.getJSONObject("callbackActions");
        if (callbackActions == null || callbackActions.isEmpty()) {
            callbackActions = bindingConfig.getJSONObject("callbackActions");
        }
        if (callbackActions == null || callbackActions.isEmpty()) {
            return null;
        }
        String normalizedResult = StringUtils.defaultString(result).toUpperCase();
        return StringUtils.firstNonBlank(
                callbackActions.getString(normalizedResult),
                callbackActions.getString(normalizedResult.toLowerCase()),
                switch (normalizedResult) {
                    case "APPROVED" -> callbackActions.getString("approvedActionCode");
                    case "REJECTED" -> callbackActions.getString("rejectedActionCode");
                    case "CANCELED" -> callbackActions.getString("canceledActionCode");
                    default -> null;
                });
    }

    private String buildFlowCallbackActionIdempotencyKey(
            AiBusinessFlowInstanceLink link, String result, String actionCode) {
        return "flowCallback:"
                + StringUtils.defaultString(link.getProcessInstanceId(), link.getBusinessKey())
                + ":" + StringUtils.defaultString(result)
                + ":" + StringUtils.defaultString(actionCode);
    }

    private Map<String, Object> buildFlowCallbackActionContext(
            AiBusinessFlowInstanceLink link, String result, BusinessFlowCallbackDTO dto) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("source", "FLOW_CALLBACK");
        context.put("flowResult", result);
        context.put("processInstanceId", link.getProcessInstanceId());
        context.put("businessKey", link.getBusinessKey());
        context.put("flowModelKey", link.getFlowModelKey());
        context.put("operatorId", dto.getOperatorId() != null ? dto.getOperatorId() : link.getStartUserId());
        if (dto.getVariables() != null && !dto.getVariables().isEmpty()) {
            context.put("variables", dto.getVariables());
        }
        return context;
    }

    private void publishFlowResultEvent(AiBusinessFlowInstanceLink link,
                                        AiBusinessDocumentConfig config,
                                        String result,
                                        Map<String, Object> previousData,
                                        Map<String, Object> currentData,
                                        BusinessFlowCallbackDTO dto) {
        String eventType = resolveBusinessEventType(result);
        if (eventType == null) {
            return;
        }
        BusinessEvent event = BusinessEvent.builder()
                .eventType(eventType)
                .suiteCode(config.getSuiteCode())
                .objectCode(link.getObjectCode())
                .configKey(config.getConfigKey())
                .recordId(String.valueOf(link.getRecordId()))
                .recordData(currentData)
                .previousData(previousData)
                .operatorId(dto.getOperatorId() != null ? dto.getOperatorId() : link.getStartUserId())
                .operatorName(usernameSupplier.get())
                .tenantId(link.getTenantId())
                .build();
        applicationEventPublisher.publishEvent(BusinessEventEnvelope.stamp(
                event, BusinessEventEnvelope.SOURCE_FLOW_CALLBACK, flowResultSourceKey(link, result)));
    }

    private void publishFlowResultEvent(AiBusinessFlowInstanceLink link,
                                        AiCrudConfig config,
                                        String result,
                                        Map<String, Object> previousData,
                                        Map<String, Object> currentData,
                                        BusinessFlowCallbackDTO dto) {
        String eventType = resolveBusinessEventType(result);
        if (eventType == null) {
            return;
        }
        BusinessEvent event = BusinessEvent.builder()
                .eventType(eventType)
                .objectCode(link.getObjectCode())
                .configKey(config.getConfigKey())
                .recordId(String.valueOf(link.getRecordId()))
                .recordData(currentData)
                .previousData(previousData)
                .operatorId(dto.getOperatorId() != null ? dto.getOperatorId() : link.getStartUserId())
                .operatorName(usernameSupplier.get())
                .tenantId(link.getTenantId())
                .build();
        applicationEventPublisher.publishEvent(BusinessEventEnvelope.stamp(
                event, BusinessEventEnvelope.SOURCE_FLOW_CALLBACK, flowResultSourceKey(link, result)));
    }

    private String flowResultSourceKey(AiBusinessFlowInstanceLink link, String result) {
        return link.getTenantId() + ":" + link.getProcessInstanceId() + ":" + result;
    }

    private String resolveBusinessEventType(String result) {
        return switch (result) {
            case "APPROVED" -> BusinessEvent.FLOW_APPROVED;
            case "REJECTED" -> BusinessEvent.FLOW_REJECTED;
            case "CANCELED" -> BusinessEvent.FLOW_CANCELED;
            default -> null;
        };
    }

    private void publishBusinessProcessApprovalResult(AiBusinessFlowInstanceLink link, String result) {
        if (link == null || link.getTenantId() == null
                || StringUtils.isAnyBlank(link.getProcessInstanceId(), result)) {
            return;
        }
        String normalized = result.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVED", "REJECTED", "CANCELED", "FAILED").contains(normalized)) {
            return;
        }
        applicationEventPublisher.publishEvent(new BusinessProcessApprovalResultEvent(
                link.getTenantId(), link.getProcessInstanceId(), normalized));
    }

    @FunctionalInterface
    interface BindingResolver {
        AiBusinessBinding resolve(Long tenantId, String objectCode);
    }
}
