package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTreeConfig;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 动态 CRUD 树查询引擎。
 *
 * <p>集中处理树配置解析、完整树/懒加载查询、祖先补链和 includeChildren 条件展开。
 * 调用方继续持有运行时数据源、数据权限和读取安全流水线的编排边界。</p>
 */
@Slf4j
final class DynamicCrudTreeQueryEngine {

    private static final int MAX_TREE_ROWS = 5000;
    private static final String TREE_LAYOUT = "tree-crud";

    private final DynamicCrudRepository repository;
    private final AiCrudConfigService configService;
    private final ObjectMapper objectMapper;

    DynamicCrudTreeQueryEngine(DynamicCrudRepository repository,
                               AiCrudConfigService configService,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    List<Map<String, Object>> selectTree(AiCrudConfig config,
                                         LowcodeTreeConfig treeConfig,
                                         String parentValue,
                                         String parentId,
                                         String loadMode,
                                         String orderByColumn,
                                         String isAsc,
                                         Map<String, String> columnMapping,
                                         DynamicCrudRepository.SqlCondition dataScopeCondition,
                                         Consumer<List<Map<String, Object>>> readPipeline) {
        String tableName = StringUtils.defaultIfBlank(treeConfig.getSourceTableName(), config.getTableName());
        String keyColumn = columnMapping.getOrDefault(treeConfig.getKeyField(),
                DynamicQueryGenerator.camelToSnake(treeConfig.getKeyField()));
        String parentColumn = columnMapping.getOrDefault(treeConfig.getParentField(),
                DynamicQueryGenerator.camelToSnake(treeConfig.getParentField()));
        Set<String> tableColumns = repository.getTableColumns(tableName);
        if (!tableColumns.contains(keyColumn)) {
            throw new BusinessException("树形主键字段不存在: " + treeConfig.getKeyField());
        }
        if (!tableColumns.contains(parentColumn)) {
            throw new BusinessException("树形父级字段不存在: " + treeConfig.getParentField());
        }
        String orderColumn = tableColumns.contains(keyColumn)
                ? keyColumn
                : (tableColumns.contains("id") ? "id" : parentColumn);
        String orderBy = StringUtils.isBlank(orderByColumn)
                ? orderColumn + " ASC"
                : DynamicQueryGenerator.buildOrderByClause(orderByColumn, isAsc, columnMapping);

        String effectiveLoadMode = StringUtils.defaultIfBlank(loadMode, treeConfig.getLoadMode());
        if ("lazy".equalsIgnoreCase(effectiveLoadMode)) {
            String effectiveParentValue = StringUtils.defaultIfBlank(parentValue, parentId);
            List<Map<String, Object>> rows = repository.selectTreeChildren(
                    tableName, parentColumn, effectiveParentValue, orderBy, MAX_TREE_ROWS, dataScopeCondition);
            List<Map<String, Object>> camelCaseRows = DynamicQueryGenerator.convertListToCamelCase(rows);
            readPipeline.accept(camelCaseRows);
            return buildLazyTreeNodes(camelCaseRows, tableName, parentColumn, treeConfig, dataScopeCondition);
        }

        List<Map<String, Object>> rows = repository.selectList(
                tableName,
                null,
                Collections.emptySet(),
                Collections.emptyMap(),
                columnMapping,
                orderBy,
                MAX_TREE_ROWS,
                dataScopeCondition
        );
        Set<String> ancestorKeys = new LinkedHashSet<>();
        rows = appendTreeAncestorRows(rows, tableName, keyColumn, parentColumn, ancestorKeys);
        List<Map<String, Object>> camelCaseRows = DynamicQueryGenerator.convertListToCamelCase(rows);
        markTreeAncestorRows(camelCaseRows, treeConfig, ancestorKeys);
        readPipeline.accept(camelCaseRows);
        return buildTree(camelCaseRows, treeConfig);
    }

    LowcodeTreeConfig resolveTreeConfig(AiCrudConfig config) {
        LowcodeTreeConfig treeConfig = new LowcodeTreeConfig();
        applyTreeConfigFromModel(config, treeConfig);
        applyTreeConfigFromOptions(config, treeConfig);
        if (StringUtils.isBlank(treeConfig.getKeyField())) {
            treeConfig.setKeyField("id");
        }
        if (StringUtils.isBlank(treeConfig.getParentField())) {
            treeConfig.setParentField("parentId");
        }
        if (StringUtils.isBlank(treeConfig.getLabelField())) {
            treeConfig.setLabelField("name");
        }
        if (StringUtils.isBlank(treeConfig.getFilterField())) {
            treeConfig.setFilterField(treeConfig.getParentField());
        }
        if (StringUtils.isBlank(treeConfig.getTargetField())) {
            treeConfig.setTargetField(treeConfig.getKeyField());
        }
        if (StringUtils.isBlank(treeConfig.getChildrenField())) {
            treeConfig.setChildrenField("children");
        }
        if (StringUtils.isBlank(treeConfig.getLoadMode())) {
            treeConfig.setLoadMode("full");
        }
        return treeConfig;
    }

    boolean isTreeRuntime(AiCrudConfig config) {
        if (config == null) {
            return false;
        }
        if (TREE_LAYOUT.equals(config.getLayoutType())) {
            return true;
        }
        if (StringUtils.isBlank(config.getOptions())) {
            return false;
        }
        try {
            JsonNode treeNode = objectMapper.readTree(config.getOptions()).get("treeConfig");
            return treeNode != null && treeNode.isObject();
        } catch (Exception e) {
            log.warn("[DynamicCrudTreeQueryEngine] 判断树形运行时失败, configKey={}", config.getConfigKey(), e);
            return false;
        }
    }

    Map<String, Object> expandIncludeChildrenParams(Map<String, Object> searchParams,
                                                     AiCrudConfig config,
                                                     String tableName,
                                                     Set<String> allowedSearchFields,
                                                     Map<String, String> searchTypeMap) {
        if (searchParams == null || searchParams.isEmpty()) {
            return searchParams;
        }
        Map<String, Object> expanded = new LinkedHashMap<>(searchParams);
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, Object> entry : searchParams.entrySet()) {
            String key = entry.getKey();
            if (!key.endsWith("_includeChildren") || !isTruthy(entry.getValue())) {
                continue;
            }
            String baseField = key.substring(0, key.length() - "_includeChildren".length());
            Object baseValue = searchParams.get(baseField);
            if (baseValue == null) {
                continue;
            }
            keysToRemove.add(key);
            List<Object> values = normalizeIncludeChildrenValues(baseValue);
            if (values == null) {
                values = resolveIncludeChildrenValues(config, tableName, baseField, baseValue);
            }
            if (values == null || values.isEmpty()) {
                continue;
            }
            expanded.put(baseField, values);
            allowedSearchFields.add(baseField);
            if (searchTypeMap != null) {
                searchTypeMap.put(baseField, "in");
            }
        }
        keysToRemove.forEach(expanded::remove);
        return expanded;
    }

