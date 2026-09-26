package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.flow.client.annotation.FlowBind;
import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.flow.client.spi.FlowBusinessListDisplayItem;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowWithdrawDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowBindingDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowCallbackDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowResubmitDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowStartDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectQueryDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskActionDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormSaveDTO;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.taskform.TaskFormUiDocumentCompiler;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessBindingSummaryVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowBindingVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import com.mdframe.forge.starter.core.enums.EnableStatus;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.defaultBusinessBinding;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.normalizeBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.normalizeStartMode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.normalizeVariableMapping;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.putBoolean;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.putText;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readBindingConfig;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.readOptions;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.resolveBindingName;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.resolveFlowModelKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.toBusinessBindingDTO;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.toConfigJson;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowBindingCodec.toDTO;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.isPublicCodeAppFormField;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.mergeCodeAppAssets;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.mergeNonNull;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.normalizeCodeAppMetadataFields;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessCodeAppFormAssetMerger.sanitizeCodeAppMetadata;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readJsonObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNullableBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeFieldPermissions;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeEditMode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeFormMode;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeForms;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.camelToSnake;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.contains;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.read;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.snakeToCamel;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.buildBusinessKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseBusinessKey;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowIdentityCodec.parseLongValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.firstStrongTaskFormControlType;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.normalizeTaskFormFieldType;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.resolveTaskFormControlType;

/**
 * 业务流程服务。
 * <p>
 * 负责业务对象与流程引擎的动态集成：
 * - 读取 ai_business_binding (binding_type=FLOW) 中的流程绑定配置
 * - 从业务记录动态发起流程（不依赖硬编码注解）
 * - 处理流程回调，更新业务记录状态
 */
@Slf4j
@Service
@FlowBind(modelKey = "*", businessType = "lowcode-business")
public class BusinessFlowService {

    private static final ThreadLocal<Map<String, Long>> TASK_FORM_DETAIL_STAGES = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> TASK_FORM_DETAIL_NOTES = new ThreadLocal<>();
    private static final BusinessFlowStartContextAssembler START_CONTEXT_ASSEMBLER =
            BusinessFlowStartContextAssembler.standard();
    private static final BusinessFlowTaskAccessPolicy TASK_ACCESS_POLICY =
            new BusinessFlowTaskAccessPolicy();
    private static final BusinessFlowTaskChildPolicy TASK_CHILD_POLICY =
            new BusinessFlowTaskChildPolicy();
    private static final BusinessFlowTaskFormPolicy TASK_FORM_POLICY =
            new BusinessFlowTaskFormPolicy();
    @Autowired(required = false)
    private FlowClient flowClient;

    /**
     * 应用页面表单资产属于应用设计快照，不属于业务对象本身。
     * 使用字段注入保持已有测试/插件扩展的构造器兼容性。
     */
    @Autowired(required = false)
    private BusinessApplicationService businessApplicationService;

    /**
     * 流程绑定保存时同步低代码托管字段，避免只保存 binding 而沿用旧列表发布快照。
     */
    @Autowired(required = false)
    private BusinessFlowStatusFieldService flowStatusFieldService;

    /**
     * 业务流程运行和应用归属用于向前端返回服务端确认的打印身份。
     * 使用字段注入保持已有扩展和单元测试的构造器兼容性。
     */
    @Autowired(required = false)
    private BusinessProcessRunMapper businessProcessRunMapper;

    @Autowired(required = false)
    private BusinessApplicationObjectMapper businessApplicationObjectMapper;

    /**
     * 任务事件状态同步使用独立事务，避免内部回写失败把外层 FlowCallback 事务标成 rollback-only。
     */
    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    private final BusinessBindingMapper bindingMapper;
    private final BusinessFlowInstanceLinkMapper flowInstanceLinkMapper;
    private final AiCrudConfigMapper crudConfigMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessDocumentRuntimeService documentRuntimeService;
    private final DynamicCrudService dynamicCrudService;
    private final BusinessFieldDesignService businessFieldDesignService;
    private final BusinessFlowVariableResolver variableResolver;
    private final BusinessCodeFormProviderRegistry codeFormProviderRegistry;
    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final BusinessFlowStartLockManager startLockManager = new BusinessFlowStartLockManager();
    private final BusinessRuntimeConfigResolver runtimeConfigResolver;
    private final BusinessFlowStatusRepairService statusRepairService;
    private final BusinessFlowTaskFormSchemaAssembler taskFormSchemaAssembler;
    private final BusinessFlowFormAssetAssembler formAssetAssembler;
    private final BusinessFlowTaskChildAssembler taskChildAssembler;
    private final BusinessFlowCodeFormCoordinator codeFormCoordinator;
    private final BusinessFlowApplicationPageFormResolver applicationPageFormResolver;
    private final BusinessFlowTaskNodeFormResolver taskNodeFormResolver;
    private final BusinessFlowRuntimeContextResolver businessRuntimeContextResolver;
    private final BusinessFlowListDisplayEnricher businessListDisplayEnricher;
    private final BusinessFlowStatusTransitionService statusTransitionService;
    private final BusinessFlowTaskEventCoordinator taskEventCoordinator;
    private final BusinessFlowCallbackCoordinator callbackCoordinator;

    public BusinessFlowService(BusinessBindingMapper bindingMapper,
                               BusinessFlowInstanceLinkMapper flowInstanceLinkMapper,
                               AiCrudConfigMapper crudConfigMapper,
                               BusinessObjectMapper businessObjectMapper,
                               BusinessDocumentConfigService documentConfigService,
                               BusinessDocumentRuntimeService documentRuntimeService,
                               DynamicCrudService dynamicCrudService,
                               BusinessFieldDesignService businessFieldDesignService,
                               BusinessFlowVariableResolver variableResolver,
                               BusinessCodeFormProviderRegistry codeFormProviderRegistry,
                               ApplicationEventPublisher applicationEventPublisher,
                               ObjectProvider<RedissonClient> redissonClientProvider,
                               ObjectProvider<BusinessActionExecutionService> actionExecutionServiceProvider) {
        this.bindingMapper = bindingMapper;
        this.flowInstanceLinkMapper = flowInstanceLinkMapper;
        this.crudConfigMapper = crudConfigMapper;
        this.businessObjectMapper = businessObjectMapper;
        this.documentConfigService = documentConfigService;
        this.documentRuntimeService = documentRuntimeService;
        this.dynamicCrudService = dynamicCrudService;
        this.businessFieldDesignService = businessFieldDesignService;
        this.variableResolver = variableResolver;
        this.codeFormProviderRegistry = codeFormProviderRegistry;
        this.redissonClientProvider = redissonClientProvider;
        this.runtimeConfigResolver = new BusinessRuntimeConfigResolver(crudConfigMapper, this::resolveTenantId);
        this.statusRepairService = new BusinessFlowStatusRepairService(
                runtimeConfigResolver, dynamicCrudService, documentConfigService);
        this.taskFormSchemaAssembler = new BusinessFlowTaskFormSchemaAssembler(
                dynamicCrudService,
                runtimeConfigResolver,
                TASK_CHILD_POLICY,
                this::resolveTenantId);
        this.formAssetAssembler = new BusinessFlowFormAssetAssembler(
                businessFieldDesignService,
                taskFormSchemaAssembler::applyRuntimeCrudFormLayout,
                taskFormSchemaAssembler::appendRuntimeChildFieldCatalog);
        this.taskChildAssembler = new BusinessFlowTaskChildAssembler(
                TASK_CHILD_POLICY,
                TASK_FORM_POLICY,
                businessObjectMapper,
                businessFieldDesignService,
                formAssetAssembler,
                this::resolveTenantId);
        this.codeFormCoordinator = new BusinessFlowCodeFormCoordinator(
                codeFormProviderRegistry,
                TASK_FORM_POLICY,
                code -> readCodeAppMetadata(resolveTenantId(), code));
        this.applicationPageFormResolver = new BusinessFlowApplicationPageFormResolver(
                () -> businessApplicationService,
                businessObjectMapper,
                this::resolveTenantId,
                this::markTaskFormDetail,
                this::noteTaskFormDetail);
        this.taskNodeFormResolver = new BusinessFlowTaskNodeFormResolver(
                () -> flowClient,
                applicationPageFormResolver,
                TASK_CHILD_POLICY,
                this::collectTaskFormAssets,
                this::markTaskFormDetail,
                this::noteTaskFormDetail);
        this.businessRuntimeContextResolver = new BusinessFlowRuntimeContextResolver(
                runtimeConfigResolver,
                documentConfigService,
                businessObjectMapper,
                flowInstanceLinkMapper,
                applicationPageFormResolver,
                taskNodeFormResolver,
                this::resolveTenantId,
                this::selectMainFlowBindingForConfig,
                this::markTaskFormDetail,
                this::noteTaskFormDetail);
        this.businessListDisplayEnricher = new BusinessFlowListDisplayEnricher(
                this::resolveTenantId,
                flowInstanceLinkMapper,
                businessRuntimeContextResolver,
                dynamicCrudService,
                codeFormProviderRegistry,
                this::queryBusinessObject,
                this::toBusinessObjectVO,
                this::selectMainFlowBindingForConfig,
                this::resolveBusinessSummary);
        this.statusTransitionService = new BusinessFlowStatusTransitionService(
                documentConfigService, dynamicCrudService);
        this.taskEventCoordinator = new BusinessFlowTaskEventCoordinator(
                () -> flowClient,
                flowInstanceLinkMapper,
                documentConfigService,
                runtimeConfigResolver,
                statusRepairService,
                () -> transactionManager,
                this::resolveTenantId,
                this::resolveUserId,
                this::selectMainFlowBindingForConfig,
                statusTransitionService);
        this.callbackCoordinator = new BusinessFlowCallbackCoordinator(
                flowInstanceLinkMapper,
                documentConfigService,
                dynamicCrudService,
                runtimeConfigResolver,
                statusRepairService,
                statusTransitionService,
                taskEventCoordinator,
                actionExecutionServiceProvider,
                applicationEventPublisher,
                this::resolveTenantId,
                this::resolveUsername,
                this::selectMainFlowBindingForConfig,
                this::resolveTerminalBusinessFlowResult);
    }

