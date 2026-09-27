package com.mdframe.forge.plugin.generator.codegen;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.GenTable;
import com.mdframe.forge.plugin.generator.domain.entity.GenTableColumn;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.util.GenUtils;
import com.mdframe.forge.plugin.generator.util.LowcodeCodegenOptionUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 关联表、主子表与树代码生成元数据规划器。
 */
@RequiredArgsConstructor
final class VelocityRelatedTablePlanner {

    private static final Set<String> MASTER_DETAIL_RELATION_TYPES = Set.of(
        "CHILD_LIST", "DETAIL", "ONE_TO_MANY");

    private final VelocityCodegenStrategy support;

    List<VelocityCodegenStrategy.RelatedTableMeta> buildRelatedTables(
        AiCrudConfig config,
        LowcodePageSchema pageSchema,
        GenTable mainTable,
        String moduleName,
        String businessPath) {
        if (pageSchema == null || pageSchema.getModelRefs() == null
            || pageSchema.getModelRefs().isEmpty()) {
            return new ArrayList<>();
        }
        List<VelocityCodegenStrategy.RelatedTableMeta> result = new ArrayList<>();
        Set<String> seenTables = new LinkedHashSet<>();
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary())
                || StringUtils.isBlank(ref.getTableName())) {
                continue;
            }
            if (StringUtils.equals(ref.getTableName(), config.getTableName())
                || !seenTables.add(ref.getTableName())) {
                continue;
            }
            GenTable table = relatedTable(config, mainTable, ref, moduleName, businessPath);
            VelocityCodegenStrategy.RelatedTableMeta meta = new VelocityCodegenStrategy.RelatedTableMeta();
            meta.setModelCode(ref.getModelCode());
            meta.setKey(StringUtils.defaultIfBlank(ref.getModelCode(), table.getClassName()));
            meta.setModelName(ref.getModelName());
            meta.setTableName(ref.getTableName());
            meta.setClassName(table.getClassName());
            meta.setVariableName(StringUtils.uncapitalize(table.getClassName()));
            meta.setMapperVarName(StringUtils.uncapitalize(table.getClassName()) + "Mapper");
            meta.setTable(table);
            meta.setColumns(table.getColumns());
            meta.setPkColumn(table.getPkColumn());
            meta.setHasLogicDelete(support.hasColumn(table.getColumns(), "del_flag"));
            meta.setUniqueDeleteMarker(meta.getPkColumn() != null
                && "Long".equals(meta.getPkColumn().getJavaType())
                && table.getColumns().stream().anyMatch(column -> "del_flag".equals(column.getColumnName())
                && "Long".equals(column.getJavaType())));
            result.add(meta);
        }
        return result;
    }

    List<VelocityCodegenStrategy.RelatedTableMeta> buildMasterDetailChildren(
        Map<String, Object> masterDetailConfig,
        List<VelocityCodegenStrategy.RelatedTableMeta> relatedTables,
        GenTable mainTable,
        LowcodePageSchema pageSchema) {
        List<VelocityCodegenStrategy.RelatedTableMeta> result = buildConfiguredMasterDetailChildren(
            masterDetailConfig, relatedTables, mainTable);
        appendPageSchemaMasterDetailChildren(result, relatedTables, mainTable, pageSchema);
        return result;
    }

    VelocityCodegenStrategy.TreeCodegenMeta buildTreeMeta(
        GenTable mainTable,
        List<VelocityCodegenStrategy.RelatedTableMeta> relatedTables,
        Map<String, Object> treeConfig) {
        if (treeConfig == null || treeConfig.isEmpty()) {
            return null;
        }
        String sourceTableName = support.text(treeConfig.get("sourceTableName"));
        String sourceModelCode = support.text(treeConfig.get("sourceModelCode"));
        VelocityCodegenStrategy.RelatedTableMeta sourceMeta = findRelatedTable(
            relatedTables, sourceModelCode, sourceTableName);
        boolean separateSource = sourceMeta != null
            && !StringUtils.equals(sourceMeta.getTableName(), mainTable.getTableName());
        List<GenTableColumn> sourceColumns = separateSource
            ? sourceMeta.getColumns() : mainTable.getColumns();

        VelocityCodegenStrategy.TreeCodegenMeta meta = new VelocityCodegenStrategy.TreeCodegenMeta();
        meta.setSeparateSource(separateSource);
        meta.setSourceModelCode(sourceModelCode);
        meta.setSourceTableName(StringUtils.defaultIfBlank(sourceTableName, mainTable.getTableName()));
        meta.setClassName(separateSource ? sourceMeta.getClassName() : mainTable.getClassName());
        meta.setMapperVarName(separateSource ? sourceMeta.getMapperVarName()
            : StringUtils.uncapitalize(mainTable.getClassName()) + "Mapper");
        meta.setKeyField(normalizeField(treeConfig, "keyField", "id"));
        meta.setParentField(normalizeField(treeConfig, "parentField", "parentId"));
        meta.setLabelField(normalizeField(treeConfig, "labelField", "name"));
        meta.setFilterField(normalizeField(treeConfig, "filterField", meta.getParentField()));
        meta.setTargetField(normalizeField(treeConfig, "targetField", meta.getKeyField()));
        meta.setChildrenField(normalizeField(treeConfig, "childrenField", "children"));
        meta.setLoadMode(StringUtils.defaultIfBlank(support.text(treeConfig.get("loadMode")), "full"));
        applyTreeDerivedFields(meta, sourceColumns);
        if (sourceMeta != null) {
            sourceMeta.setTreeSource(true);
            sourceMeta.setTreeChildrenField(meta.getChildrenField());
        }
        return meta;
    }

    List<VelocityCodegenStrategy.RelatedTableMeta> resolveInjectedRelatedTables(
        VelocityCodegenStrategy.TreeCodegenMeta treeMeta,
        List<VelocityCodegenStrategy.RelatedTableMeta> relatedTables,
        List<VelocityCodegenStrategy.RelatedTableMeta> masterDetailChildren) {
        Map<String, VelocityCodegenStrategy.RelatedTableMeta> result = new LinkedHashMap<>();
        if (treeMeta != null && treeMeta.isSeparateSource()) {
            for (VelocityCodegenStrategy.RelatedTableMeta relatedTable : relatedTables) {
                if (StringUtils.equals(relatedTable.getClassName(), treeMeta.getClassName())) {
                    result.put(relatedTable.getClassName(), relatedTable);
                }
            }
        }
        for (VelocityCodegenStrategy.RelatedTableMeta child : masterDetailChildren) {
            result.put(child.getClassName(), child);
        }
        return new ArrayList<>(result.values());
    }

    private GenTable relatedTable(AiCrudConfig config,
                                  GenTable mainTable,
                                  LowcodePageModelRef ref,
                                  String moduleName,
                                  String businessPath) {
        GenTable table = new GenTable();
        table.setTableName(ref.getTableName());
        table.setTableComment(StringUtils.defaultIfBlank(ref.getModelName(), ref.getTableName()));
        table.setFunctionName(table.getTableComment());
        String className = LowcodeCodegenOptionUtils.buildClassName(
            ref.getTableName(), support.readOption(config, "entityPrefix", ""),
            support.resolveStripTablePrefixes(config));
        table.setClassName(className);
        table.setBusinessName(support.resolveBusinessName(businessPath));
        table.setPackageName(mainTable.getPackageName());
        table.setModuleName(moduleName);
        table.setAuthor(mainTable.getAuthor());
        List<GenTableColumn> columns = buildColumnsFromModelRef(ref);
        table.setColumns(columns);
        table.setPkColumn(GenUtils.getPkColumn(columns));
        return table;
    }

    private List<GenTableColumn> buildColumnsFromModelRef(LowcodePageModelRef ref) {
        if (ref.getFields() == null || ref.getFields().isEmpty()) {
            return new ArrayList<>();
        }
        List<GenTableColumn> columns = new ArrayList<>();
        int sort = 0;
        for (Map<String, Object> fieldMap : ref.getFields()) {
            String sourceField = StringUtils.firstNonBlank(
                support.text(fieldMap.get("sourceField")), support.text(fieldMap.get("field")),
                support.text(fieldMap.get("fieldRef")));
            if (StringUtils.isBlank(sourceField)) {
                continue;
            }
            String columnName = StringUtils.defaultIfBlank(
                support.text(fieldMap.get("columnName")), support.camelToSnake(sourceField));
            GenTableColumn column = new GenTableColumn();
            column.setColumnName(columnName);
            column.setColumnComment(StringUtils.firstNonBlank(
                support.text(fieldMap.get("rawLabel")), support.text(fieldMap.get("label")), sourceField));
            LowcodeFieldSchema lowcodeField = new LowcodeFieldSchema();
            lowcodeField.setDataType(support.text(fieldMap.get("dataType")));
            lowcodeField.setLength(support.integerValue(fieldMap.get("length")));
            lowcodeField.setPrecision(support.integerValue(fieldMap.get("precision")));
            column.setColumnType(support.toColumnType(lowcodeField));
            column.setJavaType(support.toJavaType(support.text(fieldMap.get("dataType"))));
            column.setJavaField(sourceField);
            boolean primaryKey = support.booleanValue(fieldMap.get("primaryKey"))
                || "id".equals(sourceField) || "id".equals(columnName);
            boolean readonly = support.booleanValue(fieldMap.get("readonly"));
            column.setIsPk(primaryKey ? 1 : 0);
            column.setIsIncrement(support.booleanValue(fieldMap.get("autoIncrement")) || primaryKey ? 1 : 0);
            column.setIsRequired(support.booleanValue(fieldMap.get("required")) ? 1 : 0);
            column.setIsInsert(!primaryKey && !readonly ? 1 : 0);
            column.setIsEdit(!primaryKey && !readonly ? 1 : 0);
            column.setIsList(support.booleanValueDefault(fieldMap.get("listVisible"), true) ? 1 : 0);
            column.setIsQuery(support.booleanValue(fieldMap.get("searchable")) ? 1 : 0);
            column.setQueryType(StringUtils.defaultIfBlank(
                support.text(fieldMap.get("queryType")), "EQ").toUpperCase(Locale.ROOT));
            column.setHtmlType(support.toHtmlType(
                support.text(fieldMap.get("componentType")), support.text(fieldMap.get("dataType"))));
            column.setDictType(StringUtils.trimToNull(support.text(fieldMap.get("dictType"))));
            column.setDesensitizeType(support.normalizeDesensitizeType(
                support.text(fieldMap.get("sensitiveType"))));
            column.setSort(sort++);
            columns.add(column);
        }
        return columns;
    }

    @SuppressWarnings("unchecked")
    private List<VelocityCodegenStrategy.RelatedTableMeta> buildConfiguredMasterDetailChildren(
        Map<String, Object> masterDetailConfig,
        List<VelocityCodegenStrategy.RelatedTableMeta> relatedTables,
        GenTable mainTable) {
        Object childrenObj = masterDetailConfig.get("children");
        if (!(childrenObj instanceof List<?> childrenList)) {
            return new ArrayList<>();
        }
        List<VelocityCodegenStrategy.RelatedTableMeta> result = new ArrayList<>();
        for (Object childObj : childrenList) {
            if (!(childObj instanceof Map<?, ?> rawChild)) {
                continue;
            }
            Map<String, Object> child = (Map<String, Object>) rawChild;
            VelocityCodegenStrategy.RelatedTableMeta meta = findRelatedTable(
                relatedTables, support.text(child.get("modelCode")), support.text(child.get("tableName")));
            if (meta == null) {
                continue;
            }
            configureMasterDetailChild(meta,
                StringUtils.firstNonBlank(support.text(child.get("key")),
                    support.text(child.get("modelCode")), meta.getKey()),
                StringUtils.defaultIfBlank(support.text(child.get("sourceField")), "parentId"),
                StringUtils.defaultIfBlank(support.text(child.get("targetField")),
                    defaultMainKeyField(mainTable)), mainTable);
            result.add(meta);
        }
        return result;
    }

    private void appendPageSchemaMasterDetailChildren(
        List<VelocityCodegenStrategy.RelatedTableMeta> result,
        List<VelocityCodegenStrategy.RelatedTableMeta> relatedTables,
        GenTable mainTable,
        LowcodePageSchema pageSchema) {
        if (pageSchema == null || pageSchema.getModelRefs() == null
            || pageSchema.getModelRefs().isEmpty()) {
            return;
        }
        LowcodePageModelRef primaryRef = resolvePrimaryModelRef(pageSchema);
        String primaryModelCode = StringUtils.firstNonBlank(
            pageSchema.getPrimaryModelCode(), primaryRef == null ? null : primaryRef.getModelCode());
        for (LowcodePageModelRef childRef : pageSchema.getModelRefs()) {
            if (childRef == null || Boolean.TRUE.equals(childRef.getPrimary())) {
                continue;
            }
            VelocityCodegenStrategy.RelatedTableMeta meta = findRelatedTable(
                relatedTables, childRef.getModelCode(), childRef.getTableName());
            if (meta == null || result.contains(meta)) {
                continue;
            }
            MasterDetailFieldPair fieldPair = resolveMasterDetailFieldPair(
                primaryModelCode, primaryRef, childRef);
            if (fieldPair == null) {
                continue;
            }
            configureMasterDetailChild(meta,
                StringUtils.defaultIfBlank(childRef.getModelCode(), meta.getKey()),
                fieldPair.getChildField(), fieldPair.getMainField(), mainTable);
            result.add(meta);
        }
    }

    private LowcodePageModelRef resolvePrimaryModelRef(LowcodePageSchema pageSchema) {
        String primaryModelCode = pageSchema.getPrimaryModelCode();
        return pageSchema.getModelRefs().stream()
            .filter(Objects::nonNull)
            .filter(ref -> Boolean.TRUE.equals(ref.getPrimary())
                || StringUtils.equals(primaryModelCode, ref.getModelCode()))
            .findFirst()
            .orElse(null);
    }

    private MasterDetailFieldPair resolveMasterDetailFieldPair(String primaryModelCode,
                                                               LowcodePageModelRef primaryRef,
                                                               LowcodePageModelRef childRef) {
        if (StringUtils.isBlank(primaryModelCode) || StringUtils.isBlank(childRef.getModelCode())) {
            return null;
        }
        if (childRef.getRelations() != null) {
            for (LowcodeRelationSchema relation : childRef.getRelations()) {
                if (isMasterDetailRelation(relation)
                    && StringUtils.equals(primaryModelCode, relation.getTargetObjectCode())
                    && StringUtils.isNoneBlank(relation.getSourceField(), relation.getTargetField())) {
                    return new MasterDetailFieldPair(relation.getSourceField(), relation.getTargetField());
                }
            }
        }
        if (primaryRef != null && primaryRef.getRelations() != null) {
            for (LowcodeRelationSchema relation : primaryRef.getRelations()) {
                if (isMasterDetailRelation(relation)
                    && StringUtils.equals(childRef.getModelCode(), relation.getTargetObjectCode())
                    && StringUtils.isNoneBlank(relation.getSourceField(), relation.getTargetField())) {
                    return new MasterDetailFieldPair(relation.getTargetField(), relation.getSourceField());
                }
            }
        }
        return null;
    }

    private boolean isMasterDetailRelation(LowcodeRelationSchema relation) {
        return relation != null && MASTER_DETAIL_RELATION_TYPES.contains(
            StringUtils.defaultString(relation.getRelationType()).toUpperCase(Locale.ROOT));
    }

    private void configureMasterDetailChild(VelocityCodegenStrategy.RelatedTableMeta meta,
                                            String childKey,
                                            String childFkField,
                                            String mainField,
                                            GenTable mainTable) {
        meta.setMasterDetailChild(true);
        meta.setChildKey(StringUtils.defaultIfBlank(childKey, meta.getKey()));
        meta.setChildFkField(support.normalizeJavaField(childFkField));
        meta.setChildFkFieldCap(support.capJavaField(meta.getChildFkField()));
        meta.setChildFkColumn(support.resolveColumnName(meta.getColumns(), meta.getChildFkField()));
        meta.setMainField(support.normalizeJavaField(
            StringUtils.defaultIfBlank(mainField, defaultMainKeyField(mainTable))));
        meta.setMainFieldCap(support.capJavaField(meta.getMainField()));
        meta.setMainColumn(support.resolveColumnName(mainTable.getColumns(), meta.getMainField()));
    }

    private String defaultMainKeyField(GenTable mainTable) {
        return mainTable.getPkColumn() == null ? "id" : mainTable.getPkColumn().getJavaField();
    }

    private VelocityCodegenStrategy.RelatedTableMeta findRelatedTable(
        List<VelocityCodegenStrategy.RelatedTableMeta> relatedTables,
        String modelCode,
        String tableName) {
        if (relatedTables == null || relatedTables.isEmpty()) {
            return null;
        }
        for (VelocityCodegenStrategy.RelatedTableMeta relatedTable : relatedTables) {
            if (StringUtils.isNotBlank(modelCode)
                && StringUtils.equals(modelCode, relatedTable.getModelCode())) {
                return relatedTable;
            }
            if (StringUtils.isNotBlank(tableName)
                && StringUtils.equals(tableName, relatedTable.getTableName())) {
                return relatedTable;
            }
        }
        return null;
    }

    private String normalizeField(Map<String, Object> treeConfig, String key, String fallback) {
        return support.normalizeJavaField(StringUtils.defaultIfBlank(
            support.text(treeConfig.get(key)), fallback));
    }

    private void applyTreeDerivedFields(VelocityCodegenStrategy.TreeCodegenMeta meta,
                                        List<GenTableColumn> sourceColumns) {
        meta.setKeyFieldCap(support.capJavaField(meta.getKeyField()));
        meta.setParentFieldCap(support.capJavaField(meta.getParentField()));
        meta.setLabelFieldCap(support.capJavaField(meta.getLabelField()));
        meta.setFilterFieldCap(support.capJavaField(meta.getFilterField()));
        meta.setTargetFieldCap(support.capJavaField(meta.getTargetField()));
        meta.setChildrenFieldCap(support.capJavaField(meta.getChildrenField()));
        meta.setKeyColumn(support.resolveColumnName(sourceColumns, meta.getKeyField()));
        meta.setParentColumn(support.resolveColumnName(sourceColumns, meta.getParentField()));
        meta.setTargetColumn(support.resolveColumnName(sourceColumns, meta.getTargetField()));
    }

    @Data
    private static class MasterDetailFieldPair {
        private final String childField;
        private final String mainField;
    }
}
