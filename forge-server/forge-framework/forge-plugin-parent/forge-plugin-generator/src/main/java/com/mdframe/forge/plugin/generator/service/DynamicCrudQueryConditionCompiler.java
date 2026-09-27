package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 动态查询条件编译器。
 *
 * <p>以 Strategy 方式把普通搜索、自定义条件和投影字段编译为参数化 SQL 片段。</p>
 */
@Slf4j
final class DynamicCrudQueryConditionCompiler {

    private static final String OR_LIKE_SEARCH_KEY = "__orLike";

    private final Consumer<String> identifierValidator;
    private final Supplier<String> primaryKeyFieldSupplier;
    private final Supplier<String> primaryKeyColumnSupplier;

    DynamicCrudQueryConditionCompiler(Consumer<String> identifierValidator,
                                      Supplier<String> primaryKeyFieldSupplier,
                                      Supplier<String> primaryKeyColumnSupplier) {
        this.identifierValidator = identifierValidator;
        this.primaryKeyFieldSupplier = primaryKeyFieldSupplier;
        this.primaryKeyColumnSupplier = primaryKeyColumnSupplier;
    }

    private void addSearchCondition(StringBuilder whereClause, MapSqlParameterSource params,
                                     String columnName, String searchType, Object value) {
        String paramName = "param_" + columnName.replace(".", "_");

        switch (searchType.toLowerCase()) {
            case "like":
                addLikeCondition(whereClause, params, columnName, paramName, "%" + value + "%");
                break;
            case "left_like":
                addLikeCondition(whereClause, params, columnName, paramName, "%" + value);
                break;
            case "right_like":
                addLikeCondition(whereClause, params, columnName, paramName, value + "%");
                break;
            case "eq":
                addBinaryCondition(whereClause, params, columnName, "=", paramName, value);
                break;
            case "ne":
                addBinaryCondition(whereClause, params, columnName, "!=", paramName, value);
                break;
            case "gt":
                addBinaryCondition(whereClause, params, columnName, ">", paramName, value);
                break;
            case "ge":
            case "gte":
                addBinaryCondition(whereClause, params, columnName, ">=", paramName, value);
                break;
            case "lt":
                addBinaryCondition(whereClause, params, columnName, "<", paramName, value);
                break;
            case "le":
            case "lte":
                addBinaryCondition(whereClause, params, columnName, "<=", paramName, value);
                break;
            case "in":
                addInCondition(whereClause, params, columnName, paramName, value);
                break;
            case "between":
                addBetweenCondition(whereClause, params, columnName, paramName, value);
                break;
            case "is_null":
                whereClause.append(columnName).append(" IS NULL");
                break;
            case "is_not_null":
                whereClause.append(columnName).append(" IS NOT NULL");
                break;
            default:
                addBinaryCondition(whereClause, params, columnName, "=", paramName, value);
                break;
        }
    }

    private void addLikeCondition(StringBuilder whereClause, MapSqlParameterSource params,
                                  String columnName, String paramName, Object value) {
        addBinaryCondition(whereClause, params, columnName, "LIKE", paramName, value);
    }

    private void addBinaryCondition(StringBuilder whereClause, MapSqlParameterSource params,
                                    String columnName, String operator, String paramName, Object value) {
        whereClause.append(columnName).append(" ").append(operator).append(" :").append(paramName);
        params.addValue(paramName, value);
    }

    private void addInCondition(StringBuilder whereClause, MapSqlParameterSource params,
                                String columnName, String paramName, Object value) {
        List<?> values = normalizeInValues(value);
        if (values == null) {
            addBinaryCondition(whereClause, params, columnName, "=", paramName, value);
            return;
        }
        whereClause.append(columnName).append(" IN (:").append(paramName).append(")");
        params.addValue(paramName, values);
    }