    /** 查询 Flowable 模型中需要发起人选择审批人的节点，供应用级流程启动页复用。 */
    public Map<String, Object> getFlowStartConfig(String modelKey) {
        if (flowClient == null || StringUtils.isBlank(modelKey)) {
            return Map.of("modelKey", modelKey, "initiatorSelectNodes", List.of());
        }
        FlowResult<Map<String, Object>> result = flowClient.getModelStartConfig(modelKey.trim());
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return Map.of("modelKey", modelKey.trim(), "initiatorSelectNodes", List.of());
        }
        return new LinkedHashMap<>(result.getData());
    }

    /**
     * 从业务记录发起流程
     *
     * @param objectCode 业务对象编码
     * @param recordId   业务记录ID
     * @param recordData 业务记录数据
     * @return 流程发起结果
     */
    public JSONObject startFlow(String objectCode, String recordId, Map<String, Object> recordData) {
        return startFlow(objectCode, recordId, recordData, null);
    }

    public JSONObject startFlow(String objectCode, String recordId, Map<String, Object> recordData,
                                Map<String, Object> requestedVariables) {
        if (flowClient == null) {
            throw new RuntimeException("流程服务未配置，无法发起主流程");
        }
        Long tenantId = resolveTenantId();

        // 1. 查询该对象的 FLOW 绑定配置
        AiBusinessBinding flowBinding = bindingMapper.selectBindingByTypeAndCode(
                tenantId, "OBJECT", objectCode, "FLOW");

        if (flowBinding == null || flowBinding.getBindingConfig() == null) {
            throw new RuntimeException("业务对象 [" + objectCode + "] 未配置流程绑定");
        }

        JSONObject bindingConfig = readBindingConfig(flowBinding.getBindingConfig());
        String flowModelKey = resolveFlowModelKey(bindingConfig);

        if (flowModelKey == null || flowModelKey.isBlank()) {
            throw new RuntimeException("流程绑定配置中缺少 flowModelKey");
        }

        // 2. 构建流程变量
        BusinessFlowStartContextAssembler.StartContext startContext = START_CONTEXT_ASSEMBLER.assemble(
                bindingConfig, recordData, requestedVariables, objectCode);
        Map<String, Object> flowVariables = startContext.variables();

        // 3. 构建业务Key和标题
        String businessKey = objectCode + ":" + recordId;
        String title = startContext.title();

        // 4. 发起流程
        Long userId = resolveUserId();
        String userName = resolveUsername();

        FlowResult<String> result = flowClient.startProcess(
                flowModelKey, businessKey, title,
                flowVariables, String.valueOf(userId), userName, null, null);

        if (!result.isSuccess()) {
            throw new RuntimeException("流程发起失败: " + result.getMsg());
        }

        JSONObject response = new JSONObject();
        response.put("flowModelKey", flowModelKey);
        response.put("businessKey", businessKey);
        response.put("processInstanceId", result.getData());
        response.put("status", "STARTED");
        return response;
    }

    /**
     * 查询业务对象的流程绑定配置
     */
    public BusinessFlowBindingVO getFlowBinding(String objectCode) {
        Long tenantId = resolveTenantId();
        String canonicalObjectCode = businessRuntimeContextResolver.resolveCanonicalObjectCode(tenantId, objectCode);
        AiBusinessBinding binding = selectMainFlowBindingForConfig(tenantId, canonicalObjectCode, objectCode);

        if (binding == null) {
            AiBusinessDocumentConfig documentConfig = businessRuntimeContextResolver.resolveEnabledDocumentConfig(
                    tenantId, canonicalObjectCode, resolvePublishedRuntimeConfig(tenantId, objectCode));
            if (documentConfig == null || StringUtils.isBlank(documentConfig.getDefaultFlowKey())) {
                return null;
            }
            return legacyDocumentFlowToVO(canonicalObjectCode, documentConfig);
        }
        return toVO(canonicalObjectCode, binding);
    }

    /**
     * 查询正式业务发起入口需要补充的流程参数。
     */
    public Map<String, Object> getBusinessStartConfig(String objectCode) {
        BusinessFlowBindingVO binding = getFlowBinding(objectCode);
        if (binding == null || StringUtils.isBlank(binding.getFlowModelKey())) {
            throw new BusinessException("业务对象未配置主流程");
        }
        FlowResult<Map<String, Object>> result = flowClient.getModelStartConfig(binding.getFlowModelKey());
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(result == null
                    ? "读取流程发起配置失败"
                    : StringUtils.defaultIfBlank(result.getMsg(), "读取流程发起配置失败"));
        }
        return result.getData() == null ? Map.of() : result.getData();
    }

    /**
     * 查询流程模型变量候选项和字段映射建议。
     */
    public Map<String, Object> getVariableCandidates(String modelKey, String objectCode) {
        if (StringUtils.isBlank(modelKey)) {
            throw new BusinessException("流程模型Key不能为空");
        }
        return variableResolver.resolve(modelKey, objectCode);
    }

    /**
     * 批量补齐流程任务/抄送列表中的业务对象名称和业务摘要。
     */
    public void enrichBusinessListDisplay(List<FlowBusinessListDisplayItem> items) {
        businessListDisplayEnricher.enrich(items);
    }

    /**
     * 按流程模型 Key 反查已绑定的业务对象。
     */
    public List<BusinessBindingSummaryVO> listBusinessBindingsByModelKey(String modelKey) {
        String key = StringUtils.trimToNull(modelKey);
        if (key == null) {
            throw new BusinessException("流程模型Key不能为空");
        }
        return bindingMapper.selectFlowBindingsByModelKey(resolveTenantId(), key);
    }

    /**
     * 查询业务对象可供流程节点绑定的表单资产。
     */
    public Map<String, Object> getFormAssets(String objectCode) {
        return getFormAssets(objectCode, false);
    }

    /**
     * 查询业务对象可供流程节点绑定的表单资产。
     */
    public Map<String, Object> getFormAssets(String objectCode, boolean includeInternal) {
        return getFormAssets(objectCode, includeInternal, null);
    }

    /**
     * 查询当前应用中当前业务对象实际可作为审批任务表单的页面资产。
     * applicationId 为空时保留对象级/代码表单兼容目录；有应用上下文时只返回该应用的真实页面表单，
     * 避免把业务对象字段注册表误显示成一个不存在的页面。
     */
    public Map<String, Object> getFormAssets(String objectCode,
                                             boolean includeInternal,
                                             Long applicationId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectCode", objectCode);
        result.put("formAssets", List.of());
        result.put("providerCatalog", List.of());
        result.put("warnings", List.of());
        if (StringUtils.isBlank(objectCode)) {
            return result;
        }

        if (applicationId != null && applicationId > 0 && businessApplicationService != null) {
            String canonicalObjectCode = businessRuntimeContextResolver.resolveCanonicalObjectCode(
                    resolveTenantId(), objectCode);
            Map<String, Object> applicationAssets = applicationPageFormResolver.collectApplicationPageFormAssets(
                    applicationId, canonicalObjectCode);
            if (!applicationAssets.isEmpty()) {
                result.putAll(applicationAssets);
                result.put("providerCatalog", codeFormProviderRegistry.listProviderCatalog(
                        canonicalObjectCode, includeInternal));
                return result;
            }
            result.put("warnings", List.of("当前应用尚未配置属于该业务对象的表单页面，请先在应用中新增表单页面"));
            result.put("objectCode", canonicalObjectCode);
            result.put("providerCatalog", codeFormProviderRegistry.listProviderCatalog(
                    canonicalObjectCode, includeInternal));
            return result;
        }

        Long tenantId = resolveTenantId();
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> providerCatalog = codeFormProviderRegistry.listProviderCatalog(objectCode, includeInternal);
        BusinessObjectQueryDTO query = new BusinessObjectQueryDTO();
        query.setObjectCode(objectCode);
        List<BusinessObjectVO> objects = businessObjectMapper.selectObjectList(tenantId, query);
        if (objects == null || objects.isEmpty()) {
            AiCrudConfig runtimeConfig = resolvePublishedRuntimeConfig(tenantId, objectCode);
            JSONObject metadata = readCodeAppMetadata(tenantId, objectCode);
            List<Map<String, Object>> assets = new ArrayList<>(
                    formAssetAssembler.collectRuntimeCrudFormAssets(null, runtimeConfig));
            formAssetAssembler.appendUniqueFormAssets(assets, mergeCodeAppAssets(
                    objectCode, codeFormProviderRegistry.listAssets(objectCode, includeInternal), metadata, includeInternal));
            if (assets.isEmpty()) {
                warnings.add("业务对象不存在或无权限访问，且未找到代码表单资产: " + objectCode);
            } else if (runtimeConfig == null) {
                warnings.add("当前编码未匹配低代码业务对象，仅显示代码表单资产");
            }
            result.put("formAssets", assets);
            if (runtimeConfig != null) {
                result.put("objectCode", StringUtils.defaultIfBlank(runtimeConfig.getObjectCode(), objectCode));
                result.put("objectName", StringUtils.defaultIfBlank(runtimeConfig.getObjectName(), runtimeConfig.getAppName()));
                result.put("configKey", runtimeConfig.getConfigKey());
            }
            result.put("providerCatalog", providerCatalog);
            result.put("codeAppMetadata", sanitizeCodeAppMetadata(metadata, includeInternal));
            result.put("warnings", warnings);
            return result;
        }

        BusinessObjectVO object = objects.get(0);
        AiCrudConfig runtimeConfig = resolvePublishedRuntimeConfig(tenantId, StringUtils.firstNonBlank(
                object.getConfigKey(), object.getObjectCode(), objectCode));
        JSONObject designerOptions = readJsonObject(object.getDesignerOptions());
        JSONObject formSchema = readNestedObject(designerOptions.get("formDesignerSchema"));
        List<Map<String, Object>> assets = new ArrayList<>(
                formAssetAssembler.collectBusinessFormAssets(object, formSchema));
        formAssetAssembler.appendUniqueFormAssets(
                assets, formAssetAssembler.collectRuntimeCrudFormAssets(object, runtimeConfig));
        formAssetAssembler.appendObjectFieldRegistryFallback(assets, object);
        JSONObject metadata = readCodeAppMetadata(tenantId, object.getObjectCode());
        formAssetAssembler.appendUniqueFormAssets(assets, mergeCodeAppAssets(
                object.getObjectCode(), codeFormProviderRegistry.listAssets(object.getObjectCode(), includeInternal),
                metadata, includeInternal));
        if (assets.isEmpty()) {
            warnings.add("业务对象尚未配置低代码表单资产");
        }
        result.put("objectId", object.getId());
        result.put("objectCode", object.getObjectCode());
        result.put("objectName", object.getObjectName());
        result.put("configKey", runtimeConfig == null ? object.getConfigKey() : runtimeConfig.getConfigKey());
        result.put("formAssets", assets);
        result.put("providerCatalog", providerCatalog);
        result.put("codeAppMetadata", sanitizeCodeAppMetadata(metadata, includeInternal));
        result.put("warnings", warnings);
        return result;
    }

    /**
     * 从应用草稿中的页面节点和页面布局提取真实页面表单。
     * 表单 key 带应用/页面/资产三段稳定身份，运行时无需额外传 applicationId 即可重新解析页面。
     */
   public Map<String, Object> getCodeAppMetadata(String objectCode) {
        if (StringUtils.isBlank(objectCode)) {
            return Map.of();
        }
        return new LinkedHashMap<>(sanitizeCodeAppMetadata(readCodeAppMetadata(resolveTenantId(), objectCode), true));
    }

    /**
     * 只更新已有流程绑定中的代码应用元数据，避免字段/视图配置覆盖流程模型和变量映射。
     */
    public boolean saveCodeAppMetadata(String objectCode, Object metadata) {
        if (StringUtils.isBlank(objectCode) || !(metadata instanceof Map<?, ?> || metadata instanceof JSONObject)) {
            return false;
        }
        Long tenantId = resolveTenantId();
        AiBusinessBinding binding = selectMainFlowBindingForConfig(tenantId, objectCode);
        boolean created = false;
        if (binding == null) {
            binding = new AiBusinessBinding();
            binding.setTenantId(tenantId);
            binding.setTargetType("OBJECT");
            binding.setTargetCode(objectCode);
            binding.setBindingType("FLOW");
            binding.setBindingName(objectCode + "业务表单资产配置");
            binding.setStatus(EnableStatus.ENABLED.getCode());
            binding.setSortOrder(0);
            created = true;
        }
        JSONObject config = readBindingConfig(binding.getBindingConfig());
        JSONObject options = readNestedObject(config.get("options"));
        options.put("codeAppMetadata", readNestedObject(metadata));
        config.put("options", options);
        binding.setBindingConfig(config.toJSONString());
        if (created) {
            bindingMapper.insert(binding);
        } else {
            bindingMapper.updateById(binding);
        }
        return true;
    }

    /**
     * 查询待办任务对应的业务表单上下文。
     */
    public BusinessTaskFormContextVO getTaskFormContext(BusinessTaskFormContextQueryDTO query) {
        BusinessTaskFormContextQueryDTO effectiveQuery = query == null ? new BusinessTaskFormContextQueryDTO() : query;
        long startedAt = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        beginTaskFormProfiling(stages);
        try {
            long mark = System.nanoTime();
            Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(effectiveQuery.getTaskId());
            stages.put("flowFormInfoMs", elapsedMillis(mark));
            mark = System.nanoTime();
            validateTaskAccess(effectiveQuery, false, taskFormInfo);
            stages.put("accessMs", elapsedMillis(mark));
            mark = System.nanoTime();
            TaskFormRuntimeContext runtime = businessRuntimeContextResolver.resolveTask(
                    effectiveQuery, false, taskFormInfo);
            stages.put("runtimeContextMs", elapsedMillis(mark));
            return attachPrintRuntimeIdentity(
                    buildTaskFormContext(effectiveQuery, runtime, taskFormInfo, stages, startedAt), effectiveQuery);
        } finally {
            endTaskFormProfiling();
        }
    }

    /**
     * 查询当前用户已签收、可直接办理的待办上下文。
     *
     * <p>该只读校验入口供受控流程动作在 elicitation 前确认真实办理权使用，
     * 候选但未签收的任务不会被视为可办理任务。</p>
     */
    public BusinessTaskFormContextVO getActionableTaskFormContext(BusinessTaskFormContextQueryDTO query) {
        BusinessTaskFormContextQueryDTO effectiveQuery = query == null ? new BusinessTaskFormContextQueryDTO() : query;
        long startedAt = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        beginTaskFormProfiling(stages);
        try {
            long mark = System.nanoTime();
            Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(effectiveQuery.getTaskId());
            stages.put("flowFormInfoMs", elapsedMillis(mark));
            mark = System.nanoTime();
            validateTaskAccess(effectiveQuery, true, taskFormInfo);
            stages.put("accessMs", elapsedMillis(mark));
            mark = System.nanoTime();
            TaskFormRuntimeContext runtime = businessRuntimeContextResolver.resolveTask(
                    effectiveQuery, true, taskFormInfo);
            stages.put("runtimeContextMs", elapsedMillis(mark));
            return attachPrintRuntimeIdentity(
                    buildTaskFormContext(effectiveQuery, runtime, taskFormInfo, stages, startedAt), effectiveQuery);
        } finally {
            endTaskFormProfiling();
        }
    }

    /**
     * 查询历史/已办场景下的业务表单上下文，只用于只读展示，不校验运行中待办任务身份。
     */
    public BusinessTaskFormContextVO getTaskFormReadonlyContext(BusinessTaskFormContextQueryDTO query) {
        BusinessTaskFormContextQueryDTO effectiveQuery = query == null ? new BusinessTaskFormContextQueryDTO() : query;
        long startedAt = System.nanoTime();
        Map<String, Long> stages = new LinkedHashMap<>();
        beginTaskFormProfiling(stages);
        try {
            long mark = System.nanoTime();
            TaskFormRuntimeContext runtime = businessRuntimeContextResolver.resolveTask(effectiveQuery, false);
            stages.put("runtimeContextMs", elapsedMillis(mark));
            BusinessTaskFormContextVO context = attachPrintRuntimeIdentity(
                    buildTaskFormContext(effectiveQuery, runtime, Map.of(), stages, startedAt), effectiveQuery);
            TASK_FORM_POLICY.makeReadonly(context);
            return context;
        } finally {
            endTaskFormProfiling();
        }
    }

    /**
     * 保存待办任务允许编辑的业务字段，并返回最新上下文。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessTaskFormContextVO saveTaskFormContext(BusinessTaskFormSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException("业务待办表单参数不能为空");
        }
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

        Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(query.getTaskId());
        validateTaskAccess(query, true, taskFormInfo);
        TaskFormRuntimeContext runtime = businessRuntimeContextResolver.resolveTask(query, true, taskFormInfo);
        taskEventCoordinator.repairInitiatorModifyState(query, runtime, taskFormInfo);
        JSONObject nodeForm = taskNodeFormResolver.resolveTaskNodeForm(runtime, query, taskFormInfo);
        TaskFormSaveResult saveResult = persistTaskFormData(dto, query, runtime, nodeForm);
        if (saveResult.context() != null) {
            return attachPrintRuntimeIdentity(saveResult.context(), query);
        }
        return attachPrintRuntimeIdentity(
                buildTaskFormContext(query, saveResult.runtime(), taskFormInfo, new LinkedHashMap<>(), System.nanoTime()), query);
    }

    private BusinessTaskFormContextVO attachPrintRuntimeIdentity(
            BusinessTaskFormContextVO context,
            BusinessTaskFormContextQueryDTO query) {
        if (context == null) {
            return null;
        }
        if (query != null) {
            if (StringUtils.isBlank(context.getProcessInstanceId())) {
                context.setProcessInstanceId(StringUtils.trimToNull(query.getProcessInstanceId()));
            }
            if (StringUtils.isBlank(context.getObjectCode())) {
                context.setObjectCode(StringUtils.trimToNull(query.getObjectCode()));
            }
            if (StringUtils.isBlank(context.getConfigKey())) {
                context.setConfigKey(StringUtils.trimToNull(query.getConfigKey()));
            }
        }

        Long tenantId = resolveTenantId();
        String processInstanceId = StringUtils.trimToNull(context.getProcessInstanceId());
        AiBusinessProcessRun run = businessProcessRunMapper == null || processInstanceId == null
                ? null
                : businessProcessRunMapper.selectByProcessInstanceId(tenantId, processInstanceId);
        if (run != null) {
            context.setProcessRunId(run.getId());
            if (run.getApplicationId() != null) {
                String runApplicationId = String.valueOf(run.getApplicationId());
                if (StringUtils.isNotBlank(context.getApplicationId())
                        && !StringUtils.equals(context.getApplicationId(), runApplicationId)) {
                    context.getWarnings().add("流程运行应用身份与表单页面不一致，打印将使用流程运行版本");
                }
                context.setApplicationId(runApplicationId);
            }
        }

        String objectCode = StringUtils.trimToNull(context.getObjectCode());
        if (StringUtils.isBlank(context.getApplicationId())
                && objectCode != null
                && businessApplicationObjectMapper != null) {
            List<Long> applicationIds = businessApplicationObjectMapper
                    .selectPublishedApplicationIdsByObjectIdentity(
                            tenantId, objectCode, StringUtils.trimToNull(context.getConfigKey()));
            if (applicationIds != null && applicationIds.size() == 1) {
                context.setApplicationId(String.valueOf(applicationIds.get(0)));
            } else if (applicationIds != null && applicationIds.size() > 1) {
                context.getWarnings().add("业务对象归属多个已发布应用，无法确定流程打印模板范围");
            }
        }
        return context;
    }

    private TaskFormSaveResult persistTaskFormData(BusinessTaskFormSaveDTO dto,
                                                   BusinessTaskFormContextQueryDTO query,
                                                   TaskFormRuntimeContext runtime,
                                                   JSONObject nodeForm) {
        if (nodeForm == null || nodeForm.isEmpty()) {
            throw new BusinessException("当前流程节点未配置业务表单权限");
        }
        String formMode = normalizeNodeFormMode(nodeForm.getString("formMode"));
        if ("BUSINESS_CODE_FORM".equals(formMode)) {
            List<Map<String, Object>> permissions = normalizeFieldPermissions(nodeForm.get("fieldPermissions"));
            BusinessTaskFormSaveDTO filteredDto = TASK_FORM_POLICY.filterSaveData(dto, permissions);
            TASK_FORM_POLICY.validateRequiredFields(
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
                ? toBusinessObjectVO(runtime.businessObject())
                : queryBusinessObject(resolveTenantId(), runtime.objectCode(), runtime.configKey());
        JSONObject formSchema = resolveBusinessFormSchema(
                object, formKey, runtime.configKey(), runtime.publishedConfig());
        if (permissions.isEmpty()) {
            List<Map<String, Object>> fieldCatalog = taskFormSchemaAssembler.resolveBusinessTaskCrudPageFields(
                    runtime.configKey(), formKey, formSchema);
            permissions = TASK_FORM_POLICY.normalizePermissions(fieldCatalog, permissions);
        }
        JSONObject runtimeOptions = runtime.publishedConfig() == null
                ? null
                : readJsonObject(runtime.publishedConfig().getOptions());
        List<Map<String, Object>> childrenConfig = taskChildAssembler.resolveBusinessTaskChildrenConfig(
                runtime.configKey(), nodeForm, runtimeOptions, formSchema);
        Map<String, DynamicCrudService.TaskChildPermission> childPermissions =
                TASK_CHILD_POLICY.buildSavePermissions(childrenConfig, nodeForm);
        Set<String> writableFields = TASK_FORM_POLICY.collectPermissionFields(permissions, "writable", true);
        boolean hasWritableChildren = childPermissions.values().stream()
                .anyMatch(permission -> !permission.writableFields().isEmpty()
                        || permission.allowCreate() || permission.allowUpdate() || permission.allowDelete());
        if (writableFields.isEmpty() && !hasWritableChildren) {
            throw new BusinessException("当前节点没有可编辑业务字段");
        }

        Map<String, Object> input = dto.getData() == null ? Map.of() : dto.getData();
        Map<String, Object> updateData = new LinkedHashMap<>();
        Map<String, Object> mainInput = TASK_CHILD_POLICY.extractMainPayload(input);
        Map<String, Object> childrenInput = TASK_CHILD_POLICY.extractChildrenPayload(input);
        for (String field : writableFields) {
            if (mainInput.containsKey(field)) {
                updateData.put(field, mainInput.get(field));
            }
        }
        TASK_FORM_POLICY.validateRequiredFields(permissions, updateData, mainInput);
        if (updateData.isEmpty() && childrenInput.isEmpty()) {
            throw new BusinessException("未提交可编辑业务字段");
        }

        if (runtime.recordId() == null) {
            if (!childrenInput.isEmpty()) {
                throw new BusinessException("业务待办尚未关联主记录，暂不支持新增子表明细");
            }
            if (StringUtils.isBlank(runtime.configKey())) {
                throw new BusinessException("业务对象缺少已发布运行配置，无法保存业务字段");
            }
            Map<String, Object> created = dynamicCrudService.insertInternal(runtime.configKey(), updateData);
            Long createdId = businessRuntimeContextResolver.extractCreatedRecordId(created);
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

        Map<String, Object> taskData = new LinkedHashMap<>();
        taskData.put("main", updateData);
        if (!childrenInput.isEmpty()) {
            taskData.put("children", childrenInput);
        }
        dynamicCrudService.updateTaskEditableData(runtime.configKey(), runtime.recordId(), taskData,
                writableFields, childPermissions);
        return new TaskFormSaveResult(runtime, null);
    }

    /**
     * 办理低代码业务待办。该入口在 Flowable 任务完成后同步业务流程实例和业务单据状态，
     * 避免低代码单据状态停留在发起时的 IN_PROCESS。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO completeBusinessTask(BusinessTaskActionDTO dto) {
        if (dto == null) {
            throw new BusinessException("业务待办办理参数不能为空");
        }
        String action = StringUtils.defaultIfBlank(dto.getAction(), "approve").trim().toLowerCase();
        if (!"approve".equals(action) && !"reject".equals(action)
                && !"rejecttostart".equals(action) && !"return".equals(action)) {
            throw new BusinessException("当前业务待办仅支持同意、驳回、驳回至发起人或退回");
        }
        if (flowClient == null) {
            throw new BusinessException("流程服务未配置，无法办理业务待办");
        }

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

        Map<String, Object> taskFormInfo = taskNodeFormResolver.loadTaskFormInfo(query.getTaskId());
        validateTaskAccess(query, true, taskFormInfo);
        TaskFormRuntimeContext runtime = businessRuntimeContextResolver.resolveTask(query, true, taskFormInfo);
        if (dto.getData() != null && !dto.getData().isEmpty()) {
            JSONObject nodeForm = taskNodeFormResolver.resolveTaskNodeForm(runtime, query, taskFormInfo);
            TaskFormSaveResult saveResult = persistTaskFormData(
                    toTaskFormSaveDTO(dto, query), query, runtime, nodeForm);
            runtime = saveResult.runtime();
        }
        Map<String, Object> variables = dto.getVariables() == null ? Map.of() : dto.getVariables();
        String userId = String.valueOf(resolveUserId());

        FlowResult<Void> result;
        if ("rejecttostart".equals(action)) {
            result = flowClient.rejectToStart(query.getTaskId(), userId, dto.getComment(), dto.getSignature(),
                    resolveTrustedTaskTenant(dto), dto.getIdempotencyKey(), dto.getRequestDigest());
        } else if ("reject".equals(action)) {
            result = flowClient.reject(query.getTaskId(), userId, dto.getComment(), dto.getSignature(),
                    resolveTrustedTaskTenant(dto), dto.getIdempotencyKey(), dto.getRequestDigest());
        } else if ("return".equals(action)) {
            result = flowClient.returnTask(query.getTaskId(), userId, dto.getComment(), dto.getSignature(),
                    StringUtils.trimToNull(dto.getTargetActivityId()));
        } else {
            result = flowClient.approve(query.getTaskId(), userId, dto.getComment(), dto.getSignature(), variables,
                    resolveTrustedTaskTenant(dto), dto.getIdempotencyKey(), dto.getRequestDigest(),
                    dto.getApprovalPointResults());
        }
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(result == null
                    ? "业务待办办理失败"
                    : StringUtils.defaultIfBlank(result.getMsg(), "业务待办办理失败"));
        }

        return syncBusinessFlowStatusAfterTaskAction(runtime, query, action, variables);
    }

    private BusinessTaskFormSaveDTO toTaskFormSaveDTO(BusinessTaskActionDTO dto,
                                                      BusinessTaskFormContextQueryDTO query) {
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

    /**
     * 仅供 FLOW_ACTION 本地审计恢复调用。此时远程任务可能已完成，不能再要求它处于 actionable；
     * Flow 服务仍以同租户、原签收人、动作、幂等键和请求摘要做最终裁决。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO recoverCapabilityTaskAction(BusinessTaskActionDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getTaskId())
                || StringUtils.isBlank(dto.getIdempotencyKey())
                || StringUtils.isBlank(dto.getRequestDigest())) {
            throw new BusinessException(409, "FLOW_RECOVERY_EVIDENCE_REQUIRED");
        }
        String action = StringUtils.defaultIfBlank(dto.getAction(), "approve").trim().toLowerCase();
        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new BusinessException(409, "POLICY_MISMATCH");
        }
        if (flowClient == null) {
            throw new BusinessException("流程服务未配置，无法恢复业务待办");
        }
        Long tenantId = resolveTrustedTaskTenant(dto);
        String userId = String.valueOf(resolveUserId());
        Map<String, Object> variables = Map.of();
        FlowResult<Void> result = "reject".equals(action)
                ? flowClient.reject(dto.getTaskId(), userId, dto.getComment(), dto.getSignature(),
                        tenantId, dto.getIdempotencyKey(), dto.getRequestDigest())
                : flowClient.approve(dto.getTaskId(), userId, dto.getComment(), dto.getSignature(), variables,
                        tenantId, dto.getIdempotencyKey(), dto.getRequestDigest());
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(result == null
                    ? "业务待办恢复失败"
                    : StringUtils.defaultIfBlank(result.getMsg(), "业务待办恢复失败"));
        }

        BusinessTaskFormContextQueryDTO query = new BusinessTaskFormContextQueryDTO();
        query.setTaskId(dto.getTaskId());
        query.setObjectCode(dto.getObjectCode());
        query.setRecordId(dto.getRecordId());
        if (StringUtils.isNotBlank(dto.getObjectCode()) && dto.getRecordId() != null) {
            String objectCode = businessRuntimeContextResolver.resolveCanonicalObjectCode(
                    tenantId, dto.getObjectCode());
            query.setBusinessKey(buildBusinessKey(objectCode, dto.getRecordId()));
        }
        return syncBusinessFlowStatusAfterTaskAction(null, query, action, variables);
    }

    private Long resolveTrustedTaskTenant(BusinessTaskActionDTO dto) {
        Long currentTenantId = resolveTenantId();
        if (dto.getTenantId() != null && !dto.getTenantId().equals(currentTenantId)) {
            throw new BusinessException(403, "FLOW_TASK_TENANT_MISMATCH");
        }
        return currentTenantId;
    }

    private BusinessFlowRuntimeVO syncBusinessFlowStatusAfterTaskAction(TaskFormRuntimeContext runtime,
                                                                        BusinessTaskFormContextQueryDTO query,
                                                                        String action,
                                                                        Map<String, Object> variables) {
        String businessKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getBusinessKey()),
                runtime == null ? null : StringUtils.trimToNull(runtime.businessKey()));
        String processInstanceId = StringUtils.trimToNull(query.getProcessInstanceId());
        AiBusinessFlowInstanceLink link = findRuntimeLink(resolveTenantId(), processInstanceId, businessKey);
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
        String terminalResult = resolveTerminalBusinessFlowResult(engineStatus);
        if (StringUtils.isNotBlank(terminalResult)) {
            BusinessFlowCallbackDTO callback = new BusinessFlowCallbackDTO();
            callback.setProcessInstanceId(StringUtils.firstNonBlank(processInstanceId, link.getProcessInstanceId()));
            callback.setBusinessKey(link.getBusinessKey());
            callback.setResult(terminalResult);
            callback.setFlowStatus(engineStatus);
            callback.setTenantId(link.getTenantId());
            callback.setOperatorId(resolveUserId());
            callback.setVariables(variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables));
            callbackCoordinator.handleLinkedCallback(link, callback);
            return toRuntimeVO(link, "业务待办已办理，流程已结束");
        }

        BusinessDocumentFlowStatus targetStatus = "reject".equals(action)
                || "rejecttostart".equals(action)
                ? BusinessDocumentFlowStatus.NEED_MODIFY
                : BusinessDocumentFlowStatus.IN_PROCESS;
        taskEventCoordinator.applyRunningFlowState(link, targetStatus);
        return toRuntimeVO(link, "业务待办已办理，流程继续流转");
    }

    private String readFlowEngineBusinessStatus(String businessKey) {
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

    private String resolveFlowBusinessKeyForStart(String businessKey, AiBusinessFlowInstanceLink latest) {
        if (latest == null) {
            return businessKey;
        }
        return businessKey + ":R" + (latest.getId() == null ? System.currentTimeMillis() : latest.getId() + 1);
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

    private String resolveTerminalBusinessFlowResult(String engineStatus) {
        String normalized = StringUtils.trimToEmpty(engineStatus).toUpperCase();
        if (normalized.contains("APPROVED") || normalized.contains("COMPLETED")) {
            return "APPROVED";
        }
        if (normalized.contains("REJECT")) {
            return "REJECTED";
        }
        if (normalized.contains("CANCEL") || normalized.contains("TERMINAT") || normalized.contains("WITHDRAW")) {
            return "CANCELED";
        }
        return null;
    }

    /**
     * 驳回修改后重提。复杂代码业务可先在业务页保存主数据，再调用该接口完成修改节点。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO resubmit(BusinessFlowResubmitDTO dto) {
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
        validateTaskAccess(query, true, taskFormInfo);
        TaskFormRuntimeContext runtime = businessRuntimeContextResolver.resolveTask(query, true, taskFormInfo);
        Map<String, Object> variables = dto.getVariables() == null ? Map.of() : dto.getVariables();
        FlowResult<Void> result = flowClient.approve(
                query.getTaskId(),
                String.valueOf(resolveUserId()),
                StringUtils.defaultIfBlank(dto.getComment(), "修改后重提"),
                variables);
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(result == null ? "重提失败" : StringUtils.defaultIfBlank(result.getMsg(), "重提失败"));
        }

        AiBusinessFlowInstanceLink link = findRuntimeLink(resolveTenantId(), query.getProcessInstanceId(), runtime.businessKey());
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
        // 修改节点已经办完，待办随之失效；重提后的新审批待办由任务创建事件重建。
        link.setVariablesSnapshot(BusinessFlowLinkRuntimeState.writeModifyTask(
                taskEventCoordinator.mergeLinkVariablesSnapshot(link, variables), null));
        flowInstanceLinkMapper.updateById(link);
        return toRuntimeVO(link, "已重提");
    }

    /**
     * 发起人从业务记录撤回运行中的审批流程，兼容新版应用级流程和旧版主流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO withdrawDocumentFlow(BusinessFlowWithdrawDTO dto) {
        if (dto == null) {
            throw new BusinessException("撤回参数不能为空");
        }
        Long tenantId = resolveTenantId();
        Long userId = resolveUserId();
        if (userId == null) {
            throw new BusinessException("当前用户未登录，无法撤回流程");
        }
        if (flowClient == null) {
            throw new BusinessException("流程服务未配置，无法撤回流程");
        }

        String objectCode = StringUtils.trimToNull(dto.getObjectCode());
        if (StringUtils.isNotBlank(objectCode)) {
            objectCode = businessRuntimeContextResolver.resolveCanonicalObjectCode(tenantId, objectCode);
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
                link.getProcessInstanceId(),
                String.valueOf(userId),
                StringUtils.defaultIfBlank(dto.getComment(), "申请人撤回"));
        if (result == null || !result.isSuccess()) {
            throw new BusinessException(result == null
                    ? "撤回失败"
                    : StringUtils.defaultIfBlank(result.getMsg(), "撤回失败"));
        }

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

    private boolean isRunningFlowStatus(String flowStatus) {
        return BusinessDocumentFlowStatus.STARTED.matches(flowStatus)
                || BusinessDocumentFlowStatus.RUNNING.matches(flowStatus)
                || BusinessDocumentFlowStatus.IN_PROCESS.matches(flowStatus)
                || BusinessDocumentFlowStatus.NEED_MODIFY.matches(flowStatus);
    }

    private void validateTaskAccess(BusinessTaskFormContextQueryDTO query, boolean writeRequired) {
        validateTaskAccess(query, writeRequired,
                taskNodeFormResolver.loadTaskFormInfo(query == null ? null : query.getTaskId()));
    }

    private void validateTaskAccess(BusinessTaskFormContextQueryDTO query,
                                    boolean writeRequired,
                                    Map<String, Object> task) {
        TASK_ACCESS_POLICY.validate(query, writeRequired, task, flowClient != null, resolveUserId());
    }

    private BusinessTaskFormContextVO buildTaskFormContext(BusinessTaskFormContextQueryDTO query,
                                                           TaskFormRuntimeContext runtime,
                                                           Map<String, Object> taskFormInfo) {
        return buildTaskFormContext(query, runtime, taskFormInfo, new LinkedHashMap<>(), System.nanoTime());
    }

    private BusinessTaskFormContextVO buildTaskFormContext(BusinessTaskFormContextQueryDTO query,
                                                           TaskFormRuntimeContext runtime,
                                                           Map<String, Object> taskFormInfo,
                                                           Map<String, Long> preStages,
                                                           long startedAt) {
        Map<String, Long> stages = preStages == null ? new LinkedHashMap<>() : preStages;
        BusinessTaskFormContextVO vo = new BusinessTaskFormContextVO();
        vo.setTaskId(StringUtils.trimToNull(query.getTaskId()));
        vo.setBusinessKey(runtime.businessKey());
        vo.setProcessInstanceId(StringUtils.trimToNull(query.getProcessInstanceId()));
        vo.setProcessDefKey(StringUtils.trimToNull(query.getProcessDefKey()));
        vo.setTaskDefKey(StringUtils.trimToNull(query.getTaskDefKey()));
        vo.setObjectCode(runtime.objectCode());
        vo.setRecordId(runtime.recordId());
        vo.setConfigKey(runtime.configKey());
        vo.setFormType("none");
        if (taskFormInfo != null && !taskFormInfo.isEmpty()) {
            vo.setTaskFormInfo(new LinkedHashMap<>(taskFormInfo));
        }

        if (StringUtils.isBlank(runtime.objectCode())) {
            vo.getWarnings().add("未解析到业务对象");
            logTaskFormContextTiming(query, runtime, null, stages, startedAt);
            return vo;
        }

        long mark = System.nanoTime();
        JSONObject nodeForm = taskNodeFormResolver.resolveTaskNodeForm(runtime, query, taskFormInfo);
        stages.put("nodeFormMs", elapsedMillis(mark));
        if (nodeForm == null || nodeForm.isEmpty()) {
            vo.getWarnings().add("当前节点未配置业务表单策略");
            logTaskFormContextTiming(query, runtime, null, stages, startedAt);
            return vo;
        }
        String formMode = normalizeNodeFormMode(nodeForm.getString("formMode"));
        if (!"BUSINESS_OBJECT_FORM".equals(formMode)) {
            if ("BUSINESS_CODE_FORM".equals(formMode)) {
                BusinessTaskFormContextVO codeContext = codeFormCoordinator.build(
                        query,
                        nodeForm,
                        runtime.objectCode(),
                        runtime.recordId(),
                        runtime.businessKey(),
                        runtime.configKey());
                logTaskFormContextTiming(query, runtime, codeContext.getFormKey(), stages, startedAt);
                return codeContext;
            }
            vo.setFormType(formMode);
            vo.setFormKey(StringUtils.trimToNull(nodeForm.getString("formKey")));
            vo.setFormName(StringUtils.trimToNull(nodeForm.getString("formName")));
            vo.setProviderKey(StringUtils.trimToNull(nodeForm.getString("providerKey")));
            vo.setFormUrl(StringUtils.trimToNull(nodeForm.getString("formUrl")));
            vo.setEditMode(normalizeNodeEditMode(nodeForm.getString("editMode")));
            vo.setFormRef(readNestedObject(nodeForm.get("formRef")));
            taskFormSchemaAssembler.applyPageFormIdentity(vo, vo.getFormRef());
            TASK_FORM_POLICY.applyApprovalPolicy(vo, nodeForm);
            vo.getWarnings().add("当前节点表单类型暂不由低代码业务表单渲染: " + formMode);
            logTaskFormContextTiming(query, runtime, vo.getFormKey(), stages, startedAt);
            return vo;
        }
        if (StringUtils.isBlank(runtime.configKey())) {
            vo.getWarnings().add("业务对象缺少已发布运行配置，无法加载低代码业务表单");
            logTaskFormContextTiming(query, runtime, null, stages, startedAt);
            return vo;
        }

        String formKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(query.getFormKey()),
                StringUtils.trimToNull(nodeForm.getString("formKey")));

        // 优先复用运行上下文解析器已加载的对象 / 发布配置，避免再查库
        mark = System.nanoTime();
        AiCrudConfig runtimeConfig = runtime.publishedConfig() != null
                ? runtime.publishedConfig()
                : taskFormSchemaAssembler.safeGetRuntimeConfig(runtime.configKey());
        JSONObject runtimeOptions = runtimeConfig == null ? new JSONObject() : readJsonObject(runtimeConfig.getOptions());
        stages.put("runtimeConfigMs", elapsedMillis(mark));

        mark = System.nanoTime();
        BusinessObjectVO object = runtime.businessObject() != null
                ? toBusinessObjectVO(runtime.businessObject())
                : queryBusinessObject(resolveTenantId(), runtime.objectCode(), runtime.configKey());
        stages.put("businessObjectMs", elapsedMillis(mark));
        vo.setBusinessObjectName(object == null ? runtime.objectCode() : object.getObjectName());

        mark = System.nanoTime();
        JSONObject formSchema = resolveBusinessFormSchema(object, formKey, runtime.configKey(), runtimeConfig);
        stages.put("formSchemaMs", elapsedMillis(mark));
        if (formSchema.isEmpty()) {
            vo.getWarnings().add("未找到节点引用的低代码表单资产: " + formKey);
            logTaskFormContextTiming(query, runtime, formKey, stages, startedAt);
            return vo;
        }

        mark = System.nanoTime();
        List<Map<String, Object>> fieldCatalog = taskFormSchemaAssembler.resolveBusinessTaskCrudPageFields(
                runtime.configKey(), formKey, formSchema, runtimeConfig, runtimeOptions);
        taskChildAssembler.enrichTaskMainFieldsFromObjectRegistry(fieldCatalog, object);
        List<Map<String, Object>> permissions = TASK_FORM_POLICY.normalizePermissions(
                fieldCatalog, normalizeFieldPermissions(nodeForm.get("fieldPermissions")));
        List<Map<String, Object>> fields = TASK_FORM_POLICY.buildFields(fieldCatalog, permissions);
        stages.put("fieldsMs", elapsedMillis(mark));

        mark = System.nanoTime();
        Map<String, Object> recordData = runtime.recordId() == null
                ? businessRuntimeContextResolver.loadTaskVariablesAsRecord(query, taskFormInfo)
                : (runtimeConfig == null
                ? dynamicCrudService.selectById(runtime.configKey(), runtime.recordId())
                : dynamicCrudService.selectById(runtimeConfig, runtime.recordId()));
        Map<String, Object> visibleRecordData = TASK_FORM_POLICY.filterVisibleRecordData(recordData, fields);
        stages.put("recordMs", elapsedMillis(mark));

        mark = System.nanoTime();
        List<Map<String, Object>> childrenConfig = taskChildAssembler.resolveBusinessTaskChildrenConfig(
                runtime.configKey(), nodeForm, runtimeOptions, formSchema);
        TASK_CHILD_POLICY.logChildren("raw", runtime.configKey(), runtime.recordId(), childrenConfig, visibleRecordData);
        TASK_CHILD_POLICY.filterVisibleRecordChildren(visibleRecordData, childrenConfig);
        TASK_CHILD_POLICY.logChildren("filtered", runtime.configKey(), runtime.recordId(), childrenConfig, visibleRecordData);
        stages.put("childrenMs", elapsedMillis(mark));

        vo.setBusinessSummary(resolveBusinessSummary(object, runtime, recordData));

        vo.setConfigured(true);
        vo.setFormType("business-object");
        vo.setFormKey(StringUtils.firstNonBlank(formKey, formSchema.getString("formKey")));
        vo.setFormName(StringUtils.defaultIfBlank(nodeForm.getString("formName"), formSchema.getString("formName")));
        vo.setViewKey(StringUtils.defaultIfBlank(nodeForm.getString("viewKey"), "default"));
        vo.setEditMode(TASK_FORM_POLICY.resolveEditMode(nodeForm, permissions));
        taskFormSchemaAssembler.applyBusinessObjectFormLayout(vo, formSchema, runtimeOptions);
        vo.setFormRef(readNestedObject(nodeForm.get("formRef")));
        taskFormSchemaAssembler.applyPageFormIdentity(vo, vo.getFormRef());
        vo.setFieldPermissions(permissions);
        vo.setFields(fields);

        mark = System.nanoTime();
        vo.setFormAssets(taskFormSchemaAssembler.resolveBusinessTaskFormAssets(
                formSchema, runtime.configKey(), formKey, runtimeOptions));
        stages.put("formAssetsMs", elapsedMillis(mark));

        vo.setChildrenConfig(childrenConfig);
        vo.setRecordData(visibleRecordData);

        mark = System.nanoTime();
        Map<String, Object> uiDocument = TaskFormUiDocumentCompiler.compile(
                formSchema, vo.getFormKey(), fields, permissions);
        vo.setProtocolVersion(TaskFormUiDocumentCompiler.PROTOCOL_VERSION);
        vo.setUiDocument(uiDocument);
        stages.put("uiDocumentMs", elapsedMillis(mark));

        TASK_FORM_POLICY.applyApprovalPolicy(vo, nodeForm);
        if (fields.isEmpty()) {
            vo.getWarnings().add("当前业务表单没有可展示字段");
        }
        logTaskFormContextTiming(query, runtime, vo.getFormKey(), stages, startedAt);
        return vo;
    }

    private void logTaskFormContextTiming(BusinessTaskFormContextQueryDTO query,
                                          TaskFormRuntimeContext runtime,
                                          String formKey,
                                          Map<String, Long> stages,
                                          long startedAt) {
        if (!log.isInfoEnabled()) {
            return;
        }
        List<String> notes = TASK_FORM_DETAIL_NOTES.get();
        log.info("[task-form-context] taskId={} objectCode={} recordId={} formKey={} totalMs={} stages={} notes={}",
                query == null ? null : query.getTaskId(),
                runtime == null ? null : runtime.objectCode(),
                runtime == null ? null : runtime.recordId(),
                formKey,
                elapsedMillis(startedAt),
                stages,
                notes == null || notes.isEmpty() ? List.of() : List.copyOf(notes));
    }

    private void beginTaskFormProfiling(Map<String, Long> stages) {
        TASK_FORM_DETAIL_STAGES.set(stages);
        TASK_FORM_DETAIL_NOTES.set(new ArrayList<>());
    }

    private void endTaskFormProfiling() {
        TASK_FORM_DETAIL_STAGES.remove();
        TASK_FORM_DETAIL_NOTES.remove();
    }

    private void markTaskFormDetail(String key, long startedAtNanos) {
        Map<String, Long> stages = TASK_FORM_DETAIL_STAGES.get();
        if (stages == null || StringUtils.isBlank(key)) {
            return;
        }
        stages.merge(key, elapsedMillis(startedAtNanos), Long::sum);
    }

    private void noteTaskFormDetail(String note) {
        List<String> notes = TASK_FORM_DETAIL_NOTES.get();
        if (notes == null || StringUtils.isBlank(note)) {
            return;
        }
        notes.add(note);
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }


    private void ensureRuntimeLink(TaskFormRuntimeContext runtime,
                                   BusinessTaskFormContextQueryDTO query,
                                   Long recordId) {
        if (runtime == null || query == null || recordId == null || StringUtils.isBlank(runtime.objectCode())) {
            return;
        }
        Long tenantId = resolveTenantId();
        String processInstanceId = StringUtils.trimToNull(query.getProcessInstanceId());
        String businessKey = buildBusinessKey(runtime.objectCode(), recordId);
        AiBusinessFlowInstanceLink existing = StringUtils.isBlank(processInstanceId)
                ? null
                : flowInstanceLinkMapper.selectByProcessInstanceId(tenantId, processInstanceId);
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
                StringUtils.trimToNull(query.getProcessDefKey()),
                resolveFlowModelKey(runtime.bindingConfig())));
        link.setProcessInstanceId(processInstanceId);
        link.setFlowStatus(BusinessDocumentFlowStatus.RUNNING.getCode());
        link.setStartUserId(resolveUserId());
        link.setStartTime(LocalDateTime.now());
        link.setRoundNo(resolveNextRoundNo(tenantId, businessKey));
        flowInstanceLinkMapper.insert(link);
    }

    private int resolveNextRoundNo(Long tenantId, String businessKey) {
        return resolveNextRoundNo(flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, businessKey));
    }

    private int resolveNextRoundNo(AiBusinessFlowInstanceLink latest) {
        if (latest == null || latest.getRoundNo() == null || latest.getRoundNo() < 1) {
            return 1;
        }
        return latest.getRoundNo() + 1;
    }


    private BusinessObjectVO queryBusinessObject(Long tenantId, String objectCode) {
        return queryBusinessObject(tenantId, objectCode, null);
    }

    /**
     * 查询待办展示对象。配置键是运行时的稳定身份，必须优先于可能来自历史数据的 objectCode。
     */
    private BusinessObjectVO queryBusinessObject(Long tenantId, String objectCode, String configKey) {
        if (StringUtils.isNotBlank(configKey)) {
            AiBusinessObject byConfigKey = businessObjectMapper.selectByConfigKey(tenantId, configKey);
            if (byConfigKey != null) {
                return toBusinessObjectVO(byConfigKey);
            }
        }
        if (StringUtils.isBlank(objectCode)) {
            return null;
        }
        BusinessObjectQueryDTO query = new BusinessObjectQueryDTO();
        query.setObjectCode(objectCode);
        List<BusinessObjectVO> objects = businessObjectMapper.selectObjectList(tenantId, query);
        return objects == null || objects.isEmpty() ? null : objects.get(0);
    }

    private BusinessObjectVO toBusinessObjectVO(AiBusinessObject object) {
        if (object == null) {
            return null;
        }
        BusinessObjectVO vo = new BusinessObjectVO();
        vo.setId(object.getId());
        vo.setSuiteCode(object.getSuiteCode());
        vo.setObjectCode(object.getObjectCode());
        vo.setObjectName(object.getObjectName());
        vo.setObjectType(object.getObjectType());
        vo.setModelId(object.getModelId());
        vo.setModelCode(object.getModelCode());
        vo.setDisplayField(object.getDisplayField());
        vo.setIcon(object.getIcon());
        vo.setDescription(object.getDescription());
        vo.setStatus(object.getStatus());
        vo.setSortOrder(object.getSortOrder());
        vo.setOptions(object.getOptions());
        vo.setDesignStatus(object.getDesignStatus());
        vo.setConfigKey(object.getConfigKey());
        vo.setLastPublishTime(object.getLastPublishTime());
        vo.setLastPublishVersion(object.getLastPublishVersion());
        vo.setDesignerOptions(object.getDesignerOptions());
        return vo;
    }



    /**
     * 节点表单 / formRef 只要身份字段；避免把整份设计器 schema 拷进响应组装路径。
     */
   private List<Map<String, Object>> collectTaskFormAssets(String objectCode) {
        if (StringUtils.isBlank(objectCode)) {
            return List.of();
        }
        Long tenantId = resolveTenantId();
        BusinessObjectVO object = queryBusinessObject(tenantId, objectCode);
        List<Map<String, Object>> assets = new ArrayList<>();
        if (object != null) {
            AiCrudConfig runtimeConfig = resolvePublishedRuntimeConfig(tenantId, StringUtils.firstNonBlank(
                    object.getConfigKey(), object.getObjectCode(), objectCode));
            JSONObject designerOptions = readJsonObject(object.getDesignerOptions());
            JSONObject formSchema = readNestedObject(designerOptions.get("formDesignerSchema"));
            assets.addAll(formAssetAssembler.collectBusinessFormAssets(object, formSchema));
            formAssetAssembler.appendUniqueFormAssets(
                    assets, formAssetAssembler.collectRuntimeCrudFormAssets(object, runtimeConfig));
            formAssetAssembler.appendObjectFieldRegistryFallback(assets, object);
            JSONObject metadata = readCodeAppMetadata(tenantId, object.getObjectCode());
            formAssetAssembler.appendUniqueFormAssets(assets, mergeCodeAppAssets(
                    object.getObjectCode(), codeFormProviderRegistry.listAssets(object.getObjectCode()),
                    metadata, false));
        } else {
            AiCrudConfig runtimeConfig = resolvePublishedRuntimeConfig(tenantId, objectCode);
            formAssetAssembler.appendUniqueFormAssets(
                    assets, formAssetAssembler.collectRuntimeCrudFormAssets(null, runtimeConfig));
            JSONObject metadata = readCodeAppMetadata(tenantId, objectCode);
            formAssetAssembler.appendUniqueFormAssets(assets, mergeCodeAppAssets(
                    objectCode, codeFormProviderRegistry.listAssets(objectCode), metadata, false));
        }
        formAssetAssembler.appendObjectFieldRegistryFallback(assets, object);
        return assets;
    }

    private JSONObject resolveBusinessFormSchema(BusinessObjectVO object, String formKey, String configKey) {
        return resolveBusinessFormSchema(object, formKey, configKey, null);
    }

    private JSONObject resolveBusinessFormSchema(BusinessObjectVO object,
                                                 String formKey,
                                                 String configKey,
                                                 AiCrudConfig preloadedRuntimeConfig) {
        AiCrudConfig runtimeConfig = preloadedRuntimeConfig != null
                ? preloadedRuntimeConfig
                : resolveRuntimeConfigForBusinessForm(object, configKey);
        JSONObject applicationSchema = applicationPageFormResolver.resolveApplicationPageFormSchema(formKey);
        // 对象设计器当前表单优先：应用页 formAssets / 发布快照常落后于用户刚改的对象表单
        JSONObject objectLiveSchema = resolveObjectDesignerFormSchema(object, formKey);
        if (hasRenderableFormComponents(objectLiveSchema)) {
            JSONObject result = JSON.parseObject(JSON.toJSONString(objectLiveSchema));
            if (!applicationSchema.isEmpty()) {
                result.put("formKey", StringUtils.firstNonBlank(
                        StringUtils.trimToNull(applicationSchema.getString("formKey")),
                        StringUtils.trimToNull(result.getString("formKey")),
                        StringUtils.trimToNull(formKey)));
                result.put("formName", StringUtils.firstNonBlank(
                        StringUtils.trimToNull(applicationSchema.getString("formName")),
                        StringUtils.trimToNull(result.getString("formName"))));
            }
            noteTaskFormDetail("formSchema=objectDesignerLive");
            return result;
        }
        if (!applicationSchema.isEmpty()) {
            noteTaskFormDetail("formSchema=applicationPage");
            return applicationSchema;
        }
        if (object == null) {
            return formAssetAssembler.buildRuntimeCrudFormSchema(null, runtimeConfig, formKey);
        }
        if (!objectLiveSchema.isEmpty()) {
            return objectLiveSchema;
        }
        JSONObject runtimeSchema = formAssetAssembler.buildRuntimeCrudFormSchema(object, runtimeConfig, formKey);
        return runtimeSchema.isEmpty() ? buildObjectFieldRegistryFormSchema(object, formKey) : runtimeSchema;
    }

    /**
     * 读取业务对象设计器里当前保存的表单 schema（designerOptions.formDesignerSchema）。
     */
    private JSONObject resolveObjectDesignerFormSchema(BusinessObjectVO object, String formKey) {
        if (object == null) {
            return new JSONObject();
        }
        JSONObject designerOptions = readJsonObject(object.getDesignerOptions());
        JSONObject formSchema = readNestedObject(designerOptions.get("formDesignerSchema"));
        if (formSchema.isEmpty()) {
            return new JSONObject();
        }
        String targetFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(formKey),
                StringUtils.trimToNull(formSchema.getString("defaultFormKey")),
                StringUtils.trimToNull(formSchema.getString("formKey")));
        // app_ 页面 formKey 对不上对象内部 formKey，按默认/根表单取
        boolean appPageFormKey = StringUtils.startsWith(StringUtils.trimToEmpty(formKey), "app_");

        JSONObject byForms = formAssetAssembler.findFormSchemaInArray(
                readNestedArray(formSchema.get("forms")), targetFormKey);
        if (!byForms.isEmpty() && !collectBusinessFormFieldCatalog(byForms).isEmpty()) {
            return byForms;
        }
        JSONObject settings = readNestedObject(formSchema.get("settings"));
        JSONObject byAssets = formAssetAssembler.findFormSchemaInArray(
                readNestedArray(settings.get("formAssets")), targetFormKey);
        if (!byAssets.isEmpty() && !collectBusinessFormFieldCatalog(byAssets).isEmpty()) {
            return byAssets;
        }
        String rootFormKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(formSchema.getString("formKey")),
                StringUtils.trimToNull(formSchema.getString("defaultFormKey")));
        if (appPageFormKey
                || StringUtils.isBlank(targetFormKey)
                || StringUtils.equals(targetFormKey, rootFormKey)
                || hasRenderableFormComponents(formSchema)) {
            return formSchema;
        }
        return new JSONObject();
    }

    private boolean hasRenderableFormComponents(JSONObject formSchema) {
        if (formSchema == null || formSchema.isEmpty()) {
            return false;
        }
        JSONArray components = readNestedArray(formSchema.get("components"));
        if (!components.isEmpty()) {
            return true;
        }
        JSONObject settings = readNestedObject(formSchema.get("settings"));
        return !readNestedArray(settings.get("components")).isEmpty()
                || !collectBusinessFormFieldCatalog(formSchema).isEmpty();
    }

    private JSONObject buildObjectFieldRegistryFormSchema(BusinessObjectVO object, String requestedFormKey) {
        if (object == null || object.getId() == null) {
            return new JSONObject();
        }
        try {
            List<Map<String, Object>> sourceFields = new ArrayList<>();
            businessFieldDesignService.listFields(object.getId()).forEach(field ->
                    sourceFields.add(new LinkedHashMap<>(
                            JSON.parseObject(JSON.toJSONString(field), JSONObject.class))));
            List<Map<String, Object>> fields = formAssetAssembler.normalizeRuntimeCrudFormFields(sourceFields);
            if (fields.isEmpty()) {
                return new JSONObject();
            }
            String formKey = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(requestedFormKey), object.getObjectCode());
            JSONObject schema = new JSONObject();
            schema.put("formKey", formKey);
            schema.put("defaultFormKey", formKey);
            schema.put("formName", StringUtils.defaultIfBlank(object.getObjectName(), object.getObjectCode()) + "表单");
            JSONArray components = new JSONArray();
            fields.forEach(field -> components.add(formAssetAssembler.toRuntimeCrudFormComponent(field)));
            schema.put("components", components);
            return schema;
        } catch (Exception e) {
            log.debug("读取业务对象字段注册表表单 schema 失败: objectId={}, error={}", object.getId(), e.getMessage());
            return new JSONObject();
        }
    }

    private JSONObject readCodeAppMetadata(Long tenantId, String objectCode) {
        if (tenantId == null || StringUtils.isBlank(objectCode)) {
            return new JSONObject();
        }
        AiBusinessBinding binding = selectMainFlowBindingForConfig(tenantId, objectCode);
        if (binding == null) {
            return new JSONObject();
        }
        JSONObject config = readBindingConfig(binding.getBindingConfig());
        JSONObject options = readNestedObject(config.get("options"));
        return readNestedObject(options.get("codeAppMetadata"));
    }



    /**
     * 旧触发器路径兼容返回，保留 config 包装结构。
     */
    public JSONObject getFlowBindingLegacy(String objectCode) {
        BusinessFlowBindingVO binding = getFlowBinding(objectCode);
        if (binding == null) {
            return null;
        }
        JSONObject result = new JSONObject();
        result.put("bindingId", binding.getBindingId());
        result.put("bindingName", StringUtils.defaultIfBlank(binding.getFlowModelName(), binding.getFlowModelKey()));
        result.put("objectCode", binding.getObjectCode());
        result.put("status", binding.getStatus());
        result.put("config", toConfigJson(binding));
        return result;
    }

    /**
     * 保存流程绑定配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFlowBinding(String objectCode, BusinessFlowBindingDTO dto) {
        if (dto == null) {
            throw new BusinessException("流程绑定配置不能为空");
        }
        Long tenantId = resolveTenantId();
        String canonicalObjectCode = businessRuntimeContextResolver.resolveCanonicalObjectCode(tenantId, objectCode);
        JSONObject config = normalizeBindingConfig(dto);
        ensureBusinessBinding(config, tenantId, canonicalObjectCode);
        String flowModelKey = config.getString("flowModelKey");
        if (StringUtils.isBlank(flowModelKey)) {
            throw new BusinessException("流程模型Key不能为空");
        }
        ensureLowcodeFlowStatusField(tenantId, canonicalObjectCode);
        AiBusinessBinding existing = bindingMapper.selectBindingByTypeAndCode(
                tenantId, "OBJECT", canonicalObjectCode, "FLOW");

        if (existing != null) {
            existing.setTargetType("OBJECT");
            existing.setTargetCode(canonicalObjectCode);
            existing.setBindingType("FLOW");
            existing.setBindingConfig(config.toJSONString());
            existing.setBindingKey(flowModelKey);
            existing.setBindingName(resolveBindingName(config));
            existing.setStatus(EnableStatus.ENABLED.getCode());
            bindingMapper.updateById(existing);
            log.info("[低代码流程绑定] 更新主流程绑定: tenantId={}, objectCode={}, bindingId={}, flowModelKey={}",
                    tenantId, objectCode, existing.getId(), flowModelKey);
        } else {
            AiBusinessBinding binding = new AiBusinessBinding();
            binding.setTenantId(tenantId);
            binding.setTargetType("OBJECT");
            binding.setTargetCode(canonicalObjectCode);
            binding.setBindingType("FLOW");
            binding.setBindingKey(flowModelKey);
            binding.setBindingName(resolveBindingName(config));
            binding.setBindingConfig(config.toJSONString());
            binding.setStatus(EnableStatus.ENABLED.getCode());
            binding.setSortOrder(0);
            bindingMapper.insert(binding);
            log.info("[低代码流程绑定] 创建主流程绑定: tenantId={}, objectCode={}, bindingId={}, flowModelKey={}",
                    tenantId, objectCode, binding.getId(), flowModelKey);
        }
        documentConfigService.syncDefaultFlowKeyByObjectCode(tenantId, canonicalObjectCode, flowModelKey);
    }

    /**
     * 旧触发器路径保存兼容，读取 field/variable 后只落 formField/flowVariable。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFlowBinding(String objectCode, JSONObject config) {
        saveFlowBinding(objectCode, toDTO(config));
    }

    /**
     * 绑定入口也必须经过字段一致性检查。流程设计器之外的旧触发器入口此前只写
     * ai_business_binding，导致对象草稿/发布版本仍停留在不含 flowStatus 的快照。
     */
    private void ensureLowcodeFlowStatusField(Long tenantId, String objectCode) {
        if (flowStatusFieldService == null || businessObjectMapper == null || crudConfigMapper == null) {
            return;
        }
        AiBusinessObject object = businessObjectMapper.selectFirstByObjectCode(tenantId, objectCode);
        if (object == null || object.getId() == null) {
            return;
        }
        String lookup = StringUtils.firstNonBlank(object.getConfigKey(), objectCode);
        AiCrudConfig runtimeConfig = crudConfigMapper.selectRuntimeByObjectCodeOrConfigKey(tenantId, lookup);
        if (runtimeConfig == null || !"LOWCODE".equalsIgnoreCase(runtimeConfig.getBuildMode())) {
            return;
        }
        flowStatusFieldService.ensure(object.getId());
    }

    /**
     * 手动按钮发起单据流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO startDocumentFlow(BusinessFlowStartDTO dto) {
        Long tenantId = resolveTenantId();
        return TenantContextHolder.executeWithTenant(tenantId,
                () -> startDocumentFlowInternal(dto, true, null, null, tenantId, false));
    }

    /**
     * 受控 FLOW_ACTION 专用发起入口。业务 key 永远固定为 objectCode:recordId，
     * 远程成功而本地回填失败时，同 key 重试可恢复原流程实例。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO startDocumentFlowForCapability(BusinessFlowStartDTO dto) {
        Long tenantId = resolveTenantId();
        return TenantContextHolder.executeWithTenant(tenantId,
                () -> startDocumentFlowInternal(dto, true, null, null, tenantId, true));
    }

    /**
     * 旧审批入口兼容发起。接口层仍有旧权限校验，这里不再重复要求新流程按钮权限。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO startDocumentFlowForCompatibility(BusinessFlowStartDTO dto) {
        Long tenantId = resolveTenantId();
        return TenantContextHolder.executeWithTenant(tenantId,
                () -> startDocumentFlowInternal(dto, false, null, null, tenantId, false));
    }

    /**
     * 由触发器调用的流程发起（内部方法）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO startFlowFromTrigger(String flowModelKey, String businessKey, String title,
                                                      Long userId, String userName, JSONObject variables) {
        return startFlowFromTrigger(flowModelKey, businessKey, title, userId, userName, resolveTenantId(), variables);
    }

    /**
     * 由触发器调用的流程发起（内部方法）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO startFlowFromTrigger(String flowModelKey, String businessKey, String title,
                                                      Long userId, String userName, Long tenantId, JSONObject variables) {
        BusinessKeyParts parts = parseBusinessKey(businessKey);
        BusinessFlowStartDTO dto = new BusinessFlowStartDTO();
        dto.setObjectCode(parts.objectCode());
        dto.setRecordId(parts.recordId());
        dto.setFlowModelKey(flowModelKey);
        dto.setTitle(title);
        if (variables != null) {
            dto.setVariables(new LinkedHashMap<>(variables));
        }
        Long effectiveTenantId = tenantId != null ? tenantId : resolveTenantId();
        return TenantContextHolder.executeWithTenant(effectiveTenantId,
                () -> startDocumentFlowInternal(dto, false, userId, userName, effectiveTenantId, false, false));
    }

    /**
     * 应用级业务流程审批节点发起 Flowable。显式模型 Key 来自已发布/草稿画布，
     * 业务对象运行配置允许尚未发布的工作台草稿。
     */
    @Transactional(rollbackFor = Exception.class)
    public BusinessFlowRuntimeVO startFromBusinessProcess(String flowModelKey, String businessKey, String title,
                                                          Long userId, String userName, Long tenantId, JSONObject variables) {
        if (StringUtils.isBlank(flowModelKey)) {
            throw new BusinessException("审批节点未配置已发布流程模型");
        }
        BusinessKeyParts parts = parseBusinessKey(businessKey);
        BusinessFlowStartDTO dto = new BusinessFlowStartDTO();
        dto.setObjectCode(parts.objectCode());
        dto.setRecordId(parts.recordId());
        dto.setFlowModelKey(flowModelKey);
        dto.setTitle(title);
        if (variables != null) {
            dto.setVariables(new LinkedHashMap<>(variables));
        }
        Long effectiveTenantId = tenantId != null ? tenantId : resolveTenantId();
        return TenantContextHolder.executeWithTenant(effectiveTenantId,
                () -> startDocumentFlowInternal(dto, false, userId, userName, effectiveTenantId, false, true));
    }

    /**
     * 查询单据流程状态。
     */
    public BusinessFlowRuntimeVO getFlowStatus(String objectCode, Long recordId) {
        if (StringUtils.isBlank(objectCode)) {
            throw new BusinessException("业务对象编码不能为空");
        }
        if (recordId == null) {
            throw new BusinessException("记录ID不能为空");
        }
        String canonicalObjectCode = businessRuntimeContextResolver.resolveCanonicalObjectCode(
                resolveTenantId(), objectCode);
        String businessKey = buildBusinessKey(canonicalObjectCode, recordId);
        AiBusinessFlowInstanceLink link = flowInstanceLinkMapper.selectLatestByBusinessKey(resolveTenantId(), businessKey);
        if (link == null) {
            BusinessFlowRuntimeVO vo = new BusinessFlowRuntimeVO();
            vo.setObjectCode(canonicalObjectCode);
            vo.setRecordId(recordId);
            vo.setBusinessKey(businessKey);
            vo.setFlowStatus(BusinessDocumentFlowStatus.NOT_STARTED.getCode());
            vo.setMessage("尚未发起主流程");
            return vo;
        }
        return toRuntimeVO(link, null);
    }

    /**
     * 处理流程引擎回调，按流程结果回写单据状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleFlowCallback(BusinessFlowCallbackDTO dto) {
        callbackCoordinator.handleCallback(dto);
    }

    @FlowCallback(on = {
            FlowCallback.ON_TASK_CREATED,
            FlowCallback.ON_TASK_COMPLETED,
            FlowCallback.ON_COMPLETED,
            FlowCallback.ON_REJECTED,
            FlowCallback.ON_CANCELED
    })
    @Transactional(rollbackFor = Exception.class)
    public void handleFlowEngineEvent(FlowEventContext ctx) {
        if (ctx == null) {
            return;
        }
        if (FlowCallback.ON_TASK_CREATED.equals(ctx.getEvent())
                || FlowCallback.ON_TASK_COMPLETED.equals(ctx.getEvent())) {
            taskEventCoordinator.handleTaskEvent(ctx);
            return;
        }
        callbackCoordinator.handleEngineEvent(ctx);
    }

    /**
     * 任务级事件只维护流程运行期间的单据中间态（待修改 / 流程中）。
     * <p>
     * 终态一律由流程结束事件裁决，这里不写结束状态；单据状态同步失败也不能让审批动作失败，
     * 因此异常只记录日志，由发起人修改节点保存字段时的自愈逻辑兜底。
     */

    private BusinessFlowRuntimeVO startDocumentFlowInternal(BusinessFlowStartDTO dto,
                                                            boolean checkPermission,
                                                            Long starterUserId,
                                                            String starterUserName,
                                                            Long tenantId,
                                                            boolean stableBusinessKey) {
        return startDocumentFlowInternal(dto, checkPermission, starterUserId, starterUserName,
                tenantId, stableBusinessKey, false);
    }

    private BusinessFlowRuntimeVO startDocumentFlowInternal(BusinessFlowStartDTO dto,
                                                            boolean checkPermission,
                                                            Long starterUserId,
                                                            String starterUserName,
                                                            Long tenantId,
                                                            boolean stableBusinessKey,
                                                            boolean allowDraftRuntime) {
        if (dto == null) {
            throw new BusinessException("发起主流程参数不能为空");
        }
        if (StringUtils.isBlank(dto.getObjectCode())) {
            throw new BusinessException("业务对象编码不能为空");
        }
        if (dto.getRecordId() == null) {
            throw new BusinessException("请先保存记录后再发起主流程");
        }
        if (flowClient == null) {
            throw new BusinessException("流程服务未配置，无法发起主流程");
        }

        FlowStartContext startContext = resolveFlowStartContext(tenantId, dto.getObjectCode(), allowDraftRuntime);
        AiBusinessDocumentConfig documentConfig = startContext.documentConfig();
        AiCrudConfig runtimeConfig = startContext.runtimeConfig();
        String objectCode = startContext.objectCode();
        String configKey = startContext.configKey();
        Map<String, Object> recordData = allowDraftRuntime
                ? dynamicCrudService.selectByIdAllowDraft(configKey, dto.getRecordId())
                : dynamicCrudService.selectById(configKey, dto.getRecordId());
        if (recordData == null) {
            log.warn("[低代码流程启动] 业务记录查询为空: tenantId={}, objectCode={}, configKey={}, recordId={}, "
                            + "starterUserId={}, activeOrgId={}, checkPermission={}, stableBusinessKey={}",
                    tenantId, objectCode, configKey, dto.getRecordId(),
                    starterUserId != null ? starterUserId : resolveUserId(),
                    resolveActiveOrgId(), checkPermission, stableBusinessKey);
            throw new BusinessException(
                    404,
                    "记录不存在或无权限访问，请使用当前委托用户可见的已保存业务记录 ID");
        }

        String businessKey = buildBusinessKey(objectCode, dto.getRecordId());
        return executeWithFlowStartLock(tenantId, businessKey, () -> startDocumentFlowLocked(
                dto, checkPermission, starterUserId, starterUserName, tenantId, documentConfig,
                runtimeConfig, objectCode, configKey, recordData, businessKey,
                startContext.requestedObjectCode(), stableBusinessKey));
    }

    private BusinessFlowRuntimeVO startDocumentFlowLocked(BusinessFlowStartDTO dto,
                                                          boolean checkPermission,
                                                          Long starterUserId,
                                                          String starterUserName,
                                                          Long tenantId,
                                                          AiBusinessDocumentConfig documentConfig,
                                                          AiCrudConfig runtimeConfig,
                                                          String objectCode,
                                                          String configKey,
                                                          Map<String, Object> recordData,
                                                          String businessKey,
                                                          String requestedObjectCode,
                                                          boolean stableBusinessKey) {
        AiBusinessBinding binding = selectFlowBindingForStart(tenantId, objectCode, requestedObjectCode);
        JSONObject bindingConfig = binding == null ? new JSONObject() : readBindingConfig(binding.getBindingConfig());
        BusinessFlowBindingCodec.ensureBusinessBinding(bindingConfig, runtimeConfig, documentConfig);
        String dtoFlowModelKey = StringUtils.trimToNull(dto.getFlowModelKey());
        String bindingConfigFlowModelKey = resolveFlowModelKey(bindingConfig);
        String bindingKey = binding == null ? null : StringUtils.trimToNull(binding.getBindingKey());
        String documentDefaultFlowKey = documentConfig == null ? null : StringUtils.trimToNull(documentConfig.getDefaultFlowKey());
        String flowModelKey = StringUtils.firstNonBlank(
                dtoFlowModelKey,
                bindingConfigFlowModelKey,
                bindingKey,
                documentDefaultFlowKey);
        if (StringUtils.isBlank(flowModelKey)) {
            log.warn("[低代码流程启动] 主流程解析失败: tenantId={}, objectCode={}, recordId={}, checkPermission={}, " +
                            "configKey={}, documentConfigId={}, documentEnabled={}, documentDefaultFlowKey={}, " +
                            "runtimeConfigId={}, runtimeConfigKey={}, binding={}, dtoFlowModelKey={}, " +
                            "bindingConfigFlowModelKey={}, bindingConfigPreview={}",
                    tenantId, objectCode, dto.getRecordId(), checkPermission, configKey,
                    documentConfig == null ? null : documentConfig.getId(),
                    documentConfig == null ? null : documentConfig.getDocumentEnabled(),
                    documentDefaultFlowKey,
                    runtimeConfig == null ? null : runtimeConfig.getId(),
                    runtimeConfig == null ? null : runtimeConfig.getConfigKey(),
                    describeBinding(binding), dtoFlowModelKey, bindingConfigFlowModelKey, previewBindingConfig(bindingConfig));
            throw new BusinessException("请先在流程与自动化中配置主流程");
        }

        AiBusinessFlowInstanceLink latestLink = flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, businessKey);
        if (documentConfig != null) {
            BusinessDocumentConfigVO documentConfigVO = documentConfigService.toVO(documentConfig, runtimeConfig, binding);
            documentRuntimeService.validateStartAllowed(
                    objectCode, dto.getRecordId(), documentConfigVO, recordData, latestLink, checkPermission);
        }
        if (isRunningFlowLink(latestLink)) {
            return toRuntimeVO(latestLink, "当前单据已有流转中的流程");
        }
        log.info("[低代码流程启动] 主流程解析成功: tenantId={}, objectCode={}, recordId={}, configKey={}, " +
                        "flowModelKey={}, bindingId={}, bindingType={}",
                tenantId, objectCode, dto.getRecordId(), configKey, flowModelKey,
                binding == null ? null : binding.getId(), binding == null ? null : binding.getBindingType());

        BusinessFlowStartContextAssembler.StartContext startContext = START_CONTEXT_ASSEMBLER.assemble(
                bindingConfig, recordData, dto.getVariables(), objectCode);
        Map<String, Object> flowVariables = startContext.variables();
        flowVariables.put("objectCode", objectCode);
        flowVariables.put("configKey", configKey);
        flowVariables.put("recordId", dto.getRecordId());
        flowVariables.put("businessKey", businessKey);
        String flowBusinessKey = stableBusinessKey
                ? businessKey : resolveFlowBusinessKeyForStart(businessKey, latestLink);
        flowVariables.put("documentBusinessKey", businessKey);
        flowVariables.put("recordBusinessKey", businessKey);
        flowVariables.put("flowBusinessKey", flowBusinessKey);

        String userName = StringUtils.defaultIfBlank(starterUserName, resolveUsername());
        String title = START_CONTEXT_ASSEMBLER.renderTitle(
                StringUtils.defaultIfBlank(dto.getTitle(), startContext.title()),
                recordData,
                objectCode,
                userName,
                formAssetAssembler.resolveRuntimeCrudObjectName(null, runtimeConfig));
        Long userId = starterUserId != null ? starterUserId : resolveUserId();
        FlowResult<String> result = stableBusinessKey
                ? flowClient.startProcessForDelegatedUser(
                        flowModelKey, flowBusinessKey, objectCode, title, flowVariables)
                : flowClient.startProcess(
                        flowModelKey, flowBusinessKey, title, flowVariables,
                        userId == null ? null : String.valueOf(userId), userName, null, null);
        if (result == null || !result.isSuccess() || StringUtils.isBlank(result.getData())) {
            throw new BusinessException("流程发起失败: " + (result == null ? "无返回结果" : result.getMsg()));
        }

        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setTenantId(tenantId);
        link.setObjectCode(objectCode);
        link.setRecordId(dto.getRecordId());
        link.setBusinessKey(businessKey);
        link.setFlowModelKey(flowModelKey);
        link.setProcessInstanceId(result.getData());
        link.setFlowStatus(BusinessDocumentFlowStatus.RUNNING.getCode());
        link.setStartUserId(userId);
        link.setStartTime(LocalDateTime.now());
        link.setRoundNo(resolveNextRoundNo(latestLink));
        link.setVariablesSnapshot(JSON.toJSONString(flowVariables));
        flowInstanceLinkMapper.insert(link);

        AiCrudConfig statusRuntimeConfig = runtimeConfig != null
                ? runtimeConfig : resolvePublishedRuntimeConfig(tenantId, objectCode);
        if (StringUtils.isBlank(statusRepairService.configuredStatusField(dto.getVariables()))) {
            statusTransitionService.updateBusinessFlowStatus(
                    documentConfig, runtimeConfig, bindingConfig, dto.getRecordId(),
                    BusinessDocumentFlowStatus.IN_PROCESS.getCode());
        }
        statusRepairService.syncConfiguredStatusField(statusRuntimeConfig,
                dto.getRecordId(), dto.getVariables(), BusinessDocumentFlowStatus.IN_PROCESS.getCode());
        return toRuntimeVO(link, "流程已发起");
    }

    private boolean isRunningFlowLink(AiBusinessFlowInstanceLink link) {
        if (link == null) {
            return false;
        }
        return BusinessDocumentFlowStatus.STARTED.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.RUNNING.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.IN_PROCESS.matches(link.getFlowStatus())
                || BusinessDocumentFlowStatus.NEED_MODIFY.matches(link.getFlowStatus())
                || (link.getEndTime() == null && StringUtils.isBlank(link.getResult()));
    }

    private BusinessFlowRuntimeVO executeWithFlowStartLock(Long tenantId,
                                                           String businessKey,
                                                           Supplier<BusinessFlowRuntimeVO> supplier) {
        return startLockManager.execute(tenantId, businessKey, redissonClientProvider, supplier);
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


    private AiBusinessFlowInstanceLink findRuntimeLink(Long tenantId, String processInstanceId, String businessKey) {
        if (StringUtils.isNotBlank(processInstanceId)) {
            AiBusinessFlowInstanceLink link = flowInstanceLinkMapper.selectByProcessInstanceId(tenantId, processInstanceId);
            if (link != null) {
                return link;
            }
        }
        if (StringUtils.isNotBlank(businessKey)) {
            return flowInstanceLinkMapper.selectLatestByBusinessKey(tenantId, businessKey);
        }
        return null;
    }

    private AiCrudConfig resolvePublishedRuntimeConfig(Long tenantId, String objectCodeOrConfigKey) {
        return runtimeConfigResolver.published(tenantId, objectCodeOrConfigKey);
    }

    private AiCrudConfig resolveRuntimeConfig(Long tenantId, String objectCodeOrConfigKey) {
        return runtimeConfigResolver.runtime(tenantId, objectCodeOrConfigKey);
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

    private AiBusinessBinding selectFlowBindingForStart(Long tenantId, String objectCode, String... fallbackCodes) {
        BindingLookupResult result = selectMainFlowBinding(tenantId, objectCode, fallbackCodes);
        if (isBindingEnabled(result.binding())) {
            if ("APPROVAL".equalsIgnoreCase(result.binding().getBindingType())) {
                log.info("[低代码流程启动] 使用历史审批绑定作为主流程: tenantId={}, objectCode={}, binding={}",
                        tenantId, result.matchedObjectCode(), describeBinding(result.binding()));
            }
            return result.binding();
        }
        if (result.binding() != null) {
            log.warn("[低代码流程启动] 未找到启用的主流程绑定: tenantId={}, objectCodes={}, binding={}",
                    tenantId, result.candidates(), describeBinding(result.binding()));
        } else {
            log.warn("[低代码流程启动] 未找到主流程绑定记录: tenantId={}, objectCodes={}", tenantId, result.candidates());
        }
        return null;
    }

    private AiBusinessBinding selectMainFlowBindingForConfig(Long tenantId, String objectCode, String... fallbackCodes) {
        return selectMainFlowBinding(tenantId, objectCode, fallbackCodes).binding();
    }

    private BindingLookupResult selectMainFlowBinding(Long tenantId, String objectCode, String... fallbackCodes) {
        List<String> candidates = objectCodeCandidates(objectCode, fallbackCodes);
        AiBusinessBinding firstDisabled = null;
        String disabledObjectCode = null;
        for (String candidate : candidates) {
            AiBusinessBinding binding = bindingMapper.selectBindingByTypeAndCode(tenantId, "OBJECT", candidate, "FLOW");
            if (isBindingEnabled(binding)) {
                return new BindingLookupResult(binding, candidate, candidates);
            }
            if (firstDisabled == null && binding != null) {
                firstDisabled = binding;
                disabledObjectCode = candidate;
            }
            AiBusinessBinding legacyApprovalBinding = bindingMapper.selectBindingByTypeAndCode(
                    tenantId, "OBJECT", candidate, "APPROVAL");
            if (isBindingEnabled(legacyApprovalBinding)) {
                return new BindingLookupResult(legacyApprovalBinding, candidate, candidates);
            }
            if (firstDisabled == null && legacyApprovalBinding != null) {
                firstDisabled = legacyApprovalBinding;
                disabledObjectCode = candidate;
            }
        }
        return new BindingLookupResult(firstDisabled, disabledObjectCode, candidates);
    }

    private List<String> objectCodeCandidates(String objectCode, String... fallbackCodes) {
        Set<String> candidates = new LinkedHashSet<>();
        addObjectCodeCandidate(candidates, objectCode);
        if (fallbackCodes != null) {
            for (String fallbackCode : fallbackCodes) {
                addObjectCodeCandidate(candidates, fallbackCode);
            }
        }
        return new ArrayList<>(candidates);
    }

    private void addObjectCodeCandidate(Set<String> candidates, String objectCode) {
        String normalized = StringUtils.trimToNull(objectCode);
        if (StringUtils.isNotBlank(normalized)) {
            candidates.add(normalized);
        }
    }

    private FlowStartContext resolveFlowStartContext(Long tenantId, String objectCodeOrConfigKey) {
        return resolveFlowStartContext(tenantId, objectCodeOrConfigKey, false);
    }

    private FlowStartContext resolveFlowStartContext(Long tenantId, String objectCodeOrConfigKey, boolean allowDraftRuntime) {
        BusinessRuntimeContext context = businessRuntimeContextResolver.resolve(
                tenantId, objectCodeOrConfigKey, allowDraftRuntime);
        String configKey = resolveStartConfigKey(
                context.documentConfig(), context.runtimeConfig(), context.configKey(), allowDraftRuntime);
        return new FlowStartContext(
                context.requestedObjectCode(),
                context.objectCode(),
                configKey,
                context.documentConfig(),
                context.runtimeConfig());
    }


    private boolean isBindingEnabled(AiBusinessBinding binding) {
        return binding != null && !EnableStatus.DISABLED.matches(binding.getStatus());
    }

    private String describeBinding(AiBusinessBinding binding) {
        if (binding == null) {
            return "null";
        }
        return "id=" + binding.getId()
                + ", type=" + binding.getBindingType()
                + ", status=" + binding.getStatus()
                + ", targetCode=" + binding.getTargetCode()
                + ", bindingKey=" + binding.getBindingKey()
                + ", bindingName=" + binding.getBindingName()
                + ", configBlank=" + StringUtils.isBlank(binding.getBindingConfig());
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

    /**
     * 查询流程状态
     */
    public JSONObject getFlowStatus(String businessKey) {
        try {
            FlowResult<Map<String, Object>> result = flowClient.getProcessStatus(businessKey);
            if (result.isSuccess()) {
                JSONObject status = new JSONObject();
                status.put("businessKey", businessKey);
                status.put("data", result.getData());
                return status;
            }
        } catch (Exception e) {
            log.debug("查询流程状态失败: businessKey={}", businessKey);
        }
        return null;
    }

    private String resolveBusinessSummary(BusinessObjectVO object,
                                          TaskFormRuntimeContext runtime,
                                          Map<String, Object> recordData) {
        if (recordData == null || recordData.isEmpty()) {
            return null;
        }
        JSONObject objectOptions = object == null ? new JSONObject() : readJsonObject(object.getOptions());
        JSONObject designerOptions = object == null ? new JSONObject() : readJsonObject(object.getDesignerOptions());
        String template = StringUtils.firstNonBlank(
                StringUtils.trimToNull(objectOptions.getString("summaryExpression")),
                StringUtils.trimToNull(designerOptions.getString("summaryExpression")),
                runtime.bindingConfig() == null ? null : StringUtils.trimToNull(runtime.bindingConfig().getString("titleTemplate")));
        if (StringUtils.isNotBlank(template)) {
            String resolved = StringUtils.trimToNull(
                    BusinessApprovalTitleRenderer.render(template, recordData, Map.of(), null));
            if (resolved != null) {
                return resolved;
            }
        }
        String displayField = object == null ? null : StringUtils.trimToNull(object.getDisplayField());
        Object displayValue = displayField == null ? null : read(recordData, displayField);
        if (displayValue != null && StringUtils.isNotBlank(String.valueOf(displayValue))) {
            return String.valueOf(displayValue);
        }
        for (String field : List.of("orderNo", "businessNo", "title", "name", "code")) {
            Object value = read(recordData, field);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private BusinessFlowBindingVO toVO(String objectCode, AiBusinessBinding binding) {
        JSONObject config = readBindingConfig(binding.getBindingConfig());
        ensureBusinessBinding(config, binding.getTenantId(), objectCode);
        BusinessFlowBindingVO vo = new BusinessFlowBindingVO();
        vo.setBindingId(binding.getId());
        vo.setObjectCode(objectCode);
        vo.setFlowModelKey(StringUtils.defaultIfBlank(resolveFlowModelKey(config), binding.getBindingKey()));
        vo.setFlowModelName(StringUtils.defaultIfBlank(config.getString("flowModelName"), binding.getBindingName()));
        vo.setTitleTemplate(config.getString("titleTemplate"));
        vo.setStartMode(normalizeStartMode(config.getString("startMode")));
        vo.setBusinessBinding(toBusinessBindingDTO(config.getJSONObject("businessBinding")));
        vo.setVariableMapping(normalizeVariableMapping(config.getJSONArray("variableMapping")));
        vo.setNodeForms(normalizeNodeForms(readMapList(config.getJSONArray("nodeForms"))));
        vo.setConditionFlows(readMapList(config.getJSONArray("conditionFlows")));
        vo.setOptions(readOptions(config.getJSONObject("options")));
        vo.setStatus(binding.getStatus());
        enrichBindingSummary(vo, "AI_BUSINESS_BINDING");
        return vo;
    }

    private BusinessFlowBindingVO legacyDocumentFlowToVO(String objectCode, AiBusinessDocumentConfig documentConfig) {
        BusinessFlowBindingVO vo = new BusinessFlowBindingVO();
        vo.setObjectCode(objectCode);
        vo.setFlowModelKey(documentConfig.getDefaultFlowKey());
        vo.setFlowModelName(documentConfig.getDefaultFlowKey());
        vo.setStartMode("MANUAL");
        vo.setBusinessBinding(defaultBusinessBinding(null, documentConfig));
        vo.setStatus(EnableStatus.ENABLED.getCode());
        vo.setCompatibilitySource("DOCUMENT_DEFAULT_FLOW");
        vo.setComplete(false);
        vo.setGaps(List.of("历史默认流程缺少变量映射，请在流程与自动化中保存一次主流程"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("configured", true);
        summary.put("flowModelKey", documentConfig.getDefaultFlowKey());
        summary.put("flowModelName", documentConfig.getDefaultFlowKey());
        summary.put("startMode", "MANUAL");
        summary.put("businessBinding", vo.getBusinessBinding());
        summary.put("variableMappingCount", 0);
        summary.put("complete", false);
        summary.put("gaps", vo.getGaps());
        summary.put("compatibilitySource", "DOCUMENT_DEFAULT_FLOW");
        vo.setMainFlowSummary(summary);
        return vo;
    }

    private void enrichBindingSummary(BusinessFlowBindingVO vo, String compatibilitySource) {
        List<String> gaps = new ArrayList<>();
        if (StringUtils.isBlank(vo.getFlowModelKey())) {
            gaps.add("未配置主流程");
        }
        if (StringUtils.isBlank(vo.getStartMode())) {
            gaps.add("发起方式未配置");
        }
        if (vo.getVariableMapping() == null || vo.getVariableMapping().isEmpty()) {
            gaps.add("变量映射缺失");
        }
        boolean complete = gaps.isEmpty();
        vo.setComplete(complete);
        vo.setGaps(gaps);
        vo.setCompatibilitySource(compatibilitySource);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("configured", StringUtils.isNotBlank(vo.getFlowModelKey()));
        summary.put("bindingId", vo.getBindingId());
        summary.put("flowModelKey", vo.getFlowModelKey());
        summary.put("flowModelName", vo.getFlowModelName());
        summary.put("startMode", vo.getStartMode());
        summary.put("businessBinding", vo.getBusinessBinding());
        summary.put("variableMappingCount", vo.getVariableMapping() == null ? 0 : vo.getVariableMapping().size());
        summary.put("complete", complete);
        summary.put("gaps", gaps);
        summary.put("compatibilitySource", compatibilitySource);
        vo.setMainFlowSummary(summary);
    }


    private void ensureBusinessBinding(JSONObject config, Long tenantId, String objectCode) {
        if (config == null) {
            return;
        }
        AiCrudConfig runtimeConfig = resolvePublishedRuntimeConfig(tenantId, objectCode);
        AiBusinessDocumentConfig documentConfig = businessRuntimeContextResolver.resolveEnabledDocumentConfig(
                tenantId, objectCode, runtimeConfig);
        BusinessFlowBindingCodec.ensureBusinessBinding(config, runtimeConfig, documentConfig);
    }

    private AiCrudConfig resolveRuntimeConfigForBusinessForm(BusinessObjectVO object, String configKey) {
        String lookupKey = StringUtils.firstNonBlank(
                StringUtils.trimToNull(configKey),
                object == null ? null : StringUtils.trimToNull(object.getConfigKey()),
                object == null ? null : StringUtils.trimToNull(object.getObjectCode()));
        return resolvePublishedRuntimeConfig(resolveTenantId(), lookupKey);
    }




    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        if (tenantId == null) {
            tenantId = TenantContextHolder.getTenantId();
        }
        return tenantId != null ? tenantId : 1L;
    }

    private Long resolveUserId() {
        try {
            return SessionHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveActiveOrgId() {
        try {
            return SessionHelper.getActiveOrgId();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUsername() {
        try {
            var loginUser = SessionHelper.getLoginUser();
            if (loginUser != null) {
                return StringUtils.firstNonBlank(loginUser.getRealName(), loginUser.getUsername());
            }
            return SessionHelper.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    private record FlowStartContext(String requestedObjectCode,
                                    String objectCode,
                                    String configKey,
                                    AiBusinessDocumentConfig documentConfig,
                                    AiCrudConfig runtimeConfig) {
    }

    private record BindingLookupResult(AiBusinessBinding binding,
                                       String matchedObjectCode,
                                       List<String> candidates) {
    }

    private record TaskFormSaveResult(TaskFormRuntimeContext runtime,
                                      BusinessTaskFormContextVO context) {
    }

}
