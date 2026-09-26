package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.CustomQueryExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePrimaryKeyStrategy;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.domain.PageQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** 编译低代码模型关系，并生成分页、自定义查询和导出的 Join 查询计划。 */
@Slf4j
final class DynamicCrudRuntimeRelationPlanner {

    private static final String MASTER_DETAIL_LAYOUT = "master-detail-crud";

    private final DynamicCrudRepository repository;
    private final ObjectMapper objectMapper;

    DynamicCrudRuntimeRelationPlanner(DynamicCrudRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    RuntimeJoinContext buildRuntimeJoinContext(
            AiCrudConfig config,
            BiFunction<AiCrudConfig, String, Map<String, String>> columnMappingResolver,
            BiFunction<AiCrudConfig, RuntimeChildRelation, RuntimeChildRelation> readableRelationResolver) {
        if (!"LOWCODE".equals(config.getBuildMode())
                || StringUtils.isBlank(config.getPageSchema())
                || StringUtils.isBlank(config.getModelSchema())) {
            return null;
        }
        LowcodePageSchema pageSchema = readPageSchema(config);
        if (pageSchema == null || pageSchema.getModelRefs() == null || pageSchema.getModelRefs().size() <= 1) {
            return null;
        }
        LowcodeModelSchema modelSchema = readModelSchema(config);
        if (modelSchema == null) {
            return null;
        }

        LowcodePageModelRef primaryRef = pageSchema.getModelRefs().stream()
                .filter(ref -> Boolean.TRUE.equals(ref.getPrimary()))
                .findFirst()
                .orElse(pageSchema.getModelRefs().get(0));
        String primaryModelCode = StringUtils.defaultIfBlank(pageSchema.getPrimaryModelCode(), primaryRef.getModelCode());
        if (StringUtils.isBlank(primaryModelCode)) {
            primaryModelCode = modelSchema.getObject() == null ? null : modelSchema.getObject().getCode();
        }
        if (StringUtils.isBlank(primaryModelCode)) {
            return null;
        }

        Map<String, RuntimeFieldRef> fields = new LinkedHashMap<>();
        Map<String, String> fieldColumnMapping = new LinkedHashMap<>();
        List<DynamicCrudRepository.JoinField> selectFields = new ArrayList<>();
        Map<String, String> relationDisplayAliases = new LinkedHashMap<>();

        addPrimaryRuntimeFields(config.getTableName(), primaryModelCode, fields, fieldColumnMapping, selectFields);

        List<RuntimeChildRelation> childRelations = new ArrayList<>();
        List<DynamicCrudRepository.JoinSpec> joins = new ArrayList<>();
        List<LowcodeRelationSchema> primaryRelations = mergeRelations(modelSchema.getRelations(), primaryRef.getRelations());
        Map<String, String> primaryColumnMapping = columnMappingResolver.apply(config, config.getTableName());
        int aliasIndex = 1;
        for (LowcodePageModelRef ref : pageSchema.getModelRefs()) {
            if (ref == null || Boolean.TRUE.equals(ref.getPrimary()) || StringUtils.isBlank(ref.getModelCode())) {
                continue;
            }
            if (StringUtils.isBlank(ref.getTableName()) || !repository.tableExists(ref.getTableName())) {
                log.warn("[DynamicCrudRuntimeRelationPlanner] 引用模型缺少可用表名，跳过左连接, configKey={}, modelCode={}",
                        config.getConfigKey(), ref.getModelCode());
                continue;
            }
            RuntimeChildRelation relation = buildChildRelation(primaryModelCode, ref, primaryRelations,
                    "t" + aliasIndex, primaryColumnMapping, fields, fieldColumnMapping, selectFields);
            if (relation == null) {
                log.warn("[DynamicCrudRuntimeRelationPlanner] 未找到引用模型关联关系，跳过左连接, configKey={}, modelCode={}",
                        config.getConfigKey(), ref.getModelCode());
                continue;
            }
            relation = readableRelationResolver.apply(config, relation);
            childRelations.add(relation);
            joins.add(new DynamicCrudRepository.JoinSpec(
                    relation.tableName(), relation.tableAlias(), relation.childFkColumn(), relation.mainColumn()));
            addRelationDisplayField(modelSchema, primaryRelations, ref, relation,
                    fields, fieldColumnMapping, selectFields, relationDisplayAliases);
            aliasIndex++;
        }

        if (childRelations.isEmpty()) {
            return null;
        }
        return new RuntimeJoinContext(fields, childRelations, selectFields, joins, fieldColumnMapping, relationDisplayAliases);
    }

    boolean isMasterDetailRuntime(AiCrudConfig config) {
        return config != null && MASTER_DETAIL_LAYOUT.equals(config.getLayoutType());
    }

    void addPrimaryRuntimeFields(String tableName,
                                         String modelCode,
                                         Map<String, RuntimeFieldRef> fields,
                                         Map<String, String> fieldColumnMapping,
                                         List<DynamicCrudRepository.JoinField> selectFields) {
        List<String> columns = new ArrayList<>(repository.getTableColumns(tableName));
        columns.sort(Comparator.naturalOrder());
        for (String column : columns) {
            String fieldName = DynamicQueryGenerator.snakeToCamel(column);
            RuntimeFieldRef fieldRef = new RuntimeFieldRef(fieldName, modelCode, fieldName, column, tableName, "t0", true);
            fields.putIfAbsent(fieldName, fieldRef);
            fieldColumnMapping.put(fieldName, "t0." + column);
            selectFields.add(new DynamicCrudRepository.JoinField(fieldName, "t0", column));
        }
    }

    RuntimeChildRelation buildChildRelation(String primaryModelCode,
                                                    LowcodePageModelRef ref,
                                                    List<LowcodeRelationSchema> primaryRelations,
                                                    String tableAlias,
                                                    Map<String, String> primaryColumnMapping,
                                                    Map<String, RuntimeFieldRef> fields,
                                                    Map<String, String> fieldColumnMapping,
                                                    List<DynamicCrudRepository.JoinField> selectFields) {
        Map<String, String> childColumnMapping = repository.getColumnMapping(ref.getTableName());
        LowcodeRelationSchema relation = findRelationToPrimary(ref.getRelations(), primaryModelCode);
        boolean relationFromPrimary = false;
        if (relation == null) {
            LowcodeRelationSchema primaryRelation = findRelationFromPrimary(primaryRelations, ref.getModelCode());
            LowcodeRelationSchema inferredRelation = inferRelation(primaryModelCode, ref);
            if (shouldPreferInferredChildRelation(primaryRelation, inferredRelation, childColumnMapping)) {
                relation = inferredRelation;
            } else {
                relation = primaryRelation != null ? primaryRelation : inferredRelation;
                relationFromPrimary = primaryRelation != null && relation == primaryRelation;
            }
        }
        if (relation == null) {
            return null;
        }

        String mainField = relationFromPrimary ? relation.getSourceField() : relation.getTargetField();
        String childField = relationFromPrimary ? relation.getTargetField() : relation.getSourceField();
        String mainColumn = resolvePrimaryColumn(mainField, primaryColumnMapping);
        String childColumn = resolveChildColumn(childField, childColumnMapping);
        if (StringUtils.isBlank(mainColumn) || StringUtils.isBlank(childColumn)) {
            return null;
        }

        Map<String, RuntimeFieldRef> childFields = new LinkedHashMap<>();
        Map<String, RuntimeChildFieldRule> childFieldRules = new LinkedHashMap<>();
        for (Map<String, Object> source : ref.getFields()) {
            String sourceField = StringUtils.defaultIfBlank(text(source.get("sourceField")), text(source.get("field")));
            if (StringUtils.isBlank(sourceField)) {
                continue;
            }
            String fieldName = StringUtils.defaultIfBlank(text(source.get("fieldRef")), safeKey(ref.getModelCode()) + "__" + sourceField);
            String columnName = resolveChildColumn(StringUtils.defaultIfBlank(text(source.get("columnName")), sourceField), childColumnMapping);
            if (StringUtils.isBlank(columnName)) {
                continue;
            }
            RuntimeFieldRef fieldRef = new RuntimeFieldRef(
                    fieldName, ref.getModelCode(), sourceField, columnName, ref.getTableName(), tableAlias, false);
            fields.putIfAbsent(fieldName, fieldRef);
            childFields.put(fieldName, fieldRef);
            childFieldRules.put(fieldName, buildChildFieldRule(source, fieldRef));
            fieldColumnMapping.put(fieldName, tableAlias + "." + columnName);
            selectFields.add(new DynamicCrudRepository.JoinField(fieldName, tableAlias, columnName));
        }
        if (childFields.isEmpty()) {
            return null;
        }
        String saveMode = normalizeChildSaveMode(firstMap(ref.getProps()).get("saveMode"));
        return new RuntimeChildRelation(ref.getModelCode(), ref.getTableName(), tableAlias, childColumn, mainColumn,
                saveMode, childFields, childFieldRules);
    }

    RuntimeChildFieldRule buildChildFieldRule(Map<String, Object> source, RuntimeFieldRef fieldRef) {
        Map<String, Object> props = firstMap(source.get("props"), source.get("basicProps"));
        return new RuntimeChildFieldRule(
                fieldRef.fieldName(),
                fieldRef.sourceField(),
                fieldRef.columnName(),
                StringUtils.defaultIfBlank(text(source.get("label")), text(source.get("rawLabel"))),
                readBoolean(source.get("required"), false),
                StringUtils.defaultIfBlank(text(source.get("dataType")), text(source.get("storageType"))),
                firstInteger(source.get("length"), source.get("fieldLength"), props.get("maxlength"), props.get("maxLength")),
                firstInteger(source.get("precision"), props.get("precision")),
                firstDecimal(source.get("min"), source.get("minimum"), props.get("min")),
                firstDecimal(source.get("max"), source.get("maximum"), props.get("max"))
        );
    }

    Integer firstInteger(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            try {
                return new BigDecimal(String.valueOf(value).trim()).intValueExact();
            } catch (Exception ignored) {
                // 继续尝试下一个兼容字段。
            }
        }
        return null;
    }

