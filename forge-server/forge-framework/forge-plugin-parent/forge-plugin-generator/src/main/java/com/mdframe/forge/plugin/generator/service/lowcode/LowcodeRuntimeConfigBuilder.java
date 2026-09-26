package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRuntimeConfig;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.findRelationFromPrimary;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveChildRelationField;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveMainRelationField;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolvePrimaryRef;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimePageRelationResolver.resolveRuntimeRelation;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeTreeConfigBuilder.buildTreeConfig;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeTreeConfigBuilder.buildTreeOptionSource;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeTreeConfigBuilder.extractTreeConfigOverrides;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeTreeConfigBuilder.hasTreePanelBlock;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeTreeConfigBuilder.isLeftTreeRightTableLayout;
import static com.mdframe.forge.plugin.generator.service.lowcode.RuntimeTreeConfigBuilder.resolveTreeApiConfigKey;

/**
 * 将低代码业务协议转换为 AiCrudPage 运行时配置。
 */
@Service
@RequiredArgsConstructor
public class LowcodeRuntimeConfigBuilder {

    private static final Logger log = LoggerFactory.getLogger(LowcodeRuntimeConfigBuilder.class);
    private static final String MASTER_DETAIL_LAYOUT = "master-detail-crud";
    private final ObjectMapper objectMapper;
    private final LowcodeSchemaValidator schemaValidator;
    private final LowcodePolicyService policyService;
    private final RuntimeFieldMetadataCompiler fieldMetadataCompiler = new RuntimeFieldMetadataCompiler();