    private List<?> normalizeInValues(Object value) {
        if (value instanceof List) {
            List<?> values = ((List<?>) value).stream()
                    .filter(Objects::nonNull)
                    .filter(item -> !(item instanceof String) || StringUtils.isNotBlank((String) item))
                    .toList();
            return values.isEmpty() ? null : values;
        }
        if (value instanceof String) {
            List<String> values = Arrays.stream(((String) value).split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .toList();
            return values.isEmpty() ? null : values;
        }
        return null;
    }

    private void addBetweenCondition(StringBuilder whereClause, MapSqlParameterSource params,
                                     String columnName, String paramName, Object value) {
        if (!(value instanceof List)) {
            return;
        }
        List<?> range = (List<?>) value;
        if (range.size() < 2) {
            return;
        }
        whereClause.append(columnName).append(" BETWEEN :").append(paramName).append("_start AND :")
                .append(paramName).append("_end");
        params.addValue(paramName + "_start", range.get(0));
        params.addValue(paramName + "_end", range.get(1));
    }

    void appendSearchConditions(StringBuilder whereClause, MapSqlParameterSource params,
                                        Map<String, Object> searchParams,
                                        Set<String> allowedSearchFields,
                                        Map<String, String> searchTypeMap,
                                        Map<String, String> columnMapping) {
        if (searchParams == null || searchParams.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : searchParams.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            if (OR_LIKE_SEARCH_KEY.equals(fieldName)) {
                appendOrLikeConditions(whereClause, params, value, allowedSearchFields, columnMapping);
                continue;
            }
            if (shouldSkipSearchField(fieldName, value, allowedSearchFields)) {
                continue;
            }

            String columnName = resolveSearchColumn(fieldName, columnMapping);
            if (columnName == null) {
                continue;
            }

            appendWhereJoiner(whereClause);
            addSearchCondition(whereClause, params, columnName,
                    resolveSearchType(fieldName, searchTypeMap, value), value);
        }
    }

    private void appendOrLikeConditions(StringBuilder whereClause,
                                        MapSqlParameterSource params,
                                        Object rawConditions,
                                        Set<String> allowedSearchFields,
                                        Map<String, String> columnMapping) {
        if (!(rawConditions instanceof List<?> conditions) || conditions.isEmpty()) {
            return;
        }
        StringBuilder orClause = new StringBuilder();
        int index = 0;
        for (Object rawCondition : conditions) {
            if (!(rawCondition instanceof Map<?, ?> condition)) {
                continue;
            }
            String fieldName = StringUtils.trimToNull(String.valueOf(condition.get("field")));
            Object value = condition.get("value");
            if (shouldSkipSearchField(fieldName, value, allowedSearchFields)) {
                continue;
            }
            String columnName = resolveSearchColumn(fieldName, columnMapping);
            if (columnName == null || !isKnownColumn(columnName, columnMapping)) {
                continue;
            }
            if (orClause.length() > 0) {
                orClause.append(" OR ");
            }
            String paramName = "or_like_" + index + "_" + columnName.replace(".", "_");
            orClause.append(columnName).append(" LIKE :").append(paramName);
            params.addValue(paramName, "%" + value + "%");
            index++;
        }
        if (orClause.length() == 0) {
            return;
        }
        appendWhereJoiner(whereClause);
        whereClause.append("(").append(orClause).append(")");
    }

    void appendCustomConditions(StringBuilder whereClause, MapSqlParameterSource params,
                                        List<CustomQueryConditionDTO> conditions,
                                        Set<String> allowedFields,
                                        Map<String, String> columnMapping) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }

        StringBuilder customClause = new StringBuilder();
        int index = 0;
        for (CustomQueryConditionDTO condition : conditions) {
            if (condition == null || shouldSkipCustomCondition(condition, allowedFields)) {
                continue;
            }

            String columnName = resolveSearchColumn(condition.getField(), columnMapping);
            if (columnName == null || !isKnownColumn(columnName, columnMapping)) {
                continue;
            }

            String operator = normalizeOperator(condition.getOperator());
            String conditionSql = buildCustomConditionSql(params, columnName, operator, condition, index);
            if (StringUtils.isBlank(conditionSql)) {
                continue;
            }

            if (customClause.length() > 0) {
                customClause.append(" ").append(resolveRelation(condition.getRelation())).append(" ");
            }
            customClause.append(conditionSql);
            index++;
        }

