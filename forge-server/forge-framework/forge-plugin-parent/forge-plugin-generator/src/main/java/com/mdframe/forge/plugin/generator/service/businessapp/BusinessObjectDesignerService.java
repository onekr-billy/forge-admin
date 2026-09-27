package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectRelation;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessSuite;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeDomain;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeModel;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.domain.entity.GenDatasource;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignerDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectRelationDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.LinkageSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.ViewSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAuditStrategy;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeDomainRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeObjectSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRuntimeDatasourceSnapshot;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeModelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectDesignVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectRelationMapper;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.service.IGenDatasourceService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDomainService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeComponentCatalog;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDdlService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeModelSchemaNormalizer;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeSchemaValidator;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectDesignerVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectRelationVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 业务对象设计器聚合服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessObjectDesignerService implements BusinessObjectDesignContextProvider {

    private static final Logger log = LoggerFactory.getLogger(BusinessObjectDesignerService.class);
    private static final String GENERAL_DOMAIN_CODE = "general";
    /** 子表组件自动创建关系的标记，用于识别可随表单配置全量覆盖/删除的关系。 */
    private static final String AUTO_SUBTABLE_RELATION_DESC = "表单子表组件自动创建";
    private static final String FORM_DESIGNER_SCHEMA_OPTION_KEY = "formDesignerSchema";
    private static final String DESIGNER_ACTIONS_OPTION_KEY = "actions";
    private static final String VIEW_SCHEMA_OPTION_KEY = "viewSchema";
    private static final String LINKAGE_SCHEMA_OPTION_KEY = "linkageSchema";
    private static final String LINKAGE_SCHEMA_MANAGED_BY = "linkageSchema";
    private static final String OBJECT_OPTION_RUNTIME_DATASOURCE_ID = "runtimeDatasourceId";
    private static final String OBJECT_OPTION_RUNTIME_DATASOURCE = "runtimeDatasource";
    private static final Set<String> FORM_FIELD_COMPONENT_KEYS = LowcodeComponentCatalog.FIELD_COMPONENT_KEYS;
    private static final Set<String> DICT_FIELD_TYPES = Set.of("DICT", "RADIO", "CHECKBOX", "MULTI_SELECT");
    private static final Set<String> DICT_COMPONENT_TYPES = Set.of(
            "dictSelect", "select", "radio", "radioButton", "checkbox", "transfer", "cascader", "customSelect");
    private static final Map<String, String> PAGE_ZONE_ALIASES = Map.ofEntries(
            Map.entry("search", "search"),
            Map.entry("search-form", "search"),
            Map.entry("query", "search"),
            Map.entry("filter", "search"),
            Map.entry("table", "table"),
            Map.entry("data-table", "table"),
            Map.entry("list", "table"),
            Map.entry("grid", "table"),
            Map.entry("edit", "edit"),
            Map.entry("edit-form", "edit"),
            Map.entry("form", "edit"),
            Map.entry("create", "edit"),
            Map.entry("update", "edit"),
            Map.entry("detail", "detail"),
            Map.entry("detail-view", "detail"),
            Map.entry("view", "detail"),
            Map.entry("toolbar", "toolbar"),
            Map.entry("table-toolbar", "toolbar"),
            Map.entry("actions", "toolbar")
    );
    private static final Map<String, String> PAGE_ZONE_COMPONENTS = Map.of(
            "search", "search-form",
            "table", "data-table",
            "edit", "edit-form",
            "detail", "detail-view",
            "toolbar", "table-toolbar"
    );
    private static final Map<String, ComponentFieldDefaults> COMPONENT_FIELD_DEFAULTS = Map.ofEntries(
            Map.entry("input", new ComponentFieldDefaults("TEXT", "varchar", 128, 2, "like")),
            Map.entry("barcodeScanner", new ComponentFieldDefaults("TEXT", "varchar", 2048, 2, "eq")),
            Map.entry("textarea", new ComponentFieldDefaults("MULTILINE", "text", null, 2, "like")),
            Map.entry("number", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("inputNumber", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("input-number", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("inputnumber", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("integer", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("money", new ComponentFieldDefaults("MONEY", "decimal", 18, 2, "eq")),
            Map.entry("slider", new ComponentFieldDefaults("NUMBER", "int", 11, 0, "eq")),
            Map.entry("rate", new ComponentFieldDefaults("NUMBER", "decimal", 4, 1, "eq")),
            Map.entry("color", new ComponentFieldDefaults("TEXT", "varchar", 32, 2, "eq")),
            Map.entry("date", new ComponentFieldDefaults("DATE", "date", null, null, "eq")),
            Map.entry("datetime", new ComponentFieldDefaults("DATETIME", "datetime", null, null, "eq")),
            Map.entry("daterange", new ComponentFieldDefaults("TEXT", "text", null, null, "eq")),
            Map.entry("datetimerange", new ComponentFieldDefaults("TEXT", "text", null, null, "eq")),
            Map.entry("month", new ComponentFieldDefaults("TEXT", "varchar", 7, null, "eq")),
            Map.entry("year", new ComponentFieldDefaults("TEXT", "varchar", 4, null, "eq")),
            Map.entry("switch", new ComponentFieldDefaults("SWITCH", "tinyint", 1, 0, "eq")),
            Map.entry("select", new ComponentFieldDefaults("DICT", "varchar", 64, 2, "eq")),
            Map.entry("dictSelect", new ComponentFieldDefaults("DICT", "varchar", 64, 2, "eq")),
            Map.entry("radio", new ComponentFieldDefaults("RADIO", "varchar", 64, 2, "eq")),
            Map.entry("radioButton", new ComponentFieldDefaults("RADIO", "varchar", 64, 2, "eq")),
            Map.entry("checkbox", new ComponentFieldDefaults("CHECKBOX", "varchar", 255, 2, "in")),
            Map.entry("transfer", new ComponentFieldDefaults("MULTI_SELECT", "text", null, null, "in")),
            Map.entry("cascader", new ComponentFieldDefaults("DICT", "varchar", 128, 2, "eq")),
            Map.entry("treeSelect", new ComponentFieldDefaults("SELECT", "bigint", null, null, "eq")),
            Map.entry("customSelect", new ComponentFieldDefaults("SELECT", "varchar", 128, 2, "eq")),
            Map.entry("regionTreeSelect", new ComponentFieldDefaults("REGION", "varchar", 32, 2, "eq")),
            Map.entry("orgTreeSelect", new ComponentFieldDefaults("DEPT", "bigint", null, null, "eq")),
            Map.entry("userSelect", new ComponentFieldDefaults("USER", "bigint", null, null, "eq")),
            Map.entry("fileUpload", new ComponentFieldDefaults("FILE", "varchar", 512, 2, "eq")),
            Map.entry("imageUpload", new ComponentFieldDefaults("IMAGE", "varchar", 512, 2, "eq")),
            Map.entry("objectReference", new ComponentFieldDefaults("REFERENCE", "bigint", null, null, "eq")),
            Map.entry("recordSelector", new ComponentFieldDefaults("RECORD_SELECTOR", "bigint", null, null, "eq")),
            Map.entry("text", new ComponentFieldDefaults("TEXT", "varchar", 255, 2, "like")),
            Map.entry("timerange", new ComponentFieldDefaults("TEXT", "text", null, null, "eq")),
            Map.entry("time", new ComponentFieldDefaults("TEXT", "varchar", 32, null, "eq"))
    );
    private static final Set<String> BUSINESS_COMPONENT_TYPES = Set.of(
            "select", "dictSelect", "radio", "checkbox", "cascader",
            "regionTreeSelect", "orgTreeSelect", "userSelect",
            "fileUpload", "imageUpload", "objectReference", "recordSelector"
    );
    private static final Set<String> GENERIC_COMPONENT_TYPES = Set.of(
            "", "input", "textarea", "number", "inputNumber", "input-number", "inputnumber", "integer"
    );
    private static final Set<String> PRESERVED_BASIC_PROP_KEYS = Set.of(
            "dictType", "options", "recordSelector", "generation", "cascade", "cascadeConfig",
            "referenceObjectCode", "referenceDisplayField", "targetObjectCode", "targetLabelField",
            "labelField", "valueField", "fieldMappings", "searchParams", "keywordFields", "displayFields",
            "placeholder", "clearable", "filterable", "multiple", "validation"
    );
    private static final Set<String> PRESERVED_ADVANCED_PROP_KEYS = Set.of(
            "dictType", "recordSelector", "generation", "referenceObjectCode", "referenceDisplayField", "validation"
    );

    private final ObjectMapper objectMapper;
    private final BusinessObjectService objectService;
    private final BusinessSuiteService suiteService;
    private final AiLowcodeModelMapper lowcodeModelMapper;
    private final AiCrudConfigMapper crudConfigMapper;
    private final BusinessAppMapper businessAppMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessObjectRelationMapper relationMapper;
    private final BusinessObjectDesignVersionMapper designVersionMapper;
    private final AiCrudConfigService crudConfigService;
    private final LowcodeDomainService domainService;
    private final LowcodeDdlService ddlService;
    private final LowcodeModelSchemaNormalizer schemaNormalizer;
    private final LowcodeSchemaValidator schemaValidator;
    private final IGenDatasourceService datasourceService;
    private final LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver;
    private final BusinessFieldSchemaService fieldSchemaService;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessAppService businessAppService;
    private final BusinessApplicationChangeTracker applicationChangeTracker;
    private final PlatformTransactionManager transactionManager;
    /** 同对象 designPreview/发布准备串行化，避免并发写 ai_business_object_relation 锁等待。 */
    private final ConcurrentHashMap<Long, DraftPreparationLock> prepareRuntimeDraftLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CachedPreviewDraft> previewDraftCache = new ConcurrentHashMap<>();
    private static final long PREVIEW_DRAFT_CACHE_TTL_MS = 8_000L;

    public BusinessObjectDesignerVO getDesigner(Long objectId) {
        DesignerContext context = loadContext(objectId);
        applyRelationsToModel(context);
        BusinessObjectDesignerVO vo = new BusinessObjectDesignerVO();
        BusinessObjectVO objectVO = context.getObjectVO();
        AiBusinessObject object = context.getObject();
        vo.setObjectId(object.getId());
        vo.setSuiteCode(object.getSuiteCode());
        vo.setSuiteName(objectVO == null ? null : objectVO.getSuiteName());
        vo.setObjectCode(object.getObjectCode());
        vo.setObjectName(object.getObjectName());
        vo.setObjectType(object.getObjectType());
        vo.setConfigId(context.getConfig() == null ? null : context.getConfig().getId());
        vo.setConfigKey(context.getConfig() == null ? object.getConfigKey() : context.getConfig().getConfigKey());
        vo.setDisplayField(object.getDisplayField());
        vo.setIcon(object.getIcon());
        vo.setDescription(object.getDescription());
        vo.setStatus(object.getStatus());
        vo.setDesignStatus(resolveDesignStatus(object, context.getConfig()));
        vo.setPublishStatus(context.getConfig() == null ? "DRAFT" : context.getConfig().getPublishStatus());
        vo.setHasUnpublishedChanges(hasUnpublishedChanges(object, context.getConfig()));
        vo.setLastPublishVersion(object.getLastPublishVersion());
        vo.setLastPublishTime(object.getLastPublishTime());
        vo.setUpdateTime(object.getUpdateTime());
        vo.setModelSchema(context.getModelSchema());
        vo.setPageSchema(context.getPageSchema());
        vo.setFields(fieldSchemaService.toFieldVOList(context.getModelSchema()));
        vo.setRelations(sourceRelations(object, context.getRelations()));
        Map<String, Object> designerOptions = resolveDesignerOptions(object, context.getConfig());
        vo.setDesignerOptions(designerOptions);
        LinkageSchemaDTO linkageSchema = resolveLinkageSchema(designerOptions);
        FormDesignerSchemaDTO formDesignerSchema = resolveFormDesignerSchema(object, context.getModelSchema(),
                context.getPageSchema(), designerOptions);
        vo.setFormDesignerSchema(hydrateFormFieldLinkages(formDesignerSchema, linkageSchema));
        vo.setViewSchema(resolveViewSchema(context.getModelSchema(), context.getPageSchema(), designerOptions));
        vo.setLinkageSchema(linkageSchema);
        vo.setDocumentConfig(documentConfigService.getConfig(object.getId()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public DesignerContext saveDesigner(Long objectId, BusinessObjectDesignerDTO dto) {
        DesignerContext context = loadContext(objectId);
        AiBusinessObject object = context.getObject();
        if (dto != null) {
            applyObjectFields(object, dto);
            if (dto.getModelSchema() != null) {
                context.setModelSchema(enrichModelSchema(object, dto.getModelSchema()));
            } else if (dto.getFields() != null && !dto.getFields().isEmpty()) {
                context.setModelSchema(rebuildModelFields(context.getModelSchema(), dto.getFields()));
            }
            if (dto.getPageSchema() != null) {
                context.setPageSchema(ensurePageSchema(dto.getPageSchema(), context.getModelSchema()));
            }
            Map<String, Object> designerOptions = resolveDesignerOptions(object, context.getConfig());
            if (dto.getDesignerOptions() != null && !dto.getDesignerOptions().isEmpty()) {
                designerOptions.putAll(dto.getDesignerOptions());
            }
            boolean linkageSynchronizedFromForm = false;
            if (dto.getFormDesignerSchema() != null) {
                designerOptions.put(FORM_DESIGNER_SCHEMA_OPTION_KEY, dto.getFormDesignerSchema());
                if (hasFormFieldLinkages(dto.getFormDesignerSchema())) {
                    LinkageSchemaDTO legacyLinkage = dto.getLinkageSchema() == null
                            ? resolveLinkageSchema(designerOptions)
                            : dto.getLinkageSchema();
                    designerOptions.put(LINKAGE_SCHEMA_OPTION_KEY,
                            resolveUnifiedLinkageSchema(dto.getFormDesignerSchema(), legacyLinkage));
                    linkageSynchronizedFromForm = true;
                }
            }
            if (dto.getViewSchema() != null) {
                designerOptions.put(VIEW_SCHEMA_OPTION_KEY, dto.getViewSchema());
            }
            if (dto.getLinkageSchema() != null && !linkageSynchronizedFromForm) {
                designerOptions.put(LINKAGE_SCHEMA_OPTION_KEY, dto.getLinkageSchema());
            }
            if (!designerOptions.isEmpty()) {
                object.setDesignerOptions(writeJson(designerOptions, "designerOptions"));
            }
            if (dto.getRelations() != null) {
                saveSourceRelations(object, sourceRelationDTOs(object, dto.getRelations()));
                context.setRelations(relationMapper.selectRelationsByObject(
                        resolveTenantId(), object.getSuiteCode(), object.getObjectCode()));
                applyRelationsToModel(context);
            }
            boolean childRelationsCreated = ensureChildTableRelations(context, dto.getFormDesignerSchema());
            log.info("[子表自动关联] objectId={} formSchema.components.size={} childRelationsCreated={}",
                    objectId,
                    dto.getFormDesignerSchema() == null ? 0
                            : (dto.getFormDesignerSchema().getComponents() == null ? 0
                            : dto.getFormDesignerSchema().getComponents().size()),
                    childRelationsCreated);
            if (childRelationsCreated) {
                context.setRelations(relationMapper.selectRelationsByObject(
                        resolveTenantId(), object.getSuiteCode(), object.getObjectCode()));
                applyRelationsToModel(context);
            }
            if (context.getPageSchema() != null && context.getPageSchema().getModelRefs() != null) {
                log.info("[子表自动关联] pageSchema.modelRefs.size={} refs={}",
                        context.getPageSchema().getModelRefs().size(),
                        context.getPageSchema().getModelRefs().stream()
                                .map(r -> r.getModelCode() + ":" + (r.getRelations() == null ? 0 : r.getRelations().size()))
                                .toList());
            }
        }
        saveDraft(context, BusinessObjectDesignStatus.CHANGED.getCode());
        return context;
    }

    @Override
    public DesignerContext loadContext(Long objectId) {
        AiBusinessObject object = objectService.requireEntity(objectId);
        BusinessObjectVO objectVO = objectService.detail(objectId);
        AiCrudConfig config = resolveConfig(object);
        AiLowcodeModel model = resolveModel(object, config);
        LowcodeModelSchema modelSchema = resolveModelSchema(object, model, config);
        LowcodePageSchema pageSchema = resolvePageSchema(config, modelSchema);
        List<BusinessObjectRelationVO> relations = relationMapper.selectRelationsByObject(
                resolveTenantId(), object.getSuiteCode(), object.getObjectCode());

        DesignerContext context = new DesignerContext();
        context.setObject(object);
        context.setObjectVO(objectVO);
        context.setModel(model);
        context.setConfig(config);
        context.setModelSchema(modelSchema);
        context.setPageSchema(pageSchema);
        context.setRelations(relations);
        return context;
    }

    private List<BusinessObjectRelationVO> sourceRelations(AiBusinessObject object, List<BusinessObjectRelationVO> relations) {
        if (object == null || relations == null) {
            return new ArrayList<>();
        }
        return relations.stream()
                .filter(relation -> relation != null
                        && StringUtils.equals(object.getObjectCode(), relation.getSourceObjectCode()))
                .toList();
    }

    private List<BusinessObjectRelationDTO> sourceRelationDTOs(AiBusinessObject object,
                                                               List<BusinessObjectRelationDTO> relations) {
        if (object == null || relations == null) {
            return new ArrayList<>();
        }
        return relations.stream()
                .filter(relation -> relation != null
                        && (StringUtils.isBlank(relation.getSourceObjectCode())
                        || StringUtils.equals(object.getObjectCode(), relation.getSourceObjectCode())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public DesignerContext saveDraft(DesignerContext context, String designStatus) {
        return saveDraft(context, designStatus, true);
    }

    /**
     * 保存运行草稿。预览/发布前的运行时物化只同步派生配置，不应把应用重新标记为
     * “有未发布变更”；真正的设计器保存仍通过默认的 {@code markApplicationChanged=true}
     * 分支传播变更状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public DesignerContext saveDraft(DesignerContext context, String designStatus,
                                     boolean markApplicationChanged) {
        if (context == null || context.getObject() == null) {
            throw new BusinessException("业务对象设计上下文不能为空");
        }
        AiBusinessObject object = context.getObject();
        LowcodeModelSchema modelSchema = enrichModelSchema(object, context.getModelSchema());
        LowcodePageSchema pageSchema = ensurePageSchema(context.getPageSchema(), modelSchema);
        context.setModelSchema(modelSchema);
        context.setPageSchema(pageSchema);
        compileFormFirstRuntimeSchema(context);
        // compileFormFirstRuntimeSchema 内部已完成 enrich + ensure，
        // 直接读取结果无需重复调用（原二次 enrichModelSchema 已删除）
        modelSchema = context.getModelSchema();
        pageSchema = ensurePageSchema(context.getPageSchema(), modelSchema);
        String preparedModelJson = writeJson(modelSchema, "modelSchema");
        String preparedPageJson = writeJson(pageSchema, "pageSchema");
        String normalizedStatus = BusinessObjectDesignStatus.normalize(designStatus);
        // 应用发布会先 prepareRuntimeDraft 再 object.publish→saveDraft(READY)。
        // schema 未变时跳过整份 model/page 大字段回写，只收敛设计状态。
        if (isStoredRuntimeDraftCurrent(context, preparedModelJson, preparedPageJson)) {
            boolean statusChanged = !StringUtils.equals(normalizedStatus, object.getDesignStatus());
            if (statusChanged) {
                object.setDesignStatus(normalizedStatus);
                if (StringUtils.isBlank(object.getDesignerOptions())) {
                    object.setDesignerOptions("{}");
                }
                businessObjectMapper.updateById(object);
            }
            if (markApplicationChanged) {
                applicationChangeTracker.markObjectChanged(object.getId());
            }
            context.setModelSchema(modelSchema);
            context.setPageSchema(pageSchema);
            if (statusChanged || markApplicationChanged) {
                invalidatePreviewDraftCache(object.getId());
            }
            return context;
        }
        validateDraft(modelSchema, pageSchema);
        AiLowcodeModel model = saveModelDraft(object, context.getModel(), modelSchema);
        AiCrudConfig config = saveRuntimeDraft(object, context.getConfig(), modelSchema, pageSchema);

        object.setModelId(model.getId());
        object.setModelCode(model.getModelCode());
        object.setConfigKey(config.getConfigKey());
        object.setDesignStatus(normalizedStatus);
        if (StringUtils.isBlank(object.getDesignerOptions())) {
            object.setDesignerOptions("{}");
        }
        businessObjectMapper.updateById(object);
        businessAppService.syncRuntimeAppsForObject(object.getSuiteCode(), object.getObjectCode(), config.getConfigKey());
        if (markApplicationChanged) {
            applicationChangeTracker.markObjectChanged(object.getId());
        }

        context.setModel(model);
        context.setConfig(config);
        context.setModelSchema(modelSchema);
        context.setPageSchema(pageSchema);
        invalidatePreviewDraftCache(object.getId());
        return context;
    }

    private boolean isStoredRuntimeDraftCurrent(DesignerContext context,
                                                String preparedModelJson,
                                                String preparedPageJson) {
        AiCrudConfig config = context.getConfig();
        AiLowcodeModel model = context.getModel();
        return config != null
                && model != null
                && config.getId() != null
                && model.getId() != null
                && StringUtils.equals(config.getModelSchema(), preparedModelJson)
                && StringUtils.equals(config.getPageSchema(), preparedPageJson)
                && StringUtils.equals(model.getModelSchema(), preparedModelJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiCrudConfig ensureRuntimeDraft(Long objectId) {
        return saveDraft(loadContext(objectId), BusinessObjectDesignStatus.CHANGED.getCode()).getConfig();
    }

    /**
     * 物化设计预览/发布前的运行草稿。
     *
     * <p>关系同步与草稿保存各自短事务提交；schema 编译在事务外执行，避免
     * {@code designPreview} 并发渲染时长时间持有 {@code ai_business_object_relation} 行锁
     * 触发 Lock wait timeout。</p>
     */
    public AiCrudConfig prepareRuntimeDraft(Long objectId) {
        return prepareRuntimeDraft(objectId, true);
    }

    /**
     * 设计预览专用：只编译当前草稿运行配置，不在 GET 渲染链路里同步写关系表，
     * 避免并发预览/保存时对 ai_business_object_relation 抢锁超时。
     * 子表关系仍由设计器保存和发布链路的 synchronizeFormChildRelations 负责落库。
     */
    public AiCrudConfig prepareRuntimeDraftForPreview(Long objectId) {
        if (objectId == null) {
            throw new BusinessException("业务对象ID不能为空");
        }
        long now = System.currentTimeMillis();
        CachedPreviewDraft hit = previewDraftCache.get(objectId);
        if (hit != null && hit.expiresAtMs > now) {
            return hit.config;
        }
        AiCrudConfig prepared = prepareRuntimeDraft(objectId, false);
        previewDraftCache.put(objectId, new CachedPreviewDraft(prepared, now + PREVIEW_DRAFT_CACHE_TTL_MS));
        return prepared;
    }

    /**
     * 物化设计预览/发布前的运行草稿。
     *
     * <p>关系同步与草稿保存各自短事务提交；schema 编译在事务外执行，避免
     * {@code designPreview} 并发渲染时长时间持有 {@code ai_business_object_relation} 行锁
     * 触发 Lock wait timeout。预览路径可跳过关系写入。</p>
     */
    public AiCrudConfig prepareRuntimeDraft(Long objectId, boolean persistChildRelations) {
        if (objectId == null) {
            throw new BusinessException("业务对象ID不能为空");
        }
        DraftPreparationLock lock = acquirePrepareRuntimeDraftLock(objectId);
        lock.lock.lock();
        try {
            return doPrepareRuntimeDraft(objectId, persistChildRelations);
        } finally {
            lock.lock.unlock();
            releasePrepareRuntimeDraftLock(objectId, lock);
        }
    }

    private DraftPreparationLock acquirePrepareRuntimeDraftLock(Long objectId) {
        return prepareRuntimeDraftLocks.compute(objectId, (key, existing) -> {
            DraftPreparationLock lock = existing == null ? new DraftPreparationLock() : existing;
            lock.references++;
            return lock;
        });
    }

    private void releasePrepareRuntimeDraftLock(Long objectId, DraftPreparationLock lock) {
        prepareRuntimeDraftLocks.computeIfPresent(objectId, (key, current) -> {
            if (current != lock) {
                return current;
            }
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private TransactionTemplate requiresNewTransactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private AiCrudConfig doPrepareRuntimeDraft(Long objectId, boolean persistChildRelations) {
        DesignerContext context = loadContext(objectId);
        if (persistChildRelations) {
            DesignerContext relationContext = context;
            requiresNewTransactionTemplate().executeWithoutResult(status ->
                    synchronizeFormChildRelations(relationContext));
            // 关系短事务提交后重新读取，避免并发设计保存期间用旧草稿覆盖最新模型/页面配置。
            context = loadContext(objectId);
        }
        // 必须在关系同步/重载之后再取基线：否则 before 永远是 sync 前快照，短回路无法命中，
        // 应用发布会对每个对象反复写出相同的 model/page schema。
        String beforeModelSchema = writeJson(context.getModelSchema(), "modelSchema");
        String beforePageSchema = writeJson(context.getPageSchema(), "pageSchema");
        applyRelationsToModel(context);
        compileFormFirstRuntimeSchema(context);
        String preparedModelSchema = writeJson(context.getModelSchema(), "modelSchema");
        String preparedPageSchema = writeJson(context.getPageSchema(), "pageSchema");
        if (context.getConfig() != null
                && (StringUtils.equals(beforeModelSchema, preparedModelSchema)
                && StringUtils.equals(beforePageSchema, preparedPageSchema)
                || isStoredRuntimeDraftCurrent(context, preparedModelSchema, preparedPageSchema))) {
            return context.getConfig();
        }
        String currentStatus = StringUtils.defaultIfBlank(
                context.getObject().getDesignStatus(), BusinessObjectDesignStatus.DRAFT.getCode());
        DesignerContext preparedContext = context;
        return requiresNewTransactionTemplate().execute(status ->
                saveDraft(preparedContext, currentStatus, false).getConfig());
    }

    /** 预览准备的锁引用计数，避免最后一个执行者释放期间创建第二把同对象锁。 */
    private static final class DraftPreparationLock {

        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;
    }

    private void invalidatePreviewDraftCache(Long objectId) {
        if (objectId != null) {
            previewDraftCache.remove(objectId);
        }
    }

    private record CachedPreviewDraft(AiCrudConfig config, long expiresAtMs) {
    }

    /**
     * 从持久化表单 Schema 恢复自动管理的子表关系。
     *
     * <p>除设计器保存外，发布链路也会调用本方法，兼容历史上表单已保存但关系未
     * 生成的草稿。关系变化后立即回填页面 modelRefs，确保随后编译出的
     * masterDetailConfig 与当前表单一致。</p>
     */
    public boolean synchronizeFormChildRelations(DesignerContext context) {
        if (context == null || context.getObject() == null) {
            return false;
        }
        Map<String, Object> designerOptions = resolveDesignerOptions(context.getObject(), context.getConfig());
        if (!hasDesignerOption(designerOptions, FORM_DESIGNER_SCHEMA_OPTION_KEY)) {
            return false;
        }
        FormDesignerSchemaDTO formSchema = resolveFormDesignerSchema(
                context.getObject(), context.getModelSchema(), context.getPageSchema(), designerOptions);
        boolean changed = ensureChildTableRelations(context, formSchema);
        if (changed) {
            context.setRelations(relationMapper.selectRelationsByObject(
                    resolveTenantId(), context.getObject().getSuiteCode(), context.getObject().getObjectCode()));
            applyRelationsToModel(context);
        }
        return changed;
    }

    public DesignerContext compileFormFirstRuntimeSchema(DesignerContext context) {
        if (context == null || context.getObject() == null) {
            return context;
        }
        Map<String, Object> designerOptions = resolveDesignerOptions(context.getObject(), context.getConfig());
        boolean hasFormSchema = hasDesignerOption(designerOptions, FORM_DESIGNER_SCHEMA_OPTION_KEY);
        boolean hasViewSchema = hasDesignerOption(designerOptions, VIEW_SCHEMA_OPTION_KEY);
        boolean hasLinkageSchema = hasDesignerOption(designerOptions, LINKAGE_SCHEMA_OPTION_KEY);
        if (!hasFormSchema && !hasViewSchema && !hasLinkageSchema) {
            return context;
        }

        LowcodeModelSchema modelSchema = enrichModelSchema(context.getObject(), context.getModelSchema());
        LowcodePageSchema pageSchema = ensurePageSchema(context.getPageSchema(), modelSchema);
        FormDesignerSchemaDTO formSchema = hasFormSchema
                ? resolveFormDesignerSchema(context.getObject(), modelSchema, pageSchema, designerOptions)
                : null;
        ViewSchemaDTO viewSchema = hasViewSchema
                ? resolveViewSchema(modelSchema, pageSchema, designerOptions)
                : null;
        LinkageSchemaDTO legacyLinkageSchema = hasLinkageSchema ? resolveLinkageSchema(designerOptions) : null;
        LinkageSchemaDTO linkageSchema = formSchema == null
                ? legacyLinkageSchema
                : resolveUnifiedLinkageSchema(formSchema, legacyLinkageSchema);

        if (linkageSchema != null) {
            applyLinkageSchemaToModel(modelSchema, linkageSchema);
        }
        modelSchema = schemaNormalizer.normalizeModelFields(modelSchema, true);
        pageSchema = ensurePageSchema(pageSchema, modelSchema);
        if (formSchema != null) {
            applyFormDesignerSchemaToEditZone(pageSchema, modelSchema, formSchema);
        }
        if (viewSchema != null) {
            applyViewSchemaToPageZones(pageSchema, modelSchema, viewSchema);
        }
        context.setModelSchema(modelSchema);
        context.setPageSchema(pageSchema);
        return context;
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncModelRelations(Long objectId) {
        DesignerContext context = loadContext(objectId);
        applyRelationsToModel(context);
        saveDraft(context, BusinessObjectDesignStatus.CHANGED.getCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollbackDesignVersion(Long objectId, Long versionId) {
        DesignerContext context = loadContext(objectId);
        AiBusinessObjectDesignVersion version = designVersionMapper.selectVersionById(
                resolveTenantId(), objectId, versionId);
        if (version == null) {
            throw new BusinessException("设计版本不存在");
        }
        context.setModelSchema(readJson(version.getModelSnapshot(), LowcodeModelSchema.class, "modelSnapshot"));
        context.setPageSchema(readJson(version.getPageSnapshot(), LowcodePageSchema.class, "pageSnapshot"));
        restoreRelationsFromSnapshot(context.getObject(), version.getRelationSnapshot());
        restoreDesignerOptionsFromSnapshot(context.getObject(), version.getDesignerOptionsSnapshot());
        saveDraft(context, BusinessObjectDesignStatus.CHANGED.getCode());
    }

    private AiLowcodeModel resolveModel(AiBusinessObject object, AiCrudConfig config) {
        Long tenantId = resolveTenantId();
        if (object.getModelId() != null) {
            AiLowcodeModel model = lowcodeModelMapper.selectModelById(tenantId, object.getModelId());
            if (model != null) {
                return model;
            }
        }
        if (StringUtils.isNotBlank(object.getModelCode())) {
            AiLowcodeModel model = lowcodeModelMapper.selectByModelCode(tenantId, object.getModelCode());
            if (model != null) {
                return model;
            }
        }
        if (config != null && StringUtils.isNotBlank(config.getObjectCode())) {
            return lowcodeModelMapper.selectByModelCode(tenantId, config.getObjectCode());
        }
        return null;
    }

    private AiCrudConfig resolveConfig(AiBusinessObject object) {
        Long tenantId = resolveTenantId();
        if (StringUtils.isNotBlank(object.getConfigKey())) {
            AiCrudConfig config = crudConfigMapper.selectByConfigKey(tenantId, object.getConfigKey());
            if (config != null) {
                return config;
            }
        }
        AiBusinessApp app = businessAppMapper.selectRuntimeAppByObject(tenantId, object.getSuiteCode(), object.getObjectCode());
        if (app != null && StringUtils.isNotBlank(app.getConfigKey())) {
            AiCrudConfig config = crudConfigMapper.selectByConfigKey(tenantId, app.getConfigKey());
            if (config != null) {
                return config;
            }
        }
        if (StringUtils.isNotBlank(object.getModelCode())) {
            return crudConfigMapper.selectByConfigKey(tenantId, normalizeConfigKey(object.getModelCode()));
        }
        return null;
    }

    private LowcodeModelSchema resolveModelSchema(AiBusinessObject object, AiLowcodeModel model, AiCrudConfig config) {
        LowcodeModelSchema schema = null;
        if (model != null && StringUtils.isNotBlank(model.getModelSchema())) {
            schema = readJson(model.getModelSchema(), LowcodeModelSchema.class, "modelSchema");
        } else if (config != null && StringUtils.isNotBlank(config.getModelSchema())) {
            schema = readJson(config.getModelSchema(), LowcodeModelSchema.class, "modelSchema");
        }
        if (schema == null) {
            schema = buildDefaultModelSchema(object);
        }
        return enrichModelSchema(object, schema);
    }

    private LowcodePageSchema resolvePageSchema(AiCrudConfig config, LowcodeModelSchema modelSchema) {
        LowcodePageSchema pageSchema;
        if (config != null && StringUtils.isNotBlank(config.getPageSchema())) {
            pageSchema = ensurePageSchema(readJson(config.getPageSchema(), LowcodePageSchema.class, "pageSchema"), modelSchema);
        } else {
            pageSchema = ensurePageSchema(fieldSchemaService.buildDefaultPageSchema(modelSchema), modelSchema);
        }
        applyLegacyRuntimeSchemas(config, pageSchema, modelSchema);
        return pageSchema;
    }

    private LowcodeModelSchema buildDefaultModelSchema(AiBusinessObject object) {
        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setSchemaVersion(2);
        schema.setAppType("SINGLE");
        schema.setTableMode("CREATE");
        schema.setTableName(normalizeTableName(resolveModelCode(object)));
        schema.setBusinessName(object.getObjectName());
        schema.setFields(new ArrayList<>());
        return schema;
    }

    private LowcodeModelSchema enrichModelSchema(AiBusinessObject object, LowcodeModelSchema schema) {
        LowcodeModelSchema target = schema == null ? buildDefaultModelSchema(object) : schema;
        target.setSchemaVersion(2);
        target.setAppType(StringUtils.defaultIfBlank(target.getAppType(), "SINGLE").toUpperCase(Locale.ROOT));
        target.setTableMode(StringUtils.defaultIfBlank(target.getTableMode(), "CREATE").toUpperCase(Locale.ROOT));
        target.setTableName(StringUtils.defaultIfBlank(target.getTableName(), normalizeTableName(resolveModelCode(object))));
        target.setBusinessName(StringUtils.defaultIfBlank(target.getBusinessName(), object.getObjectName()));
        AiLowcodeDomain domain = resolveDomain(object, target);
        LowcodeDomainRef domainRef = target.getDomain() == null ? new LowcodeDomainRef() : target.getDomain();
        domainRef.setId(domain == null ? domainRef.getId() : domain.getId());
        domainRef.setCode(domain == null ? StringUtils.defaultIfBlank(domainRef.getCode(), object.getSuiteCode()) : domain.getDomainCode());
        domainRef.setName(domain == null ? StringUtils.defaultIfBlank(domainRef.getName(), object.getSuiteCode()) : domain.getDomainName());
        target.setDomain(domainRef);

        LowcodeObjectSchema objectSchema = target.getObject() == null ? new LowcodeObjectSchema() : target.getObject();
        objectSchema.setCode(resolveModelCode(object));
        objectSchema.setName(StringUtils.defaultIfBlank(objectSchema.getName(), object.getObjectName()));
        objectSchema.setDescription(StringUtils.defaultIfBlank(objectSchema.getDescription(), object.getDescription()));
        target.setObject(objectSchema);
        applyRuntimeDatasourceFromObjectOptions(object, target);
        if (target.getAuditStrategy() == null) {
            LowcodeAuditStrategy auditStrategy = new LowcodeAuditStrategy();
            auditStrategy.setMode("FORGE_COLUMNS");
            auditStrategy.setCreateByColumn("create_by");
            auditStrategy.setCreateTimeColumn("create_time");
            auditStrategy.setCreateDeptColumn("create_dept");
            auditStrategy.setUpdateByColumn("update_by");
            auditStrategy.setUpdateTimeColumn("update_time");
            target.setAuditStrategy(auditStrategy);
        }
        return schemaNormalizer.normalizeModelFields(target, true);
    }

    private LowcodePageSchema ensurePageSchema(LowcodePageSchema pageSchema, LowcodeModelSchema modelSchema) {
        LowcodePageSchema target = pageSchema == null ? fieldSchemaService.buildDefaultPageSchema(modelSchema) : pageSchema;
        if (StringUtils.isBlank(target.getLayoutType())) {
            target.setLayoutType("simple-crud");
        }
        // 画布已有 tree-panel 时强制左树右表，避免运行态仍按 simple-crud 渲染成普通列表
        if (hasTreePanelBlock(target) && !"tree-crud".equals(target.getLayoutType())) {
            target.setLayoutType("tree-crud");
        }
        if (target.getZones() == null) {
            target.setZones(new ArrayList<>());
        }
        Map<String, LowcodePageZone> normalizedZones = new LinkedHashMap<>();
        target.getZones().forEach(zone -> normalizePageZone(zone, normalizedZones));
        target.setZones(new ArrayList<>(normalizedZones.values()));
        Set<String> zoneKeys = new LinkedHashSet<>(normalizedZones.keySet());
        LowcodePageSchema defaults = fieldSchemaService.buildDefaultPageSchema(modelSchema);
        defaults.getZones().stream()
                .filter(zone -> !zoneKeys.contains(zone.getZoneKey()))
                .forEach(target.getZones()::add);
        return target;
    }

    private boolean hasTreePanelBlock(LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null) {
            return false;
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> itemList)) {
            return false;
        }
        for (Object item : itemList) {
            if (item instanceof Map<?, ?> block && "tree-panel".equals(String.valueOf(block.get("blockType")))) {
                return true;
            }
        }
        return false;
    }

    private void applyLegacyRuntimeSchemas(AiCrudConfig config, LowcodePageSchema pageSchema, LowcodeModelSchema modelSchema) {
        if (config == null || pageSchema == null || modelSchema == null) {
            return;
        }
        Set<String> modelFields = lowcodeFieldMap(modelSchema).keySet();
        applyLegacyRuntimeSchemaToZone(config.getSearchSchema(), pageSchema, "search", "search-form", modelFields, "field");
        applyLegacyRuntimeSchemaToZone(config.getEditSchema(), pageSchema, "edit", "edit-form", modelFields, "field");
        applyLegacyRuntimeSchemaToZone(config.getColumnsSchema(), pageSchema, "table", "data-table", modelFields, "prop");
    }

    private void applyLegacyRuntimeSchemaToZone(String schemaJson, LowcodePageSchema pageSchema,
                                                String zoneKey, String componentKey,
                                                Set<String> modelFields, String primaryFieldKey) {
        List<Map<String, Object>> legacyItems = readLegacySchemaList(schemaJson);
        if (legacyItems.isEmpty() || modelFields == null || modelFields.isEmpty()) {
            return;
        }
        LowcodePageZone zone = findOrCreateZone(pageSchema, zoneKey, componentKey);
        List<String> fieldRefs = zone.getFieldRefs() == null ? new ArrayList<>() : new ArrayList<>(zone.getFieldRefs());
        LinkedHashSet<String> mergedRefs = new LinkedHashSet<>(fieldRefs);
        Map<String, Object> props = zone.getProps() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(zone.getProps());
        Map<String, Object> settings = new LinkedHashMap<>(mapValue(props.get("fieldSettings")));

        for (Map<String, Object> item : legacyItems) {
            String fieldCode = resolveLegacyFieldCode(item, primaryFieldKey);
            if (!modelFields.contains(fieldCode)) {
                continue;
            }
            mergedRefs.add(fieldCode);
            Map<String, Object> existing = new LinkedHashMap<>(mapValue(settings.get(fieldCode)));
            mergeLegacyRuntimeFieldSetting(existing, buildLegacyRuntimeFieldSetting(item, zoneKey));
            settings.put(fieldCode, existing);
        }
        zone.setFieldRefs(new ArrayList<>(mergedRefs));
        props.put("fieldSettings", settings);
        zone.setProps(props);
    }

    private List<Map<String, Object>> readLegacySchemaList(String schemaJson) {
        if (StringUtils.isBlank(schemaJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String resolveLegacyFieldCode(Map<String, Object> item, String primaryFieldKey) {
        return StringUtils.firstNonBlank(
                text(item.get(primaryFieldKey)),
                text(item.get("field")),
                text(item.get("prop")),
                text(item.get("dataIndex")),
                text(item.get("key"))
        );
    }

    private Map<String, Object> buildLegacyRuntimeFieldSetting(Map<String, Object> item, String zoneKey) {
        Map<String, Object> setting = new LinkedHashMap<>();
        String componentType = normalizeRuntimeComponentType(StringUtils.firstNonBlank(
                text(item.get("componentType")), text(item.get("type"))));
        putIfNotBlank(setting, "componentType", componentType);
        putIfNotBlank(setting, "type", componentType);
        putIfNotBlank(setting, "label", text(item.get("label")));
        putIfNotBlank(setting, "queryType", text(item.get("queryType")));
        putIfNotBlank(setting, "align", normalizeAlign(StringUtils.firstNonBlank(text(item.get("align")), text(item.get("textAlign")))));
        putIfNotBlank(setting, "fixed", normalizeFixed(text(item.get("fixed"))));
        putIfPresent(setting, "width", item.get("width"));
        putIfPresent(setting, "minWidth", item.get("minWidth"));
        putIfPresent(setting, "span", item.get("span"));
        putIfPresent(setting, "required", item.get("required"));
        putIfPresent(setting, "readonly", item.get("readonly"));
        putIfPresent(setting, "disabled", item.get("disabled"));
        putIfPresent(setting, "defaultValue", item.get("defaultValue"));
        putIfPresent(setting, "rules", item.get("rules"));
        putIfNotBlank(setting, "requiredMessage", text(item.get("requiredMessage")));
        if (item.containsKey("sortable")) {
            setting.put("sortable", readBoolean(item.get("sortable"), false));
        }

        Map<String, Object> props = new LinkedHashMap<>(mapValue(item.get("props")));
        String dictType = StringUtils.firstNonBlank(text(item.get("dictType")), text(props.get("dictType")));
        putIfNotBlank(setting, "dictType", dictType);
        if (StringUtils.isNotBlank(dictType)) {
            props.putIfAbsent("dictType", dictType);
        }
        if (item.containsKey("generation")) {
            props.putIfAbsent("generation", item.get("generation"));
        }
        if (!props.isEmpty()) {
            setting.put("props", props);
        }

        if ("table".equals(zoneKey)) {
            Object render = item.get("render");
            Map<String, Object> renderMap = mapValue(render);
            String renderType = StringUtils.firstNonBlank(
                    text(item.get("renderType")),
                    text(renderMap.get("type")),
                    StringUtils.isNotBlank(dictType) ? "dictTag" : null
            );
            putIfNotBlank(setting, "renderType", renderType);
            if (StringUtils.isBlank(text(setting.get("dictType")))) {
                putIfNotBlank(setting, "dictType", text(renderMap.get("dictType")));
            }
        }
        return setting;
    }

    @SuppressWarnings("unchecked")
    private void mergeLegacyRuntimeFieldSetting(Map<String, Object> target, Map<String, Object> legacy) {
        if (target == null || legacy == null || legacy.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : legacy.entrySet()) {
            String key = entry.getKey();
            Object legacyValue = entry.getValue();
            Object currentValue = target.get(key);
            if ("props".equals(key) && legacyValue instanceof Map<?, ?> legacyProps) {
                Map<String, Object> merged = new LinkedHashMap<>(mapValue(legacyProps));
                merged.putAll(mapValue(currentValue));
                target.put("props", merged);
                continue;
            }
            if (("componentType".equals(key) || "type".equals(key)) && StringUtils.isNotBlank(text(legacyValue))) {
                String legacyComponent = text(legacyValue);
                if (isBlankValue(currentValue)
                        || (isGenericComponent(text(currentValue)) && !isGenericComponent(legacyComponent))) {
                    target.put(key, legacyComponent);
                }
                continue;
            }
            if (isBlankValue(currentValue)) {
                target.put(key, legacyValue);
            }
        }
    }

    private void normalizePageZone(LowcodePageZone zone, Map<String, LowcodePageZone> normalizedZones) {
        if (zone == null) {
            return;
        }
        String zoneKey = normalizePageZoneKey(zone.getZoneKey());
        if (StringUtils.isBlank(zoneKey)) {
            return;
        }
        zone.setZoneKey(zoneKey);
        if (StringUtils.isBlank(zone.getComponentKey())) {
            zone.setComponentKey(PAGE_ZONE_COMPONENTS.get(zoneKey));
        }
        if (zone.getFieldRefs() == null) {
            zone.setFieldRefs(new ArrayList<>());
        }
        if (zone.getProps() == null) {
            zone.setProps(new LinkedHashMap<>());
        }
        LowcodePageZone existing = normalizedZones.get(zoneKey);
        if (existing == null) {
            normalizedZones.put(zoneKey, zone);
            return;
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>(existing.getFieldRefs());
        refs.addAll(zone.getFieldRefs());
        existing.setFieldRefs(new ArrayList<>(refs));
        zone.getProps().forEach(existing.getProps()::putIfAbsent);
        if (existing.getEnabled() == null) {
            existing.setEnabled(zone.getEnabled());
        }
    }

    private String normalizePageZoneKey(String zoneKey) {
        String normalized = StringUtils.trimToEmpty(zoneKey).toLowerCase(Locale.ROOT);
        return PAGE_ZONE_ALIASES.get(normalized);
    }

    private void validateDraft(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        if (!hasBusinessFields(modelSchema)) {
            return;
        }
        schemaValidator.validatePage(pageSchema, modelSchema);
    }

    private AiLowcodeModel saveModelDraft(AiBusinessObject object, AiLowcodeModel model, LowcodeModelSchema modelSchema) {
        AiLowcodeDomain domain = resolveDomain(object, modelSchema);
        AiLowcodeModel target = model == null ? new AiLowcodeModel() : model;
        target.setTenantId(resolveTenantId());
        target.setDomainId(domain == null ? 1900000000000000001L : domain.getId());
        target.setDomainCode(domain == null ? GENERAL_DOMAIN_CODE : domain.getDomainCode());
        target.setModelCode(resolveModelCode(object));
        target.setModelName(object.getObjectName());
        target.setModelDesc(StringUtils.defaultIfBlank(object.getDescription(), object.getObjectName()));
        target.setStatus("ENABLED");
        target.setTenantEnabled(true);
        target.setMasterData("MASTER".equalsIgnoreCase(object.getObjectType()));
        LowcodeRuntimeDatasourceSnapshot runtimeDatasource = modelSchema.getRuntimeDatasource();
        target.setRuntimeDatasourceId(runtimeDatasource == null ? null : runtimeDatasource.getDatasourceId());
        target.setRuntimeDatasourceCode(runtimeDatasource == null ? null : runtimeDatasource.getDatasourceCode());
        target.setRuntimeTableName(StringUtils.defaultIfBlank(
                runtimeDatasource == null ? null : runtimeDatasource.getTableName(),
                modelSchema.getTableName()));
        target.setModelSchema(writeJson(modelSchema, "modelSchema"));
        if (target.getId() == null) {
            lowcodeModelMapper.insert(target);
        } else {
            lowcodeModelMapper.updateById(target);
        }
        return target;
    }

    private AiCrudConfig saveRuntimeDraft(AiBusinessObject object, AiCrudConfig config,
                                          LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        AiCrudConfig target = config == null ? new AiCrudConfig() : config;
        target.setTenantId(resolveTenantId());
        target.setConfigKey(StringUtils.defaultIfBlank(target.getConfigKey(), resolveConfigKey(object)));
        target.setTableName(modelSchema.getTableName());
        target.setTableComment(StringUtils.defaultIfBlank(modelSchema.getBusinessName(), object.getObjectName()));
        target.setAppName(object.getObjectName());
        target.setMenuName(StringUtils.defaultIfBlank(target.getMenuName(), object.getObjectName()));
        target.setMenuSort(target.getMenuSort() == null ? object.getSortOrder() : target.getMenuSort());
        target.setMode("CONFIG");
        target.setBuildMode("LOWCODE");
        target.setStatus(EnableStatus.DISABLED.codeAsString());
        target.setPublishStatus(StringUtils.defaultIfBlank(target.getPublishStatus(), "DRAFT"));
        target.setLayoutType(StringUtils.defaultIfBlank(pageSchema.getLayoutType(), "simple-crud"));
        target.setDraftVersion((target.getDraftVersion() == null ? 0 : target.getDraftVersion()) + 1);
        if (target.getPublishedVersion() == null) {
            target.setPublishedVersion(0);
        }
        LowcodeDomainRef domain = modelSchema.getDomain();
        LowcodeObjectSchema lowcodeObject = modelSchema.getObject();
        LowcodeRuntimeDatasourceSnapshot runtimeDatasource = modelSchema.getRuntimeDatasource();
        target.setDomainId(domain == null ? target.getDomainId() : domain.getId());
        target.setDomainCode(domain == null ? target.getDomainCode() : domain.getCode());
        target.setObjectCode(lowcodeObject == null ? resolveModelCode(object) : lowcodeObject.getCode());
        target.setObjectName(object.getObjectName());
        target.setRuntimeDatasourceId(runtimeDatasource == null ? null : runtimeDatasource.getDatasourceId());
        target.setRuntimeDatasourceCode(runtimeDatasource == null ? null : runtimeDatasource.getDatasourceCode());
        target.setRuntimeDatasourceSnapshot(writeJson(runtimeDatasource, "runtimeDatasourceSnapshot"));
        target.setRuntimeTableName(StringUtils.defaultIfBlank(
                runtimeDatasource == null ? null : runtimeDatasource.getTableName(),
                modelSchema.getTableName()));
        target.setModelSchema(writeJson(modelSchema, "modelSchema"));
        target.setPageSchema(writeJson(pageSchema, "pageSchema"));
        // 导入已有表时 audit/tenant/logicDelete 写在 modelSchema 里；必须同步到配置列，
        // 否则运行时只读到 NULL/旧值，新增无法自动填充 create_by 等审计字段。
        target.setTenantStrategy(writeJson(modelSchema.getTenantStrategy(), "tenantStrategy"));
        target.setAuditStrategy(writeJson(modelSchema.getAuditStrategy(), "auditStrategy"));
        target.setLogicDeleteStrategy(writeJson(modelSchema.getLogicDeleteStrategy(), "logicDeleteStrategy"));
        target.setOptions(mergeFormDesignerSchemaIntoRuntimeOptions(target.getOptions(), pageSchema));
        if (target.getId() == null) {
            crudConfigService.save(target);
        } else {
            crudConfigService.updateById(target);
        }
        return target;
    }

    private void applyRuntimeDatasourceFromObjectOptions(AiBusinessObject object, LowcodeModelSchema schema) {
        if (object == null || schema == null || schema.getRuntimeDatasource() != null) {
            return;
        }
        Long datasourceId = resolveRuntimeDatasourceId(object);
        if (datasourceId == null) {
            return;
        }
        GenDatasource datasource = datasourceService.getById(datasourceId);
        if (datasource == null || !EnableStatus.ENABLED.matches(datasource.getIsEnabled())) {
            throw new BusinessException("运行数据源不存在或已禁用");
        }
        schema.setRuntimeDatasource(runtimeDataSourceResolver.buildSnapshot(
                datasource, schema.getTableName(), schema.getTableMode()));
    }

    private Long resolveRuntimeDatasourceId(AiBusinessObject object) {
        Map<String, Object> options = readMap(object.getOptions());
        Long datasourceId = numberAsLong(options.get(OBJECT_OPTION_RUNTIME_DATASOURCE_ID));
        if (datasourceId != null) {
            return datasourceId;
        }
        Map<String, Object> runtimeDatasource = mapValue(options.get(OBJECT_OPTION_RUNTIME_DATASOURCE));
        return numberAsLong(runtimeDatasource.get("datasourceId"));
    }

    private void applyObjectFields(AiBusinessObject object, BusinessObjectDesignerDTO dto) {
        if (StringUtils.isNotBlank(dto.getObjectName())) {
            object.setObjectName(StringUtils.trim(dto.getObjectName()));
        }
        if (dto.getDescription() != null) {
            object.setDescription(StringUtils.trimToNull(dto.getDescription()));
        }
        if (dto.getIcon() != null) {
            object.setIcon(StringUtils.trimToNull(dto.getIcon()));
        }
        if (dto.getDisplayField() != null) {
            object.setDisplayField(StringUtils.trimToNull(dto.getDisplayField()));
        }
        if (dto.getStatus() != null) {
            object.setStatus(dto.getStatus());
        }
    }

    private LowcodeModelSchema rebuildModelFields(LowcodeModelSchema modelSchema, List<BusinessFieldDTO> fields) {
        LowcodeModelSchema target = modelSchema == null ? new LowcodeModelSchema() : modelSchema;
        Map<String, LowcodeFieldSchema> existingFields = lowcodeFieldMap(target);
        List<LowcodeFieldSchema> newFields = new ArrayList<>();
        if (target.getFields() != null) {
            target.getFields().stream()
                    .filter(field -> field != null && Boolean.TRUE.equals(field.getSystemField()))
                    .forEach(newFields::add);
        }
        for (BusinessFieldDTO dto : fields) {
            if (dto != null) {
                mergePreservedFieldPayload(existingFields.get(StringUtils.defaultIfBlank(dto.getFieldCode(),
                        text(mapValue(dto.getFieldBinding()).get("fieldCode")))), dto);
                LowcodeFieldSchema next = fieldSchemaService.buildFieldSchema(dto);
                mergePreservedFieldMetadata(existingFields.get(next.getField()), next);
                next.applyMultipleSelectionStorage();
                newFields.add(next);
            }
        }
        target.setFields(newFields);
        return schemaNormalizer.normalizeModelFields(target, true);
    }

    private void mergePreservedFieldPayload(LowcodeFieldSchema existing, BusinessFieldDTO dto) {
        if (existing == null || dto == null) {
            return;
        }
        Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(dto.getBasicProps()));
        mergeMissingProps(basicProps, mapValue(existing.getBasicProps()), PRESERVED_BASIC_PROP_KEYS);
        dto.setBasicProps(basicProps);
        Map<String, Object> advancedProps = new LinkedHashMap<>(mapValue(dto.getAdvancedProps()));
        mergeMissingProps(advancedProps, mapValue(existing.getAdvancedProps()), PRESERVED_ADVANCED_PROP_KEYS);
        dto.setAdvancedProps(advancedProps);
        if (StringUtils.isBlank(dto.getDictType()) && StringUtils.isNotBlank(existing.getDictType())) {
            dto.setDictType(existing.getDictType());
        }
        if (StringUtils.isBlank(dto.getReferenceObjectCode()) && StringUtils.isNotBlank(existing.getReferenceObjectCode())) {
            dto.setReferenceObjectCode(existing.getReferenceObjectCode());
        }
        if (StringUtils.isBlank(dto.getReferenceDisplayField()) && StringUtils.isNotBlank(existing.getReferenceDisplayField())) {
            dto.setReferenceDisplayField(existing.getReferenceDisplayField());
        }
        if ((dto.getFormulaConfig() == null || dto.getFormulaConfig().isEmpty())
                && existing.getFormulaConfig() != null && !existing.getFormulaConfig().isEmpty()) {
            dto.setFormulaConfig(new LinkedHashMap<>(existing.getFormulaConfig()));
        }
        if (shouldPreserveBusinessComponent(existing, dto.getComponentType())) {
            dto.setComponentType(existing.getComponentType());
            dto.setFieldType(existing.getBusinessFieldType());
            dto.setDataType(existing.getDataType());
            dto.setLength(existing.getLength());
            dto.setPrecision(existing.getPrecision());
            dto.setQueryType(existing.getQueryType());
        }
    }

    private void mergePreservedFieldMetadata(LowcodeFieldSchema existing, LowcodeFieldSchema next) {
        if (existing == null || next == null) {
            return;
        }
        if (StringUtils.isBlank(next.getDictType()) && StringUtils.isNotBlank(existing.getDictType())) {
            next.setDictType(existing.getDictType());
        }
        if (StringUtils.isBlank(next.getReferenceObjectCode()) && StringUtils.isNotBlank(existing.getReferenceObjectCode())) {
            next.setReferenceObjectCode(existing.getReferenceObjectCode());
        }
        if (StringUtils.isBlank(next.getReferenceDisplayField()) && StringUtils.isNotBlank(existing.getReferenceDisplayField())) {
            next.setReferenceDisplayField(existing.getReferenceDisplayField());
        }
        if ((next.getFormulaConfig() == null || next.getFormulaConfig().isEmpty())
                && existing.getFormulaConfig() != null && !existing.getFormulaConfig().isEmpty()) {
            next.setFormulaConfig(new LinkedHashMap<>(existing.getFormulaConfig()));
            next.setReadonly(true);
        }

        Map<String, Object> nextBasicProps = new LinkedHashMap<>(mapValue(next.getBasicProps()));
        Map<String, Object> existingBasicProps = mapValue(existing.getBasicProps());
        mergeMissingProps(nextBasicProps, existingBasicProps, PRESERVED_BASIC_PROP_KEYS);
        if (StringUtils.isBlank(next.getDictType()) && StringUtils.isNotBlank(text(nextBasicProps.get("dictType")))) {
            next.setDictType(text(nextBasicProps.get("dictType")));
        }
        next.setBasicProps(nextBasicProps);

        Map<String, Object> nextAdvancedProps = new LinkedHashMap<>(mapValue(next.getAdvancedProps()));
        mergeMissingProps(nextAdvancedProps, mapValue(existing.getAdvancedProps()), PRESERVED_ADVANCED_PROP_KEYS);
        next.setAdvancedProps(nextAdvancedProps);

        if (shouldPreserveBusinessComponent(existing, next)) {
            next.setComponentType(existing.getComponentType());
            next.setBusinessFieldType(existing.getBusinessFieldType());
            next.setDataType(existing.getDataType());
            next.setLength(existing.getLength());
            next.setPrecision(existing.getPrecision());
            next.setQueryType(existing.getQueryType());
        }
        if (StringUtils.isNotBlank(next.getDictType()) && isGenericComponent(next.getComponentType())) {
            next.setComponentType(StringUtils.defaultIfBlank(existing.getComponentType(), "select"));
            next.setBusinessFieldType(StringUtils.defaultIfBlank(existing.getBusinessFieldType(), "DICT"));
        }
    }

    private void mergeMissingProps(Map<String, Object> target, Map<String, Object> source, Set<String> keys) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (String key : keys) {
            if (!source.containsKey(key)) {
                continue;
            }
            Object current = target.get(key);
            Object preserved = source.get(key);
            if (isBlankValue(current)) {
                target.put(key, preserved);
            } else if (current instanceof Map<?, ?> currentMap && preserved instanceof Map<?, ?> preservedMap) {
                Map<String, Object> merged = new LinkedHashMap<>(mapValue(preservedMap));
                merged.putAll(mapValue(currentMap));
                target.put(key, merged);
            }
        }
    }

    private boolean shouldPreserveBusinessComponent(LowcodeFieldSchema existing, LowcodeFieldSchema next) {
        return next != null && shouldPreserveBusinessComponent(existing, next.getComponentType());
    }

    private boolean shouldPreserveBusinessComponent(LowcodeFieldSchema existing, String nextComponentType) {
        if (existing == null || StringUtils.isBlank(existing.getComponentType()) || !isGenericComponent(nextComponentType)) {
            return false;
        }
        return BUSINESS_COMPONENT_TYPES.contains(existing.getComponentType())
                || StringUtils.isNotBlank(existing.getDictType())
                || !isBlankValue(mapValue(existing.getBasicProps()).get("recordSelector"))
                || !isBlankValue(mapValue(existing.getBasicProps()).get("generation"));
    }

    private boolean isGenericComponent(String componentType) {
        return GENERIC_COMPONENT_TYPES.contains(StringUtils.defaultString(componentType));
    }

    private List<BusinessFieldDTO> normalizeDesignerFieldPayloads(List<BusinessFieldDTO> fields,
                                                                  FormDesignerSchemaDTO formSchema) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> formFieldComponents = collectFormFieldComponentMap(formSchema);
        List<BusinessFieldDTO> normalized = new ArrayList<>();
        for (BusinessFieldDTO field : fields) {
            if (field == null) {
                continue;
            }
            normalized.add(normalizeDesignerFieldPayload(field, formFieldComponents));
        }
        return normalized;
    }

    private BusinessFieldDTO normalizeDesignerFieldPayload(BusinessFieldDTO field,
                                                           Map<String, Map<String, Object>> formFieldComponents) {
        String fieldCode = StringUtils.defaultIfBlank(
                field.getFieldCode(),
                text(mapValue(field.getFieldBinding()).get("fieldCode")));
        if (StringUtils.isBlank(fieldCode)) {
            return field;
        }

        Map<String, Object> component = formFieldComponents.get(fieldCode);
        if (component != null) {
            String componentType = normalizeRuntimeComponentType(text(component.get("componentKey")));
            if (StringUtils.isNotBlank(componentType)) {
                field.setComponentType(componentType);
                applyComponentDefaults(field, componentType);
            }
            Map<String, Object> props = mapValue(component.get("props"));
            String dictType = StringUtils.firstNonBlank(
                    text(props.get("dictType")),
                    text(mapValue(component.get("advancedProps")).get("dictType")),
                    field.getDictType(),
                    text(mapValue(field.getBasicProps()).get("dictType")),
                    text(mapValue(field.getAdvancedProps()).get("dictType"))
            );
            if (StringUtils.isNotBlank(dictType)) {
                field.setDictType(dictType);
            }
            if (props.containsKey("options")) {
                Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(field.getBasicProps()));
                basicProps.putIfAbsent("options", props.get("options"));
                field.setBasicProps(basicProps);
            }
            if (props.containsKey("optionSource")) {
                Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(field.getBasicProps()));
                basicProps.put("optionSource", props.get("optionSource"));
                String labelValueField = StringUtils.firstNonBlank(
                        text(props.get("labelValueField")),
                        text(basicProps.get("labelValueField")));
                if (StringUtils.isBlank(labelValueField) && isDynamicOptionSource(props.get("optionSource"))) {
                    labelValueField = fieldCode + "Name";
                }
                if (StringUtils.isNotBlank(labelValueField)) {
                    basicProps.put("labelValueField", labelValueField);
                }
                field.setBasicProps(basicProps);
            }
            if (props.containsKey("labelValueField")) {
                Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(field.getBasicProps()));
                basicProps.put("labelValueField", props.get("labelValueField"));
                field.setBasicProps(basicProps);
            }
            if (props.containsKey("recordSelector")) {
                Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(field.getBasicProps()));
                basicProps.put("recordSelector", props.get("recordSelector"));
                field.setBasicProps(basicProps);
            }
            if (props.containsKey("multiple")) {
                Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(field.getBasicProps()));
                basicProps.put("multiple", props.get("multiple"));
                field.setBasicProps(basicProps);
            }
            if (props.containsKey("referenceObjectCode")) {
                field.setReferenceObjectCode(text(props.get("referenceObjectCode")));
            }
            if (props.containsKey("referenceDisplayField")) {
                field.setReferenceDisplayField(text(props.get("referenceDisplayField")));
            }
        }

        boolean dictField = isDictFieldPayload(field);
        boolean referenceField = isReferenceFieldPayload(field);
        if (component == null) {
            if (isUnconfiguredDictFieldPayload(field) || isUnconfiguredReferenceFieldPayload(field)) {
                downgradeDesignerFieldToText(field);
            }
            return field;
        }

        if (dictField && !requiresDictConfig(component)) {
            downgradeDesignerFieldToText(field);
        } else if (referenceField && !requiresReferenceConfig(component)) {
            downgradeDesignerFieldToText(field);
        }
        return field;
    }

    private FormDesignerSchemaDTO resolvePayloadFormSchema(BusinessObjectDesignerDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getFormDesignerSchema() != null) {
            return dto.getFormDesignerSchema();
        }
        Object rawSchema = dto.getDesignerOptions() == null
                ? null
                : dto.getDesignerOptions().get(FORM_DESIGNER_SCHEMA_OPTION_KEY);
        if (rawSchema == null) {
            return null;
        }
        try {
            if (rawSchema instanceof String text && StringUtils.isNotBlank(text)) {
                return objectMapper.readValue(text, FormDesignerSchemaDTO.class);
            }
            return objectMapper.convertValue(rawSchema, FormDesignerSchemaDTO.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Map<String, Object>> collectFormFieldComponentMap(FormDesignerSchemaDTO formSchema) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (formSchema == null) {
            return result;
        }
        collectFormFieldComponents(formSchema.getComponents(), result);
        if (formSchema.getForms() != null) {
            for (Map<String, Object> form : formSchema.getForms()) {
                Map<String, Object> schema = mapValue(form.get("schema"));
                collectFormFieldComponents(listOfMap(schema.get("components")), result);
            }
        }
        return result;
    }

    private void collectFormFieldComponents(List<Map<String, Object>> components,
                                            Map<String, Map<String, Object>> result) {
        if (components == null) {
            return;
        }
        for (Map<String, Object> component : components) {
            if (component == null) {
                continue;
            }
            String componentKey = text(component.get("componentKey"));
            Map<String, Object> binding = mapValue(component.get("fieldBinding"));
            String bindingMode = StringUtils.defaultIfBlank(text(binding.get("mode")), "field");
            String fieldCode = text(binding.get("fieldCode"));
            if (FORM_FIELD_COMPONENT_KEYS.contains(componentKey)
                    && "field".equals(bindingMode)
                    && StringUtils.isNotBlank(fieldCode)) {
                result.put(fieldCode, component);
            }
            collectFormFieldComponents(listOfMap(component.get("children")), result);
        }
    }

    private boolean isDictFieldPayload(BusinessFieldDTO field) {
        String fieldType = StringUtils.defaultString(field.getFieldType()).toUpperCase(Locale.ROOT);
        String componentType = normalizeRuntimeComponentType(field.getComponentType());
        return DICT_FIELD_TYPES.contains(fieldType) || DICT_COMPONENT_TYPES.contains(componentType);
    }

    private void applyComponentDefaults(BusinessFieldDTO field, String componentType) {
        if (field == null) {
            return;
        }
        ComponentFieldDefaults defaults = COMPONENT_FIELD_DEFAULTS.get(componentType);
        if (defaults == null) {
            return;
        }
        if (StringUtils.isBlank(field.getFieldType())) {
            field.setFieldType(defaults.fieldType());
        }
        field.setQueryType(defaults.queryType());
        if (StringUtils.isBlank(field.getDataType())) {
            field.setDataType(defaults.dataType());
            if (field.getLength() == null) {
                field.setLength(defaults.length());
            }
            if (field.getPrecision() == null) {
                field.setPrecision(defaults.precision());
            }
        }
    }

    private void applyComponentDefaults(LowcodeFieldSchema field, String componentType) {
        if (field == null) {
            return;
        }
        ComponentFieldDefaults defaults = COMPONENT_FIELD_DEFAULTS.get(componentType);
        if (defaults == null) {
            return;
        }
        if (StringUtils.isBlank(field.getBusinessFieldType())) {
            field.setBusinessFieldType(defaults.fieldType());
        }
        field.setQueryType(defaults.queryType());
        if (StringUtils.isBlank(field.getDataType())) {
            field.setDataType(defaults.dataType());
            if (field.getLength() == null) {
                field.setLength(defaults.length());
            }
            if (field.getPrecision() == null) {
                field.setPrecision(defaults.precision());
            }
        }
    }

    private boolean isUnconfiguredDictFieldPayload(BusinessFieldDTO field) {
        String dictType = StringUtils.firstNonBlank(
                field.getDictType(),
                text(mapValue(field.getBasicProps()).get("dictType")),
                text(mapValue(field.getAdvancedProps()).get("dictType"))
        );
        return isDictFieldPayload(field) && StringUtils.isBlank(dictType);
    }

    private boolean isReferenceFieldPayload(BusinessFieldDTO field) {
        String fieldType = StringUtils.defaultString(field.getFieldType()).toUpperCase(Locale.ROOT);
        String componentType = normalizeRuntimeComponentType(field.getComponentType());
        return "REFERENCE".equals(fieldType) || "objectReference".equals(componentType);
    }

    private boolean isDynamicOptionSource(Object optionSource) {
        if (!(optionSource instanceof Map<?, ?> map)) {
            return false;
        }
        Object rawType = map.get("type");
        if (rawType == null) {
            return false;
        }
        String type = String.valueOf(rawType).trim();
        if (type.isEmpty()) {
            return false;
        }
        return !"STATIC".equalsIgnoreCase(type.replace('-', '_'));
    }

    private boolean isUnconfiguredReferenceFieldPayload(BusinessFieldDTO field) {
        String referenceObjectCode = StringUtils.firstNonBlank(
                field.getReferenceObjectCode(),
                text(mapValue(field.getBasicProps()).get("referenceObjectCode"))
        );
        String referenceDisplayField = StringUtils.firstNonBlank(
                field.getReferenceDisplayField(),
                text(mapValue(field.getBasicProps()).get("referenceDisplayField"))
        );
        return isReferenceFieldPayload(field)
                && (StringUtils.isBlank(referenceObjectCode) || StringUtils.isBlank(referenceDisplayField));
    }

    private boolean requiresDictConfig(Map<String, Object> component) {
        return component != null
                && DICT_COMPONENT_TYPES.contains(normalizeRuntimeComponentType(text(component.get("componentKey"))));
    }

    private boolean requiresReferenceConfig(Map<String, Object> component) {
        return component != null
                && "objectReference".equals(normalizeRuntimeComponentType(text(component.get("componentKey"))));
    }

    private void downgradeDesignerFieldToText(BusinessFieldDTO field) {
        field.setFieldType("TEXT");
        field.setComponentType("input");
        field.setQueryType("like");
        field.setDictType("");
        field.setReferenceObjectCode("");
        field.setReferenceDisplayField("");
        Map<String, Object> basicProps = new LinkedHashMap<>(mapValue(field.getBasicProps()));
        basicProps.put("dictType", "");
        basicProps.put("referenceObjectCode", "");
        basicProps.put("referenceDisplayField", "");
        field.setBasicProps(basicProps);
        Map<String, Object> advancedProps = new LinkedHashMap<>(mapValue(field.getAdvancedProps()));
        advancedProps.put("dictType", "");
        field.setAdvancedProps(advancedProps);
    }

    public void applyRelationsToModel(DesignerContext context) {
        if (context == null || context.getObject() == null || context.getModelSchema() == null) {
            return;
        }
        List<AiBusinessObjectRelation> relations = relationMapper.selectRuntimeRelationsBySource(
                resolveTenantId(), context.getObject().getSuiteCode(), context.getObject().getObjectCode());
        List<LowcodeRelationSchema> relationSchemas = relations.stream()
                .map(this::toLowcodeRelation)
                .toList();
        context.getModelSchema().setRelations(relationSchemas);
        syncInlineEditRelationsToPageSchema(context, relations);
    }

    private void syncInlineEditRelationsToPageSchema(DesignerContext context,
                                                     List<AiBusinessObjectRelation> relations) {
        LowcodePageSchema pageSchema = context.getPageSchema() == null ? new LowcodePageSchema() : context.getPageSchema();
        Map<String, LowcodePageModelRef> existingRefs = indexPageModelRefs(pageSchema);
        LowcodePageModelRef primaryRef = toPageModelRef(context.getObject(), context.getModel(), context.getModelSchema(), true);
        List<LowcodePageModelRef> refs = new ArrayList<>();
        Set<String> addedModelCodes = new LinkedHashSet<>();
        refs.add(primaryRef);
        addedModelCodes.add(primaryRef.getModelCode());

        List<String> childFieldRefs = new ArrayList<>();
        boolean hasEmbeddedRelations = false;
        for (AiBusinessObjectRelation relation : relations) {
            AiBusinessObject target = businessObjectMapper.selectByObjectCode(
                    resolveTenantId(), relation.getSuiteCode(), relation.getTargetObjectCode());
            if (target == null) {
                continue;
            }
            DesignerContext targetContext = loadContext(target.getId());
            LowcodePageModelRef targetRef = toPageModelRef(target, targetContext.getModel(),
                    targetContext.getModelSchema(), false);
            mergeExistingPageModelRef(targetRef, existingRefs.get(targetRef.getModelCode()));
            if (isEmbeddedRelation(relation)) {
                targetRef.setRelations(List.of(toRelationToPrimary(relation, primaryRef.getModelCode())));
                targetRef.setProps(toInlineRelationProps(relation));
                hasEmbeddedRelations = true;
                addPageModelRef(refs, addedModelCodes, targetRef);
                targetRef.getFields().stream()
                        .map(item -> text(item.get("fieldRef")))
                        .filter(StringUtils::isNotBlank)
                        .forEach(childFieldRefs::add);
                continue;
            }
            if (isReferenceLookupRelation(relation)) {
                targetRef.setRelations(List.of(toLowcodeRelation(relation)));
                targetRef.setProps(toLookupRelationProps(relation));
                addPageModelRef(refs, addedModelCodes, targetRef);
            }
        }

        pageSchema.setModelRefs(sortModelRefsByFormSubTables(refs, context.getObject()));
        pageSchema.setPrimaryModelId(primaryRef.getModelId());
        pageSchema.setPrimaryModelCode(primaryRef.getModelCode());
        if (hasEmbeddedRelations) {
            pageSchema.setLayoutType("master-detail-crud");
        } else if ("master-detail-crud".equals(pageSchema.getLayoutType())) {
            pageSchema.setLayoutType("simple-crud");
        }
        syncInlineEditRefsToEditZone(pageSchema, primaryRef, childFieldRefs);
        context.setPageSchema(pageSchema);
    }

    /**
     * 关系表按 sort_order/id（约等于创建顺序）返回，发布态子表顺序又取自 modelRefs；
     * 这里按表单设计器 subTable 组件的画布顺序重排，否则画布拖动后运行页/审批顺序会反。
     */
    private List<LowcodePageModelRef> sortModelRefsByFormSubTables(List<LowcodePageModelRef> refs,
                                                                   AiBusinessObject object) {
        List<String> order = collectFormSubTableOrder(object);
        if (order.isEmpty() || refs.size() < 3) {
            return refs;
        }
        List<LowcodePageModelRef> children = new ArrayList<>(refs.subList(1, refs.size()));
        children.sort(Comparator.comparingInt(ref -> formSubTableRank(ref, order)));
        List<LowcodePageModelRef> result = new ArrayList<>(refs.size());
        result.add(refs.get(0));
        result.addAll(children);
        return result;
    }

    private int formSubTableRank(LowcodePageModelRef ref, List<String> order) {
        String relationKey = ref.getProps() == null ? null : text(ref.getProps().get("relationKey"));
        int byRelation = StringUtils.isBlank(relationKey) ? -1 : order.indexOf(relationKey);
        if (byRelation >= 0) {
            return byRelation;
        }
        int byModel = order.indexOf(ref.getModelCode());
        return byModel >= 0 ? byModel : Integer.MAX_VALUE;
    }

    private List<String> collectFormSubTableOrder(AiBusinessObject object) {
        Map<String, Object> designerOptions = readMap(object == null ? null : object.getDesignerOptions());
        Object rawSchema = designerOptions.get(FORM_DESIGNER_SCHEMA_OPTION_KEY);
        Map<String, Object> formSchema = rawSchema instanceof String json ? readMap(json) : mapValue(rawSchema);
        List<String> order = new ArrayList<>();
        collectFormSubTableKeys(listOfMap(formSchema.get("components")), order);
        return order;
    }

    private void collectFormSubTableKeys(List<Map<String, Object>> components, List<String> order) {
        for (Map<String, Object> component : components) {
            String componentKey = StringUtils.firstNonBlank(text(component.get("componentKey")), text(component.get("type")));
            if ("subTable".equalsIgnoreCase(componentKey) || "childTable".equalsIgnoreCase(componentKey)) {
                Map<String, Object> props = mapValue(component.get("props"));
                for (String key : new String[]{text(props.get("relationKey")), text(props.get("modelCode"))}) {
                    if (StringUtils.isNotBlank(key)) {
                        order.add(key.trim());
                    }
                }
            }
            collectFormSubTableKeys(listOfMap(component.get("children")), order);
        }
    }

    private Map<String, LowcodePageModelRef> indexPageModelRefs(LowcodePageSchema pageSchema) {
        Map<String, LowcodePageModelRef> result = new LinkedHashMap<>();
        if (pageSchema == null || pageSchema.getModelRefs() == null) {
            return result;
        }
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref != null && StringUtils.isNotBlank(ref.getModelCode())) {
                result.put(ref.getModelCode(), ref);
            }
        }
        return result;
    }

    private void mergeExistingPageModelRef(LowcodePageModelRef targetRef, LowcodePageModelRef existingRef) {
        if (targetRef == null || existingRef == null) {
            return;
        }
        Map<String, Object> mergedProps = new LinkedHashMap<>();
        if (targetRef.getProps() != null) {
            mergedProps.putAll(targetRef.getProps());
        }
        if (existingRef.getProps() != null) {
            mergedProps.putAll(existingRef.getProps());
        }
        targetRef.setProps(mergedProps);
        if (StringUtils.isNotBlank(existingRef.getModelName())) {
            targetRef.setModelName(existingRef.getModelName());
        }
        if (StringUtils.isNotBlank(text(existingRef.getProps().get("tabTitle")))) {
            targetRef.getProps().put("tabTitle", text(existingRef.getProps().get("tabTitle")));
        }
        if (StringUtils.isNotBlank(text(existingRef.getProps().get("relationName")))) {
            targetRef.getProps().put("relationName", text(existingRef.getProps().get("relationName")));
        }
        if (StringUtils.isBlank(text(targetRef.getProps().get("relationKey")))
                && StringUtils.isNotBlank(text(existingRef.getProps().get("relationKey")))) {
            targetRef.getProps().put("relationKey", text(existingRef.getProps().get("relationKey")));
        }
    }

    private boolean addPageModelRef(List<LowcodePageModelRef> refs, Set<String> addedModelCodes, LowcodePageModelRef ref) {
        if (ref == null || StringUtils.isBlank(ref.getModelCode()) || addedModelCodes.contains(ref.getModelCode())) {
            return false;
        }
        refs.add(ref);
        addedModelCodes.add(ref.getModelCode());
        return true;
    }

    private boolean isEmbeddedRelation(AiBusinessObjectRelation relation) {
        if (relation == null || EnableStatus.DISABLED.matches(relation.getStatus())) {
            return false;
        }
        String relationType = StringUtils.defaultString(relation.getRelationType()).toUpperCase(Locale.ROOT);
        if (!Set.of("CHILD_LIST", "DETAIL").contains(relationType)) {
            return false;
        }
        Map<String, Object> config = readMap(relation.getRelationConfig());
        return readBoolean(config.get("showInDetail"), true)
                || readBoolean(config.get("inlineCreateEnabled"), true)
                || readBoolean(config.get("inlineEditEnabled"), true);
    }

    private boolean isReferenceLookupRelation(AiBusinessObjectRelation relation) {
        if (relation == null || EnableStatus.DISABLED.matches(relation.getStatus())) {
            return false;
        }
        String relationType = StringUtils.defaultString(relation.getRelationType()).toUpperCase(Locale.ROOT);
        return "REFERENCE".equals(relationType)
                && StringUtils.isNotBlank(relation.getSourceFieldCode())
                && StringUtils.isNotBlank(relation.getTargetFieldCode());
    }

    private Map<String, Object> toInlineRelationProps(AiBusinessObjectRelation relation) {
        Map<String, Object> config = readMap(relation.getRelationConfig());
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("relationName", relation.getRelationName());
        props.put("tabTitle", StringUtils.firstNonBlank(text(config.get("detailTabTitle")),
                text(config.get("detailTab")), relation.getRelationName()));
        props.put("sourceObjectCode", relation.getSourceObjectCode());
        props.put("targetObjectCode", relation.getTargetObjectCode());
        props.put("businessObjectCode", relation.getTargetObjectCode());
        props.put("relationKey", StringUtils.defaultIfBlank(
                text(config.get("relationKey")), defaultRelationKey(relation.getTargetObjectCode())));
        props.put("showInDetail", readBoolean(config.get("showInDetail"), true));
        props.put("inlineCreateEnabled", readBoolean(config.get("inlineCreateEnabled"), true));
        props.put("inlineEditEnabled", readBoolean(config.get("inlineEditEnabled"), true));
        props.put("saveMode", normalizeChildSaveMode(config.get("saveMode")));
        List<String> childFieldCodes = readStringList(config.get("childFieldCodes"));
        if (!childFieldCodes.isEmpty()) {
            props.put("childFieldCodes", childFieldCodes);
        }
        boolean allowSelectExisting = readBoolean(config.get("allowSelectExisting"), false);
        props.put("allowSelectExisting", allowSelectExisting);
        if (StringUtils.isNotBlank(text(config.get("displayMode")))) {
            props.put("displayMode", text(config.get("displayMode")));
        }
        if (StringUtils.isNotBlank(text(config.get("defaultFilter")))) {
            props.put("defaultFilter", text(config.get("defaultFilter")));
        }
        Object recordSelector = config.get("recordSelector");
        if (recordSelector instanceof Map<?, ?> selector && !selector.isEmpty()) {
            props.put("recordSelector", selector);
            // 面板读取扁平字段，这里反向拆解保证重新打开设计器时能回显
            props.put("selectorMultiple", readBoolean(selector.get("multiple"), true));
            List<String> displayFields = readStringList(selector.get("displayFields"));
            if (!displayFields.isEmpty()) {
                props.put("selectorDisplayFields", displayFields);
            }
            if (selector.get("filterFields") instanceof List<?> filters && !filters.isEmpty()) {
                props.put("selectorFilterFields", filters);
            }
        } else if (allowSelectExisting && StringUtils.isNotBlank(relation.getTargetObjectCode())) {
            Map<String, Object> defaultSelector = new LinkedHashMap<>();
            defaultSelector.put("objectCode", relation.getTargetObjectCode());
            defaultSelector.put("businessObjectCode", relation.getTargetObjectCode());
            defaultSelector.put("buttonText", "选择记录");
            props.put("recordSelector", defaultSelector);
        }
        Object rowActions = config.get("rowActions");
        if (rowActions instanceof List<?> actions && !actions.isEmpty()) {
            props.put("rowActions", actions);
        }
        putIfNotBlank(props, "displayField", resolveRelationDisplayField(relation));
        return props;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private String normalizeChildSaveMode(Object value) {
        return "merge".equalsIgnoreCase(text(value)) ? "merge" : "replace";
    }

    private String defaultRelationKey(String value) {
        return StringUtils.defaultString(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> toLookupRelationProps(AiBusinessObjectRelation relation) {
        Map<String, Object> config = readMap(relation.getRelationConfig());
        Map<String, Object> props = new LinkedHashMap<>();
        AiBusinessObject target = businessObjectMapper.selectByObjectCode(
                resolveTenantId(), relation.getSuiteCode(), relation.getTargetObjectCode());
        props.put("relationName", relation.getRelationName());
        props.put("sourceObjectCode", relation.getSourceObjectCode());
        props.put("targetObjectCode", relation.getTargetObjectCode());
        props.put("sourceField", relation.getSourceFieldCode());
        props.put("targetField", relation.getTargetFieldCode());
        putIfNotBlank(props, "displayField", resolveRelationDisplayField(relation, target, config));
        putIfNotBlank(props, "targetConfigKey", target == null ? null : target.getConfigKey());
        putIfNotBlank(props, "targetDisplayField", target == null ? null : target.getDisplayField());
        return props;
    }

    private LowcodeRelationSchema toRelationToPrimary(AiBusinessObjectRelation relation, String primaryObjectCode) {
        LowcodeRelationSchema schema = new LowcodeRelationSchema();
        schema.setRelationType(relation.getRelationType());
        schema.setTargetObjectCode(primaryObjectCode);
        schema.setSourceField(relation.getTargetFieldCode());
        schema.setTargetField(relation.getSourceFieldCode());
        schema.setDisplayField(resolveRelationDisplayField(relation));
        return schema;
    }

    private LowcodePageModelRef toPageModelRef(AiBusinessObject object,
                                               AiLowcodeModel model,
                                               LowcodeModelSchema schema,
                                               boolean primary) {
        LowcodePageModelRef ref = new LowcodePageModelRef();
        ref.setModelId(model == null ? null : model.getId());
        String modelCode = StringUtils.firstNonBlank(
                object.getModelCode(),
                schema == null || schema.getObject() == null ? null : schema.getObject().getCode(),
                resolveModelCode(object));
        modelCode = normalizeConfigKey(modelCode);
        ref.setModelCode(modelCode);
        ref.setModelName(StringUtils.defaultIfBlank(object.getObjectName(),
                schema == null ? modelCode : StringUtils.defaultIfBlank(schema.getBusinessName(), modelCode)));
        ref.setTableName(schema == null ? null : schema.getTableName());
        ref.setPrimary(primary);
        ref.setFields(toPageModelFields(modelCode, schema, primary));
        return ref;
    }

    private List<Map<String, Object>> toPageModelFields(String modelCode,
                                                        LowcodeModelSchema schema,
                                                        boolean primary) {
        if (schema == null || schema.getFields() == null) {
            return new ArrayList<>();
        }
        return schema.getFields().stream()
                .filter(field -> field != null)
                .map(field -> toPageModelField(modelCode, field, primary))
                .toList();
    }

    private Map<String, Object> toPageModelField(String modelCode,
                                                 LowcodeFieldSchema field,
                                                 boolean primary) {
        Map<String, Object> item = new LinkedHashMap<>();
        String fieldName = field.getField();
        item.put("field", fieldName);
        item.put("sourceField", fieldName);
        item.put("fieldRef", primary ? fieldName : safeModelKey(modelCode) + "__" + fieldName);
        item.put("rawLabel", StringUtils.defaultIfBlank(field.getLabel(), fieldName));
        item.put("label", StringUtils.defaultIfBlank(field.getLabel(), fieldName));
        item.put("columnName", field.getColumnName());
        item.put("dataType", field.getDataType());
        item.put("length", field.getLength());
        item.put("precision", field.getPrecision());
        item.put("required", field.getRequired());
        item.put("defaultValue", field.getDefaultValue());
        item.put("searchable", field.getSearchable());
        item.put("listVisible", field.getListVisible());
        item.put("formVisible", field.getFormVisible());
        item.put("componentType", field.getComponentType());
        item.put("queryType", field.getQueryType());
        item.put("dictType", field.getDictType());
        item.put("sensitiveType", field.getSensitiveType());
        item.put("encryptAlgorithm", field.getEncryptAlgorithm());
        item.put("sortable", field.getSortable());
        item.put("primaryKey", field.getPrimaryKey());
        item.put("systemField", field.getSystemField());
        item.put("readonly", field.getReadonly());
        item.put("fieldStatus", field.getFieldStatus());
        item.put("autoIncrement", field.getAutoIncrement());
        item.put("width", field.getWidth());
        item.put("remark", field.getRemark());
        // 子表运行态控件依赖这些配置（选项源/引用对象/公式），快照缺失会让下拉、引用退化为输入框
        item.put("referenceObjectCode", field.getReferenceObjectCode());
        item.put("referenceDisplayField", field.getReferenceDisplayField());
        item.put("basicProps", field.getBasicProps() == null ? null : new LinkedHashMap<>(field.getBasicProps()));
        item.put("advancedProps", field.getAdvancedProps() == null ? null : new LinkedHashMap<>(field.getAdvancedProps()));
        item.put("formulaConfig", field.getFormulaConfig() == null ? null : new LinkedHashMap<>(field.getFormulaConfig()));
        return item;
    }

    private void syncInlineEditRefsToEditZone(LowcodePageSchema pageSchema,
                                              LowcodePageModelRef primaryRef,
                                              List<String> childFieldRefs) {
        if (pageSchema.getZones() == null) {
            pageSchema.setZones(new ArrayList<>());
        }
        LowcodePageZone editZone = pageSchema.getZones().stream()
                .filter(zone -> zone != null && "edit".equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
        if (editZone == null) {
            editZone = new LowcodePageZone();
            editZone.setZoneKey("edit");
            editZone.setComponentKey("edit-form");
            editZone.setEnabled(true);
            editZone.setProps(new LinkedHashMap<>());
            pageSchema.getZones().add(editZone);
        }
        Set<String> primaryFields = primaryRef.getFields().stream()
                .map(item -> text(item.get("fieldRef")))
                .filter(StringUtils::isNotBlank)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<String> primaryRefs = editZone.getFieldRefs() == null
                ? new ArrayList<>()
                : editZone.getFieldRefs().stream()
                .filter(primaryFields::contains)
                .toList();
        if (primaryRefs.isEmpty()) {
            primaryRefs = primaryRef.getFields().stream()
                    .filter(item -> !Boolean.TRUE.equals(item.get("systemField")))
                    .filter(item -> !Boolean.FALSE.equals(item.get("formVisible")))
                    .map(item -> text(item.get("fieldRef")))
                    .filter(StringUtils::isNotBlank)
                    .toList();
        }
        Set<String> childFields = new LinkedHashSet<>(childFieldRefs);
        List<String> selectedChildRefs = editZone.getFieldRefs() == null
                ? new ArrayList<>()
                : editZone.getFieldRefs().stream()
                .filter(childFields::contains)
                .toList();
        Map<String, Object> props = editZone.getProps() == null ? Map.of() : editZone.getProps();
        boolean customRelationFields = "CUSTOM".equalsIgnoreCase(text(props.get("relationFieldSelectionMode")))
                || readBoolean(props.get("relationFieldSelectionTouched"), false);
        if (selectedChildRefs.isEmpty() && !customRelationFields) {
            selectedChildRefs = childFieldRefs;
        }
        LinkedHashSet<String> refs = new LinkedHashSet<>(primaryRefs);
        refs.addAll(selectedChildRefs);
        editZone.setFieldRefs(new ArrayList<>(refs));
    }

    private String safeModelKey(String value) {
        String key = StringUtils.defaultIfBlank(value, "model").replaceAll("[^A-Za-z0-9_]", "_");
        return StringUtils.defaultIfBlank(key, "model");
    }

    private LowcodeRelationSchema toLowcodeRelation(AiBusinessObjectRelation relation) {
        LowcodeRelationSchema schema = new LowcodeRelationSchema();
        schema.setRelationType(relation.getRelationType());
        schema.setTargetObjectCode(resolveRelationModelCode(relation.getSuiteCode(), relation.getTargetObjectCode()));
        schema.setSourceField(relation.getSourceFieldCode());
        schema.setTargetField(relation.getTargetFieldCode());
        schema.setDisplayField(resolveRelationDisplayField(relation));
        return schema;
    }

    private String resolveRelationDisplayField(AiBusinessObjectRelation relation) {
        if (relation == null) {
            return null;
        }
        Map<String, Object> config = readMap(relation.getRelationConfig());
        AiBusinessObject target = businessObjectMapper.selectByObjectCode(
                resolveTenantId(), relation.getSuiteCode(), relation.getTargetObjectCode());
        return resolveRelationDisplayField(relation, target, config);
    }

    private String resolveRelationDisplayField(AiBusinessObjectRelation relation,
                                               AiBusinessObject target,
                                               Map<String, Object> config) {
        String configured = config == null ? null : text(config.get("displayField"));
        return StringUtils.firstNonBlank(configured, target == null ? null : target.getDisplayField());
    }

    private String resolveRelationModelCode(String suiteCode, String objectCode) {
        if (StringUtils.isBlank(objectCode)) {
            return objectCode;
        }
        AiBusinessObject object = businessObjectMapper.selectByObjectCode(resolveTenantId(), suiteCode, objectCode);
        if (object == null) {
            return normalizeConfigKey(objectCode);
        }
        return resolveModelCode(object);
    }

    private void saveSourceRelations(AiBusinessObject object, List<BusinessObjectRelationDTO> relations) {
        List<Long> savedIds = new ArrayList<>();
        for (BusinessObjectRelationDTO dto : relations) {
            if (dto == null) {
                continue;
            }
            AiBusinessObjectRelation relation = dto.getId() == null
                    ? new AiBusinessObjectRelation()
                    : relationMapper.selectRelationById(resolveTenantId(), dto.getId());
            if (relation == null) {
                relation = new AiBusinessObjectRelation();
            }
            relation.setTenantId(resolveTenantId());
            relation.setSuiteCode(object.getSuiteCode());
            relation.setSourceObjectCode(object.getObjectCode());
            relation.setTargetObjectCode(StringUtils.trimToNull(dto.getTargetObjectCode()));
            relation.setRelationType(StringUtils.defaultIfBlank(dto.getRelationType(), "REFERENCE").toUpperCase(Locale.ROOT));
            relation.setRelationName(StringUtils.defaultIfBlank(dto.getRelationName(), relation.getTargetObjectCode()));
            relation.setSourceFieldCode(StringUtils.trimToNull(dto.getSourceFieldCode()));
            relation.setTargetFieldCode(StringUtils.trimToNull(dto.getTargetFieldCode()));
            relation.setRelationConfig(StringUtils.trimToNull(dto.getRelationConfig()));
            relation.setDescription(StringUtils.trimToNull(dto.getDescription()));
            relation.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            relation.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
            if (relation.getId() == null) {
                relationMapper.insert(relation);
            } else {
                relationMapper.updateById(relation);
            }
            savedIds.add(relation.getId());
        }
        relationMapper.deleteMissingRelations(resolveTenantId(), object.getSuiteCode(), object.getObjectCode(), savedIds);
    }

    /**
     * 运行时主子表渲染只依赖持久化的对象关系；表单里拖入子表组件后，这里在保存时
     * 自动补齐子对象外键字段和 CHILD_LIST 关系，兑现设计器中“系统自动关联”的承诺。
     * 面板配置是唯一事实来源：已存在的关系全量覆盖，表单里删掉的组件对应关系一并移除。
     */
    private boolean ensureChildTableRelations(DesignerContext context, FormDesignerSchemaDTO formSchema) {
        if (context == null || context.getObject() == null || formSchema == null) {
            return false;
        }
        AiBusinessObject object = context.getObject();
        List<Map<String, Object>> subTables = new ArrayList<>();
        collectSubTableComponents(formSchema.getComponents(), subTables);
        collectSubTableComponents(formSchema.getForms(), subTables);
        log.info("[子表自动关联] collected subTables.size={} componentsFrom.components={} componentsFrom.forms={}",
                subTables.size(),
                formSchema.getComponents() == null ? 0 : formSchema.getComponents().size(),
                formSchema.getForms() == null ? 0 : formSchema.getForms().size());
        List<AiBusinessObjectRelation> existing = relationMapper.selectRuntimeRelationsBySource(
                resolveTenantId(), object.getSuiteCode(), object.getObjectCode());
        boolean changed = false;
        Set<String> handledTargets = new LinkedHashSet<>();
        for (Map<String, Object> component : subTables) {
            Map<String, Object> props = mapValue(component.get("props"));
            String targetObjectCode = StringUtils.trimToNull(text(props.get("modelCode")));
            if (targetObjectCode == null || targetObjectCode.equals(object.getObjectCode())
                    || !handledTargets.add(targetObjectCode)) {
                continue;
            }
            AiBusinessObjectRelation existingRelation = findEmbeddedRelationTo(existing, targetObjectCode);
            AiBusinessObject child = businessObjectMapper.selectByObjectCode(
                    resolveTenantId(), object.getSuiteCode(), targetObjectCode);
            if (child == null) {
                continue;
            }
            // 关系已存在时仍要确保外键字段在子对象模型里，否则库表永远补不上外键列。
            String foreignKeyField = ensureChildForeignKeyField(object, child);
            if (existingRelation != null) {
                if (StringUtils.isNotBlank(foreignKeyField)
                        && !StringUtils.equals(foreignKeyField, existingRelation.getTargetFieldCode())) {
                    existingRelation.setTargetFieldCode(foreignKeyField);
                    relationMapper.updateById(existingRelation);
                    changed = true;
                }
                if (overwriteChildRelationConfig(existingRelation, props)) {
                    relationMapper.updateById(existingRelation);
                    log.info("[子表自动关联] overwritten existing relation id={} target={}",
                            existingRelation.getId(), targetObjectCode);
                    changed = true;
                }
                continue;
            }
            if (StringUtils.isBlank(foreignKeyField)) {
                continue;
            }
            insertChildTableRelation(object, child, props, foreignKeyField);
            changed = true;
        }
        for (AiBusinessObjectRelation relation : existing) {
            if (relation == null || handledTargets.contains(relation.getTargetObjectCode())
                    || !AUTO_SUBTABLE_RELATION_DESC.equals(StringUtils.trimToEmpty(relation.getDescription()))) {
                continue;
            }
            relationMapper.deleteById(relation.getId());
            log.info("[子表自动关联] removed stale auto relation id={} target={}",
                    relation.getId(), relation.getTargetObjectCode());
            changed = true;
        }
        return changed;
    }

    private void collectSubTableComponents(List<Map<String, Object>> components, List<Map<String, Object>> result) {
        if (components == null) {
            return;
        }
        for (Map<String, Object> component : components) {
            if (component == null) {
                continue;
            }
            String componentKey = text(component.get("componentKey"));
            if ("subTable".equals(componentKey) || "forgeSubTable".equals(componentKey)) {
                result.add(component);
            }
            collectSubTableComponents(listOfMap(component.get("children")), result);
            // 多表单协议的节点形如 { formKey, schema: { components: [...] } }；
            // 同时兼容少量历史数据把 components 直接放在表单节点上的结构。
            collectSubTableComponents(listOfMap(component.get("components")), result);
            Map<String, Object> nestedSchema = mapValue(component.get("schema"));
            collectSubTableComponents(listOfMap(nestedSchema.get("components")), result);
        }
    }

    private AiBusinessObjectRelation findEmbeddedRelationTo(List<AiBusinessObjectRelation> relations,
                                                            String targetObjectCode) {
        if (relations == null) {
            return null;
        }
        return relations.stream()
                .filter(relation -> relation != null
                        && targetObjectCode.equals(relation.getTargetObjectCode())
                        && isEmbeddedRelation(relation))
                .findFirst()
                .orElse(null);
    }

    /**
     * 面板配置是唯一事实来源：标题、开关、展示模式、显示字段全量覆盖关系记录。
     *
     * @return 配置是否有变化
     */
    private boolean overwriteChildRelationConfig(AiBusinessObjectRelation relation, Map<String, Object> props) {
        String relationName = StringUtils.firstNonBlank(StringUtils.trimToNull(text(props.get("header"))),
                relation.getRelationName(), relation.getTargetObjectCode());
        Map<String, Object> config = buildSubTableRelationConfig(
                relation.getTargetObjectCode(), relationName, props);
        String configJson = writeJson(config, "relationConfig");
        boolean changed = !StringUtils.equals(relation.getRelationName(), relationName)
                || !jsonEquals(relation.getRelationConfig(), configJson)
                || !AUTO_SUBTABLE_RELATION_DESC.equals(StringUtils.trimToEmpty(relation.getDescription()));
        if (!changed) {
            return false;
        }
        relation.setRelationName(relationName);
        relation.setRelationConfig(configJson);
        relation.setDescription(AUTO_SUBTABLE_RELATION_DESC);
        return true;
    }

    /**
     * 关系配置按 JSON 语义比较，避免空白/键序差异导致每次 designPreview 都 updateById。
     */
    private boolean jsonEquals(String left, String right) {
        if (StringUtils.equals(left, right)) {
            return true;
        }
        if (StringUtils.isBlank(left) && StringUtils.isBlank(right)) {
            return true;
        }
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return false;
        }
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (Exception e) {
            log.debug("[子表自动关联] relationConfig JSON解析失败，按文本处理", e);
            return false;
        }
    }

    private Map<String, Object> buildSubTableRelationConfig(String targetObjectCode, String relationName,
                                                            Map<String, Object> props) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("showInDetail", true);
        config.put("inlineCreateEnabled", readBoolean(props.get("allowCreate"), true));
        config.put("inlineEditEnabled", true);
        config.put("saveMode", "replace");
        config.put("detailTabTitle", relationName);
        config.put("allowSelectExisting", readBoolean(props.get("allowSelectExisting"), false));
        String displayMode = StringUtils.trimToNull(text(props.get("displayMode")));
        if (displayMode != null) {
            config.put("displayMode", displayMode);
        }
        config.put("relationKey", StringUtils.defaultIfBlank(
                StringUtils.trimToNull(text(props.get("relationKey"))), defaultRelationKey(targetObjectCode)));
        List<String> childFieldCodes = extractChildFieldCodes(props);
        if (!childFieldCodes.isEmpty()) {
            config.put("childFieldCodes", childFieldCodes);
        }
        if (readBoolean(props.get("allowSelectExisting"), false)) {
            Map<String, Object> defaultSelector = new LinkedHashMap<>();
            defaultSelector.put("objectCode", targetObjectCode);
            defaultSelector.put("businessObjectCode", targetObjectCode);
            defaultSelector.put("buttonText", "选择记录");
            defaultSelector.put("multiple", readBoolean(props.get("selectorMultiple"), true));
            List<String> displayFields = readStringList(props.get("selectorDisplayFields"));
            if (!displayFields.isEmpty()) {
                defaultSelector.put("displayFields", displayFields);
                defaultSelector.put("keywordFields", displayFields);
            }
            List<Map<String, Object>> filterFields = readSelectorFilterFields(props.get("selectorFilterFields"));
            if (!filterFields.isEmpty()) {
                defaultSelector.put("filterFields", filterFields);
            }
            config.put("recordSelector", defaultSelector);
        }
        return config;
    }

    /**
     * 读取面板配置的“筛选字段”：弹窗顶部可供使用者输入的筛选条件，
     * 每条支持默认值——固定值或 ${form.主表字段} 联动占位。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readSelectorFilterFields(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) raw);
            String fieldCode = StringUtils.trimToNull(text(row.get("fieldCode")));
            if (fieldCode == null) {
                continue;
            }
            row.put("fieldCode", fieldCode);
            result.add(row);
        }
        return result;
    }

    private List<String> extractChildFieldCodes(Map<String, Object> props) {
        Object columns = props.get("columns");
        if (!(columns instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String code = null;
            if (item instanceof Map<?, ?> column) {
                code = StringUtils.trimToNull(text(column.get("fieldCode")));
                if (code == null) {
                    code = StringUtils.trimToNull(text(column.get("field")));
                }
            } else {
                code = StringUtils.trimToNull(text(item));
            }
            if (code != null && !result.contains(code)) {
                result.add(code);
            }
        }
        return result;
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> StringUtils.trimToNull(text(item)))
                .filter(item -> item != null)
                .toList();
    }

    /**
     * 确保子对象存在指向主对象的外键字段；缺失时自动追加隐藏的 bigint 字段并保存子对象草稿。
     *
     * @return 外键字段编码（camelCase），子对象模型不可用时返回 null
     */
    private String ensureChildForeignKeyField(AiBusinessObject master, AiBusinessObject child) {
        DesignerContext childContext = loadContext(child.getId());
        LowcodeModelSchema childModel = childContext.getModelSchema();
        if (childModel == null) {
            return null;
        }
        String fieldCode = childForeignKeyFieldCode(master.getObjectCode());
        String columnName = camelToSnakeCase(fieldCode);
        List<LowcodeFieldSchema> fields = childModel.getFields() == null
                ? new ArrayList<>()
                : new ArrayList<>(childModel.getFields());
        for (LowcodeFieldSchema field : fields) {
            if (field == null) {
                continue;
            }
            if (fieldCode.equals(field.getField()) || columnName.equals(field.getColumnName())) {
                return StringUtils.defaultIfBlank(field.getField(), fieldCode);
            }
        }
        LowcodeFieldSchema foreignKey = new LowcodeFieldSchema();
        foreignKey.setField(fieldCode);
        foreignKey.setColumnName(columnName);
        foreignKey.setLabel("所属" + StringUtils.defaultIfBlank(master.getObjectName(), master.getObjectCode()));
        foreignKey.setDataType("bigint");
        foreignKey.setBusinessFieldType("NUMBER");
        foreignKey.setComponentType("number");
        foreignKey.setRequired(false);
        foreignKey.setListVisible(false);
        foreignKey.setFormVisible(false);
        foreignKey.setFieldStatus("ENABLED");
        foreignKey.setSortOrder(fields.size());
        foreignKey.setRemark("子表组件自动外键，关联 " + master.getObjectCode() + ".id");
        fields.add(foreignKey);
        childModel.setFields(fields);
        childContext.setModelSchema(childModel);
        saveDraft(childContext, BusinessObjectDesignStatus.CHANGED.getCode());
        return fieldCode;
    }

    private void insertChildTableRelation(AiBusinessObject master, AiBusinessObject child,
                                          Map<String, Object> props, String foreignKeyField) {
        String relationName = StringUtils.firstNonBlank(StringUtils.trimToNull(text(props.get("header"))),
                child.getObjectName(), child.getObjectCode());
        Map<String, Object> config = buildSubTableRelationConfig(child.getObjectCode(), relationName, props);

        AiBusinessObjectRelation relation = new AiBusinessObjectRelation();
        relation.setTenantId(resolveTenantId());
        relation.setSuiteCode(master.getSuiteCode());
        relation.setSourceObjectCode(master.getObjectCode());
        relation.setTargetObjectCode(child.getObjectCode());
        relation.setRelationType("CHILD_LIST");
        relation.setRelationName(relationName);
        relation.setSourceFieldCode("id");
        relation.setTargetFieldCode(foreignKeyField);
        relation.setRelationConfig(writeJson(config, "relationConfig"));
        relation.setDescription(AUTO_SUBTABLE_RELATION_DESC);
        relation.setStatus(EnableStatus.ENABLED.getCode());
        relation.setSortOrder(0);
        relationMapper.insert(relation);
    }

    private String childForeignKeyFieldCode(String masterObjectCode) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : StringUtils.defaultString(masterObjectCode).toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result + "Id";
    }

    private String camelToSnakeCase(String value) {
        return StringUtils.defaultString(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private void restoreRelationsFromSnapshot(AiBusinessObject object, String relationSnapshot) {
        if (StringUtils.isBlank(relationSnapshot)) {
            return;
        }
        List<Map<String, Object>> relations;
        try {
            relations = objectMapper.readValue(relationSnapshot, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException("关系快照格式不正确");
        }
        List<BusinessObjectRelationDTO> dtoList = new ArrayList<>();
        for (Map<String, Object> item : relations) {
            if (item == null || !object.getObjectCode().equals(text(item.get("sourceObjectCode")))) {
                continue;
            }
            BusinessObjectRelationDTO dto = new BusinessObjectRelationDTO();
            dto.setId(numberAsLong(item.get("id")));
            dto.setTargetObjectCode(text(item.get("targetObjectCode")));
            dto.setRelationType(text(item.get("relationType")));
            dto.setRelationName(text(item.get("relationName")));
            dto.setSourceFieldCode(text(item.get("sourceFieldCode")));
            dto.setTargetFieldCode(text(item.get("targetFieldCode")));
            dto.setRelationConfig(text(item.get("relationConfig")));
            dto.setDescription(text(item.get("description")));
            dto.setStatus(numberAsInteger(item.get("status")));
            dto.setSortOrder(numberAsInteger(item.get("sortOrder")));
            dtoList.add(dto);
        }
        saveSourceRelations(object, dtoList);
    }

    private void restoreDesignerOptionsFromSnapshot(AiBusinessObject object, String designerOptionsSnapshot) {
        if (object == null) {
            return;
        }
        object.setDesignerOptions(StringUtils.isBlank(designerOptionsSnapshot) ? "{}" : designerOptionsSnapshot);
    }

    private AiLowcodeDomain resolveDomain(AiBusinessObject object, LowcodeModelSchema schema) {
        LowcodeDomainRef domainRef = schema == null ? null : schema.getDomain();
        if (domainRef != null && domainRef.getId() != null) {
            try {
                return domainService.requireDomain(domainRef.getId());
            } catch (BusinessException ignored) {
                // 继续按编码兜底。
            }
        }
        String domainCode = StringUtils.firstNonBlank(
                domainRef == null ? null : domainRef.getCode(),
                object.getSuiteCode(),
                GENERAL_DOMAIN_CODE);
        AiLowcodeDomain domain = domainService.getByCode(domainCode);
        if (domain == null && StringUtils.isNotBlank(domainCode)) {
            domain = domainService.getByCode(domainCode.toLowerCase(Locale.ROOT));
        }
        if (domain == null) {
            domain = domainService.getByCode(GENERAL_DOMAIN_CODE);
        }
        return domain;
    }

    private String resolveDesignStatus(AiBusinessObject object, AiCrudConfig config) {
        if (StringUtils.isNotBlank(object.getDesignStatus())) {
            return object.getDesignStatus();
        }
        if (config != null && "PUBLISHED".equals(config.getPublishStatus())) {
            return BusinessObjectDesignStatus.PUBLISHED.getCode();
        }
        return BusinessObjectDesignStatus.DRAFT.getCode();
    }

    private boolean hasUnpublishedChanges(AiBusinessObject object, AiCrudConfig config) {
        String designStatus = BusinessObjectDesignStatus.normalize(object.getDesignStatus());
        if (BusinessObjectDesignStatus.CHANGED.matches(designStatus)
                || BusinessObjectDesignStatus.DESIGNING.matches(designStatus)
                || BusinessObjectDesignStatus.READY.matches(designStatus)) {
            return true;
        }
        if (config == null) {
            return true;
        }
        if (!"PUBLISHED".equals(config.getPublishStatus())) {
            return true;
        }
        if (BusinessObjectDesignStatus.PUBLISHED.matches(designStatus)) {
            return false;
        }
        return object.getLastPublishVersion() == null && config.getPublishedVersion() == null;
    }

    private boolean hasBusinessFields(LowcodeModelSchema modelSchema) {
        return modelSchema != null && modelSchema.getFields() != null
                && modelSchema.getFields().stream().anyMatch(field -> field != null && !Boolean.TRUE.equals(field.getSystemField()));
    }

    private String resolveModelCode(AiBusinessObject object) {
        return normalizeConfigKey(StringUtils.firstNonBlank(object.getModelCode(), object.getObjectCode(), "business_object"));
    }

    private String resolveConfigKey(AiBusinessObject object) {
        String preferred = StringUtils.firstNonBlank(object.getConfigKey(), object.getModelCode(),
                object.getSuiteCode() + "_" + object.getObjectCode());
        String base = normalizeConfigKey(preferred);
        AiCrudConfig existing = crudConfigMapper.selectByConfigKey(resolveTenantId(), base);
        if (existing == null || StringUtils.equals(existing.getObjectCode(), resolveModelCode(object))) {
            return base;
        }
        return normalizeConfigKey(base + "_" + object.getId());
    }

    private String normalizeConfigKey(String value) {
        String normalized = StringUtils.defaultString(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[^a-z]+", "")
                .replaceAll("_+$", "");
        if (StringUtils.isBlank(normalized)) {
            normalized = "business_object";
        }
        return StringUtils.left(normalized, 64);
    }

    private String normalizeTableName(String value) {
        String tableName = normalizeConfigKey(value);
        if (tableName.startsWith("sys_") || tableName.startsWith("ai_") || tableName.startsWith("gen_")
                || tableName.startsWith("flow_") || tableName.startsWith("data_")) {
            tableName = "biz_" + tableName;
        }
        return StringUtils.left(tableName, 64);
    }

    private FormDesignerSchemaDTO resolveFormDesignerSchema(
            AiBusinessObject object,
            LowcodeModelSchema modelSchema,
            LowcodePageSchema pageSchema,
            Map<String, Object> designerOptions) {
        return formSchemaAssembler().resolveFormDesignerSchema(
                object, modelSchema, pageSchema, designerOptions);
    }

    private String resolveFormComponentKey(LowcodeFieldSchema field) {
        return formSchemaAssembler().resolveFormComponentKey(field);
    }

    private BusinessObjectFormSchemaAssembler formSchemaAssembler() {
        return new BusinessObjectFormSchemaAssembler(objectMapper);
    }
    private ViewSchemaDTO resolveViewSchema(
            LowcodeModelSchema modelSchema,
            LowcodePageSchema pageSchema,
            Map<String, Object> designerOptions) {
        return viewSchemaProjector().resolveViewSchema(modelSchema, pageSchema, designerOptions);
    }

    private BusinessObjectViewSchemaProjector viewSchemaProjector() {
        return new BusinessObjectViewSchemaProjector(objectMapper, this::resolveFormComponentKey);
    }
    private LinkageSchemaDTO resolveLinkageSchema(Map<String, Object> designerOptions) {
        return linkagePolicy().resolveLinkageSchema(designerOptions);
    }

    private FormDesignerSchemaDTO hydrateFormFieldLinkages(
            FormDesignerSchemaDTO formSchema,
            LinkageSchemaDTO legacyLinkageSchema) {
        return linkagePolicy().hydrateFormFieldLinkages(formSchema, legacyLinkageSchema);
    }

    private LinkageSchemaDTO resolveUnifiedLinkageSchema(
            FormDesignerSchemaDTO formSchema,
            LinkageSchemaDTO legacyLinkageSchema) {
        return linkagePolicy().resolveUnifiedLinkageSchema(formSchema, legacyLinkageSchema);
    }

    private BusinessObjectLinkagePolicy linkagePolicy() {
        return new BusinessObjectLinkagePolicy(objectMapper);
    }

    private boolean hasFormFieldLinkages(FormDesignerSchemaDTO formSchema) {
        return linkagePolicy().hasFormFieldLinkages(formSchema);
    }
    private boolean hasDesignerOption(Map<String, Object> designerOptions, String key) {
        if (designerOptions == null || !designerOptions.containsKey(key)) {
            return false;
        }
        Object value = designerOptions.get(key);
        if (value instanceof String text) {
            return StringUtils.isNotBlank(text);
        }
        return value != null;
    }

    private void applyFormDesignerSchemaToEditZone(
            LowcodePageSchema pageSchema,
            LowcodeModelSchema modelSchema,
            FormDesignerSchemaDTO formSchema) {
        runtimeFormProjector().applyFormDesignerSchemaToEditZone(pageSchema, modelSchema, formSchema);
    }

    private BusinessObjectRuntimeFormProjector runtimeFormProjector() {
        return new BusinessObjectRuntimeFormProjector(this::isDynamicOptionSource);
    }
    private void applyViewSchemaToPageZones(
            LowcodePageSchema pageSchema,
            LowcodeModelSchema modelSchema,
            ViewSchemaDTO viewSchema) {
        viewSchemaProjector().applyViewSchemaToPageZones(pageSchema, modelSchema, viewSchema);
    }
    private void applyLinkageSchemaToModel(
            LowcodeModelSchema modelSchema,
            LinkageSchemaDTO linkageSchema) {
        linkagePolicy().applyLinkageSchemaToModel(modelSchema, linkageSchema);
    }
    private LowcodePageZone findOrCreateZone(LowcodePageSchema pageSchema, String zoneKey, String componentKey) {
        if (pageSchema.getZones() == null) {
            pageSchema.setZones(new ArrayList<>());
        }
        LowcodePageZone zone = pageSchema.getZones().stream()
                .filter(item -> item != null && zoneKey.equals(item.getZoneKey()))
                .findFirst()
                .orElse(null);
        if (zone != null) {
            if (StringUtils.isBlank(zone.getComponentKey())) {
                zone.setComponentKey(componentKey);
            }
            if (zone.getProps() == null) {
                zone.setProps(new LinkedHashMap<>());
            }
            return zone;
        }
        zone = new LowcodePageZone();
        zone.setZoneKey(zoneKey);
        zone.setComponentKey(componentKey);
        zone.setEnabled(true);
        zone.setFieldRefs(new ArrayList<>());
        zone.setProps(new LinkedHashMap<>());
        pageSchema.getZones().add(zone);
        return zone;
    }

    private LowcodePageZone findZone(LowcodePageSchema pageSchema, String zoneKey) {
        if (pageSchema == null || pageSchema.getZones() == null) {
            return null;
        }
        return pageSchema.getZones().stream()
                .filter(zone -> zone != null && zoneKey.equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
    }

    private void replaceModelFieldSettings(Map<String, Object> props, Set<String> modelFields,
                                           Map<String, Object> compiledSettings) {
        Map<String, Object> existing = new LinkedHashMap<>(mapValue(props.get("fieldSettings")));
        modelFields.forEach(existing::remove);
        existing.putAll(compiledSettings);
        props.put("fieldSettings", existing);
    }

    private List<Map<String, Object>> visibleSortedItems(List<Map<String, Object>> items) {
        return items.stream()
                .filter(item -> item != null && !isFalse(item.get("visible")))
                .sorted(Comparator.comparingInt(item -> integerValue(item.get("order"), 0)))
                .toList();
    }

    private List<Map<String, Object>> flattenFormComponents(List<Map<String, Object>> components) {
        List<Map<String, Object>> result = new ArrayList<>();
        collectFormComponents(components, result);
        return result;
    }

    private void collectFormComponents(List<Map<String, Object>> components, List<Map<String, Object>> result) {
        if (components == null) {
            return;
        }
        for (Map<String, Object> component : components) {
            if (component == null) {
                continue;
            }
            result.add(component);
            collectFormComponents(listOfMap(component.get("children")), result);
        }
    }

    private Map<String, LowcodeFieldSchema> lowcodeFieldMap(LowcodeModelSchema modelSchema) {
        Map<String, LowcodeFieldSchema> fields = new LinkedHashMap<>();
        if (modelSchema != null && modelSchema.getFields() != null) {
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field != null && StringUtils.isNotBlank(field.getField())) {
                    fields.put(field.getField(), field);
                }
            }
        }
        return fields;
    }

    private String normalizeRuntimeComponentType(String componentKey) {
        return switch (StringUtils.defaultString(componentKey)) {
            case "inputNumber", "input-number", "inputnumber", "integer", "money" -> "number";
            case "upload" -> "fileUpload";
            case "orgSelect", "departmentSelect", "departmentTreeSelect", "deptSelect", "deptTreeSelect",
                    "elTreeSelect", "orgName", "deptName" -> "orgTreeSelect";
            case "userPicker", "userName" -> "userSelect";
            default -> componentKey;
        };
    }

    private String normalizeFormComponentKey(String componentKey) {
        String normalized = StringUtils.defaultIfBlank(componentKey, "input");
        if ("inputNumber".equals(normalized) || "input-number".equals(normalized) || "inputnumber".equals(normalized)) {
            return "number";
        }
        return normalized;
    }

    private String normalizeAlign(String value) {
        String align = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "center", "right").contains(align) ? align : "left";
    }

    private String normalizeLabelAlign(String value) {
        String align = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "right").contains(align) ? align : "right";
    }

    private String normalizeRuntimeFormSize(String value) {
        String size = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        if ("default".equals(size) || "medium".equals(size)) {
            return "medium";
        }
        return Set.of("small", "large").contains(size) ? size : "medium";
    }

    private String normalizeFormOpenMode(String value) {
        String mode = StringUtils.defaultString(value).trim();
        if ("tabWorkspace".equalsIgnoreCase(mode)) {
            return "tabWorkspace";
        }
        String normalized = mode.toLowerCase(Locale.ROOT);
        return Set.of("modal", "drawer", "flat").contains(normalized) ? normalized : "modal";
    }

    private String normalizeDrawerPlacement(Object value, Object fallback) {
        String placement = StringUtils.defaultString(text(value)).trim().toLowerCase(Locale.ROOT);
        if (Set.of("left", "right", "top", "bottom").contains(placement)) {
            return placement;
        }
        String fallbackPlacement = StringUtils.defaultString(text(fallback)).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "right", "top", "bottom").contains(fallbackPlacement) ? fallbackPlacement : "right";
    }

    /**
     * 表单设计器布局中的打开方式/弹窗宽度/抽屉方向/折叠配置同步到编辑区 props。
     *
     * <p>与前端 buildFormDesignerEditZone 的同步语义保持一致：打开方式总是写入，
     * 宽度类配置仅在表单设计器显式提供时覆盖，避免默认值清空列表设计器的自定义设置。</p>
     */
    private void applyFormLayoutOpenModeAndModalProps(Map<String, Object> props, Map<String, Object> layout) {
        String formOpenMode = normalizeFormOpenMode(StringUtils.firstNonBlank(
                text(layout.get("formOpenMode")), text(layout.get("modalType"))));
        props.put("formOpenMode", formOpenMode);
        props.put("modalType", "modal".equals(formOpenMode) || "drawer".equals(formOpenMode) ? formOpenMode : "modal");
        putIfNotBlank(props, "modalWidth", StringUtils.trimToNull(text(layout.get("modalWidth"))));
        putIfNotBlank(props, "detailModalWidth", StringUtils.trimToNull(text(layout.get("detailModalWidth"))));
        props.put("drawerPlacement", normalizeDrawerPlacement(layout.get("drawerPlacement"), props.get("drawerPlacement")));
        props.put("enableCollapse", readBoolean(layout.get("enableCollapse"), false));
        int maxVisibleFields = integerValue(layout.get("maxVisibleFields"), 0);
        if (maxVisibleFields > 0) {
            props.put("maxVisibleFields", maxVisibleFields);
        }
    }

    private String normalizeFixed(String value) {
        String fixed = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "right").contains(fixed) ? fixed : null;
    }

    private boolean isFalse(Object value) {
        return Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(text(value)) || "0".equals(text(value));
    }

    private boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return StringUtils.isBlank(text);
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private int integerValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                String digits = text.trim().replaceAll("[^0-9-]", "");
                if (StringUtils.isBlank(digits) || "-".equals(digits)) {
                    return defaultValue;
                }
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignoredAgain) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private Object getNestedValue(Map<String, Object> source, String path) {
        if (source == null || StringUtils.isBlank(path)) {
            return null;
        }
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 表单设计器里的开关默认值可能是 true/false，落到 tinyint 列必须是 0/1。
     */
    private Object normalizeDesignerDefaultValue(LowcodeFieldSchema field, Object defaultValue) {
        if (defaultValue == null || field == null) {
            return defaultValue;
        }
        String dataType = StringUtils.defaultString(field.getDataType()).toLowerCase(Locale.ROOT);
        String fieldType = StringUtils.defaultString(field.getBusinessFieldType()).toUpperCase(Locale.ROOT);
        String componentType = StringUtils.defaultString(field.getComponentType()).toLowerCase(Locale.ROOT);
        boolean switchLike = "tinyint".equals(dataType)
                || "SWITCH".equals(fieldType)
                || "switch".equals(componentType);
        if (!switchLike) {
            return defaultValue;
        }
        if (defaultValue instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        String text = String.valueOf(defaultValue).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return 1;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return 0;
        }
        return defaultValue;
    }

    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(this::mapValue)
                .toList();
    }

    private Map<String, Object> readMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> resolveDesignerOptions(AiBusinessObject object, AiCrudConfig config) {
        Map<String, Object> result = readMap(object == null ? null : object.getDesignerOptions());
        Map<String, Object> runtimeOptions = readMap(config == null ? null : config.getOptions());
        copyOptionFallback(result, runtimeOptions, FORM_DESIGNER_SCHEMA_OPTION_KEY);
        copyOptionFallback(result, runtimeOptions, DESIGNER_ACTIONS_OPTION_KEY);
        return result;
    }

    private void copyOptionFallback(Map<String, Object> target, Map<String, Object> fallback, String key) {
        if (!target.containsKey(key) && fallback.containsKey(key)) {
            target.put(key, fallback.get(key));
        }
    }

    private String mergeFormDesignerSchemaIntoRuntimeOptions(String optionsJson, LowcodePageSchema pageSchema) {
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        Object formSchema = editZone == null || editZone.getProps() == null
                ? null : editZone.getProps().get(FORM_DESIGNER_SCHEMA_OPTION_KEY);
        if (formSchema == null) {
            return optionsJson;
        }
        Map<String, Object> options = readMap(optionsJson);
        options.put(FORM_DESIGNER_SCHEMA_OPTION_KEY, formSchema);
        return writeJson(options, "options");
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
        String text = StringUtils.trimToEmpty(String.valueOf(value));
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private <T> T readJson(String json, Class<T> type, String fieldName) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException(fieldName + "格式不正确");
        }
    }

    public String writeJson(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(fieldName + "序列化失败");
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long numberAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Long.valueOf(text);
        }
        return null;
    }

    private Integer numberAsInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Integer.valueOf(text);
        }
        return null;
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

    @Data
    public static class DesignerContext {
        private AiBusinessObject object;
        private BusinessObjectVO objectVO;
        private AiLowcodeModel model;
        private AiCrudConfig config;
        private LowcodeModelSchema modelSchema;
        private LowcodePageSchema pageSchema;
        private List<BusinessObjectRelationVO> relations = new ArrayList<>();
    }

    private record ComponentFieldDefaults(String fieldType, String dataType, Integer length,
                                          Integer precision, String queryType) {
    }
}