    void coerceMultiValueSearchTypes(Map<String, Object> searchParams, Map<String, String> searchTypeMap) {
        if (searchParams == null || searchParams.isEmpty() || searchTypeMap == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : searchParams.entrySet()) {
            String field = entry.getKey();
            if (field == null || field.startsWith("_") || field.endsWith("_includeChildren")
                    || field.endsWith("__treeExpanded") || !isMultiSearchValue(entry.getValue())) {
                continue;
            }
            String current = searchTypeMap.get(field);
            if (current == null || "eq".equalsIgnoreCase(current)) {
                searchTypeMap.put(field, "in");
            }
        }
    }

    List<CustomQueryConditionDTO> expandCustomIncludeChildrenConditions(List<CustomQueryConditionDTO> conditions,
                                                                         AiCrudConfig config,
                                                                         String tableName,
                                                                         Set<String> allowedFields) {
        if (conditions == null || conditions.isEmpty()) {
            return conditions;
        }
        List<CustomQueryConditionDTO> expanded = new ArrayList<>(conditions.size());
        boolean changed = false;
        for (CustomQueryConditionDTO condition : conditions) {
            if (condition == null || !Boolean.TRUE.equals(condition.getIncludeChildren())
                    || StringUtils.isBlank(condition.getField()) || !allowedFields.contains(condition.getField())) {
                expanded.add(condition);
                continue;
            }
            List<Object> values = normalizeIncludeChildrenValues(condition.getValue());
            if (values == null) {
                values = resolveIncludeChildrenValues(config, tableName, condition.getField(), condition.getValue());
            }
            if (values == null || values.isEmpty()) {
                expanded.add(condition);
                continue;
            }
            CustomQueryConditionDTO next = new CustomQueryConditionDTO();
            next.setRelation(condition.getRelation());
            next.setField(condition.getField());
            next.setOperator("in");
            next.setValue(values);
            next.setValueEnd(null);
            next.setIncludeChildren(true);
            expanded.add(next);
            changed = true;
        }
        return changed ? expanded : conditions;
    }

