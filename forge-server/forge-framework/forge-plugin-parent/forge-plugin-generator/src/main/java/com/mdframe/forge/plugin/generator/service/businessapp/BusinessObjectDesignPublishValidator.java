package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessPublishCheckLevel;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeRuntimeConfigBuilder;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectRelationVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPermissionSummaryVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckItemVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务对象设计完整性发布校验链。
 *
 * <p>按关系、联动、运行配置、单据与动作权限顺序执行，保持发布门面只负责编排。</p>
 */
final class BusinessObjectDesignPublishValidator {

    private static final String LINKAGE_SCHEMA_OPTION_KEY = "linkageSchema";

    private final ObjectMapper objectMapper;
    private final BusinessObjectDesignerService designerService;
    private final BusinessObjectMapper businessObjectMapper;
    private final LowcodeRuntimeConfigBuilder runtimeConfigBuilder;
    private final BusinessDocumentConfigService documentConfigService;
    private final BusinessTriggerMapper triggerMapper;
    private final BusinessPermissionService permissionService;

    BusinessObjectDesignPublishValidator(ObjectMapper objectMapper,
                                         BusinessObjectDesignerService designerService,
                                         BusinessObjectMapper businessObjectMapper,
                                         LowcodeRuntimeConfigBuilder runtimeConfigBuilder,
                                         BusinessDocumentConfigService documentConfigService,
                                         BusinessTriggerMapper triggerMapper,
                                         BusinessPermissionService permissionService) {
        this.objectMapper = objectMapper;
        this.designerService = designerService;
        this.businessObjectMapper = businessObjectMapper;
        this.runtimeConfigBuilder = runtimeConfigBuilder;
        this.documentConfigService = documentConfigService;
        this.triggerMapper = triggerMapper;
        this.permissionService = permissionService;
    }

    void validateSchema(BusinessObjectDesignerService.DesignerContext context,
                        List<BusinessPublishCheckItemVO> items) {
        checkRelations(context, items);
        checkLinkage(context, items);
        checkRuntimeConfig(context, items);
    }

    void validateGovernance(BusinessObjectDesignerService.DesignerContext context,
                            BusinessPermissionSummaryVO permissionSummary,
                            List<BusinessPublishCheckItemVO> items) {
        checkDocumentConfig(context, items);
        checkPermissionSummary(context, permissionSummary, items);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private boolean isFalse(Object value) {
        return Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(text(value)) || "0".equals(text(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String resolveConfigKey(BusinessObjectDesignerService.DesignerContext context) {
        if (context.getConfig() != null && StringUtils.isNotBlank(context.getConfig().getConfigKey())) {
            return context.getConfig().getConfigKey();
        }
        return StringUtils.defaultString(context.getObject().getSuiteCode())
                + "_" + StringUtils.defaultString(context.getObject().getObjectCode());
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

    private void add(List<BusinessPublishCheckItemVO> items, String code, String category, String level,
                     String title, String message, String fieldCode, String zoneKey, String fixAction,
                     String fixActionLabel, String fixTarget, Integer sortOrder) {
        BusinessPublishCheckCollector.add(items, code, category, level, title, message, fieldCode, zoneKey,
                fixAction, fixActionLabel, fixTarget, sortOrder);
    }
}
