package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowStartDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.resolveFlowModelKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.buildBusinessKey;

/** Coordinates business record validation, locking, Flowable start and local state persistence. */
@Slf4j
final class BusinessFlowStartCoordinator {

    private static final BusinessFlowStartContextAssembler CONTEXT_ASSEMBLER =
            BusinessFlowStartContextAssembler.standard();

    private final Supplier<FlowClient> flowClientSupplier;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessDocumentRuntimeService documentRuntimeService;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessFlowRuntimeContextResolver runtimeContextResolver;
    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final BusinessFlowBindingResolver bindingResolver;
    private final BusinessFlowFormAssetAssembler formAssetAssembler;
    private final BusinessFlowStatusRepairService statusRepairService;
    private final BusinessFlowStatusTransitionService statusTransitionService;
    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final Supplier<Long> userIdSupplier;
    private final Supplier<Long> activeOrgIdSupplier;
    private final Supplier<String> usernameSupplier;
    private final BusinessFlowStartLockManager lockManager = new BusinessFlowStartLockManager();

    BusinessFlowStartCoordinator(
            Supplier<FlowClient> flowClientSupplier,
            BusinessFlowInstanceLinkMapper flowInstanceLinkMapper,
            BusinessDocumentConfigService documentConfigService,
            BusinessDocumentRuntimeService documentRuntimeService,
            DynamicCrudService dynamicCrudService,
            BusinessFlowRuntimeContextResolver runtimeContextResolver,
            BusinessRuntimeConfigResolver runtimeConfigResolver,
            BusinessFlowBindingResolver bindingResolver,
            BusinessFlowFormAssetAssembler formAssetAssembler,
            BusinessFlowStatusRepairService statusRepairService,
            BusinessFlowStatusTransitionService statusTransitionService,
            ObjectProvider<RedissonClient> redissonClientProvider,
            Supplier<Long> userIdSupplier,
            Supplier<Long> activeOrgIdSupplier,
            Supplier<String> usernameSupplier) {
        this.flowClientSupplier = flowClientSupplier;
        this.flowInstanceLinkMapper = flowInstanceLinkMapper;
        this.documentConfigService = documentConfigService;
        this.documentRuntimeService = documentRuntimeService;
        this.dynamicCrudService = dynamicCrudService;
        this.runtimeContextResolver = runtimeContextResolver;
        this.runtimeConfigResolver = runtimeConfigResolver;
        this.bindingResolver = bindingResolver;
        this.formAssetAssembler = formAssetAssembler;
        this.statusRepairService = statusRepairService;
        this.statusTransitionService = statusTransitionService;
        this.redissonClientProvider = redissonClientProvider;
        this.userIdSupplier = userIdSupplier;
        this.activeOrgIdSupplier = activeOrgIdSupplier;
        this.usernameSupplier = usernameSupplier;
    }

    BusinessFlowRuntimeVO start(BusinessFlowStartDTO dto,
                                boolean checkPermission,
                                Long starterUserId,
                                String starterUserName,
                                Long tenantId,
                                boolean stableBusinessKey,
                                boolean allowDraftRuntime) {
        validateRequest(dto);
        FlowClient flowClient = flowClientSupplier.get();
        if (flowClient == null) {
            throw new BusinessException("流程服务未配置，无法发起主流程");
        }

        FlowStartContext context = resolveFlowStartContext(tenantId, dto.getObjectCode(), allowDraftRuntime);
        Map<String, Object> recordData = allowDraftRuntime
                ? dynamicCrudService.selectByIdAllowDraft(context.configKey(), dto.getRecordId())
                : dynamicCrudService.selectById(context.configKey(), dto.getRecordId());
        if (recordData == null) {
            log.warn("[低代码流程启动] 业务记录查询为空: tenantId={}, objectCode={}, configKey={}, recordId={}, "
                            + "starterUserId={}, activeOrgId={}, checkPermission={}, stableBusinessKey={}",
                    tenantId, context.objectCode(), context.configKey(), dto.getRecordId(),
                    starterUserId != null ? starterUserId : userIdSupplier.get(),
                    activeOrgIdSupplier.get(), checkPermission, stableBusinessKey);
            throw new BusinessException(
                    404,
                    "记录不存在或无权限访问，请使用当前委托用户可见的已保存业务记录 ID");
        }

        String businessKey = buildBusinessKey(context.objectCode(), dto.getRecordId());
        return lockManager.execute(tenantId, businessKey, redissonClientProvider,
                () -> startLocked(flowClient, dto, checkPermission, starterUserId, starterUserName,
                        tenantId, context, recordData, businessKey, stableBusinessKey));
    }