    private void applyTreeConfigFromModel(AiCrudConfig config, LowcodeTreeConfig target) {
        if (StringUtils.isBlank(config.getModelSchema())) {
            return;
        }
        try {
            applyTreeConfigNode(objectMapper.readTree(config.getModelSchema()).get("treeConfig"), target);
        } catch (Exception e) {
            log.warn("[DynamicCrudTreeQueryEngine] 解析modelSchema.treeConfig失败, configKey={}",
                    config.getConfigKey(), e);
        }
    }

    private void applyTreeConfigFromOptions(AiCrudConfig config, LowcodeTreeConfig target) {
        if (StringUtils.isBlank(config.getOptions())) {
            return;
        }
        try {
            applyTreeConfigNode(objectMapper.readTree(config.getOptions()).get("treeConfig"), target);
        } catch (Exception e) {
            log.warn("[DynamicCrudTreeQueryEngine] 解析options.treeConfig失败, configKey={}",
                    config.getConfigKey(), e);
        }
    }

    private void applyTreeConfigNode(JsonNode treeNode, LowcodeTreeConfig target) {
        if (treeNode == null || !treeNode.isObject()) {
            return;
        }
        setIfPresent(firstText(treeNode, "keyField", "nodeKeyField"), target::setKeyField);
        setIfPresent(text(treeNode, "sourceModelCode"), target::setSourceModelCode);
        setIfPresent(text(treeNode, "sourceModelName"), target::setSourceModelName);
        setIfPresent(text(treeNode, "sourceTableName"), target::setSourceTableName);
        setIfPresent(text(treeNode, "sourceConfigKey"), target::setSourceConfigKey);
        setIfPresent(firstText(treeNode, "parentField", "parentIdField"), target::setParentField);
        setIfPresent(firstText(treeNode, "labelField", "displayField", "nameField"), target::setLabelField);
        setIfPresent(firstText(treeNode, "filterField", "rightFilterField", "listFilterField"), target::setFilterField);
        setIfPresent(firstText(treeNode, "targetField", "nodeValueField", "valueField"), target::setTargetField);
        setIfPresent(text(treeNode, "childrenField"), target::setChildrenField);
        setIfPresent(firstText(treeNode, "treeTitle", "title"), target::setTreeTitle);
        if (StringUtils.isNotBlank(text(treeNode, "loadMode"))) {
            target.setLoadMode(text(treeNode, "loadMode"));
        } else if (treeNode.has("lazy") && treeNode.get("lazy").asBoolean(false)) {
            target.setLoadMode("lazy");
        }
        if (treeNode.has("enabled") && !treeNode.get("enabled").isNull()) {
            target.setEnabled(treeNode.get("enabled").asBoolean(false));
        }
    }