    BigDecimal firstDecimal(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            BigDecimal decimal = toBigDecimal(value);
            if (decimal != null) {
                return decimal;
            }
        }
        return null;
    }

    String normalizeChildSaveMode(Object value) {
        return "merge".equalsIgnoreCase(text(value)) ? "merge" : "replace";
    }

    boolean shouldPreferInferredChildRelation(LowcodeRelationSchema primaryRelation,
                                                      LowcodeRelationSchema inferredRelation,
                                                      Map<String, String> childColumnMapping) {
        if (primaryRelation == null || inferredRelation == null) {
            return false;
        }
        String configuredChildColumn = resolveChildColumn(primaryRelation.getTargetField(), childColumnMapping);
        String inferredChildColumn = resolveChildColumn(inferredRelation.getSourceField(), childColumnMapping);
        return "id".equals(configuredChildColumn) && StringUtils.isNotBlank(inferredChildColumn)
                && !"id".equals(inferredChildColumn);
    }

    String resolvePrimaryColumn(String fieldName, Map<String, String> primaryColumnMapping) {
        if (StringUtils.isBlank(fieldName)) {
            return null;
        }
        String column = primaryColumnMapping.getOrDefault(fieldName, DynamicQueryGenerator.camelToSnake(fieldName));
        return primaryColumnMapping.containsValue(column) ? column : null;
    }

    String resolveChildColumn(String fieldName, Map<String, String> childColumnMapping) {
        if (StringUtils.isBlank(fieldName)) {
            return null;
        }
        String column = childColumnMapping.getOrDefault(fieldName, DynamicQueryGenerator.camelToSnake(fieldName));
        return childColumnMapping.containsValue(column) ? column : null;
    }

    LowcodeRelationSchema findRelationFromPrimary(List<LowcodeRelationSchema> relations, String targetModelCode) {
        if (relations == null) {
            return null;
        }
        return relations.stream()
                .filter(relation -> relation != null && targetModelCode.equals(relation.getTargetObjectCode()))
                .findFirst()
                .orElse(null);
    }

    LowcodeRelationSchema findRelationToPrimary(List<LowcodeRelationSchema> relations, String primaryModelCode) {
        if (relations == null) {
            return null;
        }
        return relations.stream()
                .filter(relation -> relation != null && primaryModelCode.equals(relation.getTargetObjectCode()))
                .findFirst()
                .orElse(null);
    }

    void addRelationDisplayField(LowcodeModelSchema modelSchema,
                                         List<LowcodeRelationSchema> primaryRelations,
                                         LowcodePageModelRef ref,
                                         RuntimeChildRelation relation,
                                         Map<String, RuntimeFieldRef> fields,
                                         Map<String, String> fieldColumnMapping,
                                         List<DynamicCrudRepository.JoinField> selectFields,
                                         Map<String, String> relationDisplayAliases) {
        if (ref == null || relation == null) {
            return;
        }
        LowcodeRelationSchema primaryRelation = findRelationFromPrimary(primaryRelations, ref.getModelCode());
        if (primaryRelation == null || !isReferenceRelation(primaryRelation)) {
            return;
        }
        String sourceField = normalizeModelFieldName(modelSchema, primaryRelation.getSourceField());
        if (StringUtils.isBlank(sourceField)) {
            return;
        }
        String displayField = resolveRelationDisplayField(ref, primaryRelation);
        if (StringUtils.isBlank(displayField)) {
            return;
        }
        String aliasField = sourceField + "Name";
        if (fields.containsKey(aliasField)) {
            relationDisplayAliases.putIfAbsent(sourceField, aliasField);
            return;
        }
        Map<String, String> childColumnMapping = repository.getColumnMapping(ref.getTableName());
        String displayColumn = resolveChildColumn(displayField, childColumnMapping);
        if (StringUtils.isBlank(displayColumn)) {
            return;
        }
        RuntimeFieldRef displayRef = new RuntimeFieldRef(
                aliasField,
                ref.getModelCode(),
                displayField,
                displayColumn,
                ref.getTableName(),
                relation.tableAlias(),
                false
        );
        fields.put(aliasField, displayRef);
        fieldColumnMapping.put(aliasField, relation.tableAlias() + "." + displayColumn);
        selectFields.add(new DynamicCrudRepository.JoinField(aliasField, relation.tableAlias(), displayColumn));
        relationDisplayAliases.put(sourceField, aliasField);
    }

    boolean isReferenceRelation(LowcodeRelationSchema relation) {
        return relation != null && "REFERENCE".equalsIgnoreCase(StringUtils.defaultString(relation.getRelationType()));
    }

    LowcodeRelationSchema inferRelation(String primaryModelCode, LowcodePageModelRef ref) {
        String expectedCamel = DynamicQueryGenerator.snakeToCamel(primaryModelCode) + "Id";
        String expectedSnake = DynamicQueryGenerator.camelToSnake(primaryModelCode) + "_id";
        for (Map<String, Object> field : ref.getFields()) {
            String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String columnName = StringUtils.defaultIfBlank(text(field.get("columnName")), sourceField);
            if (expectedCamel.equals(sourceField) || expectedSnake.equals(columnName)) {
                LowcodeRelationSchema relation = new LowcodeRelationSchema();
                relation.setRelationType("ONE_TO_MANY");
                relation.setSourceField(sourceField);
                relation.setTargetObjectCode(primaryModelCode);
                relation.setTargetField("id");
                return relation;
            }
        }
        return null;
    }

    List<LowcodeRelationSchema> mergeRelations(List<LowcodeRelationSchema> first, List<LowcodeRelationSchema> second) {
        List<LowcodeRelationSchema> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return result;
    }

    String buildJoinOrderBy(String orderByColumn, String isAsc, RuntimeJoinContext joinContext) {
        if (StringUtils.isBlank(orderByColumn)) {
            return primaryKeyOrderBy("t0", "DESC");
        }
        List<String> columns = Arrays.stream(orderByColumn.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(column -> joinContext.fieldColumnMapping().get(column))
                .filter(StringUtils::isNotBlank)
                .toList();
        if (columns.isEmpty()) {
            return primaryKeyOrderBy("t0", "DESC");
        }
        String direction = "asc".equalsIgnoreCase(isAsc) ? "ASC" : "DESC";
        return String.join(", ", columns) + " " + direction;
    }

    boolean requiresJoinedPageQuery(AiCrudConfig config,
                                            PageQuery pageQuery,
                                            Map<String, Object> searchParams,
                                            RuntimeJoinContext joinContext) {
        return containsChildField(DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper), joinContext)
                || containsRelationDisplayField(DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper), joinContext)
                || containsActiveChildSearchField(searchParams, joinContext)
                || containsChildField(splitFields(pageQuery.getOrderByColumn()), joinContext);
    }

    boolean requiresJoinedExportQuery(AiCrudConfig config,
                                              Map<String, Object> searchParams,
                                              RuntimeJoinContext joinContext) {
        return containsChildField(DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper), joinContext)
                || containsRelationDisplayField(DynamicQueryGenerator.extractFieldNames(config.getColumnsSchema(), objectMapper), joinContext)
                || containsActiveChildSearchField(searchParams, joinContext);
    }

    boolean requiresJoinedCustomQuery(CustomQueryExecuteDTO request, RuntimeJoinContext joinContext) {
        if (request == null) {
            return false;
        }
        if (containsChildField(request.getFields(), joinContext)) {
            return true;
        }
        if (containsRelationDisplayField(request.getFields(), joinContext)) {
            return true;
        }
        if (containsChildField(splitFields(request.getOrderByColumn()), joinContext)) {
            return true;
        }
        if (request.getConditions() == null) {
            return false;
        }
        for (var condition : request.getConditions()) {
            if (condition != null && isChildRuntimeField(condition.getField(), joinContext)) {
                return true;
            }
        }
        return false;
    }

    List<DynamicCrudRepository.JoinField> buildRuntimeSelectFields(RuntimeJoinContext joinContext,
                                                                           Collection<String> requestedFields,
                                                                           boolean defaultPrimaryFields) {
        LinkedHashSet<String> fieldNames = new LinkedHashSet<>();
        fieldNames.add(primaryKeyField(currentPrimaryKey()));
        if (requestedFields != null) {
            requestedFields.stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(fieldNames::add);
        }
        if (fieldNames.size() == 1 && defaultPrimaryFields) {
            joinContext.fields().values().stream()
                    .filter(RuntimeFieldRef::primary)
                    .map(RuntimeFieldRef::fieldName)
                    .forEach(fieldNames::add);
        }
        List<String> relationAliases = fieldNames.stream()
                .map(fieldName -> joinContext.relationDisplayAliases().get(fieldName))
                .filter(StringUtils::isNotBlank)
                .toList();
        fieldNames.addAll(relationAliases);

        List<DynamicCrudRepository.JoinField> selectFields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            RuntimeFieldRef fieldRef = joinContext.fields().get(fieldName);
            if (fieldRef == null) {
                continue;
            }
            selectFields.add(new DynamicCrudRepository.JoinField(
                    fieldRef.fieldName(), fieldRef.tableAlias(), fieldRef.columnName()));
        }
        if (selectFields.isEmpty()) {
            joinContext.fields().values().stream()
                    .filter(RuntimeFieldRef::primary)
                    .map(fieldRef -> new DynamicCrudRepository.JoinField(
                            fieldRef.fieldName(), fieldRef.tableAlias(), fieldRef.columnName()))
                    .forEach(selectFields::add);
        }
        return selectFields;
    }

    boolean containsChildField(Collection<String> fieldNames, RuntimeJoinContext joinContext) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return false;
        }
        for (String fieldName : fieldNames) {
            if (isChildRuntimeField(fieldName, joinContext)) {
                return true;
            }
        }
        return false;
    }

    boolean containsRelationDisplayField(Collection<String> fieldNames, RuntimeJoinContext joinContext) {
        if (fieldNames == null || fieldNames.isEmpty() || joinContext == null || joinContext.relationDisplayAliases().isEmpty()) {
            return false;
        }
        for (String fieldName : fieldNames) {
            if (joinContext.relationDisplayAliases().containsKey(fieldName)) {
                return true;
            }
        }
        return false;
    }

    boolean containsActiveChildSearchField(Map<String, Object> searchParams, RuntimeJoinContext joinContext) {
        if (searchParams == null || searchParams.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : searchParams.entrySet()) {
            if (hasQueryValue(entry.getValue()) && isChildRuntimeField(entry.getKey(), joinContext)) {
                return true;
            }
        }
        return false;
    }

    boolean isChildRuntimeField(String fieldName, RuntimeJoinContext joinContext) {
        if (StringUtils.isBlank(fieldName) || joinContext == null) {
            return false;
        }
        RuntimeFieldRef fieldRef = joinContext.fields().get(fieldName);
        return fieldRef != null && !fieldRef.primary();
    }

    List<String> splitFields(String fields) {
        if (StringUtils.isBlank(fields)) {
            return List.of();
        }
        return Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    boolean hasQueryValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.isNotBlank(text);
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    boolean aggregateChildListRows(AiCrudConfig config) {
        return !"expand".equalsIgnoreCase(resolveChildListDisplayMode(config));
    }

    String resolveChildListDisplayMode(AiCrudConfig config) {
        if (config == null) {
            return "aggregate";
        }
        LowcodePageSchema pageSchema = StringUtils.isBlank(config.getPageSchema()) ? null : readPageSchema(config);
        if (pageSchema != null && pageSchema.getZones() != null) {
            for (LowcodePageZone zone : pageSchema.getZones()) {
                if (zone == null || !"table".equals(zone.getZoneKey()) || zone.getProps() == null) {
                    continue;
                }
                String mode = StringUtils.trimToNull(text(zone.getProps().get("childListDisplayMode")));
                if (mode != null) {
                    return "expand".equalsIgnoreCase(mode) ? "expand" : "aggregate";
                }
            }
        }
        if (StringUtils.isNotBlank(config.getOptions())) {
            try {
                String mode = StringUtils.trimToNull(objectMapper.readTree(config.getOptions()).path("childListDisplayMode").asText(null));
                if ("expand".equalsIgnoreCase(mode)) {
                    return "expand";
                }
            } catch (Exception e) {
                log.warn("[DynamicCrudRuntimeRelationPlanner] 解析子表行展示方式失败, configKey={}", config.getConfigKey(), e);
            }
        }
        return "aggregate";
    }

    List<DynamicCrudRepository.JoinField> withExpandedChildKeys(List<DynamicCrudRepository.JoinField> selectFields,
                                                                        RuntimeJoinContext joinContext,
                                                                        boolean aggregateChildren) {
        if (aggregateChildren || joinContext == null || joinContext.joins() == null || joinContext.joins().isEmpty()) {
            return selectFields;
        }
        List<DynamicCrudRepository.JoinField> fields = new ArrayList<>(selectFields == null ? List.of() : selectFields);
        for (DynamicCrudRepository.JoinSpec join : joinContext.joins()) {
            if (join == null || StringUtils.isBlank(join.tableAlias()) || StringUtils.isBlank(join.tableName())) {
                continue;
            }
            if (!repository.getTableColumns(join.tableName()).contains("id")) {
                continue;
            }
            String alias = "__childId_" + join.tableAlias();
            boolean exists = fields.stream().anyMatch(field -> field != null && alias.equals(field.fieldName()));
            if (!exists) {
                fields.add(new DynamicCrudRepository.JoinField(alias, join.tableAlias(), "id"));
            }
        }
        return fields;
    }

    void stampExpandedListRowKeys(List<Map<String, Object>> rows,
                                          RuntimeJoinContext joinContext,
                                          boolean aggregateChildren) {
        if (aggregateChildren || rows == null || rows.isEmpty() || joinContext == null || joinContext.joins() == null) {
            return;
        }
        String primaryField = primaryKeyField(currentPrimaryKey());
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object mainId = row.get(primaryField);
            if (mainId == null) {
                mainId = row.get("id");
            }
            StringBuilder key = new StringBuilder(mainId == null ? "" : String.valueOf(mainId));
            for (DynamicCrudRepository.JoinSpec join : joinContext.joins()) {
                if (join == null || StringUtils.isBlank(join.tableAlias())) {
                    continue;
                }
                Object childId = row.remove("__childId_" + join.tableAlias());
                key.append(':').append(childId == null ? "" : childId);
            }
            if (StringUtils.isNotBlank(key)) {
                row.put("__listRowKey", key.toString());
            }
        }
    }

    LowcodePageSchema readPageSchema(AiCrudConfig config) {
        try {
            return objectMapper.readValue(config.getPageSchema(), LowcodePageSchema.class);
        } catch (Exception e) {
            log.warn("[DynamicCrudRuntimeRelationPlanner] 解析pageSchema失败, configKey={}", config.getConfigKey(), e);
            return null;
        }
    }

    LowcodeModelSchema readModelSchema(AiCrudConfig config) {
        try {
            return objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
        } catch (Exception e) {
            log.warn("[DynamicCrudRuntimeRelationPlanner] 解析modelSchema失败, configKey={}", config.getConfigKey(), e);
            return null;
        }
    }

    String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    String safeKey(String value) {
        String key = StringUtils.defaultIfBlank(value, "model").replaceAll("[^A-Za-z0-9_]", "_");
        return StringUtils.defaultIfBlank(key, "model");
    }

    String normalizeModelFieldName(LowcodeModelSchema modelSchema, String fieldName) {
        if (StringUtils.isBlank(fieldName) || modelSchema == null || modelSchema.getFields() == null) {
            return fieldName;
        }
        return modelSchema.getFields().stream()
                .filter(field -> fieldName.equals(field.getField()) || fieldName.equals(field.getColumnName()))
                .map(com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema::getField)
                .findFirst()
                .orElse(fieldName);
    }

    String resolveRelationDisplayField(LowcodePageModelRef ref, LowcodeRelationSchema relation) {
        if (ref == null || ref.getFields() == null || ref.getFields().isEmpty()) {
            return null;
        }
        String configured = resolveRefSourceField(ref, relation == null ? null : relation.getDisplayField());
        if (hasRefSourceField(ref, configured)) {
            return configured;
        }
        Set<String> excluded = new LinkedHashSet<>();
        excluded.add(resolveRefSourceField(ref, relation == null ? null : relation.getTargetField()));
        excluded.add(resolveRefSourceField(ref, relation == null ? null : relation.getSourceField()));
        String preferred = pickRelationDisplayField(ref, excluded, Set.of("name", "title", "label", "orgName", "deptName"));
        if (StringUtils.isNotBlank(preferred)) {
            return preferred;
        }
        return pickRelationDisplayField(ref, excluded, Set.of());
    }

    String pickRelationDisplayField(LowcodePageModelRef ref, Set<String> excluded, Set<String> preferredNames) {
        if (ref == null || ref.getFields() == null) {
            return null;
        }
        for (Map<String, Object> field : ref.getFields()) {
            String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String columnName = text(field.get("columnName"));
            if (StringUtils.isBlank(sourceField)
                    || excluded.contains(sourceField)
                    || excluded.contains(columnName)
                    || isSystemFieldName(sourceField)
                    || isSystemFieldName(columnName)) {
                continue;
            }
            if (!preferredNames.isEmpty() && !preferredNames.contains(sourceField)) {
                continue;
            }
            return sourceField;
        }
        return null;
    }

    boolean hasRefSourceField(LowcodePageModelRef ref, String sourceField) {
        if (ref == null || ref.getFields() == null || StringUtils.isBlank(sourceField)) {
            return false;
        }
        for (Map<String, Object> field : ref.getFields()) {
            String candidate = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String columnName = text(field.get("columnName"));
            if (sourceField.equals(candidate) || sourceField.equals(columnName)) {
                return true;
            }
        }
        return false;
    }

    String resolveRefSourceField(LowcodePageModelRef ref, String value) {
        if (ref == null || ref.getFields() == null || StringUtils.isBlank(value)) {
            return value;
        }
        for (Map<String, Object> field : ref.getFields()) {
            String sourceField = StringUtils.defaultIfBlank(text(field.get("sourceField")), text(field.get("field")));
            String columnName = text(field.get("columnName"));
            if (value.equals(sourceField) || value.equals(columnName)) {
                return sourceField;
            }
        }
        return value;
    }

    boolean isSystemFieldName(String fieldName) {
        return "id".equals(fieldName)
                || "tenantId".equals(fieldName)
                || "tenant_id".equals(fieldName)
                || "createBy".equals(fieldName)
                || "create_by".equals(fieldName)
                || "createTime".equals(fieldName)
                || "create_time".equals(fieldName)
                || "createDept".equals(fieldName)
                || "create_dept".equals(fieldName)
                || "updateBy".equals(fieldName)
                || "update_by".equals(fieldName)
                || "updateTime".equals(fieldName)
                || "update_time".equals(fieldName)
                || "delFlag".equals(fieldName)
                || "del_flag".equals(fieldName);
    }

    private LowcodePrimaryKeyStrategy currentPrimaryKey() {
        LowcodePrimaryKeyStrategy primaryKey = LowcodeRuntimeDataSourceContextHolder.get() == null
                ? null
                : LowcodeRuntimeDataSourceContextHolder.get().getPrimaryKey();
        if (primaryKey == null) {
            primaryKey = new LowcodePrimaryKeyStrategy();
            primaryKey.setField("id");
            primaryKey.setColumnName("id");
            primaryKey.setDataType("bigint");
            primaryKey.setAutoIncrement(true);
        }
        return primaryKey;
    }

    private String primaryKeyField(LowcodePrimaryKeyStrategy primaryKey) {
        return StringUtils.defaultIfBlank(primaryKey == null ? null : primaryKey.getField(), "id");
    }

    private String primaryKeyColumn(LowcodePrimaryKeyStrategy primaryKey) {
        return StringUtils.defaultIfBlank(primaryKey == null ? null : primaryKey.getColumnName(), "id");
    }

    private String primaryKeyOrderBy(String tableAlias, String direction) {
        String column = primaryKeyColumn(currentPrimaryKey());
        return StringUtils.isBlank(tableAlias)
                ? column + " " + direction
                : tableAlias + "." + column + " " + direction;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMap(Object... values) {
        if (values == null) {
            return Map.of();
        }
        for (Object value : values) {
            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                return (Map<String, Object>) map;
            }
        }
        return Map.of();
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
        String textValue = String.valueOf(value).trim();
        if (StringUtils.isBlank(textValue)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(textValue)
                || "1".equals(textValue)
                || "yes".equalsIgnoreCase(textValue)
                || "Y".equalsIgnoreCase(textValue);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

}

record RuntimeFieldRef(String fieldName,
                       String modelCode,
                       String sourceField,
                       String columnName,
                       String tableName,
                       String tableAlias,
                       boolean primary) {
}

record RuntimeChildRelation(String modelCode,
                            String tableName,
                            String tableAlias,
                            String childFkColumn,
                            String mainColumn,
                            String saveMode,
                            Map<String, RuntimeFieldRef> fields,
                            Map<String, RuntimeChildFieldRule> fieldRules) {
}

record RuntimeChildFieldRule(String fieldName,
                             String sourceField,
                             String columnName,
                             String label,
                             boolean required,
                             String dataType,
                             Integer length,
                             Integer precision,
                             BigDecimal minValue,
                             BigDecimal maxValue) {
}

record RuntimeJoinContext(Map<String, RuntimeFieldRef> fields,
                          List<RuntimeChildRelation> childRelations,
                          List<DynamicCrudRepository.JoinField> selectFields,
                          List<DynamicCrudRepository.JoinSpec> joins,
                          Map<String, String> fieldColumnMapping,
                          Map<String, String> relationDisplayAliases) {
}