    private BusinessFlowRuntimeVO startLocked(FlowClient flowClient,
                                              BusinessFlowStartDTO dto,
                                              boolean checkPermission,
                                              Long starterUserId,
                                              String starterUserName,
                                              Long tenantId,
                                              FlowStartContext context,
                                              Map<String, Object> recordData,
                                              String businessKey,
                                              boolean stableBusinessKey) {
        AiBusinessBinding binding = bindingResolver.selectForStart(
                tenantId, context.objectCode(), context.requestedObjectCode());
        JSONObject bindingConfig = binding == null
                ? new JSONObject()
                : readBindingConfig(binding.getBindingConfig());
        BusinessFlowBindingCodec.ensureBusinessBinding(
                bindingConfig, context.runtimeConfig(), context.documentConfig());
        String dtoFlowModelKey = StringUtils.trimToNull(dto.getFlowModelKey());
        String bindingConfigFlowModelKey = resolveFlowModelKey(bindingConfig);
        String bindingKey = binding == null ? null : StringUtils.trimToNull(binding.getBindingKey());
        String documentDefaultFlowKey = context.documentConfig() == null
                ? null : StringUtils.trimToNull(context.documentConfig().getDefaultFlowKey());
        String flowModelKey = StringUtils.firstNonBlank(
                dtoFlowModelKey, bindingConfigFlowModelKey, bindingKey, documentDefaultFlowKey);
        if (StringUtils.isBlank(flowModelKey)) {
            log.warn("[低代码流程启动] 主流程解析失败: tenantId={}, objectCode={}, recordId={}, checkPermission={}, "
                            + "configKey={}, documentConfigId={}, documentEnabled={}, documentDefaultFlowKey={}, "
                            + "runtimeConfigId={}, runtimeConfigKey={}, binding={}, dtoFlowModelKey={}, "
                            + "bindingConfigFlowModelKey={}, bindingConfigPreview={}",
                    tenantId, context.objectCode(), dto.getRecordId(), checkPermission, context.configKey(),
                    context.documentConfig() == null ? null : context.documentConfig().getId(),
                    context.documentConfig() == null ? null : context.documentConfig().getDocumentEnabled(),
                    documentDefaultFlowKey,
                    context.runtimeConfig() == null ? null : context.runtimeConfig().getId(),
                    context.runtimeConfig() == null ? null : context.runtimeConfig().getConfigKey(),
                    bindingResolver.describe(binding), dtoFlowModelKey, bindingConfigFlowModelKey,
                    previewBindingConfig(bindingConfig));
            throw new BusinessException("请先在流程与自动化中配置主流程");
        }

        AiBusinessFlowInstanceLink latestLink = flowInstanceLinkMapper.selectLatestByBusinessKey(
                tenantId, businessKey);
        validateDocumentStart(context, dto, binding, recordData, latestLink, checkPermission);
        if (isRunningFlowLink(latestLink)) {
            return toRuntimeVO(latestLink, "当前单据已有流转中的流程");
        }
        log.info("[低代码流程启动] 主流程解析成功: tenantId={}, objectCode={}, recordId={}, configKey={}, "
                        + "flowModelKey={}, bindingId={}, bindingType={}",
                tenantId, context.objectCode(), dto.getRecordId(), context.configKey(), flowModelKey,
                binding == null ? null : binding.getId(), binding == null ? null : binding.getBindingType());

        BusinessFlowStartContextAssembler.StartContext startContext = CONTEXT_ASSEMBLER.assemble(
                bindingConfig, recordData, dto.getVariables(), context.objectCode());
        Map<String, Object> flowVariables = startContext.variables();
        flowVariables.put("objectCode", context.objectCode());
        flowVariables.put("configKey", context.configKey());
        flowVariables.put("recordId", dto.getRecordId());
        flowVariables.put("businessKey", businessKey);
        String flowBusinessKey = stableBusinessKey
                ? businessKey : resolveFlowBusinessKeyForStart(businessKey, latestLink);
        flowVariables.put("documentBusinessKey", businessKey);
        flowVariables.put("recordBusinessKey", businessKey);
        flowVariables.put("flowBusinessKey", flowBusinessKey);

        String userName = StringUtils.defaultIfBlank(starterUserName, usernameSupplier.get());
        String title = CONTEXT_ASSEMBLER.renderTitle(
                StringUtils.defaultIfBlank(dto.getTitle(), startContext.title()),
                recordData,
                context.objectCode(),
                userName,
                formAssetAssembler.resolveRuntimeCrudObjectName(null, context.runtimeConfig()));
        Long userId = starterUserId != null ? starterUserId : userIdSupplier.get();
        FlowResult<String> result = stableBusinessKey
                ? flowClient.startProcessForDelegatedUser(
                        flowModelKey, flowBusinessKey, context.objectCode(), title, flowVariables)
                : flowClient.startProcess(
                        flowModelKey, flowBusinessKey, title, flowVariables,
                        userId == null ? null : String.valueOf(userId), userName, null, null);
        if (result == null || !result.isSuccess() || StringUtils.isBlank(result.getData())) {
            throw new BusinessException("流程发起失败: " + (result == null ? "无返回结果" : result.getMsg()));
        }

        AiBusinessFlowInstanceLink link = createLink(
                tenantId, context.objectCode(), dto, businessKey, flowModelKey,
                result.getData(), userId, latestLink, flowVariables);
        flowInstanceLinkMapper.insert(link);
        writeRunningStatus(tenantId, context, bindingConfig, dto);
        return toRuntimeVO(link, "流程已发起");
    }

