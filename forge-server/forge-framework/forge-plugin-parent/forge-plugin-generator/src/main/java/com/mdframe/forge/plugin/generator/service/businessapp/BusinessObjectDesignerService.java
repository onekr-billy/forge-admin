package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodeModel;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignerDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectRelationDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.LinkageSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.ViewSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeModelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectDesignVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectRelationMapper;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.service.IGenDatasourceService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDomainService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 业务对象设计器聚合服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessObjectDesignerService implements BusinessObjectDesignContextProvider {

    private static final Logger log = LoggerFactory.getLogger(BusinessObjectDesignerService.class);
    private static final String FORM_DESIGNER_SCHEMA_OPTION_KEY = "formDesignerSchema";
    private static final String DESIGNER_ACTIONS_OPTION_KEY = "actions";
    private static final String VIEW_SCHEMA_OPTION_KEY = "viewSchema";
    private static final String LINKAGE_SCHEMA_OPTION_KEY = "linkageSchema";
    private static final String LINKAGE_SCHEMA_MANAGED_BY = "linkageSchema";
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
        return draftSchemaGateway().resolveModel(object, config);
    }

    private AiCrudConfig resolveConfig(AiBusinessObject object) {
        return draftSchemaGateway().resolveConfig(object);
    }

    private LowcodeModelSchema resolveModelSchema(
            AiBusinessObject object, AiLowcodeModel model, AiCrudConfig config) {
        return draftSchemaGateway().resolveModelSchema(object, model, config);
    }

    private LowcodePageSchema resolvePageSchema(AiCrudConfig config, LowcodeModelSchema modelSchema) {
        return draftSchemaGateway().resolvePageSchema(config, modelSchema);
    }

    private LowcodeModelSchema enrichModelSchema(AiBusinessObject object, LowcodeModelSchema schema) {
        return draftSchemaGateway().enrichModelSchema(object, schema);
    }

    private LowcodePageSchema ensurePageSchema(LowcodePageSchema pageSchema, LowcodeModelSchema modelSchema) {
        return draftSchemaGateway().ensurePageSchema(pageSchema, modelSchema);
    }

    private void validateDraft(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        draftSchemaGateway().validateDraft(modelSchema, pageSchema);
    }

    private AiLowcodeModel saveModelDraft(
            AiBusinessObject object, AiLowcodeModel model, LowcodeModelSchema modelSchema) {
        return draftSchemaGateway().saveModelDraft(object, model, modelSchema);
    }

    private AiCrudConfig saveRuntimeDraft(
            AiBusinessObject object,
            AiCrudConfig config,
            LowcodeModelSchema modelSchema,
            LowcodePageSchema pageSchema) {
        return draftSchemaGateway().saveRuntimeDraft(object, config, modelSchema, pageSchema);
    }

    private BusinessObjectDraftSchemaGateway draftSchemaGateway() {
        return new BusinessObjectDraftSchemaGateway(
                objectMapper,
                lowcodeModelMapper,
                crudConfigMapper,
                businessAppMapper,
                crudConfigService,
                domainService,
                schemaNormalizer,
                schemaValidator,
                datasourceService,
                runtimeDataSourceResolver,
                fieldSchemaService,
                this::resolveTenantId
        );
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
        return fieldDesignPolicy().rebuildModelFields(modelSchema, fields);
    }

    private BusinessObjectFieldDesignPolicy fieldDesignPolicy() {
        return new BusinessObjectFieldDesignPolicy(fieldSchemaService, schemaNormalizer);
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

    public void applyRelationsToModel(DesignerContext context) {
        relationCoordinator().applyRelationsToModel(context);
    }

    private void saveSourceRelations(AiBusinessObject object, List<BusinessObjectRelationDTO> relations) {
        relationCoordinator().saveSourceRelations(object, relations);
    }

    private boolean ensureChildTableRelations(DesignerContext context, FormDesignerSchemaDTO formSchema) {
        return relationCoordinator().ensureChildTableRelations(context, formSchema);
    }

    private void restoreRelationsFromSnapshot(AiBusinessObject object, String relationSnapshot) {
        relationCoordinator().restoreRelationsFromSnapshot(object, relationSnapshot);
    }

    private BusinessObjectRelationCoordinator relationCoordinator() {
        return new BusinessObjectRelationCoordinator(
                objectMapper,
                businessObjectMapper,
                relationMapper,
                this::resolveTenantId,
                this::loadContext,
                this::saveDraft
        );
    }

    private void restoreDesignerOptionsFromSnapshot(AiBusinessObject object, String designerOptionsSnapshot) {
        if (object == null) {
            return;
        }
        object.setDesignerOptions(StringUtils.isBlank(designerOptionsSnapshot) ? "{}" : designerOptionsSnapshot);
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
    private LowcodePageZone findZone(LowcodePageSchema pageSchema, String zoneKey) {
        if (pageSchema == null || pageSchema.getZones() == null) {
            return null;
        }
        return pageSchema.getZones().stream()
                .filter(zone -> zone != null && zoneKey.equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 表单设计器里的开关默认值可能是 true/false，落到 tinyint 列必须是 0/1。
     */
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

}
