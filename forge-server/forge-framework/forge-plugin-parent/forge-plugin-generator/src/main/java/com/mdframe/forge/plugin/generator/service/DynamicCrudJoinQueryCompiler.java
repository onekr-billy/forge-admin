package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeDatabaseDialect;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 动态 CRUD 左连接查询计划编译器。
 *
 * <p>仓储负责执行 SQL 与事务边界，本类负责把 Join 描述编译为稳定的查询计划；
 * 子表文本聚合的数据库差异交给 {@link RuntimeDatabaseDialect} 策略处理。</p>
 */
final class DynamicCrudJoinQueryCompiler {

    private static final Pattern SAFE_ALIAS = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,127}$");

    record QueryPlan(String fromClause, boolean distinctMainRows) {
    }

    private final Consumer<String> tableValidator;
    private final Consumer<String> identifierValidator;
    private final Function<String, Set<String>> tableColumns;
    private final Predicate<String> logicDeleteDetector;
    private final Supplier<RuntimeDatabaseDialect> dialectSupplier;
    private final BooleanSupplier tenantEnabled;
    private final Supplier<String> tenantColumnSupplier;
    private final Supplier<String> logicDeleteColumnSupplier;

    DynamicCrudJoinQueryCompiler(Consumer<String> tableValidator,
                                 Consumer<String> identifierValidator,
                                 Function<String, Set<String>> tableColumns,
                                 Predicate<String> logicDeleteDetector,
                                 Supplier<RuntimeDatabaseDialect> dialectSupplier,
                                 BooleanSupplier tenantEnabled,
                                 Supplier<String> tenantColumnSupplier,
                                 Supplier<String> logicDeleteColumnSupplier) {
        this.tableValidator = tableValidator;
        this.identifierValidator = identifierValidator;
        this.tableColumns = tableColumns;
        this.logicDeleteDetector = logicDeleteDetector;
        this.dialectSupplier = dialectSupplier;
        this.tenantEnabled = tenantEnabled;
        this.tenantColumnSupplier = tenantColumnSupplier;
        this.logicDeleteColumnSupplier = logicDeleteColumnSupplier;
    }

    void validate(String mainTableName,
                  List<DynamicCrudRepository.JoinField> selectFields,
                  List<DynamicCrudRepository.JoinSpec> joins) {
        tableValidator.accept(mainTableName);
        identifierValidator.accept("t0");
        if (selectFields == null || selectFields.isEmpty()) {
            throw new BusinessException("左连接查询字段不能为空");
        }
        for (DynamicCrudRepository.JoinField field : selectFields) {
            identifierValidator.accept(field.tableAlias());
            identifierValidator.accept(field.columnName());
            validateAlias(field.fieldName());
        }
        for (DynamicCrudRepository.JoinSpec join : joins) {
            tableValidator.accept(join.tableName());
            identifierValidator.accept(join.tableAlias());
            identifierValidator.accept(join.joinColumn());
            identifierValidator.accept(join.mainColumn());
        }
    }

    String buildSelectClause(List<DynamicCrudRepository.JoinField> fields) {
        return buildSelectClause(fields, false);
    }

    String buildSelectClause(List<DynamicCrudRepository.JoinField> fields, boolean distinct) {
        LinkedHashSet<String> selectItems = new LinkedHashSet<>();
        for (DynamicCrudRepository.JoinField field : fields) {
            selectItems.add(qualify(field.tableAlias(), field.columnName())
                + " AS " + quote(field.fieldName()));
        }
        return "SELECT " + (distinct ? "DISTINCT " : "") + String.join(", ", selectItems);
    }

    QueryPlan resolveListPlan(String mainTableName,
                              List<DynamicCrudRepository.JoinField> selectFields,
                              List<DynamicCrudRepository.JoinSpec> joins,
                              boolean aggregateChildren) {
        boolean mainTableOnly = selectsOnlyMainTable(selectFields);
        boolean shouldAggregate = aggregateChildren && !mainTableOnly;
        String fromClause = shouldAggregate
            ? buildAggregatedFromClause(mainTableName, joins, selectFields)
            : buildJoinedFromClause(mainTableName, joins);
        return new QueryPlan(fromClause, shouldAggregate || mainTableOnly);
    }

    String buildJoinedFromClause(String mainTableName, List<DynamicCrudRepository.JoinSpec> joins) {
        StringBuilder sql = new StringBuilder("FROM ").append(mainTableName).append(" t0");
        Long tenantId = TenantContextHolder.getTenantId();
        for (DynamicCrudRepository.JoinSpec join : joins) {
            sql.append(" LEFT JOIN ")
                .append(join.tableName()).append(" ").append(join.tableAlias())
                .append(" ON ").append(qualify(join.tableAlias(), join.joinColumn()))
                .append(" = ").append(qualify("t0", join.mainColumn()));
            String tenantColumn = tenantColumnSupplier.get();
            if (tenantId != null && tenantEnabled.getAsBoolean()
                && tableColumns.apply(join.tableName()).contains(tenantColumn)) {
                sql.append(" AND ").append(qualify(join.tableAlias(), tenantColumn)).append(" = :tenantId");
            }
            if (logicDeleteDetector.test(join.tableName())) {
                sql.append(" AND ").append(qualify(join.tableAlias(), logicDeleteColumnSupplier.get()))
                    .append(" = :logicActiveValue");
            }
        }
        return sql.toString();
    }

    String qualify(String tableAlias, String columnName) {
        return StringUtils.isBlank(tableAlias) ? columnName : tableAlias + "." + columnName;
    }

    private boolean selectsOnlyMainTable(List<DynamicCrudRepository.JoinField> fields) {
        return fields != null && fields.stream().allMatch(field -> "t0".equals(field.tableAlias()));
    }

    private String buildAggregatedFromClause(String mainTableName,
                                             List<DynamicCrudRepository.JoinSpec> joins,
                                             List<DynamicCrudRepository.JoinField> selectFields) {
        StringBuilder sql = new StringBuilder("FROM ").append(mainTableName).append(" t0");
        for (DynamicCrudRepository.JoinSpec join : joins) {
            sql.append(" LEFT JOIN (").append(buildAggregatedChildSelect(join, selectFields)).append(") ")
                .append(join.tableAlias())
                .append(" ON ").append(qualify(join.tableAlias(), join.joinColumn()))
                .append(" = ").append(qualify("t0", join.mainColumn()));
        }
        return sql.toString();
    }

    private String buildAggregatedChildSelect(DynamicCrudRepository.JoinSpec join,
                                              List<DynamicCrudRepository.JoinField> selectFields) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        columns.add(join.joinColumn());
        if (selectFields != null) {
            for (DynamicCrudRepository.JoinField field : selectFields) {
                if (field != null && join.tableAlias().equals(field.tableAlias())
                    && StringUtils.isNotBlank(field.columnName())) {
                    columns.add(field.columnName());
                }
            }
        }

        Set<String> availableColumns = tableColumns.apply(join.tableName());
        boolean hasId = availableColumns.contains("id");
        StringBuilder sql = new StringBuilder("SELECT ");
        boolean first = true;
        for (String column : columns) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            if (column.equals(join.joinColumn())) {
                sql.append(quote(column));
            } else {
                sql.append(aggregate(column, hasId)).append(" AS ").append(quote(column));
            }
        }
        sql.append(" FROM ").append(join.tableName());
        appendChildFilters(sql, join.tableName(), availableColumns);
        return sql.append(" GROUP BY ").append(quote(join.joinColumn())).toString();
    }

    private void appendChildFilters(StringBuilder sql, String tableName, Set<String> availableColumns) {
        StringBuilder where = new StringBuilder();
        String tenantColumn = tenantColumnSupplier.get();
        if (TenantContextHolder.getTenantId() != null && tenantEnabled.getAsBoolean()
            && availableColumns.contains(tenantColumn)) {
            where.append(quote(tenantColumn)).append(" = :tenantId");
        }
        if (logicDeleteDetector.test(tableName)) {
            if (!where.isEmpty()) {
                where.append(" AND ");
            }
            where.append(quote(logicDeleteColumnSupplier.get())).append(" = :logicActiveValue");
        }
        if (!where.isEmpty()) {
            sql.append(" WHERE ").append(where);
        }
    }

    private String aggregate(String column, boolean hasId) {
        String quoted = quote(column);
        String orderColumn = hasId ? quote("id") : quoted;
        return dialectSupplier.get().stringAggregate(quoted, orderColumn, "、");
    }

    private String quote(String identifier) {
        validateAlias(identifier);
        return dialectSupplier.get().quote(identifier);
    }

    private void validateAlias(String alias) {
        if (StringUtils.isBlank(alias) || !SAFE_ALIAS.matcher(alias).matches()) {
            throw new BusinessException("非法字段别名: " + alias);
        }
    }
}