    private void setIfPresent(String value, Consumer<String> setter) {
        if (StringUtils.isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private List<Map<String, Object>> appendTreeAncestorRows(List<Map<String, Object>> rows,
                                                             String tableName,
                                                             String keyColumn,
                                                             String parentColumn,
                                                             Set<String> ancestorKeys) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> result = new ArrayList<>(rows);
        Set<String> knownKeys = result.stream()
                .map(row -> normalizeTreeKey(row.get(keyColumn)))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Deque<Object> parentQueue = new ArrayDeque<>();
        for (Map<String, Object> row : result) {
            Object parentValue = row.get(parentColumn);
            String parentKey = normalizeTreeKey(parentValue);
            if (StringUtils.isNotBlank(parentKey) && !isRootParent(parentKey) && !knownKeys.contains(parentKey)) {
                parentQueue.add(parentValue);
            }
        }

        int guard = 0;
        while (!parentQueue.isEmpty() && guard < MAX_TREE_ROWS) {
            guard++;
            Object parentValue = parentQueue.removeFirst();
            String parentKey = normalizeTreeKey(parentValue);
            if (StringUtils.isBlank(parentKey) || isRootParent(parentKey) || knownKeys.contains(parentKey)) {
                continue;
            }
            List<Map<String, Object>> parents = repository.selectListByColumn(tableName, keyColumn, parentValue);
            if (parents.isEmpty()) {
                continue;
            }
            Map<String, Object> parent = parents.get(0);
            String key = normalizeTreeKey(parent.get(keyColumn));
            if (StringUtils.isBlank(key) || !knownKeys.add(key)) {
                continue;
            }
            ancestorKeys.add(key);
            result.add(parent);
            String nextParentKey = normalizeTreeKey(parent.get(parentColumn));
            if (StringUtils.isNotBlank(nextParentKey) && !isRootParent(nextParentKey) && !knownKeys.contains(nextParentKey)) {
                parentQueue.add(parent.get(parentColumn));
            }
        }
        return result;
    }

    private void markTreeAncestorRows(List<Map<String, Object>> rows,
                                      LowcodeTreeConfig treeConfig,
                                      Set<String> ancestorKeys) {
        if (rows == null || rows.isEmpty() || ancestorKeys == null || ancestorKeys.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            boolean ancestor = ancestorKeys.contains(normalizeTreeKey(row.get(treeConfig.getKeyField())));
            row.put("_scopeAncestor", ancestor);
            row.put("_dataScopeWritable", !ancestor);
        }
    }

    private List<Map<String, Object>> buildLazyTreeNodes(List<Map<String, Object>> rows,
                                                         String tableName,
                                                         String parentColumn,
                                                         LowcodeTreeConfig treeConfig,
                                                         DynamicCrudRepository.SqlCondition dataScopeCondition) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = normalizeTreeKey(row.get(treeConfig.getKeyField()));
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>(row);
            node.putIfAbsent("key", row.get(treeConfig.getKeyField()));
            node.putIfAbsent("label", resolveTreeLabel(row, treeConfig.getLabelField(), treeConfig.getKeyField()));
            node.putIfAbsent("targetValue", row.get(treeConfig.getTargetField()));
            boolean hasChildren = repository.existsByColumn(
                    tableName, parentColumn, row.get(treeConfig.getKeyField()), dataScopeCondition);
            node.put("isLeaf", !hasChildren);
            nodes.add(node);
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildTree(List<Map<String, Object>> rows, LowcodeTreeConfig treeConfig) {
        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = normalizeTreeKey(row.get(treeConfig.getKeyField()));
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>(row);
            node.putIfAbsent("key", row.get(treeConfig.getKeyField()));
            node.putIfAbsent("label", resolveTreeLabel(row, treeConfig.getLabelField(), treeConfig.getKeyField()));
            node.putIfAbsent("targetValue", row.get(treeConfig.getTargetField()));
            node.put(treeConfig.getChildrenField(), new ArrayList<Map<String, Object>>());
            nodeMap.put(key, node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodeMap.values()) {
            String key = normalizeTreeKey(node.get(treeConfig.getKeyField()));
            String parentKey = normalizeTreeKey(node.get(treeConfig.getParentField()));
            Map<String, Object> parent = nodeMap.get(parentKey);
            if (isRootParent(parentKey) || parent == null || key.equals(parentKey)) {
                roots.add(node);
                continue;
            }
            Object children = parent.get(treeConfig.getChildrenField());
            if (children instanceof List<?> childList) {
                ((List<Map<String, Object>>) childList).add(node);
            }
        }
        normalizeFullTreeLeafState(roots, treeConfig.getChildrenField());
        return roots;
    }

    @SuppressWarnings("unchecked")
    private void normalizeFullTreeLeafState(List<Map<String, Object>> nodes, String childrenField) {
        for (Map<String, Object> node : nodes) {
            Object children = node.get(childrenField);
            if (children instanceof List<?> childList && !childList.isEmpty()) {
                node.put("isLeaf", false);
                normalizeFullTreeLeafState((List<Map<String, Object>>) childList, childrenField);
                continue;
            }
            node.put("isLeaf", true);
            node.remove(childrenField);
        }
    }

    private Object resolveTreeLabel(Map<String, Object> row, String labelField, String keyField) {
        List<String> candidates = Arrays.asList(
                labelField, "label", "name", "title", "treeName", "deptName", "orgName", keyField);
        for (String candidate : candidates) {
            if (StringUtils.isNotBlank(candidate)) {
                Object value = row.get(candidate);
                if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                    return value;
                }
            }
        }
        return "未命名节点";
    }

