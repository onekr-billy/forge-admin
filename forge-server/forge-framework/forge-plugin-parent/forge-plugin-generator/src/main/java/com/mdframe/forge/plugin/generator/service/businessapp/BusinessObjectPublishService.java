package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.constant.BusinessPublishCheckLevel;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignVersionDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectPublishDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishDTO;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDdlService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodePublishService;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeRuntimeConfigBuilder;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeSchemaValidator;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceResolver;
import com.mdframe.forge.plugin.generator.service.formula.CrossObjectRecomputeTaskService;
import com.mdframe.forge.plugin.generator.service.formula.FormulaObjectDependencyAnalyzer;
import com.mdframe.forge.plugin.generator.service.formula.FormulaPublishValidator;
import com.mdframe.forge.plugin.generator.service.formula.FormulaValidationResult;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectDesignVersionVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectRelationVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPermissionSummaryVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckItemVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.LowcodeDdlPreviewVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 业务对象发布检查和发布门面服务。
 */
@Service
@RequiredArgsConstructor
public class BusinessObjectPublishService {

    private static final String DDL_PERMISSION = "ai:lowcode:deploy-ddl";
    private static final String FORM_DESIGNER_SCHEMA_OPTION_KEY = "formDesignerSchema";
    private static final String LINKAGE_SCHEMA_OPTION_KEY = "linkageSchema";
    private static final String DESIGNER_ACTIONS_KEY = "actions";
    private static final Set<String> RUNTIME_OPEN_MODES = Set.of("LIST", "CREATE_FORM", "DETAIL");
    private static final Set<String> APP_ENTRY_TYPES = Set.of(
            "OBJECT_LIST", "CREATE_FORM", "DETAIL_PAGE", "APPROVAL_TODO", "REPORT_DASHBOARD", "EXTERNAL_OR_API");
    private static final BusinessObjectFormPublishValidator FORM_PUBLISH_VALIDATOR =
            new BusinessObjectFormPublishValidator();

    private final BusinessObjectDesignerService designerService;
    private final BusinessObjectDesignVersionService designVersionService;
    private final LowcodePublishService lowcodePublishService;
    private final LowcodeRuntimeConfigBuilder runtimeConfigBuilder;
    private final LowcodeSchemaValidator schemaValidator;
    private final FormulaPublishValidator formulaPublishValidator;
    private final CrossObjectRecomputeTaskService crossObjectRecomputeTaskService;
    private final LowcodeDdlService ddlService;
    private final LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver;
    private final AiCrudConfigMapper crudConfigMapper;
    private final BusinessAppMapper businessAppMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessTriggerMapper triggerMapper;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessPermissionService permissionService;
    private final MenuRegisterAdapter menuRegisterAdapter;
    private final ObjectMapper objectMapper;

    public BusinessPublishCheckVO publishCheck(Long objectId) {
        BusinessObjectDesignerService.DesignerContext context = designerService.loadContext(objectId);
        return publishCheck(context, null);
    }

    public BusinessPublishCheckVO publishCheck(Long objectId, BusinessPermissionSummaryVO permissionSummary) {
        BusinessObjectDesignerService.DesignerContext context = designerService.loadContext(objectId);
        return publishCheck(context, permissionSummary);
    }

    /**
     * 应用协调发布预检只加载设计上下文，不做对象级页面/公式/单据全量校验。
     * 上下文供后续 OBJECTS 步骤复用，避免同一请求内二次 loadContext。
     */
    public BusinessObjectDesignerService.DesignerContext loadContextForApplicationPublish(Long objectId) {
        return designerService.loadContext(objectId);
    }

    /**
     * 应用协调发布在已有对象发布版本时只收敛设计状态，不重跑 lowcode 发布。
     * 已是 PUBLISHED 的对象直接跳过，避免逐条 selectById。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markDesignPublished(List<Long> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return;
        }
        List<Long> ids = objectIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        businessObjectMapper.markDesignPublished(resolveTenantId(null), ids,
                BusinessObjectDesignStatus.PUBLISHED.getCode());
    }

    /**
     * 执行发布检查并返回加载的设计上下文，供对象级发布预检使用。
     * 应用协调发布请用 {@link #loadContextForApplicationPublish(Long)}，不要走本方法的全量校验。
     */
    public ResolvedObjectCheck publishCheckResolved(Long objectId, BusinessPermissionSummaryVO permissionSummary) {
        BusinessObjectDesignerService.DesignerContext context = designerService.loadContext(objectId);
        return new ResolvedObjectCheck(publishCheck(context, permissionSummary), context);
    }

    /** 发布检查结果与其加载的设计上下文。 */
    public record ResolvedObjectCheck(BusinessPublishCheckVO check,
                                      BusinessObjectDesignerService.DesignerContext context) {
    }

    private BusinessPublishCheckVO publishCheck(BusinessObjectDesignerService.DesignerContext context,
                                                BusinessPermissionSummaryVO permissionSummary) {
        designerService.compileFormFirstRuntimeSchema(context);
        designerService.applyRelationsToModel(context);
        List<BusinessPublishCheckItemVO> items = new ArrayList<>();
        checkFields(context.getModelSchema(), items);
        pagePublishValidator().validate(context, items);
        checkFormFirstSchemas(context, items);
        checkRelations(context, items);
        checkLinkage(context, items);
        checkRuntimeConfig(context, items);
        checkAppEntry(context, items);
        checkDocumentConfig(context, items);
        checkPermissionSummary(context, permissionSummary, items);
        checkRuntimeDataSource(context.getModelSchema(), items);
        checkTable(context.getModelSchema(), items);
        checkFormula(context, items);
        return buildResult(items);
    }

