package com.mdframe.forge.plugin.generator.service;

import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditRecordIds;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditTenantSupport;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditTransactionHolder;
import com.mdframe.forge.plugin.generator.dto.CustomQueryConditionDTO;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeDatabaseDialectFactory;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.RuntimeJdbcTemplateProvider;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态CRUD数据访问层
 * 使用NamedParameterJdbcTemplate防止SQL注入，支持多种数据库
 *
 * @author forge
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamicCrudRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final RuntimeJdbcTemplateProvider jdbcTemplateProvider;
    private final RuntimeDatabaseDialectFactory dialectFactory;

    private static final String DEFAULT_PRIMARY_KEY = "id";
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");
    private static final Pattern SAFE_ALIAS = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,127}$");

    public record JoinField(String fieldName, String tableAlias, String columnName) {
    }

    public record JoinSpec(String tableName, String tableAlias, String joinColumn, String mainColumn) {
    }

    public record SqlCondition(String sql, Map<String, Object> params) {
    }

    private volatile DynamicCrudTableMetadataGateway tableMetadataGateway;

    private NamedParameterJdbcTemplate jdbc() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        return context == null ? namedJdbcTemplate : jdbcTemplateProvider.namedJdbcTemplate(context);
    }

    // ==================== 查询操作 ====================

    /**
     * 分页查询
     */
    public Page<Map<String, Object>> selectPage(String tableName, int pageNum, int pageSize,
                                                  Map<String, Object> searchParams,
                                                  Set<String> allowedSearchFields,
                                                  Map<String, String> searchTypeMap,
                                                  Map<String, String> columnMapping,
                                                  String orderBy) {
        return selectPage(tableName, pageNum, pageSize, searchParams, allowedSearchFields,
                searchTypeMap, columnMapping, orderBy, null);
    }

    public Page<Map<String, Object>> selectPage(String tableName, int pageNum, int pageSize,
                                                  Map<String, Object> searchParams,
                                                  Set<String> allowedSearchFields,
                                                  Map<String, String> searchTypeMap,
                                                  Map<String, String> columnMapping,
                                                  String orderBy,
                                                  SqlCondition dataScopeCondition) {
        validateTableName(tableName);

        StringBuilder whereClause = buildBaseWhereClause(tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendSearchConditions(whereClause, params, searchParams, allowedSearchFields, searchTypeMap, columnMapping);

        String countSql = buildSelectSql("SELECT COUNT(*)", tableName, whereClause);
        Long total = jdbc().queryForObject(countSql, params, Long.class);

        String dataSql = buildPageDataSql(tableName, whereClause, orderBy, pageNum, pageSize);

        List<Map<String, Object>> records = jdbc().queryForList(dataSql, params);

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize, total != null ? total : 0);
        page.setRecords(records);
        return page;
    }

    /**
     * 多模型左连接分页查询。selectFields 的 fieldName 会作为返回字段别名，调用方无需再做 snake_case 转换。
     */
    public Page<Map<String, Object>> selectJoinedPage(String mainTableName,
                                                      List<JoinField> selectFields,
                                                      List<JoinSpec> joins,
                                                      int pageNum,
                                                      int pageSize,
                                                      Map<String, Object> searchParams,
                                                      Set<String> allowedSearchFields,
                                                      Map<String, String> searchTypeMap,
                                                      Map<String, String> fieldColumnMapping,
                                                      String orderBy) {
        return selectJoinedPage(mainTableName, selectFields, joins, pageNum, pageSize, searchParams,
                allowedSearchFields, searchTypeMap, fieldColumnMapping, orderBy, null, true);
    }

    public Page<Map<String, Object>> selectJoinedPage(String mainTableName,
                                                      List<JoinField> selectFields,
                                                      List<JoinSpec> joins,
                                                      int pageNum,
                                                      int pageSize,
                                                      Map<String, Object> searchParams,
                                                      Set<String> allowedSearchFields,
                                                      Map<String, String> searchTypeMap,
                                                      Map<String, String> fieldColumnMapping,
                                                      String orderBy,
                                                      SqlCondition dataScopeCondition,
                                                      boolean aggregateChildren) {
        validateJoinQuery(mainTableName, selectFields, joins);

        StringBuilder whereClause = buildBaseWhereClause(mainTableName, "t0");
        MapSqlParameterSource params = buildBaseQueryParams("t0");
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendSearchConditions(whereClause, params, searchParams, allowedSearchFields, searchTypeMap, fieldColumnMapping);

        DynamicCrudJoinQueryCompiler.QueryPlan joinedSql = joinQueryCompiler()
                .resolveListPlan(mainTableName, selectFields, joins, aggregateChildren);
        String countSql = (joinedSql.distinctMainRows() ? "SELECT COUNT(DISTINCT " + qualifyPrimaryKey("t0") + ") " : "SELECT COUNT(*) ")
                + joinedSql.fromClause() + buildWhereSql(whereClause);
        Long total = jdbc().queryForObject(countSql, params, Long.class);

        String dataSql = paginateSql(buildJoinSelectClause(selectFields, joinedSql.distinctMainRows()) + " " + joinedSql.fromClause()
                + buildWhereSql(whereClause) + buildOrderByClause(orderBy), pageNum, pageSize);
        List<Map<String, Object>> records = jdbc().queryForList(dataSql, params);

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize, total != null ? total : 0);
        page.setRecords(records);
        return page;
    }

    /**
     * 多模型左连接详情查询。
     */
    public Map<String, Object> selectJoinedById(String mainTableName,
                                                Object id,
                                                List<JoinField> selectFields,
                                                List<JoinSpec> joins) {
        return selectJoinedById(mainTableName, id, selectFields, joins, null);
    }

    public Map<String, Object> selectJoinedById(String mainTableName,
                                                Object id,
                                                List<JoinField> selectFields,
                                                List<JoinSpec> joins,
                                                SqlCondition dataScopeCondition) {
        validateJoinQuery(mainTableName, selectFields, joins);

        StringBuilder whereClause = new StringBuilder(qualifyPrimaryKey("t0") + " = :id");
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), mainTableName, "t0");
        MapSqlParameterSource params = buildBaseQueryParams("t0");
        appendIdParam(params, id);
        appendSqlCondition(whereClause, params, dataScopeCondition);

        String sql = buildJoinSelectClause(selectFields) + " " + joinQueryCompiler().buildJoinedFromClause(mainTableName, joins)
                + buildWhereSql(whereClause);
        sql = limitSql(sql, 1);
        List<Map<String, Object>> results = jdbc().queryForList(sql, params);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 限量查询列表，用于动态导出。
     */
    public List<Map<String, Object>> selectList(String tableName,
                                                Map<String, Object> searchParams,
                                                Set<String> allowedSearchFields,
                                                Map<String, String> searchTypeMap,
                                                Map<String, String> columnMapping,
                                                String orderBy,
                                                int limit) {
        return selectList(tableName, searchParams, allowedSearchFields, searchTypeMap, columnMapping, orderBy, limit, null);
    }

    public List<Map<String, Object>> selectList(String tableName,
                                                Map<String, Object> searchParams,
                                                Set<String> allowedSearchFields,
                                                Map<String, String> searchTypeMap,
                                                Map<String, String> columnMapping,
                                                String orderBy,
                                                int limit,
                                                SqlCondition dataScopeCondition) {
        validateTableName(tableName);

        StringBuilder whereClause = buildBaseWhereClause(tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendSearchConditions(whereClause, params, searchParams, allowedSearchFields, searchTypeMap, columnMapping);

        String dataSql = buildSelectSql("SELECT *", tableName, whereClause);
        dataSql += buildOrderByClause(orderBy);
        dataSql = limitSql(dataSql, Math.max(1, limit));

        return jdbc().queryForList(dataSql, params);
    }

    /**
     * 统计动态列表数据量，用于智能导出阈值判断。
     */
    public long countList(String tableName,
                          Map<String, Object> searchParams,
                          Set<String> allowedSearchFields,
                          Map<String, String> searchTypeMap,
                          Map<String, String> columnMapping,
                          SqlCondition dataScopeCondition) {
        validateTableName(tableName);

        StringBuilder whereClause = buildBaseWhereClause(tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendSearchConditions(whereClause, params, searchParams, allowedSearchFields, searchTypeMap, columnMapping);

        String countSql = buildSelectSql("SELECT COUNT(*)", tableName, whereClause);
        Long total = jdbc().queryForObject(countSql, params, Long.class);
        return total != null ? total : 0L;
    }

    /**
     * 判断指定列是否存在非空数据（含租户隔离与逻辑删除过滤）。
     * 使用 LIMIT 1 存在性探测，避免大表 COUNT 全扫的性能开销。
     * 用于字段删除守卫：只需知道有无数据，无需精确计数。
     */
    public boolean hasColumnData(String tableName, String columnName) {
        validateTableName(tableName);
        if (StringUtils.isBlank(columnName) || !SAFE_IDENTIFIER.matcher(columnName).matches()) {
            throw new BusinessException("非法列名: " + columnName);
        }
        StringBuilder whereClause = buildBaseWhereClause(tableName);
        appendWhereCondition(whereClause, columnName + " IS NOT NULL");
        MapSqlParameterSource params = buildBaseQueryParams();
        String sql = buildSelectSql("SELECT 1", tableName, whereClause) + " LIMIT 1";
        return !jdbc().queryForList(sql, params).isEmpty();
    }

    /**
     * 分页查询动态列表记录，不执行 count，用于异步导出分批读取。
     */
    public List<Map<String, Object>> selectPageRecords(String tableName,
                                                       int pageNum,
                                                       int pageSize,
                                                       Map<String, Object> searchParams,
                                                       Set<String> allowedSearchFields,
                                                       Map<String, String> searchTypeMap,
                                                       Map<String, String> columnMapping,
                                                       String orderBy,
                                                       SqlCondition dataScopeCondition) {
        validateTableName(tableName);

        StringBuilder whereClause = buildBaseWhereClause(tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendSearchConditions(whereClause, params, searchParams, allowedSearchFields, searchTypeMap, columnMapping);

        String dataSql = buildPageDataSql(tableName, whereClause, orderBy, pageNum, pageSize);
        return jdbc().queryForList(dataSql, params);
    }

    /**
     * 统计左连接动态列表数据量，用于智能导出阈值判断。
     */
    public long countJoined(String mainTableName,
                            List<JoinField> selectFields,
                            List<JoinSpec> joins,
                            Map<String, Object> searchParams,
                            Set<String> allowedSearchFields,
                            Map<String, String> searchTypeMap,
                            Map<String, String> fieldColumnMapping,
                            SqlCondition dataScopeCondition) {
        return countJoined(mainTableName, selectFields, joins, searchParams, allowedSearchFields,
                searchTypeMap, fieldColumnMapping, dataScopeCondition, true);
    }

    public long countJoined(String mainTableName,
                            List<JoinField> selectFields,
                            List<JoinSpec> joins,
                            Map<String, Object> searchParams,
                            Set<String> allowedSearchFields,
                            Map<String, String> searchTypeMap,
                            Map<String, String> fieldColumnMapping,
                            SqlCondition dataScopeCondition,
                            boolean aggregateChildren) {
        validateJoinQuery(mainTableName, selectFields, joins);

        StringBuilder whereClause = buildBaseWhereClause(mainTableName, "t0");
        MapSqlParameterSource params = buildBaseQueryParams("t0");
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendSearchConditions(whereClause, params, searchParams, allowedSearchFields, searchTypeMap, fieldColumnMapping);

        DynamicCrudJoinQueryCompiler.QueryPlan joinedSql = joinQueryCompiler()
                .resolveListPlan(mainTableName, selectFields, joins, aggregateChildren);
        String countSql = (joinedSql.distinctMainRows() ? "SELECT COUNT(DISTINCT " + qualifyPrimaryKey("t0") + ") " : "SELECT COUNT(*) ")
                + joinedSql.fromClause() + buildWhereSql(whereClause);
        Long total = jdbc().queryForObject(countSql, params, Long.class);
        return total != null ? total : 0L;
    }

    /**
     * 分页查询左连接动态列表记录，不执行 count，用于异步导出分批读取。
     */
    public List<Map<String, Object>> selectJoinedPageRecords(String mainTableName,
                                                             List<JoinField> selectFields,
                                                             List<JoinSpec> joins,
                                                             int pageNum,
                                                             int pageSize,
                                                             Map<String, Object> searchParams,
                                                             Set<String> allowedSearchFields,
                                                             Map<String, String> searchTypeMap,
                                                             Map<String, String> fieldColumnMapping,
                                                             String orderBy,
                                                             SqlCondition dataScopeCondition) {
        return selectJoinedPageRecords(mainTableName, selectFields, joins, pageNum, pageSize, searchParams,
                allowedSearchFields, searchTypeMap, fieldColumnMapping, orderBy, dataScopeCondition, true);
    }

    public List<Map<String, Object>> selectJoinedPageRecords(String mainTableName,
                                                             List<JoinField> selectFields,
                                                             List<JoinSpec> joins,
                                                             int pageNum,
                                                             int pageSize,
                                                             Map<String, Object> searchParams,
                                                             Set<String> allowedSearchFields,
                                                             Map<String, String> searchTypeMap,
                                                             Map<String, String> fieldColumnMapping,
                                                             String orderBy,
                                                             SqlCondition dataScopeCondition,
                                                             boolean aggregateChildren) {
        validateJoinQuery(mainTableName, selectFields, joins);

        StringBuilder whereClause = buildBaseWhereClause(mainTableName, "t0");
        MapSqlParameterSource params = buildBaseQueryParams("t0");
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendSearchConditions(whereClause, params, searchParams, allowedSearchFields, searchTypeMap, fieldColumnMapping);

        DynamicCrudJoinQueryCompiler.QueryPlan joinedSql = joinQueryCompiler()
                .resolveListPlan(mainTableName, selectFields, joins, aggregateChildren);
        String dataSql = paginateSql(buildJoinSelectClause(selectFields, joinedSql.distinctMainRows()) + " " + joinedSql.fromClause()
                + buildWhereSql(whereClause) + buildOrderByClause(orderBy), pageNum, pageSize);
        return jdbc().queryForList(dataSql, params);
    }

    /**
     * 自定义分页查询。
     */
    public Page<Map<String, Object>> selectCustomPage(String tableName, int pageNum, int pageSize,
                                                      List<String> selectedFields,
                                                      List<CustomQueryConditionDTO> conditions,
                                                      Set<String> allowedFields,
                                                      Map<String, String> columnMapping,
                                                      String orderBy) {
        return selectCustomPage(tableName, pageNum, pageSize, selectedFields, conditions,
                allowedFields, columnMapping, orderBy, null);
    }

    public Page<Map<String, Object>> selectCustomPage(String tableName, int pageNum, int pageSize,
                                                      List<String> selectedFields,
                                                      List<CustomQueryConditionDTO> conditions,
                                                      Set<String> allowedFields,
                                                      Map<String, String> columnMapping,
                                                      String orderBy,
                                                      SqlCondition dataScopeCondition) {
        validateTableName(tableName);

        StringBuilder whereClause = buildBaseWhereClause(tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendCustomConditions(whereClause, params, conditions, allowedFields, columnMapping);

        String countSql = buildSelectSql("SELECT COUNT(*)", tableName, whereClause);
        logDynamicSql("自定义查询统计", countSql, params);
        Long total = jdbc().queryForObject(countSql, params, Long.class);

        String dataSql = buildSelectSql(
                buildCustomSelectClause(selectedFields, allowedFields, columnMapping),
                tableName,
                whereClause
        );
        dataSql += buildOrderByClause(orderBy);
        dataSql = paginateSql(dataSql, pageNum, pageSize);
        logDynamicSql("自定义查询数据", dataSql, params);

        List<Map<String, Object>> records = jdbc().queryForList(dataSql, params);

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize, total != null ? total : 0);
        page.setRecords(records);
        return page;
    }

    /**
     * 多模型自定义分页查询，支持使用子表字段作为展示字段或查询条件。
     */
    public Page<Map<String, Object>> selectJoinedCustomPage(String mainTableName,
                                                            List<JoinField> selectFields,
                                                            List<JoinSpec> joins,
                                                            int pageNum,
                                                            int pageSize,
                                                            List<CustomQueryConditionDTO> conditions,
                                                            Set<String> allowedFields,
                                                            Map<String, String> fieldColumnMapping,
                                                            String orderBy) {
        return selectJoinedCustomPage(mainTableName, selectFields, joins, pageNum, pageSize, conditions,
                allowedFields, fieldColumnMapping, orderBy, null, true);
    }

    public Page<Map<String, Object>> selectJoinedCustomPage(String mainTableName,
                                                            List<JoinField> selectFields,
                                                            List<JoinSpec> joins,
                                                            int pageNum,
                                                            int pageSize,
                                                            List<CustomQueryConditionDTO> conditions,
                                                            Set<String> allowedFields,
                                                            Map<String, String> fieldColumnMapping,
                                                            String orderBy,
                                                            SqlCondition dataScopeCondition,
                                                            boolean aggregateChildren) {
        validateJoinQuery(mainTableName, selectFields, joins);

        StringBuilder whereClause = buildBaseWhereClause(mainTableName, "t0");
        MapSqlParameterSource params = buildBaseQueryParams("t0");
        appendSqlCondition(whereClause, params, dataScopeCondition);
        appendCustomConditions(whereClause, params, conditions, allowedFields, fieldColumnMapping);

        DynamicCrudJoinQueryCompiler.QueryPlan joinedSql = joinQueryCompiler()
                .resolveListPlan(mainTableName, selectFields, joins, aggregateChildren);
        String countSql = (joinedSql.distinctMainRows() ? "SELECT COUNT(DISTINCT " + qualifyPrimaryKey("t0") + ") " : "SELECT COUNT(*) ")
                + joinedSql.fromClause() + buildWhereSql(whereClause);
        logDynamicSql("自定义查询统计(左连接)", countSql, params);
        Long total = jdbc().queryForObject(countSql, params, Long.class);

        String dataSql = paginateSql(buildJoinSelectClause(selectFields, joinedSql.distinctMainRows()) + " " + joinedSql.fromClause()
                + buildWhereSql(whereClause) + buildOrderByClause(orderBy), pageNum, pageSize);
        logDynamicSql("自定义查询数据(左连接)", dataSql, params);
        List<Map<String, Object>> records = jdbc().queryForList(dataSql, params);

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize, total != null ? total : 0);
        page.setRecords(records);
        return page;
    }

    /**
     * 添加搜索条件
     */
    private void appendSearchConditions(StringBuilder whereClause, MapSqlParameterSource params,
                                        Map<String, Object> searchParams,
                                        Set<String> allowedSearchFields,
                                        Map<String, String> searchTypeMap,
                                        Map<String, String> columnMapping) {
        queryConditionCompiler().appendSearchConditions(
                whereClause, params, searchParams, allowedSearchFields, searchTypeMap, columnMapping);
    }

    private void appendCustomConditions(StringBuilder whereClause, MapSqlParameterSource params,
                                        List<CustomQueryConditionDTO> conditions,
                                        Set<String> allowedFields,
                                        Map<String, String> columnMapping) {
        queryConditionCompiler().appendCustomConditions(
                whereClause, params, conditions, allowedFields, columnMapping);
    }

    private String buildCustomSelectClause(List<String> selectedFields, Set<String> allowedFields,
                                           Map<String, String> columnMapping) {
        return queryConditionCompiler().buildCustomSelectClause(selectedFields, allowedFields, columnMapping);
    }

    private DynamicCrudQueryConditionCompiler queryConditionCompiler() {
        return new DynamicCrudQueryConditionCompiler(
                this::validateIdentifier, this::primaryKeyField, this::primaryKeyColumn);
    }

    private StringBuilder buildBaseWhereClause(String tableName) {
        return buildBaseWhereClause(tableName, null);
    }

    private StringBuilder buildBaseWhereClause(String tableName, String tableAlias) {
        StringBuilder whereClause = new StringBuilder();
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName, tableAlias);
        return whereClause;
    }

    private MapSqlParameterSource buildBaseQueryParams() {
        return buildBaseQueryParams(null);
    }

    private MapSqlParameterSource buildBaseQueryParams(String tableAlias) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        appendBaseQueryConditions(new StringBuilder(), params, null, tableAlias);
        appendLogicDeleteParam(params);
        return params;
    }

    private String buildPageDataSql(String tableName, StringBuilder whereClause, String orderBy, int pageNum, int pageSize) {
        String dataSql = buildSelectSql("SELECT *", tableName, whereClause);
        dataSql += buildOrderByClause(orderBy);
        return paginateSql(dataSql, pageNum, pageSize);
    }

    private String buildOrderByClause(String orderBy) {
        if (StringUtils.isNotBlank(orderBy)) {
            return " ORDER BY " + orderBy;
        }
        return " ORDER BY " + primaryKeyColumn() + " DESC";
    }

    private StringBuilder buildIdWhereClause(String tableName) {
        return buildIdWhereClause(tableName, primaryKeyColumn());
    }

    private StringBuilder buildIdWhereClause(String tableName, String primaryKeyColumn) {
        validateIdentifier(primaryKeyColumn);
        StringBuilder whereClause = new StringBuilder(primaryKeyColumn + " = :id");
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName);
        return whereClause;
    }

    private MapSqlParameterSource buildIdQueryParams(Object id) {
        MapSqlParameterSource params = buildBaseQueryParams();
        appendIdParam(params, id);
        return params;
    }

    private void appendBaseQueryConditions(StringBuilder whereClause, MapSqlParameterSource params, String tableName) {
        appendBaseQueryConditions(whereClause, params, tableName, null);
    }

    private void appendBaseQueryConditions(StringBuilder whereClause, MapSqlParameterSource params,
                                           String tableName, String tableAlias) {
        String tenantColumn = tenantColumn();
        if (tenantStrategyEnabled() && (tableName == null || getTableColumns(tableName).contains(tenantColumn))) {
            appendTenantWhereClause(whereClause, params, tableAlias);
        }
        if (tableName != null && hasDelFlag(tableName)) {
            appendWhereCondition(whereClause, qualifyColumn(tableAlias, logicDeleteColumn()) + " = :logicActiveValue");
            appendLogicDeleteParam(params);
        }
    }

    /**
     * 根据ID查询
     */
    public Map<String, Object> selectById(String tableName, Object id) {
        return selectById(tableName, id, null);
    }

    public Map<String, Object> selectById(String tableName, Object id, SqlCondition dataScopeCondition) {
        return selectById(tableName, primaryKeyColumn(), id, dataScopeCondition);
    }

    public Map<String, Object> selectById(String tableName,
                                          String primaryKeyColumn,
                                          Object id,
                                          SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);

        StringBuilder whereClause = buildIdWhereClause(tableName, primaryKeyColumn);
        MapSqlParameterSource params = buildIdQueryParams(id);
        appendSqlCondition(whereClause, params, dataScopeCondition);

        String sql = buildSelectSql("SELECT *", tableName, whereClause);
        List<Map<String, Object>> results = jdbc().queryForList(sql, params);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 批量查询：IN (:ids) 一次取回多条记录
     */
    public List<Map<String, Object>> selectByIds(String tableName,
                                                  String primaryKeyColumn,
                                                  List<?> ids,
                                                  SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource params = buildBaseQueryParams();
        params.addValue("ids", ids);
        StringBuilder whereClause = new StringBuilder(primaryKeyColumn + " IN (:ids)");
        appendBaseQueryConditions(whereClause, params, tableName);
        appendSqlCondition(whereClause, params, dataScopeCondition);

        String sql = buildSelectSql("SELECT *", tableName, whereClause);
        return jdbc().queryForList(sql, params);
    }

    /** 事务命令状态门禁使用的行锁读取，条件与普通详情查询完全一致。 */
    public Map<String, Object> selectByIdForUpdate(String tableName,
                                                   String primaryKeyColumn,
                                                   Object id,
                                                   SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);
        StringBuilder whereClause = buildIdWhereClause(tableName, primaryKeyColumn);
        MapSqlParameterSource params = buildIdQueryParams(id);
        appendSqlCondition(whereClause, params, dataScopeCondition);
        String sql = buildSelectSql("SELECT *", tableName, whereClause) + " FOR UPDATE";
        List<Map<String, Object>> results = jdbc().queryForList(sql, params);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Map<String, Object>> selectListByColumn(String tableName, String columnName, Object value) {
        validateTableName(tableName);
        validateIdentifier(columnName);
        if (value == null) {
            return List.of();
        }

        Object queryValue = normalizeColumnQueryValue(tableName, columnName, value);
        StringBuilder whereClause = new StringBuilder(columnName + " = :value");
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        params.addValue("value", queryValue);
        String orderColumn = getTableColumns(tableName).contains(primaryKeyColumn()) ? primaryKeyColumn() : columnName;
        String sql = buildSelectSql("SELECT *", tableName, whereClause) + " ORDER BY " + orderColumn + " ASC";
        return jdbc().queryForList(sql, params);
    }

    public List<Map<String, Object>> selectListByColumnIn(String tableName,
                                                          String columnName,
                                                          Collection<?> values) {
        return selectListByColumnIn(tableName, columnName, values, null);
    }

    public List<Map<String, Object>> selectListByColumnIn(String tableName,
                                                          String columnName,
                                                          Collection<?> values,
                                                          SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(columnName);
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<Object> nonNullValues = values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(Object.class::cast)
                .toList();
        if (nonNullValues.isEmpty()) {
            return List.of();
        }
        List<Object> queryValues = nonNullValues.stream()
                .map(value -> normalizeColumnQueryValue(tableName, columnName, value))
                .distinct()
                .toList();

        StringBuilder whereClause = new StringBuilder(columnName + " IN (:values)");
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        params.addValue("values", queryValues);
        appendSqlCondition(whereClause, params, dataScopeCondition);
        String orderColumn = getTableColumns(tableName).contains(primaryKeyColumn()) ? primaryKeyColumn() : columnName;
        String sql = buildSelectSql("SELECT *", tableName, whereClause) + " ORDER BY " + orderColumn + " ASC";
        return jdbc().queryForList(sql, params);
    }

    public List<Map<String, Object>> selectTreeChildren(String tableName,
                                                        String parentColumn,
                                                        Object parentValue,
                                                        String orderBy,
                                                        int limit) {
        return selectTreeChildren(tableName, parentColumn, parentValue, orderBy, limit, null);
    }

    public List<Map<String, Object>> selectTreeChildren(String tableName,
                                                        String parentColumn,
                                                        Object parentValue,
                                                        String orderBy,
                                                        int limit,
                                                        SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(parentColumn);

        boolean rootQuery = parentValue == null || StringUtils.isBlank(String.valueOf(parentValue));
        StringBuilder whereClause = rootQuery
                ? new StringBuilder("(" + parentColumn + " IS NULL OR " + parentColumn + " = :zeroValue OR " + parentColumn + " = :emptyValue)")
                : new StringBuilder(parentColumn + " = :parentValue");
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName);

        MapSqlParameterSource params = buildBaseQueryParams();
        if (rootQuery) {
            params.addValue("zeroValue", "0");
            params.addValue("emptyValue", "");
        } else {
            params.addValue("parentValue", parentValue);
        }
        appendSqlCondition(whereClause, params, dataScopeCondition);
        String sql = buildSelectSql("SELECT *", tableName, whereClause)
                + buildOrderByClause(orderBy);
        sql = limitSql(sql, Math.max(1, limit));
        return jdbc().queryForList(sql, params);
    }

    public boolean existsByColumn(String tableName, String columnName, Object value) {
        return existsByColumn(tableName, columnName, value, null);
    }

    public boolean existsByColumn(String tableName, String columnName, Object value, SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(columnName);
        if (value == null) {
            return false;
        }

        StringBuilder whereClause = new StringBuilder(columnName + " = :value");
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        params.addValue("value", value);
        appendSqlCondition(whereClause, params, dataScopeCondition);
        String sql = buildSelectSql("SELECT COUNT(1)", tableName, whereClause);
        Long count = jdbc().queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    public boolean existsByColumns(String tableName,
                                   Map<String, Object> columnValues,
                                   Object excludeId) {
        return existsByColumns(tableName, columnValues, excludeId, null);
    }

    public boolean existsByColumns(String tableName,
                                   Map<String, Object> columnValues,
                                   Object excludeId,
                                   SqlCondition dataScopeCondition) {
        return existsByColumns(tableName, columnValues, primaryKeyColumn(), excludeId, dataScopeCondition);
    }

    public boolean existsByColumns(String tableName,
                                   Map<String, Object> columnValues,
                                   String primaryKeyColumn,
                                   Object excludeId,
                                   SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);
        if (columnValues == null || columnValues.isEmpty()) {
            return false;
        }

        StringBuilder whereClause = new StringBuilder();
        MapSqlParameterSource params = buildBaseQueryParams();
        int index = 0;
        for (Map.Entry<String, Object> entry : columnValues.entrySet()) {
            String columnName = entry.getKey();
            validateIdentifier(columnName);
            String paramName = "uniqueValue" + index++;
            Object value = entry.getValue();
            if (value == null) {
                appendWhereCondition(whereClause, columnName + " IS NULL");
            } else {
                appendWhereCondition(whereClause, columnName + " = :" + paramName);
                params.addValue(paramName, value);
            }
        }
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName);
        if (excludeId != null) {
            appendWhereCondition(whereClause, primaryKeyColumn + " <> :excludeId");
            params.addValue("excludeId", excludeId);
        }
        appendSqlCondition(whereClause, params, dataScopeCondition);
        String sql = buildSelectSql("SELECT COUNT(1)", tableName, whereClause);
        Long count = jdbc().queryForObject(sql, params, Long.class);
        return count != null && count > 0;
    }

    // ==================== 新增操作 ====================

    /**
     * 新增记录
     */
    public int insert(String tableName, Map<String, Object> data) {
        validateTableName(tableName);

        Map<String, Object> insertData = prepareInsertData(tableName, data);
        DataAuditTransactionHolder.prepareWrite(tableName, primaryKeyColumn(), insertData.get(primaryKeyColumn()),
                DataAuditTransactionHolder.WriteKind.INSERT);
        int affected = jdbc().update(buildInsertSql(tableName, insertData), toSqlParams(insertData));
        DataAuditTransactionHolder.afterWrite(tableName, insertData.get(primaryKeyColumn()), insertData,
                DataAuditTransactionHolder.currentFieldSource(DataAuditSourceType.FORM),
                DataAuditTransactionHolder.WriteKind.INSERT, affected);
        return affected;
    }

    /**
     * 新增记录并返回自增主键。
     */
    public Long insertReturningId(String tableName, Map<String, Object> data) {
        Object key = insertReturningKey(tableName, data, DEFAULT_PRIMARY_KEY, true);
        if (key == null) {
            return null;
        }
        if (key instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(key));
    }

    /**
     * 新增记录并返回运行时主键。非自增主键直接返回入参中的主键值。
     */
    public Object insertReturningKey(String tableName,
                                     Map<String, Object> data,
                                     String primaryKeyColumn,
                                     boolean autoIncrement) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);

        Map<String, Object> insertData = prepareInsertData(tableName, data);
        if (!autoIncrement && !insertData.containsKey(primaryKeyColumn)) {
            throw new BusinessException("新增操作缺少主键字段: " + primaryKeyColumn);
        }
        DataAuditTransactionHolder.prepareWrite(tableName, primaryKeyColumn, insertData.get(primaryKeyColumn),
                DataAuditTransactionHolder.WriteKind.INSERT);
        Object generatedKey;
        if (!autoIncrement) {
            jdbc().update(buildInsertSql(tableName, insertData), toSqlParams(insertData));
            generatedKey = insertData.get(primaryKeyColumn);
        } else {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc().update(buildInsertSql(tableName, insertData), toSqlParams(insertData), keyHolder, new String[] { primaryKeyColumn });
            Number key = keyHolder.getKey();
            generatedKey = key == null ? insertData.get(primaryKeyColumn) : key;
        }
        DataAuditTransactionHolder.afterWrite(tableName, generatedKey, insertData,
                DataAuditTransactionHolder.currentFieldSource(DataAuditSourceType.FORM),
                DataAuditTransactionHolder.WriteKind.INSERT, 1);
        return generatedKey;
    }

    // ==================== 更新操作 ====================

    /**
     * 根据ID更新
     */
    public int updateById(String tableName, Object id, Map<String, Object> data) {
        return updateById(tableName, id, data, null);
    }

    public int updateById(String tableName, Object id, Map<String, Object> data, SqlCondition dataScopeCondition) {
        return updateById(tableName, primaryKeyColumn(), id, data, dataScopeCondition);
    }

    public int updateById(String tableName,
                          String primaryKeyColumn,
                          Object id,
                          Map<String, Object> data,
                          SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);

        Map<String, Object> updateData = prepareUpdateData(tableName, data, primaryKeyColumn);
        DataAuditTransactionHolder.prepareWrite(tableName, primaryKeyColumn, id, DataAuditTransactionHolder.WriteKind.UPDATE);
        MapSqlParameterSource params = toSqlParams(updateData, id);
        String sql = appendTenantCondition(buildUpdateSql(tableName, updateData, primaryKeyColumn), params, tableName);
        sql = appendLogicActiveCondition(sql, params, tableName);
        sql = appendSqlCondition(sql, params, dataScopeCondition);
        int affected = jdbc().update(sql, params);
        DataAuditTransactionHolder.afterWrite(tableName, id, updateData,
                DataAuditTransactionHolder.currentFieldSource(DataAuditSourceType.FORM),
                DataAuditTransactionHolder.WriteKind.UPDATE, affected);
        return affected;
    }

    /**
     * 在单条 UPDATE 中原子调整多个数值列，并把上下界和期望状态放进同一 WHERE。
     */
    public int adjustNumbersById(String tableName,
                                 String primaryKeyColumn,
                                 Object id,
                                 Map<String, BigDecimal> deltas,
                                 Map<String, BigDecimal> minimums,
                                 Map<String, BigDecimal> maximums,
                                 SqlCondition condition) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);
        if (id == null) {
            throw new BusinessException("数值调整缺少目标记录 ID");
        }
        if (deltas == null || deltas.isEmpty()) {
            throw new BusinessException("数值调整字段不能为空");
        }
        Set<String> tableColumns = getTableColumns(tableName);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", id);
        List<String> setClauses = new ArrayList<>();
        List<String> boundConditions = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, BigDecimal> entry : deltas.entrySet()) {
            String column = entry.getKey();
            validateIdentifier(column);
            if (!tableColumns.contains(column)) {
                throw new BusinessException("数值调整字段不存在");
            }
            BigDecimal delta = entry.getValue();
            if (delta == null) {
                throw new BusinessException("数值调整量不能为空");
            }
            String deltaParam = "adjustDelta" + index;
            params.addValue(deltaParam, delta);
            setClauses.add(column + " = " + column + " + :" + deltaParam);
            BigDecimal minimum = minimums == null ? null : minimums.get(column);
            if (minimum != null) {
                String minParam = "adjustMin" + index;
                params.addValue(minParam, minimum);
                boundConditions.add(column + " + :" + deltaParam + " >= :" + minParam);
            }
            BigDecimal maximum = maximums == null ? null : maximums.get(column);
            if (maximum != null) {
                String maxParam = "adjustMax" + index;
                params.addValue(maxParam, maximum);
                boundConditions.add(column + " + :" + deltaParam + " <= :" + maxParam);
            }
            index++;
        }

        Map<String, Object> auditValues = new LinkedHashMap<>();
        writePolicy().fillUpdateAuditFields(auditValues, tableColumns);
        auditValues.forEach((column, value) -> {
            if (!deltas.containsKey(column)) {
                validateIdentifier(column);
                setClauses.add(column + " = :" + column);
                params.addValue(column, value);
            }
        });

        String sql = "UPDATE " + tableName + " SET " + String.join(", ", setClauses)
                + " WHERE " + primaryKeyColumn + " = :id";
        sql = appendTenantCondition(sql, params, tableName);
        sql = appendLogicActiveCondition(sql, params, tableName);
        sql = appendSqlCondition(sql, params, condition);
        for (String bound : boundConditions) {
            sql += " AND (" + bound + ")";
        }
        DataAuditTransactionHolder.prepareWrite(tableName, primaryKeyColumn, id, DataAuditTransactionHolder.WriteKind.UPDATE);
        int affected = jdbc().update(sql, params);
        DataAuditTransactionHolder.afterWrite(tableName, id, Map.of(),
                DataAuditTransactionHolder.currentFieldSource(DataAuditSourceType.BUSINESS_ACTION),
                DataAuditTransactionHolder.WriteKind.UPDATE, affected);
        return affected;
    }

    public Long selectFirstIdByColumn(String tableName, String columnName, Object value) {
        validateTableName(tableName);
        validateIdentifier(columnName);
        if (value == null) {
            return null;
        }
        StringBuilder whereClause = new StringBuilder(columnName + " = :value");
        appendBaseQueryConditions(whereClause, new MapSqlParameterSource(), tableName);
        MapSqlParameterSource params = buildBaseQueryParams();
        params.addValue("value", value);
        String sql = buildSelectSql("SELECT id", tableName, whereClause) + " ORDER BY id ASC";
        sql = limitSql(sql, 1);
        List<Long> ids = jdbc().queryForList(sql, params, Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ==================== 删除操作 ====================

    /**
     * 根据ID删除
     */
    public int deleteById(String tableName, Object id, boolean logicDelete) {
        return deleteById(tableName, id, logicDelete, null);
    }

    public int deleteById(String tableName, Object id, boolean logicDelete, SqlCondition dataScopeCondition) {
        return deleteById(tableName, primaryKeyColumn(), id, logicDelete, dataScopeCondition);
    }

    public int deleteById(String tableName,
                          String primaryKeyColumn,
                          Object id,
                          boolean logicDelete,
                          SqlCondition dataScopeCondition) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);

        MapSqlParameterSource params = toIdParam(id);
        if (logicDelete) {
            params.addValue("deletedValue", logicDeletedValue());
        }
        DataAuditTransactionHolder.prepareWrite(tableName, primaryKeyColumn, id, DataAuditTransactionHolder.WriteKind.DELETE);
        String sql = appendTenantCondition(buildDeleteSql(tableName, logicDelete, primaryKeyColumn), params, tableName);
        sql = appendSqlCondition(sql, params, dataScopeCondition);
        int affected = jdbc().update(sql, params);
        DataAuditTransactionHolder.afterWrite(tableName, id, Map.of(),
                DataAuditTransactionHolder.currentFieldSource(DataAuditSourceType.FORM),
                DataAuditTransactionHolder.WriteKind.DELETE, affected);
        return affected;
    }

    /**
     * 批量删除（IN 子句），一条 SQL 处理多条记录
     */
    public int deleteByIds(String tableName,
                           String primaryKeyColumn,
                           List<?> ids,
                           boolean logicDelete,
                           SqlCondition dataScopeCondition) {
        return deleteByIds(tableName, primaryKeyColumn, ids, logicDelete, dataScopeCondition, null);
    }

    /**
     * @param beforeById 可选：已查询的删除前快照（key 为规范化 recordId），有则跳过逐条 FOR UPDATE 再读
     */
    public int deleteByIds(String tableName,
                           String primaryKeyColumn,
                           List<?> ids,
                           boolean logicDelete,
                           SqlCondition dataScopeCondition,
                           Map<String, Map<String, Object>> beforeById) {
        validateTableName(tableName);
        validateIdentifier(primaryKeyColumn);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("ids", ids);
        if (logicDelete) {
            params.addValue("deletedValue", logicDeletedValue());
        }
        for (Object id : ids) {
            Map<String, Object> snapshot = null;
            if (beforeById != null) {
                String key = DataAuditRecordIds.normalize(id);
                if (key != null) {
                    snapshot = beforeById.get(key);
                }
            }
            DataAuditTransactionHolder.prepareWrite(
                    tableName, primaryKeyColumn, id, DataAuditTransactionHolder.WriteKind.DELETE, snapshot);
        }
        String sql = appendTenantCondition(buildBatchDeleteSql(tableName, logicDelete, primaryKeyColumn), params, tableName);
        sql = appendSqlCondition(sql, params, dataScopeCondition);
        int affected = jdbc().update(sql, params);
        for (Object id : ids) {
            DataAuditTransactionHolder.afterWrite(tableName, id, Map.of(),
                    DataAuditTransactionHolder.currentFieldSource(DataAuditSourceType.FORM),
                    DataAuditTransactionHolder.WriteKind.DELETE, affected > 0 ? 1 : 0);
        }
        return affected;
    }

    public int deleteByColumn(String tableName, String columnName, Object value, boolean logicDelete) {
        validateTableName(tableName);
        validateIdentifier(columnName);
        if (value == null) {
            return 0;
        }
        captureColumnDelete(tableName, columnName, value);
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("value", value);
        String sql;
        if (logicDelete) {
            params.addValue("deletedValue", logicDeletedValue());
            sql = "UPDATE " + tableName + " SET " + logicDeleteSetClause(tableName)
                    + " WHERE " + columnName + " = :value";
        } else {
            sql = "DELETE FROM " + tableName + " WHERE " + columnName + " = :value";
        }
        return jdbc().update(appendTenantCondition(sql, params, tableName), params);
    }

    // ==================== 工具方法 ====================

    /**
     * 检查表是否存在
     */
    public boolean tableExists(String tableName) {
        return tableMetadataGateway().tableExists(tableName);
    }

    /**
     * 检查表是否有del_flag列（带缓存）
     */
    public boolean hasDelFlag(String tableName) {
        return tableMetadataGateway().hasLogicDeleteColumn(
                tableName, logicDeleteColumn(), logicDeleteEnabled(), () -> getTableColumns(tableName));
    }

    /**
     * 获取表的所有列名（带缓存）
     */
    public Set<String> getTableColumns(String tableName) {
        return tableMetadataGateway().columns(tableName);
    }

    /**
     * 获取表的字段映射（camelCase -> snake_case）
     */
    public Map<String, String> getColumnMapping(String tableName) {
        return tableMetadataGateway().columnMapping(tableName, () -> getTableColumns(tableName));
    }

    private Object normalizeColumnQueryValue(String tableName, String columnName, Object value) {
        return tableMetadataGateway().normalizeQueryValue(tableName, columnName, value);
    }

    private void captureColumnDelete(String tableName, String columnName, Object value) {
        if (DataAuditTransactionHolder.index().findTable(DataAuditTenantSupport.currentTenantIdOrNull(), tableName) == null) {
            return;
        }
        List<Map<String, Object>> rows = selectListByColumn(tableName, columnName, value);
        for (Map<String, Object> row : rows) {
            Object id = row.get(primaryKeyColumn());
            if (id == null) {
                id = row.get("id");
            }
            if (id == null) {
                id = row.get("ID");
            }
            DataAuditTransactionHolder.prepareWrite(tableName, primaryKeyColumn(), id, DataAuditTransactionHolder.WriteKind.DELETE);
            DataAuditTransactionHolder.afterWrite(tableName, id, row,
                    DataAuditTransactionHolder.currentFieldSource(DataAuditSourceType.FORM),
                    DataAuditTransactionHolder.WriteKind.DELETE, 1);
        }
    }

    /**
     * DDL 执行后清理动态表结构缓存，避免追加字段后运行时继续使用旧列集合。
     */
    public void clearTableMetadataCache(String tableName) {
        tableMetadataGateway().clear(tableName);
    }

    public void clearTableMetadataCache(LowcodeRuntimeDataSourceContext context, String tableName) {
        tableMetadataGateway().clear(context, tableName);
    }

    private DynamicCrudTableMetadataGateway tableMetadataGateway() {
        DynamicCrudTableMetadataGateway gateway = tableMetadataGateway;
        if (gateway == null) {
            synchronized (this) {
                gateway = tableMetadataGateway;
                if (gateway == null) {
                    gateway = new DynamicCrudTableMetadataGateway(jdbcTemplateProvider, dialectFactory);
                    tableMetadataGateway = gateway;
                }
            }
        }
        return gateway;
    }

    /**
     * 校验表名
     */
    private void validateTableName(String tableName) {
        if (StringUtils.isBlank(tableName) || !SAFE_IDENTIFIER.matcher(tableName).matches()) {
            throw new BusinessException("非法表名: " + tableName);
        }
        if (!tableExists(tableName)) {
            throw new BusinessException("数据表不存在: " + tableName);
        }
    }

    /**
     * 校验标识符
     */
    public void validateIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier) || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new BusinessException("非法标识符: " + identifier);
        }
    }

    private String buildSelectSql(String selectClause, String tableName, StringBuilder whereClause) {
        String sql = selectClause + " FROM " + tableName;
        if (whereClause.length() > 0) {
            sql += " WHERE " + whereClause;
        }
        return sql;
    }

    private String buildWhereSql(StringBuilder whereClause) {
        return whereClause.length() > 0 ? " WHERE " + whereClause : "";
    }

    private String buildJoinSelectClause(List<JoinField> fields) {
        return joinQueryCompiler().buildSelectClause(fields);
    }

    private String buildJoinSelectClause(List<JoinField> fields, boolean distinct) {
        return joinQueryCompiler().buildSelectClause(fields, distinct);
    }

    private void validateJoinQuery(String mainTableName, List<JoinField> selectFields, List<JoinSpec> joins) {
        joinQueryCompiler().validate(mainTableName, selectFields, joins);
    }

    private DynamicCrudJoinQueryCompiler joinQueryCompiler() {
        return new DynamicCrudJoinQueryCompiler(
                this::validateTableName,
                this::validateIdentifier,
                this::getTableColumns,
                this::hasDelFlag,
                () -> dialectFactory.resolve(LowcodeRuntimeDataSourceContextHolder.get()),
                this::tenantStrategyEnabled,
                this::tenantColumn,
                this::logicDeleteColumn
        );
    }

    private String qualifyColumn(String tableAlias, String columnName) {
        if (StringUtils.isBlank(tableAlias)) {
            return columnName;
        }
        return tableAlias + "." + columnName;
    }

    private String qualifyPrimaryKey(String tableAlias) {
        return qualifyColumn(tableAlias, primaryKeyColumn());
    }

    private String primaryKeyColumn() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        String columnName = context == null || context.getPrimaryKey() == null
                ? DEFAULT_PRIMARY_KEY
                : context.getPrimaryKey().getColumnName();
        columnName = StringUtils.defaultIfBlank(columnName, DEFAULT_PRIMARY_KEY);
        validateIdentifier(columnName);
        return columnName;
    }

    private String primaryKeyField() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        String field = context == null || context.getPrimaryKey() == null
                ? DEFAULT_PRIMARY_KEY
                : context.getPrimaryKey().getField();
        field = StringUtils.defaultIfBlank(field, DEFAULT_PRIMARY_KEY);
        validateIdentifier(field);
        return field;
    }

    private String quoteIdentifier(String identifier) {
        validateAlias(identifier);
        return dialectFactory.resolve(LowcodeRuntimeDataSourceContextHolder.get()).quote(identifier);
    }

    private void validateAlias(String alias) {
        if (StringUtils.isBlank(alias) || !SAFE_ALIAS.matcher(alias).matches()) {
            throw new BusinessException("非法字段别名: " + alias);
        }
    }

    private String paginateSql(String sql, int pageNum, int pageSize) {
        long limit = Math.max(1, pageSize);
        long offset = Math.max(0, pageNum - 1L) * limit;
        return dialectFactory.resolve(LowcodeRuntimeDataSourceContextHolder.get()).paginate(sql, offset, limit);
    }

    private String limitSql(String sql, int limit) {
        return dialectFactory.resolve(LowcodeRuntimeDataSourceContextHolder.get())
                .paginate(sql, 0, Math.max(1, limit));
    }

    private void appendTenantWhereClause(StringBuilder whereClause, MapSqlParameterSource params) {
        appendTenantWhereClause(whereClause, params, null);
    }

    private void appendTenantWhereClause(StringBuilder whereClause, MapSqlParameterSource params, String tableAlias) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || !tenantStrategyEnabled()) {
            return;
        }
        appendWhereCondition(whereClause, qualifyColumn(tableAlias, tenantColumn()) + " = :tenantId");
        params.addValue("tenantId", tenantId);
    }

    private void appendLogicDeleteParam(MapSqlParameterSource params) {
        if (params != null && logicDeleteEnabled()) {
            params.addValue("logicActiveValue", logicActiveValue());
        }
    }

    private void appendWhereCondition(StringBuilder whereClause, String condition) {
        appendWhereJoiner(whereClause);
        whereClause.append(condition);
    }

    private void appendSqlCondition(StringBuilder whereClause, MapSqlParameterSource params, SqlCondition condition) {
        if (condition == null || StringUtils.isBlank(condition.sql())) {
            return;
        }
        appendWhereCondition(whereClause, "(" + condition.sql() + ")");
        if (condition.params() != null) {
            condition.params().forEach(params::addValue);
        }
    }

    private void appendWhereJoiner(StringBuilder whereClause) {
        if (whereClause.length() > 0) {
            whereClause.append(" AND ");
        }
    }

    private String appendTenantCondition(String sql, MapSqlParameterSource params) {
        return appendTenantCondition(sql, params, null);
    }

    private String appendTenantCondition(String sql, MapSqlParameterSource params, String tableName) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || !tenantStrategyEnabled()) {
            return sql;
        }
        String tenantColumn = tenantColumn();
        if (tableName != null && !getTableColumns(tableName).contains(tenantColumn)) {
            return sql;
        }
        params.addValue("tenantId", tenantId);
        return sql + " AND " + tenantColumn + " = :tenantId";
    }

    private String appendLogicActiveCondition(String sql, MapSqlParameterSource params, String tableName) {
        if (tableName == null || !hasDelFlag(tableName)) {
            return sql;
        }
        appendLogicDeleteParam(params);
        return sql + " AND " + logicDeleteColumn() + " = :logicActiveValue";
    }

    private String appendSqlCondition(String sql, MapSqlParameterSource params, SqlCondition condition) {
        if (condition == null || StringUtils.isBlank(condition.sql())) {
            return sql;
        }
        if (condition.params() != null) {
            condition.params().forEach(params::addValue);
        }
        return sql + " AND (" + condition.sql() + ")";
    }

    private String buildInsertSql(String tableName, Map<String, Object> data) {
        String columns = String.join(", ", data.keySet());
        String placeholders = data.keySet().stream()
                .map(col -> ":" + col)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    private String buildUpdateSql(String tableName, Map<String, Object> data, String primaryKeyColumn) {
        String setClauses = data.entrySet().stream()
                .map(entry -> entry.getKey() + " = :" + entry.getKey())
                .collect(Collectors.joining(", "));
        return "UPDATE " + tableName + " SET " + setClauses + " WHERE " + primaryKeyColumn + " = :id";
    }

    private String buildDeleteSql(String tableName, boolean logicDelete, String primaryKeyColumn) {
        if (logicDelete) {
            return "UPDATE " + tableName + " SET " + logicDeleteSetClause(tableName) + " WHERE "
                    + primaryKeyColumn + " = :id";
        }
        return "DELETE FROM " + tableName + " WHERE " + primaryKeyColumn + " = :id";
    }

    private String buildBatchDeleteSql(String tableName, boolean logicDelete, String primaryKeyColumn) {
        if (logicDelete) {
            return "UPDATE " + tableName + " SET " + logicDeleteSetClause(tableName) + " WHERE "
                    + primaryKeyColumn + " IN (:ids)";
        }
        return "DELETE FROM " + tableName + " WHERE " + primaryKeyColumn + " IN (:ids)";
    }

    private String logicDeleteSetClause(String tableName) {
        return writePolicy().logicDeleteSetClause(getTableColumns(tableName));
    }

    private MapSqlParameterSource toSqlParams(Map<String, Object> data) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        data.forEach(params::addValue);
        return params;
    }

    private MapSqlParameterSource toSqlParams(Map<String, Object> data, Object id) {
        MapSqlParameterSource params = toSqlParams(data);
        appendIdParam(params, id);
        return params;
    }

    private MapSqlParameterSource toIdParam(Object id) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        appendIdParam(params, id);
        return params;
    }

    private void appendIdParam(MapSqlParameterSource params, Object id) {
        params.addValue("id", id);
    }

    private void logDynamicSql(String scene, String sql, MapSqlParameterSource params) {
        log.info("[DynamicCrudRepository] {} SQL: {}", scene, sql);
        log.info("[DynamicCrudRepository] {} 参数: {}", scene, params == null ? Map.of() : params.getValues());
    }

    private Map<String, Object> prepareInsertData(String tableName, Map<String, Object> data) {
        return writePolicy().prepareInsert(data, getTableColumns(tableName));
    }

    private Map<String, Object> prepareUpdateData(String tableName, Map<String, Object> data) {
        return prepareUpdateData(tableName, data, DEFAULT_PRIMARY_KEY);
    }

    private Map<String, Object> prepareUpdateData(String tableName, Map<String, Object> data, String primaryKeyColumn) {
        return writePolicy().prepareUpdate(data, getTableColumns(tableName), primaryKeyColumn);
    }

    private boolean tenantStrategyEnabled() {
        return writePolicy().tenantStrategyEnabled();
    }

    private String tenantColumn() {
        return writePolicy().tenantColumn();
    }

    private boolean logicDeleteEnabled() {
        return writePolicy().logicDeleteEnabled();
    }

    private String logicDeleteColumn() {
        return writePolicy().logicDeleteColumn();
    }

    private Object logicActiveValue() {
        return writePolicy().logicActiveValue();
    }

    private Object logicDeletedValue() {
        return writePolicy().logicDeletedValue();
    }

    private DynamicCrudWritePolicy writePolicy() {
        return new DynamicCrudWritePolicy(this::validateIdentifier);
    }
}
