package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessPublishCheckLevel;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeSchemaValidator;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectActionVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectRelationVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckItemVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 页面与事务动作发布校验器。
 *
 * <p>以 Validator Chain 顺序执行页面协议、目标引用、动作完整性和事务命令策略。</p>
 */
final class BusinessObjectPagePublishValidator {

    private static final String DESIGNER_ACTIONS_KEY = "actions";

    private final ObjectMapper objectMapper;
    private final LowcodeSchemaValidator schemaValidator;

    BusinessObjectPagePublishValidator(ObjectMapper objectMapper, LowcodeSchemaValidator schemaValidator) {
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }

    void validate(BusinessObjectDesignerService.DesignerContext context,
                  List<BusinessPublishCheckItemVO> items) {
        validatePage(context, items);
        validateTransactionalActions(context, items);
    }

    private void validatePage(BusinessObjectDesignerService.DesignerContext context,
                           List<BusinessPublishCheckItemVO> items) {
        LowcodeModelSchema modelSchema = context.getModelSchema();
        LowcodePageSchema pageSchema = context.getPageSchema();
        if (pageSchema == null) {
            add(items, "PAGE_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                    "页面布局为空", "请先配置表单、列表或详情布局", null, null,
                    "CONFIG_LAYOUT", "配置布局", "form", 100);
            return;
        }
        Set<String> modelFields = collectPageFields(modelSchema, pageSchema);
        if (pageSchema.getZones() != null) {
            for (LowcodePageZone zone : pageSchema.getZones()) {
                if (zone == null || zone.getFieldRefs() == null) {
                    continue;
                }
                if ("toolbar".equals(zone.getZoneKey())) {
                    continue;
                }
                for (String ref : zone.getFieldRefs()) {
                    if (StringUtils.isNotBlank(ref) && !modelFields.contains(ref)) {
                        add(items, "PAGE_REF_MISSING", "PAGE", BusinessPublishCheckLevel.WARN,
                                "页面引用了已删除的字段", "区域 " + zone.getZoneKey() + " 引用了已删除字段: " + ref + "，发布时会自动忽略",
                                ref, zone.getZoneKey(), "REMOVE_FIELD_REF", "移除脏引用", zone.getZoneKey(), 110);
                    }
                }
            }
        }
        try {
            schemaValidator.validatePage(pageSchema, modelSchema);
            add(items, "PAGE_PASS", "PAGE", BusinessPublishCheckLevel.PASS,
                    "页面检查通过", "表单、列表和详情布局字段引用有效", null, null, null, null, "form", 190);
        } catch (BusinessException e) {
            add(items, "PAGE_SCHEMA_INVALID", "PAGE", BusinessPublishCheckLevel.BLOCK,
                    "页面协议校验失败", e.getMessage(), null, null,
                    "FIX_LAYOUT", "修复布局", "form", 120);
        }
        checkPageTargetReferences(pageSchema, collectFormKeys(readDesignerOptions(context)), items);
        checkPageActionCompleteness(pageSchema, collectFields(modelSchema), items);
    }