    /** 应用协调发布预检已通过且子表关系未变时，复用预检结论，避免二次全量编译校验。 */
    private BusinessPublishCheckVO trustPreloadedPublishCheck() {
        BusinessPublishCheckVO check = new BusinessPublishCheckVO();
        check.setPublishable(true);
        check.setItems(List.of());
        return check;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long objectId, BusinessObjectPublishDTO dto) {
        return publish(objectId, dto, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long objectId,
                        BusinessObjectPublishDTO dto,
                        BusinessPermissionSummaryVO permissionSummary) {
        return publish(objectId, dto, permissionSummary, null);
    }

    /**
     * 发布业务对象。preloadedContext 非空时复用同一请求内预检阶段加载的设计上下文，
     * 避免重复执行 loadContext 的整组查询；仅限加载后无外部变更的编排链路（应用协调发布）传入。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long objectId,
                        BusinessObjectPublishDTO dto,
                        BusinessPermissionSummaryVO permissionSummary,
                        BusinessObjectDesignerService.DesignerContext preloadedContext) {
        BusinessObjectDesignerService.DesignerContext context = preloadedContext != null
                ? preloadedContext
                : designerService.loadContext(objectId);
        if (dto != null && dto.getModelSchema() != null) {
            context.setModelSchema(dto.getModelSchema());
        }
        if (dto != null && dto.getPageSchema() != null) {
            context.setPageSchema(dto.getPageSchema());
        }
        // DETAIL 在 prepare 阶段 persistChildRelations=false，必须在最终发布前再同步子表关系；
        // PRIMARY 已在 prepare 同步过时通常无写入（短回路），代价可接受。
        boolean relationsChanged = designerService.synchronizeFormChildRelations(context);
        designerService.applyRelationsToModel(context);
        context = designerService.saveDraft(context, BusinessObjectDesignStatus.READY.getCode());
        // 应用协调发布已在 readiness 做过 publishCheckResolved；关系未变则跳过二次编译校验。
        BusinessPublishCheckVO check = (preloadedContext != null && !relationsChanged)
                ? trustPreloadedPublishCheck()
                : publishCheck(context, permissionSummary);
        boolean force = dto != null && Boolean.TRUE.equals(dto.getForce());
        if (Boolean.FALSE.equals(check.getPublishable())
                && (!force || containsNonForceableCommandBlock(check))) {
            throw new BusinessException(buildPublishBlockedMessage(check));
        }

        LowcodePublishDTO publishDTO = buildPublishDTO(context, dto);
        Long crudConfigVersionId = lowcodePublishService.publish(context.getConfig().getId(), publishDTO);
        AiCrudConfig publishedConfig = crudConfigMapper.selectById(context.getConfig().getId());
        if (publishedConfig == null) {
            throw new BusinessException("发布后运行配置不存在");
        }
        AiBusinessObject object = businessObjectMapper.selectById(objectId);
        if (object == null) {
            throw new BusinessException("发布后业务对象不存在");
        }
        object.setConfigKey(publishedConfig.getConfigKey());
        object.setModelCode(publishedConfig.getObjectCode());
        object.setDesignStatus(BusinessObjectDesignStatus.PUBLISHED.getCode());
        object.setLastPublishVersion(publishedConfig.getPublishedVersion());
        object.setLastPublishTime(publishedConfig.getPublishTime());
        if (businessObjectMapper.updateById(object) == 0) {
            throw new BusinessException("业务对象发布状态更新失败");
        }

        BusinessObjectDesignVersionDTO versionDTO = new BusinessObjectDesignVersionDTO();
        versionDTO.setObjectId(objectId);
        versionDTO.setSuiteCode(object.getSuiteCode());
        versionDTO.setObjectCode(object.getObjectCode());
        versionDTO.setConfigId(publishedConfig.getId());
        versionDTO.setConfigKey(publishedConfig.getConfigKey());
        versionDTO.setCrudConfigVersionId(crudConfigVersionId);
        versionDTO.setVersionType("publish");
        versionDTO.setModelSnapshot(context.getModelSchema());
        versionDTO.setPageSnapshot(context.getPageSchema());
        versionDTO.setRelationSnapshot(context.getRelations());
        versionDTO.setDesignerOptionsSnapshot(readDesignerOptions(context));
        versionDTO.setPublishStatus("PUBLISHED");
        versionDTO.setPublishVersion(publishedConfig.getPublishedVersion());
        versionDTO.setRemark(dto == null ? null : dto.getRemark());
        Long versionId = designVersionService.createVersion(versionDTO);
        menuRegisterAdapter.syncBusinessObjectActionPermissions(
                object.getObjectCode(), object.getObjectName(), publishedConfig.getConfigKey());
        enqueueCrossObjectRecompute(context);
        return versionId;
    }

    private String buildPublishBlockedMessage(BusinessPublishCheckVO check) {
        List<BusinessPublishCheckItemVO> blockItems = check == null || check.getBlockItems() == null
                ? List.of()
                : check.getBlockItems();
        if (blockItems.isEmpty()) {
            return "发布检查存在阻断项，请先修复后再发布";
        }
        String summary = blockItems.stream()
                .limit(3)
                .map(this::formatBlockItem)
                .filter(StringUtils::isNotBlank)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        int remain = Math.max(blockItems.size() - 3, 0);
        String suffix = remain > 0 ? "；另有 " + remain + " 项" : "";
        return "发布检查存在 " + blockItems.size() + " 个阻断项：" + summary + suffix;
    }

    private boolean containsNonForceableCommandBlock(BusinessPublishCheckVO check) {
        return check != null && check.getBlockItems() != null
                && check.getBlockItems().stream().anyMatch(item -> "COMMAND".equals(item.getCategory()));
    }

    private String formatBlockItem(BusinessPublishCheckItemVO item) {
        if (item == null) {
            return "";
        }
        String title = StringUtils.defaultIfBlank(item.getTitle(), item.getItemCode());
        String target = StringUtils.defaultIfBlank(item.getZoneKey(), item.getFieldCode());
        if (StringUtils.isNotBlank(target)) {
            return title + "（" + target + "）";
        }
        return title;
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long objectId, Long versionId) {
        rollbackInternal(objectId, versionId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long rollbackForApplication(Long objectId, Long versionId) {
        return rollbackInternal(objectId, versionId, false);
    }

    private Long rollbackInternal(Long objectId, Long versionId, boolean syncMenu) {
        BusinessObjectDesignVersionVO version = designVersionService.detail(objectId, versionId);
        if (version.getConfigId() != null && version.getCrudConfigVersionId() != null) {
            lowcodePublishService.rollback(version.getConfigId(), version.getCrudConfigVersionId(), syncMenu);
        }
        designerService.rollbackDesignVersion(objectId, versionId);
        BusinessObjectDesignerService.DesignerContext context = designerService.loadContext(objectId);
        BusinessObjectDesignVersionDTO rollbackVersion = new BusinessObjectDesignVersionDTO();
        rollbackVersion.setObjectId(objectId);
        rollbackVersion.setSuiteCode(context.getObject().getSuiteCode());
        rollbackVersion.setObjectCode(context.getObject().getObjectCode());
        rollbackVersion.setConfigId(context.getConfig() == null ? null : context.getConfig().getId());
        rollbackVersion.setConfigKey(context.getConfig() == null ? null : context.getConfig().getConfigKey());
        rollbackVersion.setVersionType("rollback");
        rollbackVersion.setModelSnapshot(context.getModelSchema());
        rollbackVersion.setPageSnapshot(context.getPageSchema());
        rollbackVersion.setRelationSnapshot(context.getRelations());
        rollbackVersion.setDesignerOptionsSnapshot(readDesignerOptions(context));
        rollbackVersion.setPublishStatus(context.getConfig() == null ? "DRAFT" : context.getConfig().getPublishStatus());
        rollbackVersion.setPublishVersion(context.getConfig() == null ? null : context.getConfig().getPublishedVersion());
        rollbackVersion.setRemark("回滚到设计版本 " + version.getVersionNo());
        Long rollbackVersionId = designVersionService.createVersion(rollbackVersion);
        AiBusinessObject object = businessObjectMapper.selectById(objectId);
        if (object == null) {
            throw new BusinessException("业务对象不存在");
        }
        object.setDesignStatus(BusinessObjectDesignStatus.PUBLISHED.getCode());
        if (context.getConfig() != null) {
            object.setLastPublishVersion(context.getConfig().getPublishedVersion());
            object.setLastPublishTime(context.getConfig().getPublishTime());
        }
        businessObjectMapper.updateById(object);
        return rollbackVersionId;
    }

    private LowcodePublishDTO buildPublishDTO(BusinessObjectDesignerService.DesignerContext context,
                                              BusinessObjectPublishDTO dto) {
        LowcodePublishDTO publishDTO = new LowcodePublishDTO();
        boolean syncTable = dto != null && Boolean.TRUE.equals(dto.getSyncTable());
        publishDTO.setDeployMode(syncTable ? "ONLINE_CREATE_TABLE" : "SKIP_DDL");
        publishDTO.setConfirmOnlineDdl(syncTable);
        publishDTO.setMenuName(context.getObject().getObjectName());
        publishDTO.setMenuSort(context.getConfig() == null ? context.getObject().getSortOrder() : context.getConfig().getMenuSort());
        publishDTO.setSyncMenu(dto == null ? null : dto.getSyncMenu());
        publishDTO.setBusinessSuiteCode(context.getObject().getSuiteCode());
        publishDTO.setBusinessObjectCode(context.getObject().getObjectCode());
        publishDTO.setBusinessObjectName(context.getObject().getObjectName());
        publishDTO.setObjectCode(context.getObject().getObjectCode());
        publishDTO.setObjectName(context.getObject().getObjectName());
        publishDTO.setRemark(dto == null ? null : dto.getRemark());
        publishDTO.setModelSchema(context.getModelSchema());
        publishDTO.setPageSchema(buildPublishPageSchema(context));
        return publishDTO;
    }

    private LowcodePageSchema buildPublishPageSchema(BusinessObjectDesignerService.DesignerContext context) {
        LowcodePageSchema pageSchema = context.getPageSchema();
        List<Map<String, Object>> runtimeActions = buildRuntimeCustomActions(context, readDesignerOptions(context));
        if (pageSchema == null || runtimeActions.isEmpty()) {
            return pageSchema;
        }
        LowcodePageSchema next = objectMapper.convertValue(pageSchema, LowcodePageSchema.class);
        List<Map<String, Object>> pageActions = runtimeActions.stream()
                .filter(action -> !"childRow".equals(action.get("position")))
                .toList();
        if (!pageActions.isEmpty()) {
            LowcodePageZone tableZone = findOrCreateZone(next, "table");
            Map<String, Object> props = tableZone.getProps() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(tableZone.getProps());
            props.put("customActions", pageActions);
            tableZone.setProps(props);
        }
        applyChildRowActions(next, runtimeActions.stream()
                .filter(action -> "childRow".equals(action.get("position")))
                .toList());
        return next;
    }

    private void applyChildRowActions(LowcodePageSchema pageSchema, List<Map<String, Object>> childActions) {
        if (pageSchema == null || pageSchema.getModelRefs() == null || childActions.isEmpty()) {
            return;
        }
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || StringUtils.isBlank(ref.getModelCode())) {
                continue;
            }
            Map<String, Object> props = ref.getProps() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(ref.getProps());
            String relationKey = StringUtils.defaultIfBlank(text(props.get("relationKey")), ref.getModelCode());
            List<Map<String, Object>> matched = childActions.stream()
                    .filter(action -> relationKey.equals(text(action.get("relationKey"))))
                    .<Map<String, Object>>map(action -> new LinkedHashMap<>(action))
                    .toList();
            if (!matched.isEmpty()) {
                props.put("relationKey", relationKey);
                props.put("rowActions", matched);
                ref.setProps(props);
            }
        }
    }

    private LowcodePageZone findOrCreateZone(LowcodePageSchema pageSchema, String zoneKey) {
        if (pageSchema.getZones() == null) {
            pageSchema.setZones(new ArrayList<>());
        }
        for (LowcodePageZone zone : pageSchema.getZones()) {
            if (zone != null && zoneKey.equalsIgnoreCase(zone.getZoneKey())) {
                return zone;
            }
        }
        LowcodePageZone zone = new LowcodePageZone();
        zone.setZoneKey(zoneKey);
        zone.setComponentKey(zoneKey);
        zone.setEnabled(true);
        pageSchema.getZones().add(zone);
        return zone;
    }

    private List<Map<String, Object>> buildRuntimeCustomActions(BusinessObjectDesignerService.DesignerContext context,
                                                                Map<String, Object> designerOptions) {
        List<Map<String, Object>> actions = listOfMap(designerOptions.get(DESIGNER_ACTIONS_KEY));
        if (actions.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> action : actions) {
            if (!Integer.valueOf(1).equals(intValue(action.get("status"), 1))) {
                continue;
            }
            String actionCode = text(action.get("actionCode"));
            String actionName = text(action.get("actionName"));
            String actionType = StringUtils.defaultIfBlank(text(action.get("actionType")), "OPEN_PAGE").toUpperCase();
            String position = normalizeActionPosition(action.get("actionPosition"));
            Map<String, Object> config = mapValue(action.get("actionConfig"));
            if (isBusinessAutomationAction(actionType, config) && !"childRow".equals(position)) {
                continue;
            }
            if (isBuiltinCrudAction(position, actionCode, actionName, actionType, config)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", StringUtils.defaultIfBlank(actionCode, "custom_" + result.size()));
            item.put("actionCode", StringUtils.defaultIfBlank(actionCode, "custom_" + result.size()));
            item.put("label", StringUtils.defaultIfBlank(actionName, "自定义操作"));
            item.put("position", position);
            item.put("type", resolveActionButtonType(actionType));
            item.put("actionType", resolveRuntimeActionType(actionType));
            putIfNotBlank(item, "relationKey", text(config.get("relationKey")));
            AiBusinessObject object = context == null ? null : context.getObject();
            if (object != null) {
                putIfNotBlank(item, "suiteCode", object.getSuiteCode());
                putIfNotBlank(item, "objectCode", object.getObjectCode());
                putIfNotBlank(item, "businessObjectCode", object.getObjectCode());
                putIfNotBlank(item, "targetObjectCode", object.getObjectCode());
            }
            putIfNotBlank(item, "routePath", resolveActionRoutePath(actionType, config));
            putIfNotBlank(item, "targetFormKey", text(config.get("targetFormKey")));
            putIfNotBlank(item, "openTarget", StringUtils.defaultIfBlank(text(config.get("openTarget")), "_self"));
            String permissionKey = StringUtils.firstNonBlank(
                    text(action.get("permissionKey")),
                    text(action.get("permissionCode")),
                    text(action.get("permission")));
            putIfNotBlank(item, "permissionKey", permissionKey);
            putIfNotBlank(item, "permissionCode", permissionKey);
            putIfNotBlank(item, "permissionStrategy", text(action.get("permissionStrategy")));
            putIfNotBlank(item, "successMessage", text(action.get("successMessage")));
            putIfNotBlank(item, "failureMessage", text(action.get("failureMessage")));
            putIfNotBlank(item, "successBehavior", text(config.get("successBehavior")));
            List<Map<String, Object>> params = listOfMap(config.get("params"));
            if (!params.isEmpty()) {
                item.put("params", params);
            }
            if (("CALL_API".equals(actionType) || "COMMAND".equals(actionType)) && !config.isEmpty()) {
                item.put("actionConfig", new LinkedHashMap<>(config));
            }
            if (Boolean.TRUE.equals(booleanValue(action.get("confirmRequired")))) {
                item.put("confirmText", "确认执行“" + StringUtils.defaultIfBlank(actionName, "该操作") + "”？");
            }
            result.add(item);
        }
        return result;
    }

    private boolean isBusinessAutomationAction(String actionType, Map<String, Object> config) {
        if ("COMMAND".equals(actionType)) {
            return hasActionSteps(config);
        }
        return false;
    }

    private boolean hasActionSteps(Map<String, Object> config) {
        return config != null && (config.get("steps") instanceof List<?> || config.get("stepList") instanceof List<?>);
    }

    private boolean isBuiltinCrudAction(String position, String actionCode, String actionName,
                                        String actionType, Map<String, Object> config) {
        if (!"OPEN_PAGE".equals(actionType) || StringUtils.isNotBlank(resolveActionRoutePath(actionType, config))) {
            return false;
        }
        if ("toolbar".equals(position)) {
            return matchesActionIdentity(actionCode, actionName, Set.of("add", "create", "new", "新增", "新建"));
        }
        if ("row".equals(position)) {
            return matchesActionIdentity(actionCode, actionName, Set.of("edit", "detail", "delete", "addchild",
                    "编辑", "查看详情", "详情", "删除", "添加下级"));
        }
        return false;
    }

    private boolean matchesActionIdentity(String actionCode, String actionName, Set<String> candidates) {
        String code = normalizeActionIdentity(actionCode);
        String name = normalizeActionIdentity(actionName);
        return candidates.contains(code) || candidates.contains(name);
    }

    private String normalizeActionIdentity(String value) {
        return StringUtils.lowerCase(StringUtils.defaultString(value))
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .trim();
    }

    private String normalizeActionPosition(Object value) {
        String position = StringUtils.defaultIfBlank(text(value), "ROW").trim().toUpperCase();
        if ("TOOLBAR".equals(position)) {
            return "toolbar";
        }
        if ("DETAIL".equals(position)) {
            return "detail";
        }
        if ("CHILD_ROW".equals(position)) {
            return "childRow";
        }
        return "row";
    }

    private String resolveRuntimeActionType(String actionType) {
        return switch (actionType) {
            case "START_FLOW" -> "START_FLOW";
            case "START_APPROVAL" -> "START_FLOW";
            case "COMMAND" -> "COMMAND";
            case "OPEN_EXTERNAL" -> "external";
            case "TRIGGER" -> "TRIGGER";
            case "CALL_API" -> "CALL_API";
            default -> "route";
        };
    }

    private String resolveActionButtonType(String actionType) {
        return switch (actionType) {
            case "START_FLOW" -> "success";
            case "OPEN_EXTERNAL" -> "info";
            case "TRIGGER", "CALL_API" -> "warning";
            default -> "primary";
        };
    }

    private String resolveActionRoutePath(String actionType, Map<String, Object> config) {
        if ("OPEN_EXTERNAL".equals(actionType)) {
            return text(config.get("url"));
        }
        if ("OPEN_PAGE".equals(actionType)) {
            return StringUtils.firstNonBlank(text(config.get("targetPath")), text(config.get("routePath")));
        }
        return null;
    }

    private void checkFields(LowcodeModelSchema modelSchema, List<BusinessPublishCheckItemVO> items) {
        if (modelSchema == null || modelSchema.getFields() == null) {
            add(items, "FIELD_EMPTY", "FIELD", BusinessPublishCheckLevel.BLOCK,
                    "字段为空", "业务对象至少需要一个业务字段", null, null, "ADD_FIELD", "添加字段", "fields", 10);
            return;
        }
        Map<String, Integer> fieldCount = new LinkedHashMap<>();
        int businessFieldCount = 0;
        for (LowcodeFieldSchema field : modelSchema.getFields()) {
            if (field == null || Boolean.TRUE.equals(field.getSystemField())) {
                continue;
            }
            String fieldStatus = StringUtils.defaultString(field.getFieldStatus());
            if ("HIDDEN".equalsIgnoreCase(fieldStatus)) {
                continue;
            }
            businessFieldCount++;
            if (StringUtils.isBlank(field.getLabel())) {
                add(items, "FIELD_LABEL_EMPTY", "FIELD", BusinessPublishCheckLevel.BLOCK,
                        "字段名称为空", "字段缺少展示名称", field.getField(), null,
                        "EDIT_FIELD", "编辑字段", "fields", 20);
            }
            if (StringUtils.isBlank(field.getField())) {
                add(items, "FIELD_CODE_EMPTY", "FIELD", BusinessPublishCheckLevel.BLOCK,
                        "字段编码为空", "字段缺少稳定编码", null, null,
                        "EDIT_FIELD", "编辑字段", "fields", 30);
            } else {
                fieldCount.merge(field.getField(), 1, Integer::sum);
            }
            if ("DISABLED".equalsIgnoreCase(fieldStatus)) {
                add(items, "FIELD_DISABLED", "FIELD", BusinessPublishCheckLevel.WARN,
                        "字段已停用", "停用字段不会进入默认表单和列表: " + field.getLabel(), field.getField(), null,
                        "EDIT_FIELD", "检查字段", "fields", 40);
            }
        }
        if (businessFieldCount == 0) {
            add(items, "FIELD_EMPTY", "FIELD", BusinessPublishCheckLevel.BLOCK,
                    "字段为空", "业务对象至少需要一个业务字段", null, null, "ADD_FIELD", "添加字段", "fields", 50);
        }
        fieldCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .forEach(entry -> add(items, "FIELD_DUPLICATE", "FIELD", BusinessPublishCheckLevel.BLOCK,
                        "字段编码重复", "字段编码重复: " + entry.getKey(), entry.getKey(), null,
                        "EDIT_FIELD", "修复字段编码", "fields", 60));
        if (items.stream().noneMatch(item -> "FIELD".equals(item.getCategory()))) {
            add(items, "FIELD_PASS", "FIELD", BusinessPublishCheckLevel.PASS,
                    "字段检查通过", "业务字段命名、编码和数量满足发布要求", null, null, null, null, "fields", 90);
        }
    }

    private BusinessObjectPagePublishValidator pagePublishValidator() {
        return new BusinessObjectPagePublishValidator(objectMapper, schemaValidator);
    }
    private void checkFormFirstSchemas(BusinessObjectDesignerService.DesignerContext context,
                                       List<BusinessPublishCheckItemVO> items) {
        FORM_PUBLISH_VALIDATOR.validate(
                readDesignerOptions(context),
                collectPageFields(context.getModelSchema(), context.getPageSchema()),
                hasBusinessFields(context.getModelSchema()),
                items);
    }

    private void checkRelations(BusinessObjectDesignerService.DesignerContext context,
                                List<BusinessPublishCheckItemVO> items) {
        List<BusinessObjectRelationVO> relations = context.getRelations();
        if (relations == null || relations.isEmpty()) {
            add(items, "RELATION_EMPTY", "RELATION", BusinessPublishCheckLevel.WARN,
                    "未配置对象关系", "对象可以先以单表发布，后续再补充关系", null, null,
                    "CONFIG_RELATION", "配置关系", "relations", 200);
            return;
        }
        Set<String> currentFields = collectFields(context.getModelSchema());
        for (BusinessObjectRelationVO relation : relations) {
            if (relation == null || !StringUtils.equals(context.getObject().getObjectCode(), relation.getSourceObjectCode())) {
                continue;
            }
            if (StringUtils.isBlank(relation.getTargetObjectCode())) {
                add(items, "RELATION_TARGET_EMPTY", "RELATION", BusinessPublishCheckLevel.BLOCK,
                        "关系目标为空", "关系缺少目标业务对象: " + relation.getRelationName(), null, null,
                        "EDIT_RELATION", "编辑关系", "relations", 210);
                continue;
            }
            AiBusinessObject target = businessObjectMapper.selectByObjectCode(
                    resolveTenantId(context), context.getObject().getSuiteCode(), relation.getTargetObjectCode());
            if (target == null) {
                add(items, "RELATION_TARGET_MISSING", "RELATION", BusinessPublishCheckLevel.BLOCK,
                        "关系目标不存在", "目标业务对象不存在: " + relation.getTargetObjectCode(), null, null,
                        "EDIT_RELATION", "编辑关系", "relations", 220);
                continue;
            }
            if (StringUtils.isBlank(relation.getSourceFieldCode())) {
                add(items, "RELATION_SOURCE_FIELD_EMPTY", "RELATION", BusinessPublishCheckLevel.BLOCK,
                        "当前对象字段为空", "关系缺少当前对象字段: " + relation.getRelationName(),
                        null, null, "EDIT_RELATION", "编辑关系", "relations", 225);
            } else if (!currentFields.contains(relation.getSourceFieldCode())) {
                add(items, "RELATION_SOURCE_FIELD_MISSING", "RELATION", BusinessPublishCheckLevel.BLOCK,
                        "关系字段不存在", "当前对象关系字段不存在: " + relation.getSourceFieldCode(),
                        relation.getSourceFieldCode(), null, "EDIT_RELATION", "编辑关系", "relations", 230);
            }
            Set<String> targetFields = collectFields(designerService.loadContext(target.getId()).getModelSchema());
            if (StringUtils.isBlank(relation.getTargetFieldCode())) {
                add(items, "RELATION_TARGET_FIELD_EMPTY", "RELATION", BusinessPublishCheckLevel.BLOCK,
                        "目标对象字段为空", "关系缺少目标对象字段: " + relation.getRelationName(),
                        null, null, "EDIT_RELATION", "编辑关系", "relations", 235);
            } else if (!targetFields.contains(relation.getTargetFieldCode())) {
                add(items, "RELATION_TARGET_FIELD_MISSING", "RELATION", BusinessPublishCheckLevel.BLOCK,
                        "目标字段不存在", "目标对象字段不存在: " + relation.getTargetFieldCode(),
                        relation.getTargetFieldCode(), null, "EDIT_RELATION", "编辑关系", "relations", 240);
            }
        }
        if (items.stream().noneMatch(item -> "RELATION".equals(item.getCategory())
                && BusinessPublishCheckLevel.BLOCK.equals(item.getLevel()))) {
            add(items, "RELATION_PASS", "RELATION", BusinessPublishCheckLevel.PASS,
                    "关系检查通过", "对象关系目标和当前对象字段有效", null, null, null, null, "relations", 290);
        }
    }

    private void checkLinkage(BusinessObjectDesignerService.DesignerContext context,
                              List<BusinessPublishCheckItemVO> items) {
        List<Map<String, Object>> rules = resolveLinkageRules(context);
        if (rules.isEmpty()) {
            add(items, "LINKAGE_PASS", "LINKAGE", BusinessPublishCheckLevel.PASS,
                    "级联检查通过", "未配置字段级联规则", null, null, null, null, "relations", 295);
            return;
        }
        Map<String, LowcodeFieldSchema> fieldMap = collectFieldMap(context.getModelSchema());
        Set<String> ruleIds = new LinkedHashSet<>();
        for (int index = 0; index < rules.size(); index++) {
            Map<String, Object> rule = rules.get(index);
            String ruleId = StringUtils.defaultIfBlank(text(rule.get("ruleId")), "第 " + (index + 1) + " 条规则");
            if (!ruleIds.add(ruleId)) {
                add(items, "LINKAGE_RULE_DUPLICATE", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                        "级联规则重复", "级联规则 ID 重复: " + ruleId, null, null,
                        "EDIT_LINKAGE", "编辑级联", "relations", 296);
            }
            if (isFalse(rule.get("enabled"))) {
                continue;
            }
            String sourceField = text(rule.get("sourceField"));
            String targetField = text(rule.get("targetField"));
            if (StringUtils.isBlank(sourceField)) {
                add(items, "LINKAGE_SOURCE_EMPTY", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                        "级联上级字段为空", ruleId + " 缺少上级字段", null, null,
                        "EDIT_LINKAGE", "编辑级联", "relations", 297);
            } else if (!fieldMap.containsKey(sourceField)) {
                add(items, "LINKAGE_SOURCE_MISSING", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                        "级联上级字段不存在", ruleId + " 引用了不存在的上级字段: " + sourceField, sourceField, null,
                        "EDIT_LINKAGE", "编辑级联", "relations", 298);
            }
            LowcodeFieldSchema target = null;
            if (StringUtils.isBlank(targetField)) {
                add(items, "LINKAGE_TARGET_EMPTY", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                        "级联目标字段为空", ruleId + " 缺少目标字段", null, null,
                        "EDIT_LINKAGE", "编辑级联", "relations", 299);
            } else {
                target = fieldMap.get(targetField);
                if (target == null) {
                    add(items, "LINKAGE_TARGET_MISSING", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                            "级联目标字段不存在", ruleId + " 引用了不存在的目标字段: " + targetField, targetField, null,
                            "EDIT_LINKAGE", "编辑级联", "relations", 300);
                }
            }
            checkLinkageRuleConfig(items, ruleId, rule, target);
        }
        if (items.stream().noneMatch(item -> "LINKAGE".equals(item.getCategory())
                && BusinessPublishCheckLevel.BLOCK.equals(item.getLevel()))) {
            add(items, "LINKAGE_PASS", "LINKAGE", BusinessPublishCheckLevel.PASS,
                    "级联检查通过", "字段级联规则引用和参数完整", null, null, null, null, "relations", 305);
        }
    }

    private void checkLinkageRuleConfig(List<BusinessPublishCheckItemVO> items, String ruleId,
                                        Map<String, Object> rule, LowcodeFieldSchema target) {
        String type = StringUtils.defaultIfBlank(text(rule.get("type")), text(rule.get("matchMode")));
        String dataSourceType = StringUtils.defaultIfBlank(text(rule.get("dataSourceType")), resolveLinkageDataSourceType(type));
        Map<String, Object> dictConfig = mapValue(rule.get("dictConfig"));
        Map<String, Object> remoteConfig = mapValue(rule.get("remoteConfig"));
        Map<String, Object> objectConfig = mapValue(rule.get("objectConfig"));
        if ("dict".equals(dataSourceType)) {
            String targetDictType = StringUtils.defaultIfBlank(text(dictConfig.get("targetDictType")),
                    target == null ? null : target.getDictType());
            if (StringUtils.isBlank(targetDictType)) {
                add(items, "LINKAGE_DICT_TARGET_EMPTY", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                        "目标字典类型为空", ruleId + " 缺少目标字典类型", target == null ? null : target.getField(), null,
                        "EDIT_LINKAGE", "编辑级联", "relations", 301);
            }
            if ("linkedDict".equals(type)) {
                String linkedDictType = StringUtils.defaultIfBlank(text(dictConfig.get("linkedDictType")),
                        text(dictConfig.get("sourceDictType")));
                if (StringUtils.isBlank(linkedDictType)) {
                    add(items, "LINKAGE_DICT_LINKED_EMPTY", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                            "关联字典类型为空", ruleId + " 缺少 linked_dict_type 匹配值", target == null ? null : target.getField(), null,
                            "EDIT_LINKAGE", "编辑级联", "relations", 302);
                }
            }
            return;
        }
        String paramName = StringUtils.defaultIfBlank(text(remoteConfig.get("paramName")), text(rule.get("paramName")));
        if (StringUtils.isBlank(paramName)) {
            add(items, "LINKAGE_REMOTE_PARAM_EMPTY", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                    "远程参数为空", ruleId + " 缺少请求参数名", target == null ? null : target.getField(), null,
                    "EDIT_LINKAGE", "编辑级联", "relations", 303);
        }
        if ("remote".equals(dataSourceType) && "remoteParam".equals(type)
                && StringUtils.isBlank(text(remoteConfig.get("url")))) {
            add(items, "LINKAGE_REMOTE_URL_EMPTY", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                    "远程接口为空", ruleId + " 缺少远程选项接口", target == null ? null : target.getField(), null,
                    "EDIT_LINKAGE", "编辑级联", "relations", 304);
        }
        if ("object".equals(dataSourceType)) {
            String targetObjectCode = StringUtils.defaultIfBlank(text(objectConfig.get("targetObjectCode")),
                    target == null ? null : target.getReferenceObjectCode());
            if (StringUtils.isBlank(targetObjectCode)) {
                add(items, "LINKAGE_OBJECT_TARGET_EMPTY", "LINKAGE", BusinessPublishCheckLevel.BLOCK,
                        "目标对象为空", ruleId + " 缺少引用目标对象", target == null ? null : target.getField(), null,
                        "EDIT_LINKAGE", "编辑级联", "relations", 306);
            }
        }
    }

    private void checkRuntimeConfig(BusinessObjectDesignerService.DesignerContext context,
                                    List<BusinessPublishCheckItemVO> items) {
        try {
            runtimeConfigBuilder.buildRuntimeConfig(resolveConfigKey(context),
                    context.getModelSchema(), context.getPageSchema());
            add(items, "RUNTIME_PASS", "RUNTIME", BusinessPublishCheckLevel.PASS,
                    "运行配置可生成", "字段和页面配置可以转换为 AiCrudPage 运行配置", null, null,
                    null, null, "publish", 300);
        } catch (Exception e) {
            add(items, "RUNTIME_INVALID", "RUNTIME", BusinessPublishCheckLevel.BLOCK,
                    "运行配置生成失败", e.getMessage(), null, null,
                    "FIX_SCHEMA", "修复配置", "publish", 310);
        }
    }

    private void checkAppEntry(BusinessObjectDesignerService.DesignerContext context,
                               List<BusinessPublishCheckItemVO> items) {
        Long tenantId = resolveTenantId(context);
        AiBusinessObject object = context.getObject();
        AiBusinessApp app = businessAppMapper.selectRuntimeAppByObject(
                tenantId, object.getSuiteCode(), object.getObjectCode());
        if (app == null) {
            add(items, "APP_ENTRY_MISSING", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "应用入口未创建", "发布后需要配置业务应用入口和菜单挂载，否则用户无法从菜单进入填报页", null, null,
                    "CONFIG_APP_ENTRY", "配置入口", "publish", 320);
            return;
        }
        if (EnableStatus.DISABLED.matches(app.getStatus())) {
            add(items, "APP_ENTRY_DISABLED", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "应用入口已停用", "当前业务应用入口已停用，菜单点击后不会进入运行态", null, null,
                    "ENABLE_APP_ENTRY", "启用入口", "publish", 321);
        }
        if (!"RUNTIME".equalsIgnoreCase(StringUtils.defaultString(app.getEntryMode()))) {
            add(items, "APP_ENTRY_MODE_INVALID", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "入口打开模式不是运行态", "业务单据挂载建议使用 RUNTIME 模式，直接打开填报/列表页面", null, null,
                    "CONFIG_APP_ENTRY", "调整入口", "publish", 322);
        }
        if (StringUtils.isBlank(app.getConfigKey())) {
            add(items, "APP_ENTRY_CONFIG_EMPTY", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "入口缺少运行配置", "应用入口没有绑定 configKey，无法稳定打开动态填报页面", null, null,
                    "CONFIG_APP_ENTRY", "绑定运行配置", "publish", 323);
        }
        Map<String, Object> options = readAppOptions(app.getOptions());
        String entryType = StringUtils.defaultIfBlank(text(options.get("entryType")), "OBJECT_LIST").toUpperCase();
        if (!APP_ENTRY_TYPES.contains(entryType)) {
            add(items, "APP_ENTRY_TYPE_INVALID", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "入口类型不合法", "入口类型仅支持对象列表、新增表单、详情、审批/待办、报表/看板、外链/API", entryType, null,
                    "CONFIG_APP_ENTRY", "调整入口类型", "publish", 323);
        }
        String permissionCode = text(options.get("permissionCode"));
        if (StringUtils.isBlank(permissionCode)) {
            add(items, "APP_ENTRY_PERMISSION_EMPTY", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "入口权限码未配置", "建议为每个入口配置独立权限码，方便多个入口指向同一业务对象时分开授权", null, null,
                    "CONFIG_APP_ENTRY", "配置入口权限", "publish", 323);
        }
        Set<String> pageKeys = BusinessPublishTargetCatalog.pageKeys(context.getPageSchema());
        Set<String> formKeys = BusinessPublishTargetCatalog.formKeys(readDesignerOptions(context));
        String targetPageKey = text(options.get("targetPageKey"));
        if (StringUtils.isNotBlank(targetPageKey) && !pageKeys.contains(targetPageKey)) {
            add(items, "APP_ENTRY_PAGE_MISSING", "APP_ENTRY", BusinessPublishCheckLevel.BLOCK,
                    "入口目标页面不存在", "访问入口引用了不存在的页面: " + targetPageKey, targetPageKey, null,
                    "CONFIG_APP_ENTRY", "修复入口页面", "publish", 323);
        }
        String targetFormKey = text(options.get("targetFormKey"));
        if (StringUtils.isNotBlank(targetFormKey) && !formKeys.contains(targetFormKey)) {
            add(items, "APP_ENTRY_FORM_MISSING", "APP_ENTRY", BusinessPublishCheckLevel.BLOCK,
                    "入口目标表单不存在", "访问入口引用了不存在的表单: " + targetFormKey, targetFormKey, null,
                    "CONFIG_APP_ENTRY", "修复入口表单", "publish", 324);
        }
        Map<String, Object> adminMenu = mapValue(options.get("adminMenu"));
        Object menuResourceId = firstNonNull(adminMenu.get("menuResourceId"), options.get("menuResourceId"));
        String mountTarget = StringUtils.defaultIfBlank(text(options.get("mountTarget")), "ADMIN");
        boolean syncEnabled = isTrue(firstNonNull(adminMenu.get("syncEnabled"), options.get("adminMenuSyncEnabled")));
        if ("ADMIN".equalsIgnoreCase(mountTarget) && syncEnabled
                && (menuResourceId == null || StringUtils.isBlank(String.valueOf(menuResourceId)))) {
            add(items, "APP_MENU_MISSING", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "菜单资源未同步", "未发现应用入口的菜单资源 ID，动态菜单可能无法保持选中态", null, null,
                    "CONFIG_APP_ENTRY", "同步菜单", "publish", 324);
        }
        Object runtimeOpenModeValue = firstNonNull(options.get("runtimeOpenMode"), adminMenu.get("runtimeOpenMode"));
        String runtimeOpenMode = text(runtimeOpenModeValue);
        if (StringUtils.isNotBlank(runtimeOpenMode)
                && !RUNTIME_OPEN_MODES.contains(runtimeOpenMode.trim().toUpperCase())) {
            add(items, "APP_RUNTIME_OPEN_MODE_EMPTY", "APP_ENTRY", BusinessPublishCheckLevel.WARN,
                    "运行打开模式不合法", "运行打开模式仅支持 LIST、CREATE_FORM、DETAIL，缺省时系统按 LIST 打开", null, null,
                    "CONFIG_APP_ENTRY", "配置打开方式", "publish", 325);
        }
        Map<String, Object> defaultParams = mapValue(options.get("defaultParams"));
        if (!defaultParams.isEmpty()) {
            for (String key : defaultParams.keySet()) {
                String lowerKey = StringUtils.lowerCase(key);
                if (StringUtils.containsAny(lowerKey, "password", "passwd", "token", "secret", "apikey", "accesskey")) {
                    add(items, "APP_ENTRY_PARAM_SENSITIVE", "APP_ENTRY", BusinessPublishCheckLevel.BLOCK,
                            "入口默认参数包含敏感键", "默认参数不能保存密码、Token 或密钥类长期凭证: " + key,
                            key, null, "CONFIG_APP_ENTRY", "清理默认参数", "publish", 326);
                }
            }
        }
        if (items.stream().noneMatch(item -> "APP_ENTRY".equals(item.getCategory())
                && !BusinessPublishCheckLevel.PASS.equals(item.getLevel()))) {
            add(items, "APP_ENTRY_PASS", "APP_ENTRY", BusinessPublishCheckLevel.PASS,
                    "应用入口检查通过", "运行入口、菜单资源和打开模式已具备基础配置", null, null,
                    null, null, "publish", 329);
        }
    }

    private void checkDocumentConfig(BusinessObjectDesignerService.DesignerContext context,
                                     List<BusinessPublishCheckItemVO> items) {
        BusinessDocumentConfigVO config = documentConfigService.getConfig(context.getObject().getId());
        if (!Boolean.TRUE.equals(config.getDocumentEnabled())) {
            add(items, "DOCUMENT_DISABLED", "DOCUMENT", BusinessPublishCheckLevel.PASS,
                    "单据模式未启用", "当前对象按普通 CRUD 发布", null, null,
                    null, null, "flow", 350);
            return;
        }
        Set<String> fields = collectDocumentFields(context.getModelSchema());
        if (StringUtils.isBlank(config.getStatusField())) {
            add(items, "DOCUMENT_STATUS_EMPTY", "DOCUMENT", BusinessPublishCheckLevel.BLOCK,
                    "单据状态字段为空", "启用单据模式后必须配置状态字段", null, null,
                    "CONFIG_DOCUMENT", "配置单据", "flow", 351);
        } else if (!fields.contains(config.getStatusField())) {
            add(items, "DOCUMENT_STATUS_MISSING", "DOCUMENT", BusinessPublishCheckLevel.BLOCK,
                    "单据状态字段不存在", "状态字段不存在: " + config.getStatusField(), config.getStatusField(), null,
                    "CONFIG_DOCUMENT", "配置单据", "flow", 352);
        }
        if (StringUtils.isNotBlank(config.getStarterField()) && !fields.contains(config.getStarterField())) {
            add(items, "DOCUMENT_STARTER_MISSING", "DOCUMENT", BusinessPublishCheckLevel.BLOCK,
                    "单据发起人字段不存在", "发起人字段不存在: " + config.getStarterField(), config.getStarterField(), null,
                    "CONFIG_DOCUMENT", "配置单据", "flow", 353);
        }
        if (StringUtils.isNotBlank(config.getOwnerField()) && !fields.contains(config.getOwnerField())) {
            add(items, "DOCUMENT_OWNER_MISSING", "DOCUMENT", BusinessPublishCheckLevel.BLOCK,
                    "单据负责人字段不存在", "负责人字段不存在: " + config.getOwnerField(), config.getOwnerField(), null,
                    "CONFIG_DOCUMENT", "配置单据", "flow", 354);
        }
        if (StringUtils.isBlank(config.getNoRuleTemplate()) && StringUtils.isBlank(config.getDocumentNoRule())) {
            add(items, "DOCUMENT_NO_RULE_EMPTY", "DOCUMENT", BusinessPublishCheckLevel.WARN,
                    "编号规则未配置", "单据可运行，但建议配置编号规则以便追踪流程和消息", null, null,
                    "CONFIG_DOCUMENT", "配置编号规则", "document", 354);
        }
        if (config.getStatusMappingRows() == null || config.getStatusMappingRows().isEmpty()) {
            add(items, "DOCUMENT_STATUS_MAPPING_EMPTY", "DOCUMENT", BusinessPublishCheckLevel.BLOCK,
                    "状态映射为空", "启用单据模式后必须配置标准状态到字段值的映射", null, null,
                    "CONFIG_DOCUMENT", "配置状态映射", "document", 354);
        }
        Map<String, Object> mainFlowSummary = config.getMainFlowSummary() == null
                ? Map.of()
                : config.getMainFlowSummary();
        boolean mainFlowConfigured = Boolean.TRUE.equals(mainFlowSummary.get("configured"));
        String startMode = StringUtils.defaultIfBlank(text(mainFlowSummary.get("startMode")), "MANUAL");
        if (!mainFlowConfigured) {
            add(items, "DOCUMENT_FLOW_EMPTY", "DOCUMENT", BusinessPublishCheckLevel.WARN,
                    "主流程未配置", "单据可保存，但发起主流程前需要先在流程与自动化中配置主流程", null, null,
                    "CONFIG_FLOW", "配置流程", "flow", 355);
        } else if (!Boolean.TRUE.equals(mainFlowSummary.get("complete"))) {
            String gapText = summarizeFlowGaps(mainFlowSummary.get("gaps"));
            add(items, "DOCUMENT_FLOW_INCOMPLETE", "DOCUMENT", BusinessPublishCheckLevel.WARN,
                    "主流程配置不完整", StringUtils.defaultIfBlank(gapText, "主流程已选择，但变量映射、发起方式或按钮配置仍需补齐"), null, null,
                    "CONFIG_FLOW", "配置流程", "flow", 356);
        }
        if (requiresTrigger(startMode)) {
            Long triggerCount = triggerMapper.countActiveByObjectAndAction(
                    resolveTenantId(context), context.getObject().getObjectCode(), "START_FLOW");
            if (triggerCount == null || triggerCount <= 0) {
                add(items, "DOCUMENT_TRIGGER_MISSING", "DOCUMENT", BusinessPublishCheckLevel.WARN,
                        "自动发起触发器缺失", "主流程发起方式包含触发器，但当前对象没有启用的发起主流程触发器", null, null,
                        "CONFIG_TRIGGER", "配置触发器", "trigger", 357);
            }
        }
        if (requiresManualButton(startMode)) {
            add(items, "DOCUMENT_MANUAL_BUTTON_PASS", "DOCUMENT", BusinessPublishCheckLevel.PASS,
                    "手动发起按钮自动生成", "业务对象页面会按状态、权限和流程绑定自动生成发起主流程按钮", null, null,
                    null, null, "publish", 358);
        }
        if (items.stream().noneMatch(item -> "DOCUMENT".equals(item.getCategory())
                && !BusinessPublishCheckLevel.PASS.equals(item.getLevel()))) {
            add(items, "DOCUMENT_PASS", "DOCUMENT", BusinessPublishCheckLevel.PASS,
                    "单据检查通过", "单据状态字段和流程配置满足发布要求", null, null,
                    null, null, "flow", 359);
        }
    }

    private void checkRuntimeDataSource(LowcodeModelSchema modelSchema, List<BusinessPublishCheckItemVO> items) {
        if (modelSchema == null) {
            add(items, "DATASOURCE_MODEL_EMPTY", "DATASOURCE", BusinessPublishCheckLevel.BLOCK,
                    "运行数据源无法解析", "模型协议为空，无法解析发布目标库", null, null,
                    "ADVANCED_CONFIG", "高级配置", "advanced", 390);
            return;
        }
        try {
            LowcodeRuntimeDataSourceContext context = runtimeDataSourceResolver.resolve(modelSchema);
            add(items, "DATASOURCE_TARGET_PASS", "DATASOURCE", BusinessPublishCheckLevel.PASS,
                    "发布目标库已确认",
                    "目标库: " + runtimeDatasourceLabel(context) + "；表: " + context.getTableName()
                            + "；主键: " + runtimePrimaryKeyLabel(context),
                    null, null, null, null, "publish", 391);
            if (context.isReadonly() || !context.isAllowWrite()) {
                add(items, "DATASOURCE_WRITE_DISABLED", "DATASOURCE", BusinessPublishCheckLevel.WARN,
                        "目标库未开放写入",
                        "运行态新增、编辑、删除和导入会被拦截；仅查询、导出和详情可用",
                        null, null, "CHECK_DATABASE", "检查数据源", "advanced", 392);
            }
            if (!context.isAllowDdl()) {
                add(items, "DATASOURCE_DDL_DISABLED", "DATASOURCE", BusinessPublishCheckLevel.WARN,
                        "目标库禁止在线 DDL",
                        "发布时不会在该数据源自动建表或变更字段，需由数据库管理员手工同步表结构",
                        null, null, "CHECK_DATABASE", "检查数据源", "advanced", 393);
            }
            if ("HIGH".equalsIgnoreCase(StringUtils.defaultString(context.getRiskLevel()))) {
                add(items, "DATASOURCE_HIGH_RISK", "DATASOURCE", BusinessPublishCheckLevel.WARN,
                        "高风险数据源",
                        "当前运行目标被标记为高风险，发布前请确认不是旧系统生产写库或已启用只读保护",
                        null, null, "CHECK_DATABASE", "检查数据源", "advanced", 394);
            }
        } catch (Exception e) {
            add(items, "DATASOURCE_UNAVAILABLE", "DATASOURCE", BusinessPublishCheckLevel.BLOCK,
                    "运行数据源不可用", e.getMessage(), null, null,
                    "CHECK_DATABASE", "检查数据源", "advanced", 390);
        }
    }

    private void checkTable(LowcodeModelSchema modelSchema, List<BusinessPublishCheckItemVO> items) {
        if (modelSchema == null || StringUtils.isBlank(modelSchema.getTableName())) {
            add(items, "TABLE_NAME_EMPTY", "TABLE", BusinessPublishCheckLevel.BLOCK,
                    "数据表缺失", "模型缺少运行数据表", null, null,
                    "ADVANCED_CONFIG", "高级配置", "advanced", 400);
            return;
        }
        try {
            LowcodeRuntimeDataSourceContext runtimeContext = runtimeDataSourceResolver.resolve(modelSchema);
            if (!ddlService.tableExists(modelSchema)) {
                boolean canOnlineDdl = hasPermission(DDL_PERMISSION);
                boolean canExecuteOnlineDdl = canOnlineDdl && runtimeContext.isAllowDdl();
                add(items, "TABLE_MISSING", "TABLE", canExecuteOnlineDdl ? BusinessPublishCheckLevel.WARN : BusinessPublishCheckLevel.BLOCK,
                        "数据表不存在", canExecuteOnlineDdl
                                ? "可在发布时勾选同步表结构自动创建到目标库: " + runtimeDatasourceLabel(runtimeContext)
                                : (!runtimeContext.isAllowDdl() ? "目标数据源禁止在线 DDL，请联系数据库管理员在 "
                                + runtimeDatasourceLabel(runtimeContext) + " 手工创建数据表"
                                : "缺少在线建表权限，请联系管理员同步表结构"),
                        null, null, "SYNC_TABLE", "同步表结构", "publish", 410);
                return;
            }
            if (!ddlService.hasSinglePrimaryKey(modelSchema)) {
                add(items, "TABLE_PK_MISSING", "TABLE", BusinessPublishCheckLevel.BLOCK,
                        "主键不符合要求", "业务表必须包含单字段主键", null, null,
                        "FIX_TABLE", "修复数据表", "advanced", 420);
                return;
            }
            List<String> retiredColumns = findRetiredBusinessColumns(modelSchema);
            if (!retiredColumns.isEmpty()) {
                add(items, "TABLE_COLUMN_RETIRED", "TABLE", BusinessPublishCheckLevel.WARN,
                        "存在已隐藏字段列", "字段已隐藏或停用，发布不会物理删除数据表列: " + String.join("、", retiredColumns),
                        null, null, "CHECK_FIELD", "检查字段", "fields", 425);
            }

            LowcodeDdlPreviewVO preview = ddlService.previewCreateTable(modelSchema);
            List<String> ddlStatements = preview.getDdlStatements();
            if (ddlStatements != null && !ddlStatements.isEmpty()) {
                boolean canOnlineDdl = hasPermission(DDL_PERMISSION);
                boolean containsUnsafeDdl = ddlService.containsUnsafeOnlineDdl(ddlStatements);
                boolean canExecuteOnlineDdl = canOnlineDdl && runtimeContext.isAllowDdl()
                        && Boolean.TRUE.equals(preview.getExecutable()) && !containsUnsafeDdl;
                String itemCode = containsUnsafeDdl
                        ? "TABLE_COLUMN_CHANGED" : resolveTableSyncItemCode(ddlStatements);
                String unavailableMessage = containsUnsafeDdl
                        ? "数据表差异包含字段类型、长度或必填属性调整，仅允许在数据结构中预览并导出脚本后人工审核执行: "
                        + summarizeDdlStatements(ddlStatements)
                        : (!runtimeContext.isAllowDdl() ? "目标数据源禁止在线 DDL，需手工同步: "
                        + summarizeDdlStatements(ddlStatements)
                        : "数据表结构与字段配置不一致且当前用户无在线同步权限: "
                        + summarizeDdlStatements(ddlStatements));
                add(items, itemCode, "TABLE", canExecuteOnlineDdl ? BusinessPublishCheckLevel.WARN : BusinessPublishCheckLevel.BLOCK,
                        "数据表结构未同步",
                        canExecuteOnlineDdl ? "发布时勾选同步数据表结构后，会在 " + runtimeDatasourceLabel(runtimeContext)
                                + " 自动执行受控变更: " + summarizeDdlStatements(ddlStatements)
                                : unavailableMessage,
                        null, null, canExecuteOnlineDdl ? "SYNC_TABLE" : "REVIEW_DDL",
                        canExecuteOnlineDdl ? "同步表结构" : "查看数据库差异",
                        canExecuteOnlineDdl ? "publish" : "advanced", 430);
                if (!canExecuteOnlineDdl) {
                    return;
                }
            }
            add(items, "TABLE_PASS", "TABLE", BusinessPublishCheckLevel.PASS,
                    "数据表检查通过", "目标库 " + runtimeDatasourceLabel(runtimeContext)
                            + " 中数据表存在且主键符合低代码运行要求", null, null, null, null, "publish", 490);
        } catch (Exception e) {
            add(items, "TABLE_CHECK_WARN", "TABLE", BusinessPublishCheckLevel.WARN,
                    "数据表检查未完成", "当前环境无法完成数据表检查: " + e.getMessage(), null, null,
                    "CHECK_DATABASE", "检查数据库", "advanced", 430);
        }
    }

    private String runtimeDatasourceLabel(LowcodeRuntimeDataSourceContext context) {
        if (context == null) {
            return "未知数据源";
        }
        if (context.isMaster()) {
            return "平台主库";
        }
        return StringUtils.firstNonBlank(
                context.getDatasourceName(),
                context.getDatasourceCode(),
                context.getDatasourceId() == null ? null : String.valueOf(context.getDatasourceId()),
                "外部数据源"
        );
    }

    private String runtimePrimaryKeyLabel(LowcodeRuntimeDataSourceContext context) {
        if (context == null || context.getPrimaryKey() == null) {
            return "id";
        }
        String field = StringUtils.defaultIfBlank(context.getPrimaryKey().getField(), "id");
        String column = StringUtils.defaultIfBlank(context.getPrimaryKey().getColumnName(), field);
        return StringUtils.equals(field, column) ? column : field + "/" + column;
    }

    private String summarizeFlowGaps(Object gaps) {
        if (gaps instanceof List<?> list && !list.isEmpty()) {
            return "主流程缺口: " + String.join("、", list.stream()
                    .map(String::valueOf)
                    .filter(StringUtils::isNotBlank)
                    .toList());
        }
        return null;
    }

    private boolean requiresTrigger(String startMode) {
        String normalized = StringUtils.defaultIfBlank(startMode, "MANUAL").trim().toUpperCase();
        return "TRIGGER".equals(normalized)
                || "BOTH".equals(normalized)
                || "MANUAL_AND_TRIGGER".equals(normalized)
                || "MANUAL_TRIGGER".equals(normalized);
    }

    private boolean requiresManualButton(String startMode) {
        String normalized = StringUtils.defaultIfBlank(startMode, "MANUAL").trim().toUpperCase();
        return "MANUAL".equals(normalized)
                || "BOTH".equals(normalized)
                || "MANUAL_AND_TRIGGER".equals(normalized)
                || "MANUAL_TRIGGER".equals(normalized);
    }

    private void checkPermissionSummary(BusinessObjectDesignerService.DesignerContext context,
                                        BusinessPermissionSummaryVO permissionSummary,
                                        List<BusinessPublishCheckItemVO> items) {
        BusinessPermissionSummaryVO summary = permissionSummary == null
                ? permissionService.documentActionSummary(context.getObject()) : permissionSummary;
        List<String> missingRequired = summary.getActionPermissions().stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequired()) && !Boolean.TRUE.equals(item.getConfigured()))
                .map(BusinessPermissionSummaryVO.ActionPermissionVO::getActionName)
                .toList();
        if (!missingRequired.isEmpty()) {
            add(items, "PERMISSION_ACTION_MISSING", "PERMISSION", BusinessPublishCheckLevel.WARN,
                    "关键按钮权限未配置", "缺少动作权限资源: " + String.join("、", missingRequired),
                    null, null, "CONFIG_PERMISSION", "配置权限", "permission", 360);
            return;
        }
        add(items, "PERMISSION_ACTION_PASS", "PERMISSION", BusinessPublishCheckLevel.PASS,
                "按钮权限检查通过", "保存、提交、流程、触发器和报表动作权限已有摘要", null, null,
                null, null, "permission", 369);
    }

    private BusinessPublishCheckVO buildResult(List<BusinessPublishCheckItemVO> items) {
        items.sort(Comparator.comparing(item -> item.getSortOrder() == null ? Integer.MAX_VALUE : item.getSortOrder()));
        BusinessPublishCheckVO vo = new BusinessPublishCheckVO();
        vo.setItems(items);
        vo.setPassItems(items.stream().filter(item -> BusinessPublishCheckLevel.PASS.equals(item.getLevel())).toList());
        vo.setWarnItems(items.stream().filter(item -> BusinessPublishCheckLevel.WARN.equals(item.getLevel())).toList());
        vo.setBlockItems(items.stream().filter(item -> BusinessPublishCheckLevel.BLOCK.equals(item.getLevel())).toList());
        vo.setPassCount(vo.getPassItems().size());
        vo.setWarnCount(vo.getWarnItems().size());
        vo.setBlockCount(vo.getBlockItems().size());
        vo.setPublishable(vo.getBlockCount() == 0);
        vo.setOverallStatus(vo.getBlockCount() > 0 ? BusinessPublishCheckLevel.BLOCK
                : vo.getWarnCount() > 0 ? BusinessPublishCheckLevel.WARN : BusinessPublishCheckLevel.PASS);
        return vo;
    }

    private List<String> findRetiredBusinessColumns(LowcodeModelSchema modelSchema) {
        if (modelSchema.getFields() == null) {
            return List.of();
        }
        Set<String> existingColumns = ddlService.listColumns(modelSchema);
        return modelSchema.getFields().stream()
                .filter(field -> field != null && !Boolean.TRUE.equals(field.getSystemField()))
                .filter(field -> "DISABLED".equalsIgnoreCase(StringUtils.defaultString(field.getFieldStatus()))
                        || "HIDDEN".equalsIgnoreCase(StringUtils.defaultString(field.getFieldStatus())))
                .map(LowcodeFieldSchema::getColumnName)
                .filter(StringUtils::isNotBlank)
                .filter(column -> !isSystemColumn(column))
                .filter(existingColumns::contains)
                .distinct()
                .toList();
    }

    private String resolveTableSyncItemCode(List<String> ddlStatements) {
        boolean hasModify = ddlStatements.stream().anyMatch(ddl -> StringUtils.containsIgnoreCase(ddl, " MODIFY COLUMN "));
        if (hasModify) {
            return "TABLE_COLUMN_CHANGED";
        }
        boolean hasAdd = ddlStatements.stream().anyMatch(ddl -> StringUtils.containsIgnoreCase(ddl, " ADD COLUMN "));
        if (hasAdd) {
            return "TABLE_COLUMN_MISSING";
        }
        return "TABLE_INDEX_MISSING";
    }

    private String summarizeDdlStatements(List<String> ddlStatements) {
        List<String> summary = ddlStatements.stream()
                .map(this::summarizeDdlStatement)
                .filter(StringUtils::isNotBlank)
                .limit(6)
                .toList();
        String suffix = ddlStatements.size() > summary.size() ? " 等 " + ddlStatements.size() + " 项" : "";
        return String.join("、", summary) + suffix;
    }

    private String summarizeDdlStatement(String ddl) {
        if (StringUtils.isBlank(ddl)) {
            return "";
        }
        String normalized = ddl.toUpperCase();
        if (normalized.contains(" ADD COLUMN ")) {
            return "新增列 " + extractBacktickValueAfter(ddl, "ADD COLUMN");
        }
        if (normalized.contains(" MODIFY COLUMN ")) {
            return "修改列 " + extractBacktickValueAfter(ddl, "MODIFY COLUMN");
        }
        if (normalized.contains(" ADD UNIQUE KEY ")) {
            return "新增唯一索引 " + extractBacktickValueAfter(ddl, "ADD UNIQUE KEY");
        }
        if (normalized.contains(" ADD KEY ")) {
            return "新增索引 " + extractBacktickValueAfter(ddl, "ADD KEY");
        }
        return ddl.length() > 80 ? ddl.substring(0, 80) + "..." : ddl;
    }

    private String extractBacktickValueAfter(String ddl, String marker) {
        String upper = ddl.toUpperCase();
        int markerIndex = upper.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int start = ddl.indexOf('`', markerIndex + marker.length());
        int end = ddl.indexOf('`', start + 1);
        if (start < 0 || end <= start) {
            return "";
        }
        return "`" + ddl.substring(start + 1, end) + "`";
    }

    private boolean isSystemColumn(String columnName) {
        return "id".equals(columnName)
                || "tenant_id".equals(columnName)
                || "create_by".equals(columnName)
                || "create_time".equals(columnName)
                || "create_dept".equals(columnName)
                || "update_by".equals(columnName)
                || "update_time".equals(columnName)
                || "del_flag".equals(columnName);
    }

    private void add(List<BusinessPublishCheckItemVO> items, String code, String category, String level,
                     String title, String message, String fieldCode, String zoneKey, String fixAction,
                     String fixActionLabel, String fixTarget, Integer sortOrder) {
        BusinessPublishCheckCollector.add(items, code, category, level, title, message, fieldCode, zoneKey,
                fixAction, fixActionLabel, fixTarget, sortOrder);
    }

    private List<Map<String, Object>> resolveLinkageRules(BusinessObjectDesignerService.DesignerContext context) {
        List<Map<String, Object>> rules = new ArrayList<>(readLinkageRulesFromDesignerOptions(context));
        Set<String> existingKeys = new LinkedHashSet<>();
        rules.forEach(rule -> existingKeys.add(linkageKey(rule)));
        if (context.getModelSchema() != null && context.getModelSchema().getFields() != null) {
            for (LowcodeFieldSchema field : context.getModelSchema().getFields()) {
                Map<String, Object> cascade = mapValue(field == null || field.getBasicProps() == null
                        ? null : field.getBasicProps().get("cascade"));
                if (cascade.isEmpty() || isFalse(cascade.get("enabled"))) {
                    continue;
                }
                String sourceField = text(cascade.get("sourceField"));
                String targetField = field.getField();
                if (StringUtils.isBlank(sourceField) || StringUtils.isBlank(targetField)) {
                    continue;
                }
                Map<String, Object> rule = buildLinkageRuleFromCascade(field, cascade);
                String key = linkageKey(rule);
                if (existingKeys.add(key)) {
                    rules.add(rule);
                }
            }
        }
        return rules;
    }

    private List<Map<String, Object>> readLinkageRulesFromDesignerOptions(
            BusinessObjectDesignerService.DesignerContext context) {
        Map<String, Object> designerOptions = readDesignerOptions(context);
        try {
            Object value = designerOptions.get(LINKAGE_SCHEMA_OPTION_KEY);
            Map<String, Object> schema;
            if (value instanceof String text && StringUtils.isNotBlank(text)) {
                schema = objectMapper.readValue(text, new TypeReference<>() {});
            } else {
                schema = mapValue(value);
            }
            Object rules = schema.get("rules");
            if (rules instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> mapValue(item))
                        .toList();
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private Map<String, Object> readDesignerOptions(BusinessObjectDesignerService.DesignerContext context) {
        if (context == null || context.getObject() == null
                || StringUtils.isBlank(context.getObject().getDesignerOptions())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(context.getObject().getDesignerOptions(), new TypeReference<>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> buildLinkageRuleFromCascade(LowcodeFieldSchema field, Map<String, Object> cascade) {
        String mode = StringUtils.defaultIfBlank(text(cascade.get("mode")), text(cascade.get("matchMode")));
        String type = StringUtils.defaultIfBlank(text(cascade.get("type")), mode);
        if (StringUtils.isBlank(type)) {
            type = "linkedDict";
        }
        Map<String, Object> rule = new LinkedHashMap<>();
        String sourceField = text(cascade.get("sourceField"));
        rule.put("ruleId", StringUtils.defaultIfBlank(text(cascade.get("ruleId")),
                "linkage_" + sourceField + "_" + field.getField()));
        rule.put("type", type);
        rule.put("sourceField", sourceField);
        rule.put("targetField", field.getField());
        rule.put("dataSourceType", resolveLinkageDataSourceType(type));
        rule.put("matchMode", type);
        rule.put("dictConfig", buildCascadeDictConfig(field, cascade));
        rule.put("remoteConfig", buildCascadeRemoteConfig(cascade));
        rule.put("objectConfig", buildCascadeObjectConfig(field, cascade));
        rule.put("emptyStrategy", StringUtils.defaultIfBlank(text(cascade.get("emptyStrategy")), "empty"));
        rule.put("clearOnSourceChange", !isFalse(cascade.get("clearOnSourceChange"))
                && !isFalse(cascade.get("clearOnParentChange")));
        rule.put("enabled", !isFalse(cascade.get("enabled")));
        return rule;
    }

    private Map<String, Object> buildCascadeDictConfig(LowcodeFieldSchema field, Map<String, Object> cascade) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sourceDictType", text(cascade.get("sourceDictType")));
        config.put("targetDictType", StringUtils.defaultIfBlank(text(cascade.get("targetDictType")),
                field.getDictType()));
        config.put("linkedDictType", text(cascade.get("linkedDictType")));
        return config;
    }

    private Map<String, Object> buildCascadeRemoteConfig(Map<String, Object> cascade) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", text(cascade.get("url")));
        config.put("method", StringUtils.defaultIfBlank(text(cascade.get("method")), "GET"));
        config.put("paramName", StringUtils.defaultIfBlank(text(cascade.get("paramName")),
                text(cascade.get("sourceField"))));
        return config;
    }

    private Map<String, Object> buildCascadeObjectConfig(LowcodeFieldSchema field, Map<String, Object> cascade) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("targetObjectCode", StringUtils.defaultIfBlank(text(cascade.get("targetObjectCode")),
                field.getReferenceObjectCode()));
        config.put("displayField", StringUtils.defaultIfBlank(text(cascade.get("displayField")),
                field.getReferenceDisplayField()));
        return config;
    }

    private String linkageKey(Map<String, Object> rule) {
        return text(rule.get("sourceField")) + "->" + text(rule.get("targetField"));
    }

    private String resolveLinkageDataSourceType(String type) {
        if ("parentDictCode".equals(type) || "linkedDict".equals(type)) {
            return "dict";
        }
        if ("orgScope".equals(type)) {
            return "org";
        }
        if ("objectReference".equals(type)) {
            return "object";
        }
        return "remote";
    }

    private Map<String, LowcodeFieldSchema> collectFieldMap(LowcodeModelSchema modelSchema) {
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

    private boolean hasBusinessFields(LowcodeModelSchema modelSchema) {
        return modelSchema != null && modelSchema.getFields() != null
                && modelSchema.getFields().stream()
                .anyMatch(field -> field != null && !Boolean.TRUE.equals(field.getSystemField()));
    }

    private Set<String> collectFields(LowcodeModelSchema modelSchema) {
        Set<String> fields = new LinkedHashSet<>();
        if (modelSchema != null && modelSchema.getFields() != null) {
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field != null && StringUtils.isNotBlank(field.getField())) {
                    fields.add(field.getField());
                }
            }
        }
        return fields;
    }

    private Set<String> collectDocumentFields(LowcodeModelSchema modelSchema) {
        Set<String> fields = new LinkedHashSet<>(collectFields(modelSchema));
        fields.addAll(Set.of("id", "tenantId", "tenant_id", "createBy", "create_by", "createTime", "create_time",
                "createDept", "create_dept", "updateBy", "update_by", "updateTime", "update_time"));
        if (modelSchema != null && modelSchema.getFields() != null) {
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field == null) {
                    continue;
                }
                if (StringUtils.isNotBlank(field.getColumnName())) {
                    fields.add(field.getColumnName());
                    fields.add(snakeToCamel(field.getColumnName()));
                }
            }
        }
        return fields;
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

    private Set<String> collectPageFields(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        Set<String> fields = collectFields(modelSchema);
        if (pageSchema == null || pageSchema.getModelRefs() == null) {
            return fields;
        }
        pageSchema.getModelRefs().stream()
                .filter(ref -> ref != null && ref.getFields() != null)
                .forEach(ref -> {
                    String modelCode = StringUtils.trimToEmpty(ref.getModelCode());
                    for (Map<String, Object> field : ref.getFields()) {
                        String sourceField = text(field.get("sourceField"));
                        if (StringUtils.isBlank(sourceField)) {
                            sourceField = text(field.get("field"));
                        }
                        String fieldRef = text(field.get("fieldRef"));
                        String columnName = text(field.get("columnName"));
                        if (StringUtils.isNotBlank(fieldRef)) {
                            fields.add(fieldRef);
                        }
                        if (StringUtils.isNotBlank(sourceField)) {
                            fields.add(sourceField);
                        }
                        if (StringUtils.isNotBlank(columnName)) {
                            fields.add(columnName);
                        }
                        if (StringUtils.isNotBlank(modelCode) && StringUtils.isNotBlank(sourceField)) {
                            fields.add(modelCode + "." + sourceField);
                            fields.add(modelCode + "__" + sourceField);
                        }
                    }
                });
        return fields;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private Integer intValue(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return Integer.parseInt(text);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Map<String, Object> readAppOptions(String options) {
        if (StringUtils.isBlank(options)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(options, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
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

    private boolean isFalse(Object value) {
        return Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(text(value)) || "0".equals(text(value));
    }

    private boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(text(value)) || "1".equals(text(value));
    }

    private boolean hasPermission(String permission) {
        try {
            return SessionHelper.hasPermission(permission);
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveConfigKey(BusinessObjectDesignerService.DesignerContext context) {
        if (context.getConfig() != null && StringUtils.isNotBlank(context.getConfig().getConfigKey())) {
            return context.getConfig().getConfigKey();
        }
        return StringUtils.defaultString(context.getObject().getSuiteCode())
                + "_" + StringUtils.defaultString(context.getObject().getObjectCode());
    }

    private void checkFormula(BusinessObjectDesignerService.DesignerContext context,
                              List<BusinessPublishCheckItemVO> items) {
        if (context == null || context.getModelSchema() == null) {
            return;
        }
        FormulaValidationResult result = formulaPublishValidator.validate(
                context.getModelSchema(), buildFormulaObjectContexts(context));
        if (result == null) {
            return;
        }
        if (result.isValid() && !result.hasWarnings()) {
            add(items, "FORMULA_PASS", "FORMULA", BusinessPublishCheckLevel.PASS,
                    "Formula check passed", "All formula configurations passed validation",
                    null, null, null, null, null, 410);
            return;
        }
        if (result.hasErrors()) {
            for (FormulaValidationResult.FormulaError error : result.getErrors()) {
                add(items, "FORMULA_ERROR", "FORMULA", BusinessPublishCheckLevel.BLOCK,
                        "Formula configuration error",
                        "[" + error.getCategory() + "] " + error.getFieldName() + ": " + error.getMessage(),
                        error.getFieldName(), null,
                        "EDIT_FIELD", "Edit field formula",
                        "fields", 420);
            }
        }
        if (result.hasWarnings()) {
            for (String warning : result.getWarnings()) {
                add(items, "FORMULA_WARN", "FORMULA", BusinessPublishCheckLevel.WARN,
                        "Formula warning", warning,
                        null, null, null, null, null, 430);
            }
        }
        if (!result.hasErrors() && result.hasWarnings()) {
            add(items, "FORMULA_PASS", "FORMULA", BusinessPublishCheckLevel.PASS,
                    "Formula check passed (with warnings)", "Formula validation passed with warnings",
                    null, null, null, null, null, 440);
        }
    }

    private List<FormulaObjectDependencyAnalyzer.ObjectContext> buildFormulaObjectContexts(
            BusinessObjectDesignerService.DesignerContext context) {
        // 只校验当前对象公式；禁止按套件全量 loadContext（小应用也会放大成数十次编译读库）。
        List<FormulaObjectDependencyAnalyzer.ObjectContext> contexts = new ArrayList<>();
        addFormulaObjectContext(contexts, context);
        return contexts;
    }

    private void addFormulaObjectContext(List<FormulaObjectDependencyAnalyzer.ObjectContext> contexts,
                                         BusinessObjectDesignerService.DesignerContext context) {
        if (context == null || context.getObject() == null || StringUtils.isBlank(context.getObject().getObjectCode())) {
            return;
        }
        contexts.add(formulaPublishValidator.buildObjectContext(
                context.getObject().getObjectCode(),
                context.getModelSchema(),
                toFormulaObjectRelations(context.getRelations())));
    }

    private List<FormulaObjectDependencyAnalyzer.ObjectRelation> toFormulaObjectRelations(
            List<BusinessObjectRelationVO> relations) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        List<FormulaObjectDependencyAnalyzer.ObjectRelation> result = new ArrayList<>();
        for (BusinessObjectRelationVO relation : relations) {
            if (relation == null) {
                continue;
            }
            result.add(new FormulaObjectDependencyAnalyzer.ObjectRelation(
                    relation.getId() == null ? null : String.valueOf(relation.getId()),
                    relation.getRelationName(),
                    relation.getSourceObjectCode(),
                    relation.getTargetObjectCode(),
                    relation.getSourceFieldCode(),
                    relation.getTargetFieldCode()));
        }
        return result;
    }

    private void enqueueCrossObjectRecompute(BusinessObjectDesignerService.DesignerContext context) {
        if (context == null || context.getObject() == null || context.getModelSchema() == null) {
            return;
        }
        crossObjectRecomputeTaskService.enqueueForPublish(formulaPublishValidator.buildObjectContext(
                context.getObject().getObjectCode(),
                context.getModelSchema(),
                toFormulaObjectRelations(context.getRelations())));
    }

    private Long resolveTenantId(BusinessObjectDesignerService.DesignerContext context) {
        if (context != null && context.getObject() != null && context.getObject().getTenantId() != null) {
            return context.getObject().getTenantId();
        }
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId != null ? tenantId : 1L;
    }
}