    public LowcodeRuntimeConfig buildRuntimeConfig(String configKey,
                                                   LowcodeModelSchema modelSchema,
                                                   LowcodePageSchema pageSchema) {
        if (StringUtils.isBlank(configKey)) {
            throw new BusinessException("configKey不能为空");
        }
        policyService.normalizeModelSchema(modelSchema);
        schemaValidator.validatePage(pageSchema, modelSchema);

        LowcodeRuntimeConfig runtimeConfig = new LowcodeRuntimeConfig();
        runtimeConfig.setConfigKey(configKey);
        runtimeConfig.setObjectCode(StringUtils.firstNonBlank(
                modelSchema.getObject() == null ? null : modelSchema.getObject().getCode(),
                configKey,
                modelSchema.getTableName()));
        runtimeConfig.setTableName(modelSchema.getTableName());
        runtimeConfig.setTableComment(modelSchema.getBusinessName());
        String layoutType = StringUtils.defaultIfBlank(pageSchema.getLayoutType(), "simple-crud");
        if (hasTreePanelBlock(pageSchema)) {
            layoutType = "tree-crud";
        }
        runtimeConfig.setLayoutType(layoutType);

        try {
            runtimeConfig.setSearchSchema(objectMapper.writeValueAsString(buildSearchSchema(configKey, modelSchema, pageSchema)));
            runtimeConfig.setColumnsSchema(objectMapper.writeValueAsString(buildColumnsSchema(modelSchema, pageSchema)));
            runtimeConfig.setEditSchema(objectMapper.writeValueAsString(buildEditSchema(configKey, modelSchema, pageSchema)));
            runtimeConfig.setApiConfig(objectMapper.writeValueAsString(buildApiConfig(configKey, modelSchema, pageSchema)));
            runtimeConfig.setOptions(objectMapper.writeValueAsString(buildOptions(modelSchema, pageSchema)));
            runtimeConfig.setDictConfig(objectMapper.writeValueAsString(fieldMetadataCompiler.dictConfig(modelSchema)));
            runtimeConfig.setDesensitizeConfig(objectMapper.writeValueAsString(fieldMetadataCompiler.desensitizeConfig(modelSchema)));
            runtimeConfig.setEncryptConfig(objectMapper.writeValueAsString(fieldMetadataCompiler.encryptConfig(modelSchema)));
            runtimeConfig.setTransConfig(objectMapper.writeValueAsString(buildTransConfig(modelSchema, pageSchema)));
            return runtimeConfig;
        } catch (Exception e) {
            throw new BusinessException("低代码运行时配置生成失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildSearchSchema(String configKey, LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        List<Map<String, Object>> fields = resolveFields(modelSchema, pageSchema, "search", field -> Boolean.TRUE.equals(field.getSearchable()))
                .stream()
                .map(field -> buildSearchField(field, resolveRuntimeFieldSetting(pageSchema, "search", field.getField()),
                        modelSchema, pageSchema, configKey))
                .collect(Collectors.toCollection(ArrayList::new));
        appendTreeRuntimeField(fields, modelSchema, pageSchema, "search");
        RuntimeTreeFieldDecorator.decorate(fields, configKey, modelSchema, pageSchema,
                isTreeRuntime(modelSchema, pageSchema), isEmbeddedTreeTableRuntime(modelSchema, pageSchema));
        return fields;
    }

    private List<Map<String, Object>> buildColumnsSchema(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        List<Map<String, Object>> columns = resolveFields(modelSchema, pageSchema, "table",
                field -> field.getListVisible() == null || Boolean.TRUE.equals(field.getListVisible()))
                .stream()
                .map(field -> buildTableColumn(field, resolveRuntimeFieldSetting(pageSchema, "table", field.getField()),
                        modelSchema, pageSchema))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("key", "actions");
        actions.put("title", "操作");
        actions.put("dataIndex", "actions");
        List<Map<String, Object>> rowActions = RuntimeActionCompiler.rowActions(
                resolveTableProps(pageSchema), isEmbeddedTreeTableRuntime(modelSchema, pageSchema));
        actions.put("width", Math.max(180, rowActions.size() * 58));
        actions.put("fixed", "right");
        actions.put("actions", rowActions);
        actions.put("maxActionButtons", 3);
        columns.add(actions);
        return columns;
    }

    private List<Map<String, Object>> buildEditSchema(String configKey, LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        Set<String> childFieldRefs = buildChildFieldRefs(pageSchema);
        List<LowcodeFieldSchema> orderedFields = sortByCanvasOrder(
                resolveFields(modelSchema, pageSchema, "edit",
                        field -> isEditFieldVisibleAtDesignTime(pageSchema, field)),
                pageSchema,
                "edit"
        );
        List<Map<String, Object>> fields = orderedFields
                .stream()
                .filter(field -> !childFieldRefs.contains(field.getField()))
                .map(field -> buildEditField(field, resolveEditFieldSetting(pageSchema, field.getField()),
                        modelSchema, pageSchema))
                .collect(Collectors.toCollection(ArrayList::new));
        appendTreeRuntimeField(fields, modelSchema, pageSchema, "edit");
        RuntimeTreeFieldDecorator.decorate(fields, configKey, modelSchema, pageSchema,
                isTreeRuntime(modelSchema, pageSchema), isEmbeddedTreeTableRuntime(modelSchema, pageSchema));
        return fields;
    }

    private Map<String, String> buildApiConfig(String configKey, LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        Map<String, String> apiConfig = new LinkedHashMap<>();
        apiConfig.put("list", "get@/ai/crud/" + configKey + "/page");
        if (isTreeRuntime(modelSchema, pageSchema)) {
            apiConfig.put("tree", "get@/ai/crud/" + resolveTreeApiConfigKey(configKey, pageSchema) + "/tree");
        }
        apiConfig.put("detail", "get@/ai/crud/" + configKey + "/:id");
        apiConfig.put("create", "post@/ai/crud/" + configKey);
        apiConfig.put("update", "put@/ai/crud/" + configKey);
        apiConfig.put("delete", "delete@/ai/crud/" + configKey + "/:id");
        apiConfig.put("import", "post@/ai/crud/" + configKey + "/import");
        apiConfig.put("export", "post@/ai/crud/" + configKey + "/export");
        apiConfig.put("importTemplate", "get@/ai/crud/" + configKey + "/import-template");
        return apiConfig;
    }

    private boolean isEmbeddedTreeTableRuntime(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        return isTreeRuntime(modelSchema, pageSchema) && !"tree-crud".equals(StringUtils.defaultIfBlank(
                pageSchema == null ? null : pageSchema.getLayoutType(), "simple-crud"));
    }

    private Map<String, Object> buildOptions(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        Map<String, Object> options = new LinkedHashMap<>();
        boolean masterDetailRuntime = isMasterDetailRuntime(pageSchema);
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        Map<String, Object> editProps = editZone == null || editZone.getProps() == null ? Map.of() : editZone.getProps();
        Map<String, Object> crudBlockProps = resolveGridBlockProps(pageSchema, List.of("AiCrudPage"));
        String formOpenMode = RuntimeFormContainerOptionsCompiler.formOpenMode(firstNonBlank(
                editProps.get("formOpenMode"),
                crudBlockProps.get("formOpenMode"),
                editProps.get("modalType"),
                crudBlockProps.get("modalType")));
        options.put("formOpenMode", formOpenMode);
        options.put("modalType", RuntimeFormContainerOptionsCompiler.modalType(firstNonBlank(
                editProps.get("modalType"),
                crudBlockProps.get("modalType"),
                formOpenMode)));
        options.put("tabWorkspace", RuntimeFormContainerOptionsCompiler.tabWorkspaceOptions(editProps, crudBlockProps));
        int editGridCols = resolveEditGridCols(pageSchema);
        options.put("modalWidth", RuntimeFormContainerOptionsCompiler.runtimeModalWidth(editProps, crudBlockProps,
                RuntimeFormContainerOptionsCompiler.defaultModalWidth(masterDetailRuntime, editGridCols)));
        options.put("searchGridCols", integerValue(crudBlockProps.get("searchGridCols")) == null
                ? 4
                : integerValue(crudBlockProps.get("searchGridCols")));
        options.put("editGridCols", editGridCols);
        options.put("editLabelPlacement", StringUtils.defaultIfBlank(text(editProps.get("labelPlacement")),
                StringUtils.defaultIfBlank(text(crudBlockProps.get("editLabelPlacement")), "left")));
        options.put("editLabelAlign", StringUtils.defaultIfBlank(text(editProps.get("labelAlign")),
                StringUtils.defaultIfBlank(text(crudBlockProps.get("editLabelAlign")), "right")));
        options.put("editLabelWidth", editProps.getOrDefault("labelWidth",
                crudBlockProps.getOrDefault("editLabelWidth", "auto")));
        options.put("editSize", normalizeRuntimeFormSize(StringUtils.defaultIfBlank(text(editProps.get("size")),
                text(crudBlockProps.get("editSize")))));
        options.put("editShowFeedback", booleanWithDefault(firstPresent(editProps.get("showFeedback"),
                crudBlockProps.get("editShowFeedback")), true));
        options.put("editEnableCollapse", booleanWithDefault(editProps.get("enableCollapse"), false));
        Integer editMaxVisibleFields = integerValue(editProps.get("maxVisibleFields"));
        if (editMaxVisibleFields != null && editMaxVisibleFields > 0) {
            options.put("editMaxVisibleFields", editMaxVisibleFields);
        }
        copyOption(editProps, options, "editFormClass");
        copyOption(editProps, options, "editFormStyle");
        options.put("editXGap", intValue(editProps.get("columnGap"), 16));
        options.put("editYGap", intValue(editProps.get("rowGap"), 16));
        Object formLayout = editProps.get("formLayout");
        if (formLayout instanceof List<?> layout && !layout.isEmpty()) {
            options.put("editFormLayout", layout);
        }
        Object formDesignerSchema = editProps.get("formDesignerSchema");
        if (formDesignerSchema != null) {
            options.put("formDesignerSchema", formDesignerSchema);
        }

        Map<String, Object> tableProps = resolveTableProps(pageSchema);
        if (!tableProps.isEmpty()) {
            copyOption(tableProps, options, "showImport");
            copyOption(tableProps, options, "showExport");
            copyOption(tableProps, options, "showPagination");
            copyOption(tableProps, options, "hideAdd");
            copyOption(tableProps, options, "hideToolbar");
            copyOption(tableProps, options, "hideSelection");
            copyOption(tableProps, options, "hideBatchDelete");
            copyOption(tableProps, options, "enableCustomQuery");
            copyOption(tableProps, options, "showRenderModeSwitch");
            copyOption(tableProps, options, "renderMode");
            copyOption(tableProps, options, "tableSize");
            copyOption(tableProps, options, "bordered");
            copyOption(tableProps, options, "striped");
            copyOption(tableProps, options, "drawerPlacement");
            // 左树右表右表是平铺列表：强制关闭「添加下级」，忽略表区/区块残留配置
            if (isLeftTreeRightTableLayout(pageSchema)) {
                options.put("enableTreeAddChild", Boolean.FALSE);
            } else {
                copyOption(tableProps, options, "enableTreeAddChild");
            }
            // 表单设计器布局里配置的抽屉方向优先于列表/表格区设置
            copyOption(editProps, options, "drawerPlacement");
            copyOption(tableProps, options, "tabWorkspace");
            options.put("tableRowGap", intValue(tableProps.get("rowGap"), 8));
        }
        formOpenMode = RuntimeFormContainerOptionsCompiler.formOpenMode(firstNonBlank(
                editProps.get("formOpenMode"),
                crudBlockProps.get("formOpenMode"),
                tableProps.get("formOpenMode"),
                editProps.get("modalType"),
                crudBlockProps.get("modalType"),
                tableProps.get("modalType"),
                options.get("formOpenMode")));
        options.put("formOpenMode", formOpenMode);
        options.put("modalType", RuntimeFormContainerOptionsCompiler.modalType(firstNonBlank(
                Set.of("modal", "drawer").contains(formOpenMode) ? formOpenMode : null,
                editProps.get("modalType"),
                crudBlockProps.get("modalType"),
                tableProps.get("modalType"),
                options.get("modalType"))));
        Set<String> toolbarActions = resolveToolbarStandardActions(pageSchema);
        if (!toolbarActions.isEmpty()) {
            options.put("hideAdd", !toolbarActions.contains("add"));
            options.put("showImport", toolbarActions.contains("import"));
            options.put("showExport", toolbarActions.contains("export"));
            options.put("hideBatchDelete", !toolbarActions.contains("batch-delete"));
            options.put("enableCustomQuery", toolbarActions.contains("custom-query"));
        }
        options.put("toolbarActions", RuntimeActionCompiler.customActions(tableProps, "toolbar"));
        options.put("rowActions", RuntimeActionCompiler.customActions(tableProps, "row"));
        options.put("detailActions", RuntimeActionCompiler.customActions(tableProps, "detail"));
        options.put("formActions", RuntimeActionCompiler.customActions(tableProps, "form"));
        options.put("defaultSort", buildDefaultSort(modelSchema, pageSchema));
        options.put("childListDisplayMode", normalizeChildListDisplayMode(tableProps.get("childListDisplayMode")));
        options.put("joinConfig", buildJoinConfig(modelSchema, pageSchema));
        if (masterDetailRuntime) {
            options.put("masterDetailConfig", RuntimeChildTableCompiler.buildMasterDetailConfig(
                    modelSchema, pageSchema,
                    (ref, selectedRefs, childFk) -> RuntimeChildFieldCompiler.compile(
                            ref, selectedRefs, childFk, this::buildEditField)));
        }
        LowcodePageZone detailZone = findZone(pageSchema, "detail");
        Map<String, Object> detailProps = detailZone == null || detailZone.getProps() == null ? Map.of() : detailZone.getProps();
        Object quantityPanels = detailProps.get("quantityPanels");
        if (quantityPanels instanceof List<?> panels && !panels.isEmpty()) {
            options.put("detailPanels", panels);
        }
        if (Boolean.TRUE.equals(detailProps.get("showDataChangeLog"))
                || readDesignerLayoutFlag(formDesignerSchema, "showDataChangeLog")) {
            options.put("showDataChangeLog", true);
        }
        if (isTreeRuntime(modelSchema, pageSchema)) {
            options.put("treeConfig", buildTreeConfig(modelSchema, pageSchema, extractTreeConfigOverrides(pageSchema)));
        }
        return options;
    }

    private Set<String> resolveToolbarStandardActions(LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null) {
            return Set.of();
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> list)) {
            return Set.of();
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> block) || !"toolbar".equals(text(block.get("blockType")))) {
                continue;
            }
            Object props = block.get("props");
            if (!(props instanceof Map<?, ?> propsMap)) {
                return Set.of();
            }
            Object actions = propsMap.get("actions");
            if (!(actions instanceof List<?> actionList)) {
                return Set.of();
            }
            return actionList.stream()
                    .map(this::text)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return Set.of();
    }

    private int resolveEditGridCols(LowcodePageSchema pageSchema) {
        int cols = 1;
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        if (editZone != null && editZone.getProps() != null) {
            Integer configuredCols = integerValue(editZone.getProps().get("editGridCols"));
            if (configuredCols != null && configuredCols > 0) {
                return Math.max(1, Math.min(3, configuredCols));
            }
            cols = Math.max(cols, resolveCanvasGridCols(editZone));
        }
        for (Map<String, Object> rule : extractFormRules(pageSchema)) {
            Object col = rule.get("col");
            if (!(col instanceof Map<?, ?> colMap)) {
                continue;
            }
            Integer span = integerValue(colMap.get("span"));
            if (span == null || span <= 0 || span >= 24) {
                continue;
            }
            cols = Math.max(cols, Math.min(3, Math.max(1, (int) Math.ceil(24.0 / span))));
        }
        return cols;
    }

    @SuppressWarnings("unchecked")
    private int resolveCanvasGridCols(LowcodePageZone editZone) {
        List<Map<String, Object>> items = extractCanvasItems(editZone);
        if (items.isEmpty()) {
            return 1;
        }
        List<Integer> columns = new ArrayList<>();
        items.stream()
                .filter(item -> StringUtils.isNotBlank(text(item.get("fieldRef"))))
                .sorted(Comparator.comparingInt(item -> intValue(item.get("x"), 0)))
                .forEach(item -> {
                    int x = intValue(item.get("x"), 0);
                    boolean exists = columns.stream().anyMatch(columnX -> Math.abs(columnX - x) < 80);
                    if (!exists) {
                        columns.add(x);
                    }
                });
        return Math.max(1, Math.min(3, columns.isEmpty() ? 1 : columns.size()));
    }

    private List<LowcodeFieldSchema> sortByCanvasOrder(List<LowcodeFieldSchema> fields,
                                                       LowcodePageSchema pageSchema,
                                                       String zoneKey) {
        if (fields == null || fields.size() <= 1) {
            return fields == null ? List.of() : fields;
        }
        LowcodePageZone zone = findZone(pageSchema, zoneKey);
        if (zone != null && zone.getProps() != null
                && "formDesignerSchema".equals(text(zone.getProps().get("compiledFrom")))) {
            return fields;
        }
        List<Map<String, Object>> items = extractCanvasItems(zone);
        if (items.isEmpty()) {
            return fields;
        }
        Map<String, LowcodeFieldSchema> fieldMap = fields.stream()
                .collect(Collectors.toMap(
                        LowcodeFieldSchema::getField,
                        field -> field,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<LowcodeFieldSchema> ordered = new ArrayList<>();
        items.stream()
                .sorted(this::compareCanvasItemPosition)
                .map(item -> text(item.get("fieldRef")))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .forEach(fieldRef -> {
                    LowcodeFieldSchema field = fieldMap.remove(fieldRef);
                    if (field != null) {
                        ordered.add(field);
                    }
                });
        ordered.addAll(fieldMap.values());
        return ordered;
    }

    private int compareCanvasItemPosition(Map<String, Object> left, Map<String, Object> right) {
        int leftRow = Math.round(intValue(left.get("y"), 0) / 16.0f);
        int rightRow = Math.round(intValue(right.get("y"), 0) / 16.0f);
        if (leftRow != rightRow) {
            return Integer.compare(leftRow, rightRow);
        }
        int xCompare = Integer.compare(intValue(left.get("x"), 0), intValue(right.get("x"), 0));
        if (xCompare != 0) {
            return xCompare;
        }
        return Integer.compare(intValue(left.get("zIndex"), 0), intValue(right.get("zIndex"), 0));
    }

    private List<Map<String, Object>> buildJoinConfig(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getModelRefs() == null || pageSchema.getModelRefs().size() <= 1) {
            return List.of();
        }
        LowcodePageModelRef primaryRef = pageSchema.getModelRefs().stream()
                .filter(ref -> Boolean.TRUE.equals(ref.getPrimary()))
                .findFirst()
                .orElse(null);
        String fallbackPrimaryCode = primaryRef == null
                ? modelSchema.getObject() == null ? null : modelSchema.getObject().getCode()
                : primaryRef.getModelCode();
        String primaryModelCode = StringUtils.defaultIfBlank(pageSchema.getPrimaryModelCode(), fallbackPrimaryCode);
        if (StringUtils.isBlank(primaryModelCode)) {
            return List.of();
        }
        List<LowcodeRelationSchema> primaryRelations = primaryRef != null && primaryRef.getRelations() != null
                ? primaryRef.getRelations()
                : modelSchema.getRelations();
        List<Map<String, Object>> result = new ArrayList<>();
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || StringUtils.isBlank(ref.getModelCode())) {
                continue;
            }
            LowcodeRelationSchema relation = resolveRuntimeRelation(primaryModelCode, ref, primaryRelations);
            if (relation == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("modelCode", ref.getModelCode());
            item.put("modelName", ref.getModelName());
            item.put("tableName", ref.getTableName());
            item.put("sourceField", relation.getSourceField());
            item.put("targetField", relation.getTargetField());
            item.put("targetObjectCode", relation.getTargetObjectCode());
            item.put("relationType", StringUtils.defaultIfBlank(relation.getRelationType(), "REFERENCE"));
            LowcodeRelationSchema primaryRelation = findRelationFromPrimary(primaryRelations, ref.getModelCode());
            if (primaryRelation != null) {
                String displaySourceField = RuntimeRelationLookupCompiler.resolveDisplayField(ref, primaryRelation);
                String relationSourceField = RuntimeRelationLookupCompiler.normalizePrimaryFieldName(
                        modelSchema, primaryRelation.getSourceField());
                putIfNotBlank(item, "displayField", displaySourceField);
                putIfNotBlank(item, "displayAlias", RuntimeRelationLookupCompiler.displayAlias(relationSourceField));
            }
            result.add(item);
        }
        return result;
    }




    private boolean isTreeRuntime(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        if (modelSchema != null) {
            String appType = StringUtils.defaultIfBlank(modelSchema.getAppType(), "SINGLE").toUpperCase(Locale.ROOT);
            if ("TREE".equals(appType)
                    || (modelSchema.getTreeConfig() != null && Boolean.TRUE.equals(modelSchema.getTreeConfig().getEnabled()))) {
                return true;
            }
        }
        return (pageSchema != null && "tree-crud".equals(pageSchema.getLayoutType()))
                || extractTreeConfigOverrides(pageSchema) instanceof Map<?, ?>
                || hasTreePanelBlock(pageSchema);
    }

    private boolean isMasterDetailRuntime(LowcodePageSchema pageSchema) {
        return pageSchema != null && MASTER_DETAIL_LAYOUT.equals(pageSchema.getLayoutType());
    }

    private Set<String> buildChildFieldRefs(LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getModelRefs() == null) {
            return Set.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || ref.getFields() == null) {
                continue;
            }
            for (Map<String, Object> field : ref.getFields()) {
                String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
                String fieldRef = StringUtils.defaultIfBlank(text(field.get("fieldRef")),
                        safeKey(ref.getModelCode()) + "__" + sourceField);
                if (StringUtils.isNotBlank(fieldRef)) {
                    refs.add(fieldRef);
                }
            }
        }
        return refs;
    }

    @SuppressWarnings("unchecked")

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }


    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private void appendTreeRuntimeField(List<Map<String, Object>> fields,
                                        LowcodeModelSchema modelSchema,
                                        LowcodePageSchema pageSchema,
                                        String zoneKey) {
        if (!isTreeRuntime(modelSchema, pageSchema)) {
            return;
        }
        String filterField = String.valueOf(buildTreeConfig(modelSchema, pageSchema, extractTreeConfigOverrides(pageSchema)).get("filterField"));
        boolean exists = fields.stream().anyMatch(item -> filterField.equals(item.get("field"))
                || filterField.equals(item.get("prop"))
                || filterField.equals(item.get("dataIndex"))
                || filterField.equals(item.get("key")));
        if (exists) {
            return;
        }
        LowcodeFieldSchema fieldSchema = findField(modelSchema, filterField);
        if (fieldSchema == null) {
            return;
        }
        Map<String, Object> runtimeField = "edit".equals(zoneKey)
                ? buildEditField(fieldSchema, Map.of(), modelSchema, pageSchema)
                // 隐藏筛选项不传 runtimeConfigKey，避免外部树源误挂本表 defaultSort
                : buildSearchField(fieldSchema, Map.of(), modelSchema, pageSchema, null);
        if (!"edit".equals(zoneKey)) {
            runtimeField.put("hidden", true);
        }
        runtimeField.put("queryType", "eq");
        runtimeField.put("required", false);
        fields.add(runtimeField);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }


    private LowcodeFieldSchema findField(LowcodeModelSchema modelSchema, String fieldName) {
        if (modelSchema == null || modelSchema.getFields() == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        return modelSchema.getFields().stream()
                .filter(field -> fieldName.equals(field.getField()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildTransConfig(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        Map<String, Object> result = fieldMetadataCompiler.translationConfig(modelSchema);
        RuntimeRelationLookupCompiler.appendDisplayTranslations(result, modelSchema, pageSchema);
        return result;
    }

    private Map<String, Object> buildSearchField(LowcodeFieldSchema field) {
        return buildSearchField(field, Map.of(), null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSearchField(LowcodeFieldSchema field,
                                                 Map<String, Object> pageSetting,
                                                 LowcodeModelSchema modelSchema,
                                                 LowcodePageSchema pageSchema) {
        return buildSearchField(field, pageSetting, modelSchema, pageSchema, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSearchField(LowcodeFieldSchema field,
                                                 Map<String, Object> pageSetting,
                                                 LowcodeModelSchema modelSchema,
                                                 LowcodePageSchema pageSchema,
                                                 String runtimeConfigKey) {
        Map<String, Object> item = new LinkedHashMap<>();
        String configuredQueryFieldName = StringUtils.defaultIfBlank(text(pageSetting.get("queryField")), field.getField());
        LowcodeFieldSchema queryField = findRuntimeField(modelSchema, pageSchema, configuredQueryFieldName);
        String queryFieldName = queryField == null ? field.getField() : configuredQueryFieldName;
        LowcodeFieldSchema effectiveField = queryField == null ? field : queryField;
        String queryType = StringUtils.defaultIfBlank(text(pageSetting.get("queryType")),
                StringUtils.defaultIfBlank(effectiveField.getQueryType(), StringUtils.defaultIfBlank(field.getQueryType(), "eq")))
                .toLowerCase(Locale.ROOT);
        item.put("field", queryFieldName);
        item.put("label", StringUtils.defaultIfBlank(field.getLabel(), field.getField()));
        String componentType = resolveSearchComponentType(effectiveField, queryType, pageSetting);
        item.put("type", componentType);
        item.put("queryType", queryType);
        applyAlignment(item, pageSetting);
        if (pageSetting.containsKey("defaultValue")) {
            item.put("defaultValue", pageSetting.get("defaultValue"));
        }
        if (pageSetting.containsKey("collapsed")) {
            item.put("collapsed", booleanWithDefault(pageSetting.get("collapsed"), false));
        }
        RuntimeRelationLookupCompiler.RelationLookupMeta lookupMeta = RuntimeRelationLookupCompiler.resolve(
                modelSchema, pageSchema, field.getField());
        if (lookupMeta != null) {
            item.put("type", "select");
            item.put("queryType", "eq");
            item.put("relationLookup", RuntimeRelationLookupCompiler.buildConfig(lookupMeta));
        }
        if ("daterange".equals(componentType) || "datetimerange".equals(componentType) || "timerange".equals(componentType)) {
            item.put("startPlaceholder", "开始" + StringUtils.defaultIfBlank(field.getLabel(), field.getField()));
            item.put("endPlaceholder", "结束" + StringUtils.defaultIfBlank(field.getLabel(), field.getField()));
        }
        String dictType = StringUtils.defaultIfBlank(text(pageSetting.get("dictType")), effectiveField.getDictType());
        if (StringUtils.isNotBlank(dictType)) {
            item.put("dictType", dictType);
        }
        Map<String, Object> props = sanitizeFieldBasicProps(field);
        Object designerProps = pageSetting.get("props");
        if (designerProps instanceof Map<?, ?> designerPropsMap) {
            props.putAll((Map<String, Object>) designerPropsMap);
        }
        // 查询区选项源与表单字段保持一致：优先用表单设计器 props / 模型 basicProps
        if (!RuntimeTreeFieldDecorator.hasEffectiveOptionSource(props.get("optionSource"))
                && !RuntimeTreeFieldDecorator.hasEffectiveOptionSource(item.get("optionSource"))) {
            Map<String, Object> editSetting = resolveEditFieldSetting(pageSchema, field.getField());
            Object editPropsValue = editSetting.get("props");
            if (editPropsValue instanceof Map<?, ?> editProps) {
                Object editOptionSource = editProps.get("optionSource");
                if (RuntimeTreeFieldDecorator.hasEffectiveOptionSource(editOptionSource)) {
                    props.put("optionSource", editOptionSource);
                    item.put("optionSource", editOptionSource);
                }
            }
        }
        if (RuntimeTreeFieldDecorator.hasEffectiveOptionSource(props.get("optionSource"))
                && !RuntimeTreeFieldDecorator.hasEffectiveOptionSource(item.get("optionSource"))) {
            item.put("optionSource", props.get("optionSource"));
        }
        if (!props.isEmpty()) {
            item.put("props", props);
        }
        if (lookupMeta != null) {
            RuntimeRelationLookupCompiler.applyProps(
                    item, lookupMeta, StringUtils.defaultIfBlank(field.getLabel(), field.getField()));
        }
        // 查询区树形默认支持本级+子集；选项源优先对齐左树（同一 tree API / 同一排序）
        if ("treeSelect".equals(text(item.get("type"))) || "orgTreeSelect".equals(text(item.get("type")))) {
            String searchTreeConfigKey = StringUtils.defaultIfBlank(
                    configKeyForSearchTree(pageSchema, modelSchema),
                    runtimeConfigKey);
            applySearchTreeSelectDefaults(item, props, modelSchema, pageSchema, searchTreeConfigKey);
        }
        return item;
    }

    private String configKeyForSearchTree(LowcodePageSchema pageSchema, LowcodeModelSchema modelSchema) {
        Object overrides = extractTreeConfigOverrides(pageSchema);
        if (overrides instanceof Map<?, ?> map) {
            String sourceConfigKey = text(map.get("sourceConfigKey"));
            if (StringUtils.isNotBlank(sourceConfigKey)) {
                return sourceConfigKey;
            }
        }
        if (modelSchema != null && modelSchema.getTreeConfig() != null
                && StringUtils.isNotBlank(modelSchema.getTreeConfig().getSourceConfigKey())) {
            return modelSchema.getTreeConfig().getSourceConfigKey();
        }
        return null;
    }

    private void applySearchTreeSelectDefaults(Map<String, Object> item,
                                               Map<String, Object> props,
                                               LowcodeModelSchema modelSchema,
                                               LowcodePageSchema pageSchema,
                                               String leftTreeConfigKey) {
        if (item == null) {
            return;
        }
        Map<String, Object> nextProps = props == null ? new LinkedHashMap<>() : props;
        // 与左树一致：默认本级+下级；仅显式 false 时关闭
        if (!nextProps.containsKey("includeChildren")) {
            nextProps.put("includeChildren", Boolean.TRUE);
        }
        item.put("includeChildren", nextProps.get("includeChildren"));
        String fieldName = text(item.get("field"));
        Map<String, Object> treeConfig = buildTreeConfig(modelSchema, pageSchema, extractTreeConfigOverrides(pageSchema));
        String filterField = firstNonBlank(text(treeConfig.get("filterField")), text(treeConfig.get("parentField")));
        boolean alignWithLeftTree = StringUtils.isNotBlank(leftTreeConfigKey)
                && StringUtils.isNotBlank(filterField)
                && filterField.equals(fieldName);
        // 与左树筛选字段相同时，查询树强制共用左树 tree API（节点序用树接口默认序，不用列表 defaultSort）
        if (alignWithLeftTree) {
            Map<String, Object> optionSource = buildTreeOptionSource(leftTreeConfigKey, treeConfig, Map.of());
            nextProps.put("optionSource", optionSource);
            item.put("optionSource", optionSource);
        }
        if (!nextProps.isEmpty()) {
            item.put("props", nextProps);
        }
    }

    private LowcodeFieldSchema findRuntimeField(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema, String fieldName) {
        if (StringUtils.isBlank(fieldName) || modelSchema == null) {
            return null;
        }
        LowcodeFieldSchema field = findField(modelSchema, fieldName);
        if (field != null) {
            return field;
        }
        Map<String, LowcodeFieldSchema> fieldMap = buildRuntimeFieldMap(modelSchema, pageSchema);
        return fieldMap.get(fieldName);
    }

    private Map<String, Object> buildTableColumn(LowcodeFieldSchema field) {
        return buildTableColumn(field, Map.of());
    }

    private Map<String, Object> buildTableColumn(LowcodeFieldSchema field, Map<String, Object> pageSetting) {
        return buildTableColumn(field, pageSetting, null, null);
    }

    private Map<String, Object> buildTableColumn(LowcodeFieldSchema field,
                                                 Map<String, Object> pageSetting,
                                                 LowcodeModelSchema modelSchema,
                                                 LowcodePageSchema pageSchema) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", field.getField());
        item.put("title", resolveTableColumnTitle(field, pageSetting, pageSchema));
        item.put("dataIndex", field.getField());
        applyAlignment(item, pageSetting);
        Integer settingWidth = integerValue(pageSetting.get("width"));
        if (settingWidth != null && settingWidth > 0) {
            item.put("width", settingWidth);
        } else if (field.getWidth() != null && field.getWidth() > 0) {
            item.put("width", field.getWidth());
        }
        String fixed = normalizeFixed(text(pageSetting.get("fixed")));
        if (StringUtils.isNotBlank(fixed)) {
            item.put("fixed", fixed);
        }
        Object sortable = pageSetting.get("sortable");
        if (Boolean.TRUE.equals(sortable) || Boolean.TRUE.equals(field.getSortable())) {
            item.put("sorter", true);
        }
        if (field.getAdvancedProps() != null && !field.getAdvancedProps().isEmpty()) {
            item.put("advancedProps", new LinkedHashMap<>(field.getAdvancedProps()));
        }
        if (StringUtils.isNotBlank(field.getFieldStatus())) {
            item.put("fieldStatus", field.getFieldStatus());
        }
        item.put("listVisible", true);
        String componentType = StringUtils.defaultIfBlank(text(pageSetting.get("componentType")), field.getComponentType());
        componentType = StringUtils.defaultIfBlank(componentType, "input");
        String renderType = StringUtils.defaultIfBlank(text(pageSetting.get("renderType")),
                resolveDefaultRenderType(field, componentType));
        String targetField = StringUtils.defaultIfBlank(text(pageSetting.get("targetField")), field.getField() + "Name");
        RuntimeRelationLookupCompiler.RelationLookupMeta lookupMeta = RuntimeRelationLookupCompiler.resolve(
                modelSchema, pageSchema, field.getField());
        if (lookupMeta != null) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "relationName");
            render.put("targetField", RuntimeRelationLookupCompiler.displayAlias(field.getField()));
            render.put("relationModelCode", lookupMeta.modelCode());
            render.put("displayField", lookupMeta.displayField());
            item.put("render", render);
        } else if (field.isReferenceField()) {
            // 字段级引用：选中记录时显示名称冗余写入伴随列，relationName 渲染优先读伴随列，存量空值退化显示 ID。
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "relationName");
            render.put("targetField", field.referenceDisplayFieldName());
            item.put("render", render);
        } else if ("dictTag".equals(renderType) || (StringUtils.isBlank(renderType) && StringUtils.isNotBlank(field.getDictType()))) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "dictTag");
            render.put("dictType", field.getDictType());
            item.put("render", render);
        } else if ("orgName".equals(renderType)) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "orgName");
            render.put("targetField", targetField);
            item.put("render", render);
        } else if ("userName".equals(renderType)) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "userName");
            render.put("targetField", targetField);
            item.put("render", render);
        } else if ("regionName".equals(renderType)) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "regionName");
            render.put("targetField", targetField);
            item.put("render", render);
        } else if ("fileUpload".equals(renderType)) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "fileUpload");
            render.put("targetField", targetField);
            item.put("render", render);
        } else if ("imageUpload".equals(renderType)) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "imageUpload");
            render.put("targetField", targetField);
            item.put("render", render);
        } else if (fieldMetadataCompiler.hasDynamicOptionSource(field, componentType)) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "relationName");
            render.put("targetField", field.getField() + "Name");
            item.put("render", render);
        } else if ("switch".equals(renderType) || "switch".equals(componentType)) {
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("type", "switch");
            Map<String, Object> basicProps = field.getBasicProps() == null ? Map.of() : field.getBasicProps();
            Object checkedValue = firstPresent(pageSetting.get("checkedValue"), basicProps.get("checkedValue"), 1);
            Object uncheckedValue = firstPresent(pageSetting.get("uncheckedValue"), basicProps.get("uncheckedValue"), 0);
            render.put("checkedValue", checkedValue);
            render.put("uncheckedValue", uncheckedValue);
            Object checkedText = firstPresent(pageSetting.get("checkedText"), basicProps.get("checkedText"));
            Object uncheckedText = firstPresent(pageSetting.get("uncheckedText"), basicProps.get("uncheckedText"));
            if (checkedText != null) {
                render.put("checkedText", checkedText);
            }
            if (uncheckedText != null) {
                render.put("uncheckedText", uncheckedText);
            }
            item.put("render", render);
            if (!item.containsKey("width") && integerValue(pageSetting.get("width")) == null) {
                item.put("width", 100);
            }
            if (!item.containsKey("align")) {
                item.put("align", "center");
            }
        }
        copyTableColumnDesignerSettings(item, pageSetting);
        return item;
    }

    private void copyTableColumnDesignerSettings(Map<String, Object> item, Map<String, Object> pageSetting) {
        putIfNotBlank(item, "renderType", text(pageSetting.get("renderType")));
        putIfNotBlank(item, "targetField", text(pageSetting.get("targetField")));
        putIfNotBlank(item, "textColor", text(pageSetting.get("textColor")));
        String clickAction = StringUtils.defaultIfBlank(text(pageSetting.get("clickAction")), "none");
        if (!"none".equals(clickAction)) {
            item.put("clickAction", clickAction);
            putIfNotBlank(item, "targetPageKey", text(pageSetting.get("targetPageKey")));
            putIfNotBlank(item, "targetFormKey", text(pageSetting.get("targetFormKey")));
            putIfNotBlank(item, "targetParamName", text(pageSetting.get("targetParamName")));
            putIfNotBlank(item, "targetParamField", text(pageSetting.get("targetParamField")));
        }
    }

    private String resolveDefaultRenderType(LowcodeFieldSchema field, String componentType) {
        if (field != null && StringUtils.isNotBlank(field.getDictType())) {
            return "dictTag";
        }
        return switch (StringUtils.defaultString(componentType)) {
            case "orgTreeSelect" -> "orgName";
            case "userSelect" -> "userName";
            case "regionTreeSelect" -> "regionName";
            case "fileUpload", "imageUpload", "switch" -> componentType;
            default -> "";
        };
    }

    private void applyAlignment(Map<String, Object> item, Map<String, Object> pageSetting) {
        String align = normalizeAlign(StringUtils.defaultIfBlank(text(pageSetting.get("align")),
                text(pageSetting.get("textAlign"))));
        if (StringUtils.isNotBlank(align)) {
            item.put("align", align);
        }
    }

    private String normalizeAlign(String value) {
        String align = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "center", "right").contains(align) ? align : null;
    }

    private String normalizeFixed(String value) {
        String fixed = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        return Set.of("left", "right").contains(fixed) ? fixed : null;
    }

    private Map<String, Object> sanitizeFieldBasicProps(LowcodeFieldSchema field) {
        if (field == null || field.getBasicProps() == null || field.getBasicProps().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> props = new LinkedHashMap<>();
        copyBasicProp(field.getBasicProps(), props, "placeholder");
        copyBasicProp(field.getBasicProps(), props, "cascade");
        copyBasicProp(field.getBasicProps(), props, "cascadeConfig");
        copyBasicProp(field.getBasicProps(), props, "sourceField");
        copyBasicProp(field.getBasicProps(), props, "sourceDictType");
        copyBasicProp(field.getBasicProps(), props, "linkedDictType");
        copyBasicProp(field.getBasicProps(), props, "linkedDictValue");
        copyBasicProp(field.getBasicProps(), props, "parentDictCode");
        copyBasicProp(field.getBasicProps(), props, "matchMode");
        copyBasicProp(field.getBasicProps(), props, "emptyStrategy");
        copyBasicProp(field.getBasicProps(), props, "clearOnSourceChange");
        copyBasicProp(field.getBasicProps(), props, "clearable");
        copyBasicProp(field.getBasicProps(), props, "filterable");
        copyBasicProp(field.getBasicProps(), props, "multiple");
        copyBasicProp(field.getBasicProps(), props, "optionSource");
        copyBasicProp(field.getBasicProps(), props, "fieldMappings");
        copyBasicProp(field.getBasicProps(), props, "mappings");
        copyBasicProp(field.getBasicProps(), props, "labelValueField");
        copyBasicProp(field.getBasicProps(), props, "targetField");
        copyBasicProp(field.getBasicProps(), props, "rootCode");
        copyBasicProp(field.getBasicProps(), props, "dataRight");
        copyBasicProp(field.getBasicProps(), props, "virtualDisabled");
        copyBasicProp(field.getBasicProps(), props, "limit");
        copyBasicProp(field.getBasicProps(), props, "fileSize");
        copyBasicProp(field.getBasicProps(), props, "fileType");
        copyBasicProp(field.getBasicProps(), props, "storageType");
        copyBasicProp(field.getBasicProps(), props, "valueType");
        copyBasicProp(field.getBasicProps(), props, "showTip");
        copyBasicProp(field.getBasicProps(), props, "showFileList");
        copyBasicProp(field.getBasicProps(), props, "uploadButtonText");
        copyBasicProp(field.getBasicProps(), props, "businessType");
        copyBasicProp(field.getBasicProps(), props, "businessId");
        copyBasicProp(field.getBasicProps(), props, "referenceObjectCode");
        copyBasicProp(field.getBasicProps(), props, "referenceDisplayField");
        copyBasicProp(field.getBasicProps(), props, "referenceValueField");
        copyBasicProp(field.getBasicProps(), props, "targetObjectCode");
        copyBasicProp(field.getBasicProps(), props, "recordSelector");
        copyBasicProp(field.getBasicProps(), props, "recordSelectorConfig");
        copyBasicProp(field.getBasicProps(), props, "selector");
        copyBasicProp(field.getBasicProps(), props, "selectorConfig");
        copyBasicProp(field.getBasicProps(), props, "relationKey");
        copyBasicProp(field.getBasicProps(), props, "inlineCreateEnabled");
        copyBasicProp(field.getBasicProps(), props, "showInDetail");
        copyBasicProp(field.getBasicProps(), props, "validation");
        copyBasicProp(field.getBasicProps(), props, "min");
        copyBasicProp(field.getBasicProps(), props, "max");
        copyBasicProp(field.getBasicProps(), props, "minimum");
        copyBasicProp(field.getBasicProps(), props, "maximum");
        copyBasicProp(field.getBasicProps(), props, "step");
        copyBasicProp(field.getBasicProps(), props, "precision");
        copyBasicProp(field.getBasicProps(), props, "maxlength");
        copyBasicProp(field.getBasicProps(), props, "maxLength");
        copyBasicProp(field.getBasicProps(), props, "checkedValue");
        copyBasicProp(field.getBasicProps(), props, "uncheckedValue");
        copyBasicProp(field.getBasicProps(), props, "checkedText");
        copyBasicProp(field.getBasicProps(), props, "uncheckedText");
        copyBasicProp(field.getBasicProps(), props, "runtimeRules");
        copyBasicProp(field.getBasicProps(), props, "__events");
        return props;
    }

    private void copyBasicProp(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveFieldSetting(LowcodePageSchema pageSchema, String zoneKey, String fieldName) {
        LowcodePageZone zone = findZone(pageSchema, zoneKey);
        if (zone == null || zone.getProps() == null || StringUtils.isBlank(fieldName)) {
            return Map.of();
        }
        Object settings = zone.getProps().get("fieldSettings");
        if (!(settings instanceof Map<?, ?> settingsMap)) {
            return Map.of();
        }
        Object value = settingsMap.get(fieldName);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, Object> resolveRuntimeFieldSetting(LowcodePageSchema pageSchema, String zoneKey, String fieldName) {
        Map<String, Object> result = new LinkedHashMap<>(resolveFieldSetting(pageSchema, zoneKey, fieldName));
        Map<String, Object> gridSetting = resolveGridFieldSetting(pageSchema, zoneKey, fieldName);
        result.putAll(gridSetting);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveGridFieldSetting(LowcodePageSchema pageSchema, String zoneKey, String fieldName) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null || StringUtils.isBlank(fieldName)) {
            return Map.of();
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> list)) {
            return Map.of();
        }
        for (String blockType : runtimeSettingBlockTypes(zoneKey)) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> block)) {
                    continue;
                }
                if (!blockType.equals(text(block.get("blockType")))) {
                    continue;
                }
                Object propsValue = block.get("props");
                if (!(propsValue instanceof Map<?, ?> props)) {
                    continue;
                }
                String globalAlign = "table".equals(zoneKey) ? normalizeAlign(text(props.get("globalAlign"))) : null;
                // AiCrudPage 查询区配置写在 searchFieldSettings，不能误读表格 fieldSettings
                Object settingsValue = props.get("fieldSettings");
                if ("search".equals(zoneKey) && "AiCrudPage".equals(blockType) && props.containsKey("searchFieldSettings")) {
                    settingsValue = props.get("searchFieldSettings");
                }
                if (!(settingsValue instanceof Map<?, ?> settings)) {
                    if (StringUtils.isNotBlank(globalAlign)) {
                        return Map.of("align", globalAlign);
                    }
                    continue;
                }
                Object value = settings.get(fieldName);
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) map);
                    if (StringUtils.isNotBlank(globalAlign)
                            && StringUtils.isBlank(normalizeAlign(StringUtils.defaultIfBlank(
                            text(result.get("align")), text(result.get("textAlign")))))) {
                        result.put("align", globalAlign);
                    }
                    return result;
                }
                if (StringUtils.isNotBlank(globalAlign)) {
                    return Map.of("align", globalAlign);
                }
            }
        }
        return Map.of();
    }

    private List<String> runtimeSettingBlockTypes(String zoneKey) {
        if ("search".equals(zoneKey)) {
            return List.of("search-form", "AiCrudPage");
        }
        if ("table".equals(zoneKey)) {
            return List.of("data-table", "AiCrudPage", "AiTable");
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveGridBlockProps(LowcodePageSchema pageSchema, List<String> blockTypes) {
        if (pageSchema == null || pageSchema.getListGridLayout() == null || blockTypes == null || blockTypes.isEmpty()) {
            return Map.of();
        }
        Object items = pageSchema.getListGridLayout().get("items");
        if (!(items instanceof List<?> list)) {
            return Map.of();
        }
        for (String blockType : blockTypes) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> block)) {
                    continue;
                }
                if (!blockType.equals(text(block.get("blockType")))) {
                    continue;
                }
                Object props = block.get("props");
                if (props instanceof Map<?, ?> map) {
                    return new LinkedHashMap<>((Map<String, Object>) map);
                }
            }
        }
        return Map.of();
    }

    private Map<String, Object> resolveEditFieldSetting(LowcodePageSchema pageSchema, String fieldName) {
        Map<String, Object> setting = new LinkedHashMap<>(resolveFieldSetting(pageSchema, "edit", fieldName));
        Map<String, Object> designerSetting = resolveFormRuleSetting(pageSchema, fieldName);
        setting.putAll(designerSetting);
        Map<String, Object> canvasSetting = resolveCanvasFieldSetting(pageSchema, fieldName);
        setting.putAll(canvasSetting);
        return setting;
    }

    private Map<String, Object> resolveCanvasFieldSetting(LowcodePageSchema pageSchema, String fieldName) {
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        if (editZone == null || StringUtils.isBlank(fieldName)) {
            return Map.of();
        }
        int gridCols = Math.max(1, resolveEditGridCols(pageSchema));
        int canvasWidth = 1040;
        if (editZone.getProps() != null) {
            Object canvas = editZone.getProps().get("canvas");
            if (canvas instanceof Map<?, ?> canvasMap) {
                canvasWidth = intValue(canvasMap.get("width"), canvasWidth);
            }
        }
        int colWidth = Math.max(1, (canvasWidth - 64) / gridCols);
        for (Map<String, Object> item : extractCanvasItems(editZone)) {
            if (!fieldName.equals(text(item.get("fieldRef")))) {
                continue;
            }
            Map<String, Object> setting = new LinkedHashMap<>();
            int itemWidth = intValue(item.get("w"), 280);
            int span = Math.max(1, Math.min(gridCols, Math.round((float) itemWidth / colWidth)));
            setting.put("span", span);
            Object style = item.get("style");
            if (style instanceof Map<?, ?> styleMap && styleMap.get("labelWidth") != null) {
                setting.put("labelWidth", styleMap.get("labelWidth"));
            }
            return setting;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveFormRuleSetting(LowcodePageSchema pageSchema, String fieldName) {
        if (StringUtils.isBlank(fieldName)) {
            return Map.of();
        }
        for (Map<String, Object> rule : extractFormRules(pageSchema)) {
            if (!fieldName.equals(text(rule.get("field")))) {
                continue;
            }
            Map<String, Object> setting = new LinkedHashMap<>();
            Object props = rule.get("props");
            if (props instanceof Map<?, ?> propsMap) {
                setting.put("props", new LinkedHashMap<>((Map<String, Object>) propsMap));
            }
            String componentType = extractForgeComponentType(rule);
            if (StringUtils.isNotBlank(componentType)) {
                setting.put("componentType", componentType);
            }
            String dictType = text(getNestedValue(rule, "props.dictType"));
            if (StringUtils.isNotBlank(dictType)) {
                setting.put("dictType", dictType);
            }
            Object requiredSwitch = rule.get("$required");
            List<Map<String, Object>> validationRules = copyRuleList(rule.get("validate"));
            boolean requiredFromSwitch = isRequiredSwitchEnabled(requiredSwitch);
            boolean required = requiredFromSwitch
                    || validationRules.stream().anyMatch(item -> booleanWithDefault(item.get("required"), false));
            if (required) {
                setting.put("required", true);
                String requiredMessage = requiredFromSwitch && requiredSwitch instanceof String message && StringUtils.isNotBlank(message)
                        ? message
                        : validationRules.stream()
                        .filter(item -> booleanWithDefault(item.get("required"), false))
                        .map(item -> text(item.get("message")))
                        .filter(StringUtils::isNotBlank)
                        .findFirst()
                        .orElse("");
                if (StringUtils.isNotBlank(requiredMessage)) {
                    setting.put("requiredMessage", requiredMessage);
                }
                if (requiredFromSwitch && validationRules.stream().noneMatch(item -> booleanWithDefault(item.get("required"), false))) {
                    Map<String, Object> requiredRule = new LinkedHashMap<>();
                    requiredRule.put("required", true);
                    requiredRule.put("message", StringUtils.defaultIfBlank(requiredMessage, "该字段为必填项"));
                    requiredRule.put("trigger", List.of("blur", "change"));
                    validationRules.add(0, requiredRule);
                }
            } else if (requiredSwitch != null) {
                setting.put("required", false);
                validationRules.removeIf(item -> booleanWithDefault(item.get("required"), false));
            }
            if (!validationRules.isEmpty()) {
                setting.put("rules", validationRules);
            }
            Object style = rule.get("style");
            if (style != null) {
                setting.put("componentStyle", style);
            }
            Object className = firstPresent(rule.get("className"), rule.get("class"));
            if (className != null) {
                setting.put("formItemClass", className);
            }
            Object forgeLayout = getNestedValue(rule, "_forge.layout");
            if (forgeLayout instanceof Map<?, ?> layoutMap) {
                Object align = layoutMap.get("align");
                if (align != null) {
                    setting.put("align", align);
                }
                Object forgeLabelWidth = layoutMap.get("labelWidth");
                if (forgeLabelWidth != null) {
                    setting.put("labelWidth", forgeLabelWidth);
                }
            }
            Object col = rule.get("col");
            if (col instanceof Map<?, ?> colMap) {
                int gridCols = resolveEditGridCols(pageSchema);
                Integer span = integerValue(colMap.get("span"));
                if (span != null && span > 0) {
                    int gridSpan = (int) Math.ceil(gridCols * Math.min(24, span) / 24.0);
                    setting.put("span", Math.max(1, Math.min(gridCols, gridSpan)));
                }
                Object gridStyle = colMap.get("style");
                if (gridStyle != null) {
                    setting.put("gridStyle", gridStyle);
                }
            }
            Object labelWidth = rule.get("labelWidth");
            if (labelWidth != null) {
                setting.put("labelWidth", labelWidth);
            }
            return setting;
        }
        return Map.of();
    }

    private String extractForgeComponentType(Map<String, Object> rule) {
        String componentKey = text(getNestedValue(rule, "_forge.componentKey"));
        if (StringUtils.isNotBlank(componentKey)) {
            return componentKey;
        }
        String dragTag = text(rule.get("_fc_drag_tag"));
        if (StringUtils.isBlank(dragTag)) {
            return null;
        }
        return switch (dragTag) {
            case "forgeDictSelect" -> "dictSelect";
            case "forgeRegionTreeSelect" -> "regionTreeSelect";
            case "forgeOrgTreeSelect" -> "orgTreeSelect";
            case "forgeUserSelect" -> "userSelect";
            case "forgeFileUpload" -> "fileUpload";
            case "forgeImageUpload" -> "imageUpload";
            case "forgeObjectReference" -> "objectReference";
            case "forgeRecordSelector" -> "recordSelector";
            case "forgeSubTable" -> "subTable";
            default -> null;
        };
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFormRules(LowcodePageSchema pageSchema) {
        LowcodePageZone editZone = findZone(pageSchema, "edit");
        if (editZone == null || editZone.getProps() == null) {
            return List.of();
        }
        Object rules = editZone.getProps().get("formCreateRule");
        if (!(rules instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        collectFormRules(list, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractCanvasItems(LowcodePageZone zone) {
        if (zone == null || zone.getProps() == null) {
            return List.of();
        }
        Object canvas = zone.getProps().get("canvas");
        if (!(canvas instanceof Map<?, ?> canvasMap)) {
            return List.of();
        }
        Object items = canvasMap.get("items");
        if (!(items instanceof List<?> itemList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : itemList) {
            if (item instanceof Map<?, ?> itemMap) {
                result.add((Map<String, Object>) itemMap);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectFormRules(List<?> rules, List<Map<String, Object>> result) {
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> rule)) {
                continue;
            }
            Map<String, Object> typedRule = (Map<String, Object>) rule;
            result.add(typedRule);
            Object children = typedRule.get("children");
            if (children instanceof List<?> childRules) {
                collectFormRules(childRules, result);
            }
        }
    }

    private Map<String, Object> buildEditField(LowcodeFieldSchema field) {
        return buildEditField(field, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildEditField(LowcodeFieldSchema field, Map<String, Object> pageSetting) {
        return buildEditField(field, pageSetting, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildEditField(LowcodeFieldSchema field,
                                               Map<String, Object> pageSetting,
                                               LowcodeModelSchema modelSchema,
                                               LowcodePageSchema pageSchema) {
        Map<String, Object> item = new LinkedHashMap<>();
        String label = StringUtils.defaultIfBlank(text(pageSetting.get("label")),
                StringUtils.defaultIfBlank(field.getLabel(), field.getField()));
        RuntimeRelationLookupCompiler.RelationLookupMeta lookupMeta = RuntimeRelationLookupCompiler.resolve(
                modelSchema, pageSchema, field.getField());
        String componentType = resolveEditComponentType(field, pageSetting);
        if (lookupMeta != null) {
            componentType = "select";
        }
        Map<String, Object> formulaConfig = field.getFormulaConfig();
        boolean formulaField = formulaConfig != null && !formulaConfig.isEmpty();
        item.put("field", field.getField());
        item.put("label", label);
        item.put("type", componentType);
        applyAlignment(item, pageSetting);
        boolean required = pageSetting.containsKey("required")
                ? booleanWithDefault(pageSetting.get("required"), false)
                : Boolean.TRUE.equals(field.getRequired());
        List<Map<String, Object>> validationRules = resolveRuntimeValidationRules(pageSetting);
        if (!pageSetting.containsKey("required")
                && validationRules.stream().anyMatch(rule -> booleanWithDefault(rule.get("required"), false))) {
            required = true;
        }
        boolean readonly = pageSetting.containsKey("readonly")
                ? booleanWithDefault(pageSetting.get("readonly"), false)
                : Boolean.TRUE.equals(field.getReadonly());
        if (formulaField) {
            readonly = true;
            required = false;
            validationRules.removeIf(rule -> booleanWithDefault(rule.get("required"), false));
        }
        if (!required) {
            validationRules.removeIf(rule -> booleanWithDefault(rule.get("required"), false));
        }
        item.put("required", !isSystemField(field) && required);
        String requiredMessage = StringUtils.defaultIfBlank(text(pageSetting.get("requiredMessage")),
                resolveRequiredRuleMessage(validationRules));
        if (StringUtils.isNotBlank(requiredMessage)) {
            item.put("requiredMessage", requiredMessage);
        }
        Object trigger = StringUtils.isNotBlank(text(pageSetting.get("trigger")))
                ? pageSetting.get("trigger")
                : resolveRequiredRuleTrigger(validationRules);
        if (trigger != null) {
            item.put("trigger", trigger);
        }
        if (isSystemField(field) || readonly) {
            item.put("disabled", true);
            item.put("readonly", true);
        }
        copyRuntimeSetting(item, pageSetting, "hidden");
        copyRuntimeSetting(item, pageSetting, "formVisible");
        copyRuntimeSetting(item, pageSetting, "runtimeRules");
        if (formulaField) {
            item.put("formulaConfig", new LinkedHashMap<>(formulaConfig));
        }
        if (field.getAdvancedProps() != null && !field.getAdvancedProps().isEmpty()) {
            item.put("advancedProps", new LinkedHashMap<>(field.getAdvancedProps()));
        }
        String dictType = StringUtils.defaultIfBlank(text(pageSetting.get("dictType")), field.getDictType());
        if (StringUtils.isNotBlank(dictType)) {
            item.put("dictType", dictType);
        }
        if (pageSetting.containsKey("defaultValue")) {
            item.put("defaultValue", pageSetting.get("defaultValue"));
        } else if (field.getDefaultValue() != null) {
            item.put("defaultValue", field.getDefaultValue());
        }
        Object span = pageSetting.get("span");
        if (span != null) {
            item.put("span", span);
        }
        Object formItemStyle = pageSetting.get("formItemStyle");
        if (formItemStyle != null) {
            item.put("formItemStyle", formItemStyle);
        }
        Object gridStyle = pageSetting.get("gridStyle");
        if (gridStyle != null) {
            item.put("gridStyle", gridStyle);
        }
        Object labelWidth = pageSetting.get("labelWidth");
        if (labelWidth != null) {
            item.put("labelWidth", labelWidth);
        }
        copyRuntimeSetting(item, pageSetting, "componentStyle");
        copyRuntimeSetting(item, pageSetting, "componentClass");
        copyRuntimeSetting(item, pageSetting, "formItemClass");
        copyRuntimeSetting(item, pageSetting, "showFeedback");
        copyRuntimeSetting(item, pageSetting, "showLabel");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("placeholder", buildPlaceholder(componentType, label));
        if (isSystemField(field) || readonly) {
            props.put("disabled", true);
            props.put("readonly", true);
        }
        if (field.getLength() != null && field.getLength() > 0 && isTextComponent(componentType)) {
            props.put("maxlength", field.getLength());
        }
        if (field.getPrecision() != null && field.getPrecision() >= 0 && "number".equals(componentType)) {
            props.put("precision", field.getPrecision());
        }
        props.putAll(sanitizeFieldBasicProps(field));
        Object designerProps = pageSetting.get("props");
        if (designerProps instanceof Map<?, ?> designerPropsMap) {
            Map<String, Object> sanitizedDesignerProps = new LinkedHashMap<>((Map<String, Object>) designerPropsMap);
            Map<String, Object> formCreateMeta = mapValue(sanitizedDesignerProps.get("__fc"));
            sanitizedDesignerProps.remove("__fc");
            sanitizedDesignerProps.remove("__fcType");
            sanitizedDesignerProps.remove("fieldBinding");
            props.putAll(sanitizedDesignerProps);
            applyFormCreateMeta(item, formCreateMeta, props);
        }
        LowcodeFieldConstraintSupport.applyRuntimeConstraints(field, componentType, props);
        // optionSource 配置存在时清除残留的静态 options，避免 currentOptions 优先级链中
        // 静态 options 抢在 remoteOptionSource 之前返回，导致 QUERY_SOURCE/REMOTE 不生效
        if (props.containsKey("optionSource") && props.get("optionSource") instanceof Map<?, ?> os
                && !String.valueOf(os.get("type") != null ? os.get("type") : "").isEmpty()) {
            props.remove("options");
        }
        copyRuntimePropsToField(item, props);
        applySelectionLabelProps(props, field.getField(), componentType);
        if (isSystemField(field) || readonly) {
            props.put("disabled", true);
            props.put("readonly", true);
        }
        item.put("props", props);
        if (lookupMeta != null) {
            item.put("relationLookup", RuntimeRelationLookupCompiler.buildConfig(lookupMeta));
            RuntimeRelationLookupCompiler.applyProps(item, lookupMeta, label);
        } else if (field.isSelectionLabelField()) {
            // 引用/人员/部门/动态选项下拉：选中时同步提交显示名称到伴随列（<field>Name），
            // 编辑回显与列表渲染使用冗余字段，无需再查源表。
            props.putIfAbsent("labelValueField", field.referenceDisplayFieldName());
        } else {
            // 页面 props 已带动态 optionSource、但模型字段尚未回写 basicProps 时，仍补齐伴随字段绑定
            ensureDynamicOptionSourceLabelValueField(props, field.getField(), componentType);
        }

        if (required) {
            String message = StringUtils.defaultIfBlank(requiredMessage, buildPlaceholder(componentType, label));
            if (validationRules.stream().noneMatch(rule -> booleanWithDefault(rule.get("required"), false))) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("required", true);
                rule.put("message", message);
                rule.put("trigger", trigger == null ? List.of("blur", "change") : trigger);
                validationRules.add(0, rule);
            } else {
                validationRules.forEach(rule -> {
                    if (booleanWithDefault(rule.get("required"), false) && StringUtils.isBlank(text(rule.get("message")))) {
                        rule.put("message", message);
                    }
                });
            }
            item.put("requiredMessage", message);
        }
        if (!validationRules.isEmpty()) {
            item.put("rules", validationRules);
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resolveRuntimeValidationRules(Map<String, Object> pageSetting) {
        Object source = pageSetting.get("rules");
        if (!(source instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rules = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rules.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return rules;
    }

    private String resolveRequiredRuleMessage(List<Map<String, Object>> validationRules) {
        return validationRules.stream()
                .filter(rule -> booleanWithDefault(rule.get("required"), false))
                .map(rule -> text(rule.get("message")))
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("");
    }

    private Object resolveRequiredRuleTrigger(List<Map<String, Object>> validationRules) {
        return validationRules.stream()
                .filter(rule -> booleanWithDefault(rule.get("required"), false))
                .map(rule -> rule.get("trigger"))
                .filter(value -> value != null && StringUtils.isNotBlank(String.valueOf(value)))
                .findFirst()
                .orElse(null);
    }

    private void copyRuntimeSetting(Map<String, Object> item, Map<String, Object> pageSetting, String key) {
        if (pageSetting.containsKey(key)) {
            item.put(key, pageSetting.get(key));
        }
    }

    private void copyRuntimePropsToField(Map<String, Object> item, Map<String, Object> props) {
        List.of("placeholder", "clearable", "filterable", "multiple", "size", "maxlength", "showCount",
                        "rows", "autosize", "min", "max", "step", "precision", "showButton",
                        "checkedValue", "uncheckedValue", "checkedText", "uncheckedText", "format",
                        "valueFormat", "startPlaceholder", "endPlaceholder", "showFeedback", "showLabel")
                .forEach(key -> {
                    if (props.containsKey(key)) {
                        item.put(key, props.get(key));
                    }
                });
    }

    private void applyFormCreateMeta(Map<String, Object> item, Map<String, Object> formCreateMeta, Map<String, Object> props) {
        if (formCreateMeta == null || formCreateMeta.isEmpty()) {
            return;
        }
        Object style = firstPresent(formCreateMeta.get("style"), props.get("style"));
        if (style != null) {
            item.put("componentStyle", style);
        }
        Object componentClass = firstPresent(props.get("className"), props.get("class"));
        if (componentClass != null) {
            item.put("componentClass", componentClass);
        }
        Object formItemClass = firstPresent(formCreateMeta.get("className"), formCreateMeta.get("class"));
        if (formItemClass != null) {
            item.put("formItemClass", formItemClass);
        }
        Map<String, Object> wrap = mapValue(formCreateMeta.get("wrap"));
        if (wrap.get("style") != null) {
            item.put("formItemStyle", wrap.get("style"));
        }
        if (wrap.containsKey("labelWidth")) {
            item.put("labelWidth", wrap.get("labelWidth"));
        }
        if (wrap.containsKey("show") && !booleanWithDefault(wrap.get("show"), true)) {
            item.put("showLabel", false);
        }
    }

    private Object firstPresent(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (StringUtils.isNotBlank(text(value))) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> copyRuleList(Object source) {
        if (!(source instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    private boolean isRequiredSwitchEnabled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)
                && !"false".equalsIgnoreCase(text) && !"0".equals(text)) {
            return true;
        }
        return booleanWithDefault(value, false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private String normalizeChildListDisplayMode(Object value) {
        return "expand".equalsIgnoreCase(text(value)) ? "expand" : "aggregate";
    }

    private Map<String, Object> buildDefaultSort(LowcodeModelSchema modelSchema, LowcodePageSchema pageSchema) {
        Map<String, Object> props = resolveTableProps(pageSchema);
        Object defaultSort = props.get("defaultSort");
        String sortField = text(props.get("defaultSortField"));
        String sortOrder = text(props.get("defaultSortOrder"));
        if (defaultSort instanceof Map<?, ?> defaultSortMap) {
            sortField = StringUtils.defaultIfBlank(sortField,
                    StringUtils.defaultIfBlank(text(defaultSortMap.get("orderByColumn")), text(defaultSortMap.get("field"))));
            sortOrder = StringUtils.defaultIfBlank(sortOrder,
                    StringUtils.defaultIfBlank(text(defaultSortMap.get("isAsc")), text(defaultSortMap.get("order"))));
        }

        Set<String> allowedFields = modelSchema == null || modelSchema.getFields() == null
                ? Set.of("id")
                : modelSchema.getFields().stream()
                .map(LowcodeFieldSchema::getField)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String orderByColumn = StringUtils.defaultIfBlank(sortField, "id");
        if (!allowedFields.contains(orderByColumn)) {
            orderByColumn = "id";
        }

        String isAsc = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderByColumn", orderByColumn);
        result.put("isAsc", isAsc);
        return result;
    }

    private Map<String, Object> resolveTableProps(LowcodePageSchema pageSchema) {
        LowcodePageZone tableZone = findZone(pageSchema, "table");
        Map<String, Object> props = new LinkedHashMap<>();
        if (tableZone != null && tableZone.getProps() != null) {
            props.putAll(tableZone.getProps());
        }
        props.putAll(resolveGridBlockProps(pageSchema, List.of("data-table", "AiCrudPage", "AiTable")));
        return props;
    }

    private boolean isSystemField(LowcodeFieldSchema field) {
        return field != null && Boolean.TRUE.equals(field.getSystemField());
    }

    private List<LowcodeFieldSchema> resolveFields(LowcodeModelSchema modelSchema,
                                                   LowcodePageSchema pageSchema,
                                                   String zoneKey,
                                                   Predicate<LowcodeFieldSchema> fallbackPredicate) {
        Map<String, LowcodeFieldSchema> fieldMap = buildRuntimeFieldMap(modelSchema, pageSchema);
        LowcodePageZone zone = findZone(pageSchema, zoneKey);
        List<String> gridFieldRefs = resolveListGridFieldRefs(pageSchema, zoneKey);
        List<String> selectedRefs = !gridFieldRefs.isEmpty()
                ? gridFieldRefs
                : zone == null || zone.getFieldRefs() == null ? List.of() : zone.getFieldRefs();
        if (selectedRefs.isEmpty()
                || (gridFieldRefs.isEmpty() && (zone == null || Boolean.FALSE.equals(zone.getEnabled())))) {
            return fieldMap.values().stream()
                    .filter(this::isActiveField)
                    .filter(fallbackPredicate)
                    .toList();
        }

        Set<String> refs = new LinkedHashSet<>(selectedRefs);
        Set<String> childFieldRefs = buildChildFieldRefs(pageSchema);
        List<LowcodeFieldSchema> selectedFields = refs.stream()
                .map(ref -> resolveRuntimeField(fieldMap, ref))
                .filter(field -> field != null)
                .filter(field -> isZoneFieldAllowed(field, zoneKey, fallbackPredicate)
                        || ("table".equals(zoneKey)
                            && isActiveField(field)
                            && childFieldRefs.contains(field.getField())))
                .collect(Collectors.toCollection(ArrayList::new));
        if (selectedFields.isEmpty()) {
            return fieldMap.values().stream()
                    .filter(this::isActiveField)
                    .filter(fallbackPredicate)
                    .toList();
        }
        if ("table".equals(zoneKey)) {
            appendManagedBusinessFlowStatusFields(selectedFields, fieldMap, pageSchema);
        }
        return selectedFields;
    }

    /**
     * 平台托管的 flowStatus 在列表自由布局旧快照里常被漏掉。
     * 发布运行配置时强制补列，除非用户在列表设计里显式隐藏。
     */
    private void appendManagedBusinessFlowStatusFields(List<LowcodeFieldSchema> selectedFields,
                                                       Map<String, LowcodeFieldSchema> fieldMap,
                                                       LowcodePageSchema pageSchema) {
        if (selectedFields == null || fieldMap == null || fieldMap.isEmpty()) {
            return;
        }
        Set<String> present = selectedFields.stream()
                .map(LowcodeFieldSchema::getField)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (LowcodeFieldSchema field : fieldMap.values()) {
            if (!isManagedBusinessFlowStatusField(field) || !isActiveField(field)) {
                continue;
            }
            if (field.getListVisible() != null && !Boolean.TRUE.equals(field.getListVisible())) {
                continue;
            }
            if (isTableFieldExplicitlyHidden(pageSchema, field.getField())) {
                continue;
            }
            if (present.add(field.getField())) {
                selectedFields.add(field);
            }
        }
    }

    /**
     * 已发布快照早于流程托管字段时，按发布同一规则生成该字段的列表列；
     * 非托管字段、已停用、列表不可见或在列表设计里显式隐藏时返回 null。
     */
    public Map<String, Object> buildManagedFlowStatusColumn(LowcodeModelSchema modelSchema,
                                                            LowcodePageSchema pageSchema,
                                                            LowcodeFieldSchema field) {
        if (!isManagedBusinessFlowStatusField(field) || !isActiveField(field)
                || (field.getListVisible() != null && !Boolean.TRUE.equals(field.getListVisible()))
                || isTableFieldExplicitlyHidden(pageSchema, field.getField())) {
            return null;
        }
        return buildTableColumn(field, resolveRuntimeFieldSetting(pageSchema, "table", field.getField()),
                modelSchema, pageSchema);
    }

    private boolean isManagedBusinessFlowStatusField(LowcodeFieldSchema field) {
        if (field == null) {
            return false;
        }
        if ("flowStatus".equalsIgnoreCase(field.getField())
                || "flow_status".equalsIgnoreCase(field.getColumnName())) {
            return true;
        }
        Map<String, Object> advancedProps = field.getAdvancedProps();
        if (advancedProps != null
                && "BUSINESS_FLOW".equalsIgnoreCase(String.valueOf(advancedProps.get("managedBy")))) {
            return true;
        }
        return "business_flow_status".equalsIgnoreCase(field.getDictType());
    }

    private boolean isTableFieldExplicitlyHidden(LowcodePageSchema pageSchema, String fieldCode) {
        if (pageSchema == null || StringUtils.isBlank(fieldCode)) {
            return false;
        }
        Map<String, Object> setting = resolveRuntimeFieldSetting(pageSchema, "table", fieldCode);
        return setting != null && Boolean.FALSE.equals(setting.get("visible"));
    }

    /**
     * 自由列表设计器的 gridLayout 是列表字段的直接事实来源。历史草稿可能因 viewSchema
     * 只识别主表字段而留下过期的 table zone，发布时必须优先读取网格区块的显式选列。
     */
    private List<String> resolveListGridFieldRefs(LowcodePageSchema pageSchema, String zoneKey) {
        List<String> fromListGrid = extractGridFieldRefs(pageSchema == null ? null : pageSchema.getListGridLayout(), zoneKey);
        if (!fromListGrid.isEmpty()) {
            return fromListGrid;
        }
        // 兼容仅写在 pages[list].gridLayout 的草稿
        if (pageSchema == null || pageSchema.getPages() == null) {
            return List.of();
        }
        for (Map<String, Object> page : pageSchema.getPages()) {
            if (page == null || !"list".equals(text(page.get("pageKey")))) {
                continue;
            }
            Object grid = page.get("gridLayout");
            if (!(grid instanceof Map<?, ?> gridMap)) {
                continue;
            }
            Map<String, Object> layout = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : gridMap.entrySet()) {
                if (entry.getKey() != null) {
                    layout.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            List<String> fromPage = extractGridFieldRefs(layout, zoneKey);
            if (!fromPage.isEmpty()) {
                return fromPage;
            }
        }
        return List.of();
    }

    private List<String> extractGridFieldRefs(Map<String, Object> gridLayout, String zoneKey) {
        if (gridLayout == null || gridLayout.isEmpty()) {
            return List.of();
        }
        Object itemsValue = gridLayout.get("items");
        if (!(itemsValue instanceof List<?> items)) {
            return List.of();
        }
        for (String blockType : runtimeSettingBlockTypes(zoneKey)) {
            for (Object itemValue : items) {
                if (!(itemValue instanceof Map<?, ?> item)
                        || !blockType.equals(text(item.get("blockType")))) {
                    continue;
                }
                Object refsValue = item.get("fieldRefs");
                if ("search".equals(zoneKey) && "AiCrudPage".equals(blockType)) {
                    Object propsValue = item.get("props");
                    if (propsValue instanceof Map<?, ?> props && props.containsKey("searchFieldRefs")) {
                        refsValue = props.get("searchFieldRefs");
                    }
                }
                if (!(refsValue instanceof List<?> refs)) {
                    continue;
                }
                return refs.stream()
                        .map(this::text)
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .toList();
            }
        }
        return List.of();
    }

    private Map<String, LowcodeFieldSchema> buildRuntimeFieldMap(LowcodeModelSchema modelSchema,
                                                                  LowcodePageSchema pageSchema) {
        Map<String, LowcodeFieldSchema> fieldMap = modelSchema.getFields().stream()
                .collect(Collectors.toMap(
                        LowcodeFieldSchema::getField,
                        field -> field,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (pageSchema == null || pageSchema.getModelRefs() == null) {
            return fieldMap;
        }
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || ref.getFields() == null) {
                continue;
            }
            for (Map<String, Object> source : ref.getFields()) {
                LowcodeFieldSchema field = RuntimePageRefFieldFactory.build(ref, source);
                if (field == null) {
                    continue;
                }
                fieldMap.putIfAbsent(field.getField(), field);
            }
        }
        return fieldMap;
    }

    private LowcodeFieldSchema resolveRuntimeField(Map<String, LowcodeFieldSchema> fieldMap, String ref) {
        if (StringUtils.isBlank(ref) || fieldMap == null || fieldMap.isEmpty()) {
            return null;
        }
        LowcodeFieldSchema field = fieldMap.get(ref);
        if (field != null) {
            return field;
        }
        for (Map.Entry<String, LowcodeFieldSchema> entry : fieldMap.entrySet()) {
            if (ref.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolveTableColumnTitle(LowcodeFieldSchema field,
                                           Map<String, Object> pageSetting,
                                           LowcodePageSchema pageSchema) {
        String title = StringUtils.firstNonBlank(
                text(pageSetting.get("title")),
                text(pageSetting.get("label")),
                field.getLabel(),
                field.getField());
        return RuntimePageRefFieldFactory.stripChildModelNamePrefix(
                title, resolveChildModelName(pageSchema, field.getField()));
    }

    private String resolveChildModelName(LowcodePageSchema pageSchema, String fieldName) {
        if (pageSchema == null || pageSchema.getModelRefs() == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || ref.getFields() == null) {
                continue;
            }
            for (Map<String, Object> source : ref.getFields()) {
                String sourceField = StringUtils.defaultIfBlank(text(source.get("sourceField")), text(source.get("field")));
                String fieldRef = StringUtils.defaultIfBlank(text(source.get("fieldRef")),
                        safeKey(ref.getModelCode()) + "__" + sourceField);
                if (fieldName.equals(fieldRef)) {
                    return ref.getModelName();
                }
            }
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean booleanWithDefault(Object value, boolean defaultValue) {
        Boolean bool = booleanValue(value);
        return bool == null ? defaultValue : bool;
    }

    private boolean readDesignerLayoutFlag(Object formDesignerSchema, String key) {
        Object schema = formDesignerSchema;
        if (schema instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                schema = objectMapper.readValue(text, Map.class);
            } catch (Exception ignored) {
                return false;
            }
        }
        if (!(schema instanceof Map<?, ?> schemaMap)) {
            return false;
        }
        Object layout = schemaMap.get("layout");
        if (!(layout instanceof Map<?, ?> layoutMap)) {
            return false;
        }
        return Boolean.TRUE.equals(layoutMap.get(key));
    }

    private boolean isZoneFieldAllowed(LowcodeFieldSchema field,
                                       String zoneKey,
                                       Predicate<LowcodeFieldSchema> fallbackPredicate) {
        if (!isActiveField(field)) {
            return false;
        }
        if ("search".equals(zoneKey)) {
            return !isSystemField(field);
        }
        if ("edit".equals(zoneKey)) {
            return !isSystemField(field)
                    && !Boolean.TRUE.equals(field.getReadonly())
                    && fallbackPredicate.test(field);
        }
        return fallbackPredicate.test(field);
    }

    private boolean isEditFieldVisibleAtDesignTime(LowcodePageSchema pageSchema,
                                                   LowcodeFieldSchema field) {
        if (field == null || Boolean.TRUE.equals(field.getSystemField())
                || Boolean.TRUE.equals(field.getReadonly())) {
            return false;
        }
        if (field.getFormVisible() == null || Boolean.TRUE.equals(field.getFormVisible())) {
            return true;
        }
        Map<String, Object> setting = resolveEditFieldSetting(pageSchema, field.getField());
        return containsVisibilityRuntimeRules(setting.get("runtimeRules"))
                || containsVisibilityRuntimeRules(mapValue(setting.get("props")).get("runtimeRules"));
    }

    private boolean containsVisibilityRuntimeRules(Object value) {
        if (!(value instanceof List<?> rules)) {
            return false;
        }
        return rules.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> (Map<?, ?>) item)
                .anyMatch(rule -> {
                    Object effectValue = rule.get("effect");
                    if (effectValue instanceof Map<?, ?> effect) {
                        return effect.containsKey("visible") || effect.containsKey("hidden");
                    }
                    return rule.containsKey("visible") || rule.containsKey("hidden");
                });
    }

    private boolean isActiveField(LowcodeFieldSchema field) {
        String status = StringUtils.defaultString(field == null ? null : field.getFieldStatus());
        return !"DISABLED".equalsIgnoreCase(status) && !"HIDDEN".equalsIgnoreCase(status);
    }

    private Integer integerValue(Object value) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int intValue(Object value, int defaultValue) {
        Integer parsed = integerValue(value);
        return parsed == null ? defaultValue : parsed;
    }

    private String safeKey(String value) {
        String key = StringUtils.defaultIfBlank(value, "model").replaceAll("[^A-Za-z0-9_]", "_");
        return StringUtils.defaultIfBlank(key, "model");
    }

    private String snakeToCamel(String value) {
        if (StringUtils.isBlank(value)) {
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

    private LowcodePageZone findZone(LowcodePageSchema pageSchema, String zoneKey) {
        if (pageSchema == null || pageSchema.getZones() == null) {
            return null;
        }
        return pageSchema.getZones().stream()
                .filter(zone -> zoneKey.equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
    }

    private void copyOption(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private String resolveSearchComponentType(LowcodeFieldSchema field, String queryType) {
        return resolveSearchComponentType(field, queryType, Map.of());
    }

    private String resolveSearchComponentType(LowcodeFieldSchema field, String queryType, Map<String, Object> pageSetting) {
        if (hasRecordSelectorConfig(field, pageSetting)) {
            return "recordSelector";
        }
        // 查询组件与表单字段保持一致：字段已有明确 UI 类型时，忽略 searchFieldSettings 里的旧覆盖
        String fieldComponentType = normalizeEditComponentType(StringUtils.defaultIfBlank(field.getComponentType(), ""));
        if (isPreferredSearchFieldComponent(fieldComponentType)) {
            return resolveSearchComponentTypeFromField(field, queryType, fieldComponentType);
        }
        String configuredType = StringUtils.defaultIfBlank(text(pageSetting.get("componentType")), text(pageSetting.get("type")));
        if (StringUtils.isNotBlank(configuredType)) {
            String normalizedConfigured = normalizeEditComponentType(configuredType);
            if (!shouldIgnoreConfiguredSearchComponent(normalizedConfigured, field)) {
                return normalizedConfigured;
            }
        }
        String componentType = StringUtils.defaultIfBlank(field.getComponentType(), field.getDataType());
        componentType = StringUtils.defaultIfBlank(componentType, "input");
        componentType = normalizeEditComponentType(componentType);
        return resolveSearchComponentTypeFromField(field, queryType, componentType);
    }

    private String resolveSearchComponentTypeFromField(LowcodeFieldSchema field, String queryType, String componentType) {
        if (isBusinessSelectComponent(componentType)) {
            return componentType;
        }
        if (StringUtils.isNotBlank(field.getDictType())) {
            return "select";
        }
        if ("between".equals(queryType)) {
            if ("datetime".equals(componentType)) {
                return "datetimerange";
            }
            if ("date".equals(componentType)) {
                return "daterange";
            }
            if ("time".equals(componentType)) {
                return "timerange";
            }
        }
        if (LowcodeComponentCatalog.isFieldComponent(componentType)) {
            return componentType;
        }
        if ("number".equals(componentType)) {
            return "number";
        }
        if ("date".equals(componentType)) {
            return "date";
        }
        if ("datetime".equals(componentType)) {
            return "datetime";
        }
        if ("time".equals(componentType)) {
            return "time";
        }
        if ("treeSelect".equals(componentType)) {
            return "treeSelect";
        }
        if ("cascader".equals(componentType)) {
            return "cascader";
        }
        return "input";
    }

    private boolean isPreferredSearchFieldComponent(String componentType) {
        return Set.of(
                "treeSelect", "orgTreeSelect", "regionTreeSelect", "cascader", "userSelect",
                "select", "dictSelect", "radio", "checkbox", "switch",
                "date", "datetime", "time", "textarea", "number", "input"
        ).contains(componentType);
    }

    /**
     * 页面 searchFieldSettings 把树形字段误配成 number/input 时忽略该配置，回落到字段自身组件类型。
     */
    private boolean shouldIgnoreConfiguredSearchComponent(String configuredType, LowcodeFieldSchema field) {
        if (field == null || StringUtils.isBlank(configuredType)) {
            return false;
        }
        String fieldType = normalizeEditComponentType(StringUtils.defaultIfBlank(field.getComponentType(), ""));
        if (!Set.of("treeSelect", "orgTreeSelect", "regionTreeSelect", "cascader", "userSelect").contains(fieldType)) {
            return false;
        }
        return Set.of("input", "number", "textarea").contains(configuredType);
    }

    private String resolveEditComponentType(LowcodeFieldSchema field) {
        if (hasRecordSelectorConfig(field, Map.of())) {
            return "recordSelector";
        }
        return normalizeEditComponentType(StringUtils.defaultIfBlank(field.getComponentType(), "input"));
    }

    private String resolveEditComponentType(LowcodeFieldSchema field, Map<String, Object> pageSetting) {
        if (hasRecordSelectorConfig(field, pageSetting)) {
            return "recordSelector";
        }
        String componentType = StringUtils.defaultIfBlank(text(pageSetting.get("componentType")), text(pageSetting.get("type")));
        componentType = StringUtils.defaultIfBlank(componentType, field.getComponentType());
        return normalizeEditComponentType(StringUtils.defaultIfBlank(componentType, "input"));
    }

    private boolean hasRecordSelectorConfig(LowcodeFieldSchema field, Map<String, Object> pageSetting) {
        // 下拉模式（objectReference 组件）的 basicProps.recordSelector 只承载搜索字段/过滤参数等高级配置，不触发弹窗渲染；
        // 页面级显式配置的 selector 仍然优先，支持单页覆盖为弹窗选择。
        if (field != null && field.getBasicProps() != null && !"objectReference".equals(field.getComponentType())) {
            Object selector = firstPresent(
                    field.getBasicProps().get("recordSelector"),
                    field.getBasicProps().get("recordSelectorConfig"),
                    field.getBasicProps().get("selector"),
                    field.getBasicProps().get("selectorConfig"));
            if (selector instanceof Map<?, ?> map && !map.isEmpty()) {
                return true;
            }
        }
        Object props = pageSetting == null ? null : pageSetting.get("props");
        if (props instanceof Map<?, ?> propsMap) {
            Object selector = firstPresent(
                    propsMap.get("recordSelector"),
                    propsMap.get("recordSelectorConfig"),
                    propsMap.get("selector"),
                    propsMap.get("selectorConfig"));
            return selector instanceof Map<?, ?> map && !map.isEmpty();
        }
        return false;
    }

    private String normalizeEditComponentType(String componentType) {
        return switch (StringUtils.defaultString(componentType)) {
            case "inputNumber", "input-number", "inputnumber", "integer", "money" -> "number";
            case "orgSelect", "organizationSelect", "departmentSelect", "deptSelect",
                    "departmentTreeSelect", "deptTreeSelect", "elTreeSelect", "orgName", "deptName",
                    "forgeOrgTreeSelect" -> "orgTreeSelect";
            case "userPicker", "user", "userName", "sysUserSelect", "forgeUserSelect" -> "userSelect";
            default -> componentType;
        };
    }

    private String normalizeRuntimeFormSize(String value) {
        String size = StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
        if ("default".equals(size) || "medium".equals(size)) {
            return "medium";
        }
        return Set.of("small", "large").contains(size) ? size : "medium";
    }

    private void applySelectionLabelProps(Map<String, Object> props, String fieldName, String componentType) {
        if (StringUtils.isBlank(fieldName)
                || (!"orgTreeSelect".equals(componentType) && !"userSelect".equals(componentType))) {
            return;
        }
        String labelField = fieldName + "Name";
        if (StringUtils.isBlank(text(props.get("labelValueField")))
                || fieldName.equals(text(props.get("labelValueField")))) {
            props.put("labelValueField", labelField);
        }
        if (StringUtils.isBlank(text(props.get("targetField")))
                || fieldName.equals(text(props.get("targetField")))) {
            props.put("targetField", labelField);
        }
    }

    private void ensureDynamicOptionSourceLabelValueField(Map<String, Object> props,
                                                          String fieldName,
                                                          String componentType) {
        if (props == null || StringUtils.isBlank(fieldName) || StringUtils.isNotBlank(text(props.get("labelValueField")))) {
            return;
        }
        if (!Set.of("select", "radio", "radioButton", "checkbox", "cascader", "treeSelect", "transfer")
                .contains(StringUtils.defaultString(componentType))) {
            return;
        }
        Object source = props.get("optionSource");
        if (!(source instanceof Map<?, ?> map)) {
            return;
        }
        String type = text(map.get("type"));
        if (StringUtils.isBlank(type) || "STATIC".equalsIgnoreCase(type.replace('-', '_'))) {
            return;
        }
        props.put("labelValueField", fieldName + "Name");
    }

    private boolean isBusinessSelectComponent(String componentType) {
        return "dictSelect".equals(componentType)
                || "treeSelect".equals(componentType)
                || "orgTreeSelect".equals(componentType)
                || "userSelect".equals(componentType)
                || "regionTreeSelect".equals(componentType)
                || "cascader".equals(componentType)
                || "objectReference".equals(componentType)
                || "recordSelector".equals(componentType);
    }

    private String buildPlaceholder(String componentType, String label) {
        if ("select".equals(componentType) || "radio".equals(componentType) || "radioButton".equals(componentType)
                || "checkbox".equals(componentType) || "transfer".equals(componentType) || "customSelect".equals(componentType)
                || "date".equals(componentType) || "datetime".equals(componentType) || "time".equals(componentType)
                || "daterange".equals(componentType) || "datetimerange".equals(componentType) || "timerange".equals(componentType)
                || "dictSelect".equals(componentType) || "treeSelect".equals(componentType) || "orgTreeSelect".equals(componentType)
                || "userSelect".equals(componentType) || "regionTreeSelect".equals(componentType) || "cascader".equals(componentType)
                || "objectReference".equals(componentType) || "recordSelector".equals(componentType) || "fileUpload".equals(componentType)
                || "imageUpload".equals(componentType) || "upload".equals(componentType)) {
            return "请选择" + label;
        }
        return "请输入" + label;
    }

    private boolean isTextComponent(String componentType) {
        return "input".equals(componentType) || "textarea".equals(componentType);
    }
}