    void validateTransactionalActions(
            BusinessObjectDesignerService.DesignerContext context,
            List<BusinessPublishCheckItemVO> items) {
        List<Map<String, Object>> actions = listOfMap(readDesignerOptions(context).get(DESIGNER_ACTIONS_KEY));
        if (actions.isEmpty()) {
            return;
        }
        Set<String> childRelationKeys = collectChildRelationKeys(context);
        Set<String> actionCodes = new LinkedHashSet<>();
        int index = 0;
        for (Map<String, Object> action : actions) {
            index++;
            String actionType = StringUtils.upperCase(StringUtils.defaultIfBlank(text(action.get("actionType")), "OPEN_PAGE"));
            Map<String, Object> config = mapValue(action.get("actionConfig"));
            String actionPosition = StringUtils.upperCase(StringUtils.defaultIfBlank(
                    text(action.get("actionPosition")), "ROW")).replace('-', '_');
            boolean childRowAction = "CHILD_ROW".equals(actionPosition);
            String actionName = StringUtils.defaultIfBlank(text(action.get("actionName")), "未命名动作");
            if (childRowAction) {
                String relationKey = text(config.get("relationKey"));
                String triggerScene = StringUtils.upperCase(StringUtils.defaultString(text(config.get("triggerScene"))));
                if (!"COMMAND".equals(actionType)) {
                    addCommandBlock(items, "COMMAND_PROTOCOL_INVALID", actionName,
                            "子表行按钮仅支持 COMMAND 动作", index);
                    continue;
                }
                if (!"MANUAL".equals(triggerScene)) {
                    addCommandBlock(items, "COMMAND_PROTOCOL_INVALID", actionName,
                            "子表行按钮执行场景必须为 MANUAL", index);
                    continue;
                }
                if (StringUtils.isBlank(relationKey)
                        || !relationKey.matches("^[A-Za-z][A-Za-z0-9_-]{0,127}$")
                        || !childRelationKeys.contains(relationKey)) {
                    addCommandBlock(items, "COMMAND_PROTOCOL_INVALID", actionName,
                            "子表行按钮 relationKey 未指向当前对象的启用明细关系", index);
                    continue;
                }
            }
            if (!"COMMAND".equals(actionType) && !hasActionSteps(config)) {
                continue;
            }
            String actionCode = text(action.get("actionCode"));
            if (StringUtils.isBlank(actionCode) || !actionCode.matches("^[a-z][a-z0-9_]{1,63}$")) {
                addCommandBlock(items, "COMMAND_CODE_INVALID", actionName,
                        "事务型动作编码必须为 2～64 位小写字母、数字和下划线", index);
                continue;
            }
            if (!actionCodes.add(actionCode)) {
                addCommandBlock(items, "COMMAND_CODE_DUPLICATE", actionName,
                        "事务型动作编码重复: " + actionCode, index);
                continue;
            }
            try {
                List<BusinessActionStepDTO> steps = parseCommandSteps(config);
                if (steps.isEmpty()) {
                    throw new BusinessException("事务型动作未配置执行步骤");
                }
                BusinessObjectActionVO vo = new BusinessObjectActionVO();
                vo.setActionCode(actionCode);
                vo.setActionName(actionName);
                vo.setActionConfig(config);
                BusinessActionCommandPolicy.validateDefinition(vo, steps);
                validateCommandStepCompleteness(steps);
            } catch (BusinessException e) {
                addCommandBlock(items, "COMMAND_PROTOCOL_INVALID", actionName, e.getMessage(), index);
            } catch (Exception e) {
                addCommandBlock(items, "COMMAND_PROTOCOL_INVALID", actionName,
                        "事务型动作配置格式不正确", index);
            }
        }
    }