        if (customClause.length() > 0) {
            appendWhereJoiner(whereClause);
            whereClause.append("(").append(customClause).append(")");
        }
    }

    private boolean shouldSkipCustomCondition(CustomQueryConditionDTO condition, Set<String> allowedFields) {
        if (StringUtils.isBlank(condition.getField()) || !allowedFields.contains(condition.getField())) {
            return true;
        }
        String operator = normalizeOperator(condition.getOperator());
        if ("is_null".equals(operator) || "is_not_null".equals(operator)) {
            return false;
        }
        Object value = condition.getValue();
        return value == null || (value instanceof String && StringUtils.isBlank((String) value));
    }

    private String normalizeOperator(String operator) {
        String normalized = StringUtils.defaultIfBlank(operator, "eq").trim().toLowerCase();
        return switch (normalized) {
            case "=", "eq" -> "eq";
            case "!=", "<>", "ne" -> "ne";
            case "like", "left_like", "right_like", "gt", "ge", "gte", "lt", "le", "lte",
                    "in", "between", "is_null", "is_not_null" -> normalized;
            case ">", "<" -> "gt".equals(normalized) ? "gt" : normalized;
            default -> throw new BusinessException("不支持的查询操作符: " + operator);
        };
    }

    private String resolveRelation(String relation) {
        return "OR".equalsIgnoreCase(relation) ? "OR" : "AND";
    }

    private String buildCustomConditionSql(MapSqlParameterSource params, String columnName, String operator,
                                           CustomQueryConditionDTO condition, int index) {
        String paramName = "custom_" + index + "_" + columnName.replace(".", "_");
        Object value = condition.getValue();

        return switch (operator) {
            case "like" -> addCustomBinaryCondition(params, columnName, "LIKE", paramName, "%" + value + "%");
            case "left_like" -> addCustomBinaryCondition(params, columnName, "LIKE", paramName, "%" + value);
            case "right_like" -> addCustomBinaryCondition(params, columnName, "LIKE", paramName, value + "%");
            case "eq" -> addCustomBinaryCondition(params, columnName, "=", paramName, value);
            case "ne" -> addCustomBinaryCondition(params, columnName, "!=", paramName, value);
            case "gt", ">" -> addCustomBinaryCondition(params, columnName, ">", paramName, value);
            case "ge", "gte" -> addCustomBinaryCondition(params, columnName, ">=", paramName, value);
            case "lt", "<" -> addCustomBinaryCondition(params, columnName, "<", paramName, value);
            case "le", "lte" -> addCustomBinaryCondition(params, columnName, "<=", paramName, value);
            case "in" -> addCustomInCondition(params, columnName, paramName, value);
            case "between" -> addCustomBetweenCondition(params, columnName, paramName, condition);
            case "is_null" -> columnName + " IS NULL";
            case "is_not_null" -> columnName + " IS NOT NULL";
            default -> throw new BusinessException("不支持的查询操作符: " + operator);
        };
    }

    private String addCustomBinaryCondition(MapSqlParameterSource params, String columnName, String operator,
                                            String paramName, Object value) {
        params.addValue(paramName, value);
        return columnName + " " + operator + " :" + paramName;
    }

    private String addCustomInCondition(MapSqlParameterSource params, String columnName, String paramName, Object value) {
        List<?> values = normalizeInValues(value);
        if (values == null || values.isEmpty()) {
            return null;
        }
        params.addValue(paramName, values);
        return columnName + " IN (:" + paramName + ")";
    }

    private String addCustomBetweenCondition(MapSqlParameterSource params, String columnName, String paramName,
                                             CustomQueryConditionDTO condition) {
        List<?> range = normalizeBetweenValues(condition);
        if (range == null || range.size() < 2) {
            return null;
        }
        params.addValue(paramName + "_start", range.get(0));
        params.addValue(paramName + "_end", range.get(1));
        return columnName + " BETWEEN :" + paramName + "_start AND :" + paramName + "_end";
    }

    private List<?> normalizeBetweenValues(CustomQueryConditionDTO condition) {
        Object value = condition.getValue();
        if (value instanceof List && ((List<?>) value).size() >= 2) {
            return (List<?>) value;
        }
        if (condition.getValueEnd() == null
                || (condition.getValueEnd() instanceof String && StringUtils.isBlank((String) condition.getValueEnd()))) {
            return null;
        }
        return Arrays.asList(value, condition.getValueEnd());
    }

    private boolean shouldSkipSearchField(String fieldName, Object value, Set<String> allowedSearchFields) {
        if (!allowedSearchFields.contains(fieldName)) {
            return true;
        }
        return value == null || (value instanceof String && StringUtils.isBlank((String) value));
    }

    private String resolveSearchColumn(String fieldName, Map<String, String> columnMapping) {
        String columnName = columnMapping.getOrDefault(fieldName, DynamicQueryGenerator.camelToSnake(fieldName));
        if (DynamicQueryGenerator.containsSqlInjection(columnName)) {
            log.warn("[DynamicCrudRepository] 检测到SQL注入尝试, fieldName={}", fieldName);
            return null;
        }
        return columnName;
    }

    String buildCustomSelectClause(List<String> selectedFields, Set<String> allowedFields,
                                           Map<String, String> columnMapping) {
        if (selectedFields == null || selectedFields.isEmpty()) {
            return "SELECT *";
        }
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        addSelectedColumn(columns, primaryKeyFieldSupplier.get(), allowedFields, columnMapping);
        addSelectedColumn(columns, primaryKeyColumnSupplier.get(), allowedFields, columnMapping);
        for (String fieldName : selectedFields) {
            addSelectedColumn(columns, fieldName, allowedFields, columnMapping);
        }
        if (columns.isEmpty()) {
            return "SELECT *";
        }
        return "SELECT " + String.join(", ", columns);
    }

    private void addSelectedColumn(Set<String> columns, String fieldName, Set<String> allowedFields,
                                   Map<String, String> columnMapping) {
        if (StringUtils.isBlank(fieldName) || !allowedFields.contains(fieldName)) {
            return;
        }
        String columnName = columnMapping.getOrDefault(fieldName, DynamicQueryGenerator.camelToSnake(fieldName));
        if (!isKnownColumn(columnName, columnMapping) || DynamicQueryGenerator.containsSqlInjection(columnName)) {
            return;
        }
        identifierValidator.accept(columnName);
        columns.add(columnName);
    }

    private boolean isKnownColumn(String columnName, Map<String, String> columnMapping) {
        return columnMapping.containsValue(columnName);
    }

    private String resolveSearchType(String fieldName, Map<String, String> searchTypeMap) {
        return resolveSearchType(fieldName, searchTypeMap, null);
    }

    private String resolveSearchType(String fieldName, Map<String, String> searchTypeMap, Object value) {
        String searchType = searchTypeMap == null
                ? "eq"
                : searchTypeMap.getOrDefault(fieldName, "eq");
        // 已展开的本级+子集（1,5 / List）即使前端仍传 eq，也必须按 IN 查
        if (isMultiSearchValue(value) && ("eq".equalsIgnoreCase(searchType) || StringUtils.isBlank(searchType))) {
            return "in";
        }
        return searchType;
    }

    private boolean isMultiSearchValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size() > 1
                    || (collection.size() == 1 && String.valueOf(collection.iterator().next()).contains(","));
        }
        if (value instanceof Object[] array) {
            return array.length > 1;
        }
        if (value instanceof String text) {
            return text.contains(",");
        }
        return false;
    }

    private void appendWhereJoiner(StringBuilder whereClause) {
        if (whereClause.length() > 0) {
            whereClause.append(" AND ");
        }
    }
}