    private boolean isRootParent(String parentKey) {
        return StringUtils.isBlank(parentKey) || "0".equals(parentKey) || "null".equalsIgnoreCase(parentKey);
    }

    private String normalizeTreeKey(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return StringUtils.isBlank(normalized) ? null : normalized;
    }

    private boolean isTruthy(Object value) {
        if (Boolean.TRUE.equals(value)) {
            return true;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        return value instanceof Number number && number.intValue() == 1;
    }

    private boolean isMultiSearchValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size() > 1
                    || (collection.size() == 1 && String.valueOf(collection.iterator().next()).contains(","));
        }
        if (value instanceof Object[] array) {
            return array.length > 1;
        }
        return value instanceof String text && text.contains(",");
    }

    private List<Object> normalizeIncludeChildrenValues(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> !(item instanceof String text) || StringUtils.isNotBlank(text))
                    .distinct()
                    .map(item -> (Object) item)
                    .toList();
        }
        if (value instanceof Object[] array) {
            return Arrays.stream(array)
                    .filter(Objects::nonNull)
                    .filter(item -> !(item instanceof String text) || StringUtils.isNotBlank(text))
                    .distinct()
                    .map(item -> (Object) item)
                    .toList();
        }
        if (value instanceof String text && text.contains(",")) {
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .map(item -> (Object) item)
                    .toList();
        }
        return null;
    }

    private List<Object> resolveIncludeChildrenValues(AiCrudConfig config,
                                                       String tableName,
                                                       String baseField,
                                                       Object baseValue) {
        if (baseValue == null) {
            return List.of();
        }
        Object normalizedBaseValue = normalizeIncludeChildrenBaseValue(baseValue);
        LowcodeTreeConfig treeConfig = resolveIncludeChildrenTreeConfig(config, baseField);
        String sourceTable = resolveIncludeChildrenSourceTable(treeConfig, tableName);
        if (StringUtils.isBlank(sourceTable) || !repository.tableExists(sourceTable)) {
            return List.of(normalizedBaseValue);
        }
        Map<String, String> columnMapping = repository.getColumnMapping(sourceTable);
        String keyField = treeConfig == null ? "id" : StringUtils.defaultIfBlank(treeConfig.getKeyField(), "id");
        String parentField = treeConfig == null
                ? "parentId"
                : StringUtils.defaultIfBlank(treeConfig.getParentField(), "parentId");
        String targetField = treeConfig == null
                ? "id"
                : StringUtils.defaultIfBlank(treeConfig.getTargetField(), keyField);
        String keyColumn = resolveColumnName(keyField, columnMapping);
        String parentColumn = resolveColumnName(parentField, columnMapping);
        String targetColumn = resolveColumnName(targetField, columnMapping);
        if (StringUtils.isBlank(keyColumn) || StringUtils.isBlank(parentColumn) || StringUtils.isBlank(targetColumn)) {
            log.warn("[DynamicCrudTreeQueryEngine] includeChildren 字段映射失败, table={}, key={}, parent={}, target={}",
                    sourceTable, keyField, parentField, targetField);
            return List.of(normalizedBaseValue);
        }

        LinkedHashSet<Object> resultValues = new LinkedHashSet<>();
        Deque<Object> queue = new ArrayDeque<>();
        LinkedHashSet<String> visitedKeys = new LinkedHashSet<>();
        if (targetColumn.equals(keyColumn)) {
            resultValues.add(normalizedBaseValue);
            queue.add(normalizedBaseValue);
            visitedKeys.add(String.valueOf(normalizedBaseValue));
        } else {
            List<Map<String, Object>> seeds = repository.selectListByColumn(sourceTable, targetColumn, normalizedBaseValue);
            if (seeds.isEmpty()) {
                return List.of(normalizedBaseValue);
            }
            for (Map<String, Object> seed : seeds) {
                Object keyValue = readRowColumnValue(seed, keyColumn, keyField);
                if (keyValue == null) {
                    continue;
                }
                queue.add(keyValue);
                visitedKeys.add(String.valueOf(keyValue));
                Object targetValue = readRowColumnValue(seed, targetColumn, targetField);
                resultValues.add(targetValue != null ? targetValue : normalizedBaseValue);
            }
        }

        while (!queue.isEmpty()) {
            Object currentKey = queue.removeFirst();
            List<Map<String, Object>> children = repository.selectListByColumn(sourceTable, parentColumn, currentKey);
            for (Map<String, Object> child : children) {
                Object childKey = readRowColumnValue(child, keyColumn, keyField);
                if (childKey != null && visitedKeys.add(String.valueOf(childKey))) {
                    queue.addLast(childKey);
                }
                Object targetValue = readRowColumnValue(child, targetColumn, targetField);
                if (targetValue != null) {
                    resultValues.add(targetValue);
                }
            }
        }
        return resultValues.isEmpty() ? List.of(normalizedBaseValue) : new ArrayList<>(resultValues);
    }

    private String resolveIncludeChildrenSourceTable(LowcodeTreeConfig treeConfig, String fallbackTable) {
        if (treeConfig != null && StringUtils.isNotBlank(treeConfig.getSourceTableName())) {
            return treeConfig.getSourceTableName();
        }
        if (treeConfig != null && StringUtils.isNotBlank(treeConfig.getSourceConfigKey())) {
            try {
                AiCrudConfig sourceConfig = configService.getByConfigKey(treeConfig.getSourceConfigKey());
                if (sourceConfig != null && StringUtils.isNotBlank(sourceConfig.getTableName())) {
                    return sourceConfig.getTableName();
                }
            } catch (Exception e) {
                log.warn("[DynamicCrudTreeQueryEngine] 解析 includeChildren 源配置失败, sourceConfigKey={}",
                        treeConfig.getSourceConfigKey(), e);
            }
        }
        return fallbackTable;
    }

    private Object readRowColumnValue(Map<String, Object> row, String columnName, String fieldName) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        if (StringUtils.isNotBlank(columnName) && row.containsKey(columnName)) {
            return row.get(columnName);
        }
        if (StringUtils.isNotBlank(fieldName) && row.containsKey(fieldName)) {
            return row.get(fieldName);
        }
        if (StringUtils.isNotBlank(columnName)) {
            String camel = DynamicQueryGenerator.snakeToCamel(columnName);
            if (row.containsKey(camel)) {
                return row.get(camel);
            }
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (columnName.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private LowcodeTreeConfig resolveIncludeChildrenTreeConfig(AiCrudConfig config, String baseField) {
        if (config == null || StringUtils.isBlank(baseField)) {
            return null;
        }
        LowcodeTreeConfig treeConfig = resolveTreeConfig(config);
        if (StringUtils.equals(baseField, treeConfig.getFilterField())) {
            return treeConfig;
        }
        LowcodeTreeConfig systemTreeConfig = resolveSystemTreeConfig(config, baseField);
        if (systemTreeConfig != null) {
            return systemTreeConfig;
        }
        return isTreeRuntime(config) ? treeConfig : null;
    }

    private Object normalizeIncludeChildrenBaseValue(Object baseValue) {
        if (baseValue instanceof String text && text.endsWith("ALL")) {
            return text.substring(0, text.length() - 3);
        }
        return baseValue;
    }

    private LowcodeTreeConfig resolveSystemTreeConfig(AiCrudConfig config, String baseField) {
        JsonNode fieldNode = findSchemaField(config.getSearchSchema(), baseField);
        if (fieldNode == null) {
            fieldNode = findSchemaField(config.getEditSchema(), baseField);
        }
        if (fieldNode == null) {
            return null;
        }
        String fieldType = firstText(fieldNode, "type", "componentType");
        if ("orgTreeSelect".equals(fieldType)) {
            return buildStaticTreeConfig("sys_org", "id", "parentId", "id", baseField);
        }
        if ("regionTreeSelect".equals(fieldType)) {
            return buildStaticTreeConfig("sys_region_code", "code", "parentCode", "code", baseField);
        }
        if (!"treeSelect".equals(fieldType)) {
            return null;
        }
        JsonNode optionSource = fieldNode.path("props").path("optionSource");
        if (optionSource.isMissingNode() || optionSource.isNull()) {
            optionSource = fieldNode.path("optionSource");
        }
        String api = firstText(optionSource, "api");
        String sourceConfigKey = extractCrudConfigKeyFromTreeApi(api);
        if (StringUtils.isNotBlank(sourceConfigKey)) {
            try {
                AiCrudConfig sourceConfig = configService.getByConfigKey(sourceConfigKey);
                if (sourceConfig != null) {
                    LowcodeTreeConfig sourceTree = resolveTreeConfig(sourceConfig);
                    sourceTree.setFilterField(baseField);
                    if (StringUtils.isBlank(sourceTree.getSourceConfigKey())) {
                        sourceTree.setSourceConfigKey(sourceConfigKey);
                    }
                    if (StringUtils.isBlank(sourceTree.getSourceTableName())) {
                        sourceTree.setSourceTableName(sourceConfig.getTableName());
                    }
                    return sourceTree;
                }
            } catch (Exception e) {
                log.warn("[DynamicCrudTreeQueryEngine] 解析 treeSelect includeChildren 源配置失败, sourceConfigKey={}",
                        sourceConfigKey, e);
            }
        }
        return StringUtils.contains(api, "/ai/crud/") ? resolveTreeConfig(config) : null;
    }

    private String extractCrudConfigKeyFromTreeApi(String api) {
        if (StringUtils.isBlank(api)) {
            return null;
        }
        String normalized = api.trim();
        int at = normalized.indexOf('@');
        if (at >= 0) {
            normalized = normalized.substring(at + 1);
        }
        int marker = normalized.indexOf("/ai/crud/");
        if (marker < 0) {
            return null;
        }
        String rest = normalized.substring(marker + "/ai/crud/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return StringUtils.trimToNull(rest.substring(0, slash));
    }

    private LowcodeTreeConfig buildStaticTreeConfig(String sourceTable,
                                                     String keyField,
                                                     String parentField,
                                                     String targetField,
                                                     String filterField) {
        LowcodeTreeConfig treeConfig = new LowcodeTreeConfig();
        treeConfig.setSourceTableName(sourceTable);
        treeConfig.setKeyField(keyField);
        treeConfig.setParentField(parentField);
        treeConfig.setTargetField(targetField);
        treeConfig.setFilterField(filterField);
        treeConfig.setChildrenField("children");
        return treeConfig;
    }

    private JsonNode findSchemaField(String schemaJson, String fieldName) {
        if (StringUtils.isBlank(schemaJson) || StringUtils.isBlank(fieldName)) {
            return null;
        }
        try {
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            if (!schemaNode.isArray()) {
                return null;
            }
            for (JsonNode item : schemaNode) {
                if (fieldName.equals(firstText(item, "field", "prop", "key", "dataIndex"))) {
                    return item;
                }
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudTreeQueryEngine] 查找树形查询字段失败, field={}", fieldName, e);
        }
        return null;
    }

    private String resolveColumnName(String fieldName, Map<String, String> columnMapping) {
        if (StringUtils.isBlank(fieldName)) {
            return null;
        }
        String column = columnMapping.getOrDefault(fieldName, DynamicQueryGenerator.camelToSnake(fieldName));
        return columnMapping.containsValue(column) ? column : null;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && StringUtils.isNotBlank(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }
}