    private void validateRequest(BusinessFlowStartDTO dto) {
        if (dto == null) {
            throw new BusinessException("发起主流程参数不能为空");
        }
        if (StringUtils.isBlank(dto.getObjectCode())) {
            throw new BusinessException("业务对象编码不能为空");
        }
        if (dto.getRecordId() == null) {
            throw new BusinessException("请先保存记录后再发起主流程");
        }
    }

    private FlowStartContext resolveFlowStartContext(
            Long tenantId, String objectCodeOrConfigKey, boolean allowDraftRuntime) {
        BusinessRuntimeContext context = runtimeContextResolver.resolve(
                tenantId, objectCodeOrConfigKey, allowDraftRuntime);
        String configKey = resolveStartConfigKey(
                context.documentConfig(), context.runtimeConfig(), context.configKey(), allowDraftRuntime);
        return new FlowStartContext(
                context.requestedObjectCode(), context.objectCode(), configKey,
                context.documentConfig(), context.runtimeConfig());
    }

    private String resolveStartConfigKey(AiBusinessDocumentConfig documentConfig,
                                         AiCrudConfig runtimeConfig,
                                         String fallbackConfigKey,
                                         boolean allowDraftRuntime) {
        if (documentConfig != null) {
            if (StringUtils.isNotBlank(documentConfig.getConfigKey())) {
                return documentConfig.getConfigKey();
            }
            if (allowDraftRuntime && StringUtils.isNotBlank(fallbackConfigKey)) {
                return fallbackConfigKey;
            }
            throw new BusinessException("单据缺少发布配置，无法发起主流程");
        }
        if (runtimeConfig != null && StringUtils.isNotBlank(runtimeConfig.getConfigKey())) {
            return runtimeConfig.getConfigKey();
        }
        if (allowDraftRuntime && StringUtils.isNotBlank(fallbackConfigKey)) {
            return fallbackConfigKey;
        }
        throw new BusinessException("业务对象缺少已发布运行配置，无法发起主流程");
    }