    private Set<String> collectChildRelationKeys(BusinessObjectDesignerService.DesignerContext context) {
        if (context == null || context.getRelations() == null || context.getObject() == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (BusinessObjectRelationVO relation : context.getRelations()) {
            if (relation == null || EnableStatus.DISABLED.matches(relation.getStatus())
                    || !StringUtils.equals(context.getObject().getObjectCode(), relation.getSourceObjectCode())) {
                continue;
            }
            String type = StringUtils.upperCase(StringUtils.defaultString(relation.getRelationType()));
            if (!Set.of("DETAIL", "CHILD_LIST", "ONE_TO_MANY").contains(type)) {
                continue;
            }
            Map<String, Object> config;
            try {
                config = StringUtils.isBlank(relation.getRelationConfig())
                        ? Map.of()
                        : objectMapper.readValue(relation.getRelationConfig(), new TypeReference<>() { });
            } catch (Exception ignored) {
                config = Map.of();
            }
            String relationKey = StringUtils.defaultIfBlank(
                    text(config.get("relationKey")), defaultRelationKey(relation.getTargetObjectCode()));
            if (StringUtils.isNotBlank(relationKey)) {
                keys.add(relationKey);
            }
        }
        return keys;
    }

    private String defaultRelationKey(String value) {
        return StringUtils.defaultString(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private List<BusinessActionStepDTO> parseCommandSteps(Map<String, Object> config) {
        Object raw = config.get("steps");
        if (!(raw instanceof List<?>)) {
            raw = config.get("stepList");
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<BusinessActionStepDTO> steps = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                throw new BusinessException("事务型动作步骤格式不正确");
            }
            BusinessActionStepDTO step = objectMapper.convertValue(item, BusinessActionStepDTO.class);
            step.setStepType(StringUtils.upperCase(StringUtils.defaultString(step.getStepType())
                    .replaceAll("([a-z])([A-Z])", "$1_$2").replace('-', '_')));
            if (step.getStepConfig() == null) {
                step.setStepConfig(new LinkedHashMap<>());
            }
            steps.add(step);
        }
        return steps;
    }

    private void validateCommandStepCompleteness(List<BusinessActionStepDTO> steps) {
        Set<String> supported = Set.of(
                "CREATE_RECORD", "UPDATE_FIELD", "ADJUST_NUMBER", "TRANSITION_STATUS", "ASSERT_RECORD", "FOREACH",
                "DOMAIN_ACTION", "SEND_MESSAGE", "START_FLOW", "CALL_API");
        for (BusinessActionStepDTO step : steps) {
            String type = StringUtils.upperCase(StringUtils.defaultString(step.getStepType()));
            if (!supported.contains(type)) {
                throw new BusinessException("不支持的事务型动作步骤: " + type);
            }
            Map<String, Object> config = step.getStepConfig();
            if ("CREATE_RECORD".equals(type)
                    && StringUtils.isBlank(text(config.get("targetConfigKey")))) {
                throw new BusinessException("创建记录步骤缺少 targetConfigKey");
            }
            if (Set.of("CREATE_RECORD", "UPDATE_FIELD").contains(type)
                    && listOfMap(config.get("fieldMapping")).isEmpty()
                    && listOfMap(config.get("fieldMappings")).isEmpty()
                    && mapValue(config.get("staticValues")).isEmpty()) {
                throw new BusinessException(type + " 步骤没有配置字段映射");
            }
            if ("ADJUST_NUMBER".equals(type) && listOfMap(config.get("adjustments")).isEmpty()) {
                throw new BusinessException("数值调整步骤没有配置调整字段");
            }
            if ("ASSERT_RECORD".equals(type)) {
                if (StringUtils.isBlank(text(config.get("targetConfigKey")))) {
                    throw new BusinessException("记录门禁步骤缺少 targetConfigKey");
                }
                if (StringUtils.isBlank(text(config.get("targetRecordIdField")))) {
                    throw new BusinessException("记录门禁步骤缺少 targetRecordIdField");
                }
            }
            if ("TRANSITION_STATUS".equals(type)) {
                if (StringUtils.isBlank(text(config.get("targetConfigKey")))) {
                    throw new BusinessException("状态迁移步骤缺少 targetConfigKey");
                }
                if (StringUtils.isBlank(text(config.get("targetRecordIdField")))) {
                    throw new BusinessException("状态迁移步骤缺少 targetRecordIdField");
                }
                if (StringUtils.isBlank(text(config.get("statusField")))) {
                    throw new BusinessException("状态迁移步骤缺少 statusField");
                }
                if (config.get("fromValue") == null || config.get("toValue") == null) {
                    throw new BusinessException("状态迁移步骤缺少 fromValue/toValue");
                }
            }
            if ("FOREACH".equals(type)) {
                if (StringUtils.isBlank(text(config.get("collectionPath")))) {
                    throw new BusinessException("逐行步骤缺少集合路径");
                }
                List<BusinessActionStepDTO> nested = parseCommandSteps(config);
                if (nested.isEmpty()) {
                    throw new BusinessException("逐行步骤没有配置子步骤");
                }
                validateCommandStepCompleteness(nested);
            }
            if ("CALL_API".equals(type)
                    && StringUtils.isBlank(text(config.get("sourceKey")))
                    && StringUtils.isBlank(text(config.get("querySourceKey")))) {
                throw new BusinessException("CALL_API 步骤缺少 sourceKey");
            }
        }
    }

    private void addCommandBlock(List<BusinessPublishCheckItemVO> items,
                                 String code,
                                 String actionName,
                                 String message,
                                 int index) {
        add(items, code, "COMMAND", BusinessPublishCheckLevel.BLOCK,
                "事务型动作配置无效", actionName + "：" + message,
                null, actionName, "CONFIG_COMMAND", "修复业务动作", "automation", 195 + index);
    }

    private void checkPageTargetReferences(LowcodePageSchema pageSchema, Set<String> formKeys,
                                           List<BusinessPublishCheckItemVO> items) {
        Set<String> pageKeys = collectPageKeys(pageSchema);
        List<Map<String, Object>> targets = collectPageTargetConfigs(pageSchema);
        int index = 0;
        for (Map<String, Object> target : targets) {
            index++;
            String source = StringUtils.defaultIfBlank(text(target.get("_source")), "页面动作");
            String targetPageKey = text(target.get("targetPageKey"));
            if (StringUtils.isNotBlank(targetPageKey) && !pageKeys.contains(targetPageKey)) {
                add(items, "PAGE_TARGET_MISSING", "PAGE", BusinessPublishCheckLevel.BLOCK,
                        "目标页面不存在", source + " 引用了不存在的页面: " + targetPageKey,
                        targetPageKey, source, "FIX_PAGE_TARGET", "修复页面跳转", "list", 121 + index);
            }
            String targetFormKey = text(target.get("targetFormKey"));
            if (StringUtils.isNotBlank(targetFormKey) && !formKeys.contains(targetFormKey)) {
                add(items, "FORM_TARGET_MISSING", "PAGE", BusinessPublishCheckLevel.BLOCK,
                        "目标表单不存在", source + " 引用了不存在的表单: " + targetFormKey,
                        targetFormKey, source, "FIX_FORM_TARGET", "修复表单跳转", "list", 141 + index);
            }
        }
    }

    private Set<String> collectPageKeys(LowcodePageSchema pageSchema) {
        return BusinessPublishTargetCatalog.pageKeys(pageSchema);
    }

    private Set<String> collectFormKeys(Map<String, Object> designerOptions) {
        return BusinessPublishTargetCatalog.formKeys(designerOptions);
    }

    private boolean hasActionSteps(Map<String, Object> config) {
        return !listOfMap(config.get("steps")).isEmpty() || !listOfMap(config.get("stepList")).isEmpty();
    }

    private void addIfNotBlank(Set<String> keys, String value) {
        if (StringUtils.isNotBlank(value)) {
            keys.add(value);
        }
    }

    private List<Map<String, Object>> collectPageTargetConfigs(LowcodePageSchema pageSchema) {
        if (pageSchema == null) {
            return List.of();
        }
        List<Map<String, Object>> targets = new ArrayList<>();
        collectLayoutTargetConfigs(pageSchema.getListGridLayout(), "列表页", targets);
        if (pageSchema.getPages() != null) {
            pageSchema.getPages().forEach(page -> collectLayoutTargetConfigs(
                    mapValue(page.get("gridLayout")),
                    StringUtils.defaultIfBlank(text(page.get("pageName")), text(page.get("pageKey"))),
                    targets));
        }
        return targets;
    }

    private void collectLayoutTargetConfigs(Map<String, Object> layout, String pageName,
                                            List<Map<String, Object>> targets) {
        for (Map<String, Object> block : listOfMap(layout.get("items"))) {
            Map<String, Object> props = mapValue(block.get("props"));
            collectTargetConfig(props, pageName + "/" + StringUtils.defaultIfBlank(text(block.get("blockType")), "区块"), targets);
            listOfMap(props.get("events")).forEach(event -> collectTargetConfig(event, pageName + "/事件", targets));
            listOfMap(props.get("customActions")).forEach(action -> collectTargetConfig(action, pageName + "/自定义操作", targets));
            mapValue(props.get("fieldSettings")).forEach((field, setting) ->
                    collectTargetConfig(mapValue(setting), pageName + "/字段 " + field, targets));
        }
    }

    private void collectTargetConfig(Map<String, Object> source, String sourceName,
                                     List<Map<String, Object>> targets) {
        String targetPageKey = text(source.get("targetPageKey"));
        String targetFormKey = text(source.get("targetFormKey"));
        if (StringUtils.isBlank(targetPageKey) && StringUtils.isBlank(targetFormKey)) {
            return;
        }
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("_source", sourceName);
        putIfNotBlank(target, "targetPageKey", targetPageKey);
        putIfNotBlank(target, "targetFormKey", targetFormKey);
        targets.add(target);
    }

    private void checkPageActionCompleteness(LowcodePageSchema pageSchema, Set<String> modelFields,
                                             List<BusinessPublishCheckItemVO> items) {
        if (pageSchema == null) {
            return;
        }
        List<Map<String, Object>> layouts = new ArrayList<>();
        layouts.add(pageSchema.getListGridLayout());
        if (pageSchema.getPages() != null) {
            pageSchema.getPages().forEach(page -> layouts.add(mapValue(page.get("gridLayout"))));
        }
        int index = 0;
        for (Map<String, Object> layout : layouts) {
            for (Map<String, Object> block : listOfMap(layout.get("items"))) {
                index++;
                String blockType = text(block.get("blockType"));
                Map<String, Object> props = mapValue(block.get("props"));
                String blockName = StringUtils.defaultIfBlank(text(props.get("title")), blockType);
                checkPreviewCompleteness(blockType, props, blockName, items, index);
                if ("action-button".equals(blockType) && listOfMap(props.get("events")).isEmpty()) {
                    add(items, "PAGE_BUTTON_ACTION_EMPTY", "PAGE", BusinessPublishCheckLevel.WARN,
                            "按钮未配置点击动作", "按钮「" + blockName + "」没有配置事件回调，运行态点击后不会执行业务动作",
                            text(block.get("id")), blockName, "CONFIG_BUTTON_ACTION", "配置按钮动作", "list", 151 + index);
                }
                int eventSortOrder = 170 + index;
                int actionSortOrder = 190 + index;
                listOfMap(props.get("events")).forEach(event ->
                        checkEventActionCompleteness(event, StringUtils.defaultIfBlank(blockName, "区块") + "/事件", modelFields, items, eventSortOrder));
                listOfMap(props.get("customActions")).forEach(action ->
                        checkCustomActionCompleteness(action, StringUtils.defaultIfBlank(blockName, "区块") + "/自定义操作", modelFields, items, actionSortOrder));
            }
        }
    }

    private void checkPreviewCompleteness(String blockType, Map<String, Object> props, String blockName,
                                          List<BusinessPublishCheckItemVO> items, int index) {
        if (!"AiCrudPage".equals(blockType) || !Boolean.TRUE.equals(props.get("previewLiveData"))) {
            return;
        }
        String status = text(props.get("lastPreviewStatus"));
        String error = text(props.get("lastPreviewError"));
        String mode = StringUtils.defaultIfBlank(text(props.get("previewMode")), "realList");
        if ("error".equalsIgnoreCase(status) || StringUtils.isNotBlank(error)) {
            add(items, "PAGE_PREVIEW_API_ERROR", "PAGE", BusinessPublishCheckLevel.BLOCK,
                    "真实接口预览失败", "组件「" + blockName + "」的 " + mode + " 预览失败: " + StringUtils.defaultIfBlank(error, "未知错误"),
                    null, blockName, "FIX_PREVIEW_API", "修复接口预览", "list", 160 + index);
        } else if (!"success".equalsIgnoreCase(status)) {
            add(items, "PAGE_PREVIEW_API_NOT_VERIFIED", "PAGE", BusinessPublishCheckLevel.WARN,
                    "真实接口预览未验证通过", "组件「" + blockName + "」已开启真实接口预览，但还没有成功请求记录",
                    null, blockName, "CHECK_PREVIEW_API", "执行接口预览", "list", 161 + index);
        }
    }

    private void checkEventActionCompleteness(Map<String, Object> event, String source,
                                              Set<String> modelFields, List<BusinessPublishCheckItemVO> items, int sortOrder) {
        String action = StringUtils.defaultIfBlank(text(event.get("action")), "none");
        if ("none".equalsIgnoreCase(action)) {
            add(items, "PAGE_EVENT_ACTION_NONE", "PAGE", BusinessPublishCheckLevel.WARN,
                    "事件未配置动作", source + " 选择了无动作，触发后不会产生业务行为",
                    null, source, "CONFIG_EVENT_ACTION", "配置事件动作", "list", sortOrder);
            return;
        }
        if ("navigate".equalsIgnoreCase(action) && StringUtils.isBlank(text(event.get("targetPageKey")))) {
            add(items, "PAGE_EVENT_TARGET_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                    "跳转事件缺少目标页面", source + " 是页面跳转，但未选择目标页面",
                    null, source, "CONFIG_EVENT_TARGET", "配置目标页面", "list", sortOrder + 1);
        }
        if (Set.of("refreshBlock", "filterBlock").contains(action) && StringUtils.isBlank(text(event.get("targetBlockId")))) {
            add(items, "PAGE_EVENT_BLOCK_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                    "组件事件缺少目标组件", source + " 需要选择目标组件",
                    null, source, "CONFIG_EVENT_TARGET", "配置目标组件", "list", sortOrder + 2);
        }
        if ("request".equalsIgnoreCase(action) && StringUtils.isBlank(text(event.get("requestUrl")))) {
            add(items, "PAGE_EVENT_API_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                    "接口事件缺少地址", source + " 是接口请求，但未配置请求地址",
                    null, source, "CONFIG_EVENT_API", "配置接口地址", "list", sortOrder + 3);
        }
        checkActionParams(event.get("params"), source + "/参数", modelFields, items, sortOrder + 4);
    }

    private void checkCustomActionCompleteness(Map<String, Object> action, String source,
                                               Set<String> modelFields, List<BusinessPublishCheckItemVO> items, int sortOrder) {
        String actionType = StringUtils.defaultIfBlank(text(action.get("actionType")), "route");
        if (isActionTypeWithoutRoute(actionType)) {
            return;
        }
        if (isApiActionType(actionType)) {
            Map<String, Object> config = mapValue(action.get("actionConfig"));
            String apiUrl = StringUtils.firstNonBlank(
                    text(config.get("url")),
                    text(config.get("apiUrl")),
                    text(config.get("urlPath")),
                    text(config.get("path"))
            );
            if (StringUtils.isBlank(apiUrl)) {
                add(items, "PAGE_CUSTOM_ACTION_API_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                        "自定义操作缺少接口地址", source + " 是接口调用，但未配置接口地址",
                        text(action.get("key")), source, "CONFIG_CUSTOM_ACTION", "配置自定义操作", "list", sortOrder);
            }
            checkActionParams(action.get("params"), source + "/参数", modelFields, items, sortOrder + 1);
            return;
        }
        if (isRouteActionType(actionType) && StringUtils.isBlank(text(action.get("routePath")))) {
            String actionKind = isExternalRouteActionType(actionType) ? "外部链接" : "站内跳转";
            add(items, "PAGE_CUSTOM_ACTION_TARGET_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                    "自定义操作缺少目标地址", source + " 是" + actionKind + "，但未配置目标地址",
                    text(action.get("key")), source, "CONFIG_CUSTOM_ACTION", "配置自定义操作", "list", sortOrder);
        }
        checkActionParams(action.get("params"), source + "/参数", modelFields, items, sortOrder + 1);
    }

    private boolean isActionTypeWithoutRoute(String actionType) {
        return Set.of("REFRESH", "COMMAND", "START_FLOW", "TRIGGER")
                .contains(StringUtils.upperCase(StringUtils.defaultString(actionType)));
    }

    private boolean isApiActionType(String actionType) {
        return Set.of("CALL_API", "API", "REQUEST")
                .contains(StringUtils.upperCase(StringUtils.defaultString(actionType)));
    }

    private boolean isRouteActionType(String actionType) {
        String normalized = StringUtils.upperCase(StringUtils.defaultString(actionType));
        return Set.of("ROUTE", "OPEN_PAGE", "EXTERNAL", "OPEN_EXTERNAL").contains(normalized)
                || StringUtils.isBlank(normalized);
    }

    private boolean isExternalRouteActionType(String actionType) {
        return Set.of("EXTERNAL", "OPEN_EXTERNAL")
                .contains(StringUtils.upperCase(StringUtils.defaultString(actionType)));
    }

    private void checkActionParams(Object paramsValue, String source, Set<String> modelFields,
                                   List<BusinessPublishCheckItemVO> items, int sortOrder) {
        if (!(paramsValue instanceof List<?> params) || params.isEmpty()) {
            return;
        }
        int index = 0;
        for (Object item : params) {
            index++;
            if (!(item instanceof Map<?, ?> param)) {
                continue;
            }
            String name = text(param.get("name"));
            String sourceType = StringUtils.defaultIfBlank(text(param.get("sourceType")), "static");
            String sourceField = text(param.get("sourceField"));
            String value = text(param.get("value"));
            if (StringUtils.isBlank(name)) {
                add(items, "PAGE_ACTION_PARAM_NAME_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                        "动作参数缺少参数名", source + " 第 " + index + " 行未填写参数名",
                        null, source, "CONFIG_ACTION_PARAM", "配置参数映射", "list", sortOrder + index);
            }
            if ("rowField".equalsIgnoreCase(sourceType)) {
                if (StringUtils.isBlank(sourceField)) {
                    add(items, "PAGE_ACTION_PARAM_FIELD_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                            "动作参数缺少来源字段", source + " 参数「" + name + "」选择了当前行字段，但未选择字段",
                            name, source, "CONFIG_ACTION_PARAM", "配置参数映射", "list", sortOrder + index + 10);
                } else if (!modelFields.contains(sourceField)) {
                    add(items, "PAGE_ACTION_PARAM_FIELD_MISSING", "PAGE", BusinessPublishCheckLevel.BLOCK,
                            "动作参数引用字段不存在", source + " 参数「" + name + "」引用了不存在的字段: " + sourceField,
                            sourceField, source, "CONFIG_ACTION_PARAM", "修复参数映射", "list", sortOrder + index + 20);
                }
            } else if (Set.of("routeQuery", "system").contains(sourceType) && StringUtils.isBlank(sourceField)) {
                add(items, "PAGE_ACTION_PARAM_SOURCE_EMPTY", "PAGE", BusinessPublishCheckLevel.BLOCK,
                        "动作参数缺少来源", source + " 参数「" + name + "」选择了" + sourceType + "，但未选择具体来源",
                        name, source, "CONFIG_ACTION_PARAM", "配置参数映射", "list", sortOrder + index + 25);
            } else if (!Set.of("static", "routeQuery", "system").contains(sourceType) && StringUtils.isNotBlank(sourceType)) {
                add(items, "PAGE_ACTION_PARAM_SOURCE_INVALID", "PAGE", BusinessPublishCheckLevel.BLOCK,
                        "动作参数来源无效", source + " 参数「" + name + "」使用了不支持的来源类型: " + sourceType,
                        name, source, "CONFIG_ACTION_PARAM", "修复参数映射", "list", sortOrder + index + 30);
            }
            if ("static".equalsIgnoreCase(sourceType) && StringUtils.isBlank(value)) {
                add(items, "PAGE_ACTION_PARAM_VALUE_EMPTY", "PAGE", BusinessPublishCheckLevel.WARN,
                        "动作参数固定值为空", source + " 参数「" + name + "」没有固定值，运行态会传空",
                        name, source, "CONFIG_ACTION_PARAM", "检查参数映射", "list", sortOrder + index + 40);
            }
        }
    }

    private Map<String, Object> readDesignerOptions(BusinessObjectDesignerService.DesignerContext context) {
        if (context == null || context.getObject() == null
                || StringUtils.isBlank(context.getObject().getDesignerOptions())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(context.getObject().getDesignerOptions(), new TypeReference<>() { });
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
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
                        addIfNotBlank(fields, fieldRef);
                        addIfNotBlank(fields, sourceField);
                        addIfNotBlank(fields, columnName);
                        if (StringUtils.isNotBlank(modelCode) && StringUtils.isNotBlank(sourceField)) {
                            fields.add(modelCode + "." + sourceField);
                            fields.add(modelCode + "__" + sourceField);
                        }
                    }
                });
        return fields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(this::mapValue).toList();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private void add(List<BusinessPublishCheckItemVO> items, String code, String category, String level,
                     String title, String message, String fieldCode, String zoneKey, String fixAction,
                     String fixActionLabel, String fixTarget, Integer sortOrder) {
        BusinessPublishCheckCollector.add(items, code, category, level, title, message, fieldCode, zoneKey,
                fixAction, fixActionLabel, fixTarget, sortOrder);
    }
}