    private void validateDocumentStart(FlowStartContext context,
                                       BusinessFlowStartDTO dto,
                                       AiBusinessBinding binding,
                                       Map<String, Object> recordData,
                                       AiBusinessFlowInstanceLink latestLink,
                                       boolean checkPermission) {
        if (context.documentConfig() == null) {
            return;
        }
        BusinessDocumentConfigVO documentConfigVO = documentConfigService.toVO(
                context.documentConfig(), context.runtimeConfig(), binding);
        documentRuntimeService.validateStartAllowed(
                context.objectCode(), dto.getRecordId(), documentConfigVO,
                recordData, latestLink, checkPermission);
    }

    private AiBusinessFlowInstanceLink createLink(Long tenantId,
                                                  String objectCode,
                                                  BusinessFlowStartDTO dto,
                                                  String businessKey,
                                                  String flowModelKey,
                                                  String processInstanceId,
                                                  Long userId,
                                                  AiBusinessFlowInstanceLink latestLink,
                                                  Map<String, Object> flowVariables) {
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setTenantId(tenantId);
        link.setObjectCode(objectCode);
        link.setRecordId(dto.getRecordId());
        link.setBusinessKey(businessKey);
        link.setFlowModelKey(flowModelKey);
        link.setProcessInstanceId(processInstanceId);
        link.setFlowStatus(BusinessDocumentFlowStatus.RUNNING.getCode());
        link.setStartUserId(userId);
        link.setStartTime(LocalDateTime.now());
        link.setRoundNo(resolveNextRoundNo(latestLink));
        link.setVariablesSnapshot(JSON.toJSONString(flowVariables));
        return link;
    }

    private void writeRunningStatus(Long tenantId,
                                    FlowStartContext context,
                                    JSONObject bindingConfig,
                                    BusinessFlowStartDTO dto) {
        AiCrudConfig statusRuntimeConfig = context.runtimeConfig() != null
                ? context.runtimeConfig()
                : runtimeConfigResolver.published(tenantId, context.objectCode());
        if (StringUtils.isBlank(statusRepairService.configuredStatusField(dto.getVariables()))) {
            statusTransitionService.updateBusinessFlowStatus(
                    context.documentConfig(), context.runtimeConfig(), bindingConfig,
                    dto.getRecordId(), BusinessDocumentFlowStatus.IN_PROCESS.getCode());
        }
        statusRepairService.syncConfiguredStatusField(
                statusRuntimeConfig, dto.getRecordId(), dto.getVariables(),
                BusinessDocumentFlowStatus.IN_PROCESS.getCode());
    }

    private String resolveFlowBusinessKeyForStart(String businessKey, AiBusinessFlowInstanceLink latest) {
        if (latest == null) {
            return businessKey;
        }
        return businessKey + ":R" + (latest.getId() == null
                ? System.currentTimeMillis() : latest.getId() + 1);
    }

    private boolean isRunningFlowLink(AiBusinessFlowInstanceLink link) {
        return link != null && (BusinessDocumentFlowStatus.STARTED.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.RUNNING.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.IN_PROCESS.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.NEED_MODIFY.matches(link.getFlowStatus())
                || (link.getEndTime() == null && StringUtils.isBlank(link.getResult())));
    }

    private int resolveNextRoundNo(AiBusinessFlowInstanceLink latest) {
        if (latest == null || latest.getRoundNo() == null || latest.getRoundNo() < 1) {
            return 1;
        }
        return latest.getRoundNo() + 1;
    }

    private String previewBindingConfig(JSONObject bindingConfig) {
        if (bindingConfig == null || bindingConfig.isEmpty()) {
            return "{}";
        }
        return StringUtils.left(bindingConfig.toJSONString(), 400);
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

    private record FlowStartContext(
            String requestedObjectCode,
            String objectCode,
            String configKey,
            AiBusinessDocumentConfig documentConfig,
            AiCrudConfig runtimeConfig) {
    }
}
