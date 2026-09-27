package com.mdframe.forge.plugin.generator.service;

import cn.dev33.satoken.exception.SaTokenException;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAuditStrategy;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeLogicDeleteStrategy;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeTenantStrategy;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContext;
import com.mdframe.forge.plugin.generator.service.lowcode.runtime.LowcodeRuntimeDataSourceContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 动态写入字段策略。
 *
 * <p>根据当前运行数据源上下文统一处理租户、审计和逻辑删除字段，
 * Repository 只负责在命令执行前后调用本策略。</p>
 */
@Slf4j
@RequiredArgsConstructor
final class DynamicCrudWritePolicy {

    private static final String DEFAULT_PRIMARY_KEY = "id";

    private final Consumer<String> identifierValidator;

    Map<String, Object> prepareInsert(Map<String, Object> data, Set<String> columns) {
        Map<String, Object> insertData = requireData(data, "没有可写入的字段");
        fillInsertAuditFields(insertData, columns);
        return insertData;
    }

    Map<String, Object> prepareUpdate(Map<String, Object> data,
                                      Set<String> columns,
                                      String primaryKeyColumn) {
        Map<String, Object> updateData = requireData(data, "没有可更新的字段");
        removeImmutableFields(updateData, DEFAULT_PRIMARY_KEY, primaryKeyColumn, "tenant_id", tenantColumn());
        fillUpdateAuditFields(updateData, columns);
        return updateData;
    }

    boolean tenantStrategyEnabled() {
        LowcodeTenantStrategy strategy = tenantStrategy();
        return strategy == null || !isNoneMode(strategy.getMode());
    }

    String tenantColumn() {
        LowcodeTenantStrategy strategy = tenantStrategy();
        String column = strategy == null ? null : strategy.getColumnName();
        column = StringUtils.defaultIfBlank(column, "tenant_id");
        identifierValidator.accept(column);
        return column;
    }

    boolean logicDeleteEnabled() {
        LowcodeLogicDeleteStrategy strategy = logicDeleteStrategy();
        return strategy == null || !isNoneMode(strategy.getMode());
    }

    String logicDeleteColumn() {
        LowcodeLogicDeleteStrategy strategy = logicDeleteStrategy();
        String column = strategy == null ? null : strategy.getColumnName();
        column = StringUtils.defaultIfBlank(column, "del_flag");
        identifierValidator.accept(column);
        return column;
    }

    Object logicActiveValue() {
        LowcodeLogicDeleteStrategy strategy = logicDeleteStrategy();
        return StringUtils.defaultIfBlank(strategy == null ? null : strategy.getActiveValue(), "0");
    }

    Object logicDeletedValue() {
        LowcodeLogicDeleteStrategy strategy = logicDeleteStrategy();
        return StringUtils.defaultIfBlank(strategy == null ? null : strategy.getDeletedValue(), "1");
    }

    String logicDeleteSetClause(Set<String> columns) {
        StringBuilder setClause = new StringBuilder(logicDeleteColumn()).append(" = :deletedValue");
        LowcodeAuditStrategy strategy = auditStrategy();
        String updateTimeColumn = auditUpdateTimeColumn(strategy);
        if (auditStrategyEnabled() && columns.contains(updateTimeColumn)) {
            setClause.append(", ").append(updateTimeColumn).append(" = CURRENT_TIMESTAMP");
        }
        return setClause.toString();
    }

    private Map<String, Object> requireData(Map<String, Object> data, String emptyMessage) {
        if (data == null || data.isEmpty()) {
            throw new BusinessException(emptyMessage);
        }
        return data;
    }

    private void removeImmutableFields(Map<String, Object> data, String... fields) {
        for (String field : fields) {
            data.remove(field);
        }
    }

    private void fillInsertAuditFields(Map<String, Object> data, Set<String> columns) {
        Date now = new Date();
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = auditSessionValue(SessionHelper::getUserId);
        Long mainOrgId = auditSessionValue(SessionHelper::getMainOrgId);

        if (tenantStrategyEnabled()) {
            putIfColumnExists(data, columns, tenantColumn(), tenantId);
        }
        if (logicDeleteEnabled()) {
            putIfColumnExists(data, columns, logicDeleteColumn(), logicActiveValue());
        }
        if (!shouldFillAuditColumns(columns)) {
            return;
        }
        LowcodeAuditStrategy strategy = effectiveAuditStrategy(columns);
        putIfColumnExists(data, columns, auditCreateByColumn(strategy), userId);
        putIfColumnExists(data, columns, auditCreateDeptColumn(strategy), mainOrgId);
        putIfColumnExists(data, columns, auditCreateTimeColumn(strategy), now);
        putIfColumnExists(data, columns, auditUpdateByColumn(strategy), userId);
        putIfColumnExists(data, columns, auditUpdateTimeColumn(strategy), now);
    }

    void fillUpdateAuditFields(Map<String, Object> data, Set<String> columns) {
        if (!shouldFillAuditColumns(columns)) {
            return;
        }
        Date now = new Date();
        Long userId = auditSessionValue(SessionHelper::getUserId);
        LowcodeAuditStrategy strategy = effectiveAuditStrategy(columns);
        putIfColumnExists(data, columns, auditUpdateByColumn(strategy), userId);
        putIfColumnExists(data, columns, auditUpdateTimeColumn(strategy), now);
    }

    private boolean shouldFillAuditColumns(Set<String> columns) {
        return auditStrategyEnabled() || hasStandardAuditColumn(columns);
    }

    private LowcodeAuditStrategy effectiveAuditStrategy(Set<String> columns) {
        LowcodeAuditStrategy strategy = auditStrategy();
        if (auditStrategyEnabled()) {
            return strategy;
        }
        LowcodeAuditStrategy fallback = new LowcodeAuditStrategy();
        fallback.setMode("FORGE_COLUMNS");
        if (columns == null || columns.isEmpty()) {
            fallback.setCreateByColumn("create_by");
            fallback.setCreateTimeColumn("create_time");
            fallback.setCreateDeptColumn("create_dept");
            fallback.setUpdateByColumn("update_by");
            fallback.setUpdateTimeColumn("update_time");
            return fallback;
        }
        if (columns.contains("create_by")) {
            fallback.setCreateByColumn("create_by");
        }
        if (columns.contains("create_time")) {
            fallback.setCreateTimeColumn("create_time");
        }
        if (columns.contains("create_dept")) {
            fallback.setCreateDeptColumn("create_dept");
        }
        if (columns.contains("update_by")) {
            fallback.setUpdateByColumn("update_by");
        }
        if (columns.contains("update_time")) {
            fallback.setUpdateTimeColumn("update_time");
        }
        return fallback;
    }

    private boolean hasStandardAuditColumn(Set<String> columns) {
        return columns != null && !columns.isEmpty()
            && (columns.contains("create_by")
            || columns.contains("create_time")
            || columns.contains("create_dept")
            || columns.contains("update_by")
            || columns.contains("update_time"));
    }

    private Long auditSessionValue(Supplier<Long> supplier) {
        try {
            return supplier.get();
        } catch (SaTokenException exception) {
            log.debug("[DynamicCrudWritePolicy] 后台写入无 Web 审计会话: {}", exception.getMessage());
            return null;
        }
    }

    private LowcodeTenantStrategy tenantStrategy() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        return context == null ? null : context.getTenantStrategy();
    }

    private boolean auditStrategyEnabled() {
        LowcodeAuditStrategy strategy = auditStrategy();
        return strategy == null || !isNoneMode(strategy.getMode());
    }

    private LowcodeAuditStrategy auditStrategy() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        return context == null ? null : context.getAuditStrategy();
    }

    private String auditCreateByColumn(LowcodeAuditStrategy strategy) {
        return auditColumn(strategy == null ? null : strategy.getCreateByColumn(), "create_by");
    }

    private String auditCreateTimeColumn(LowcodeAuditStrategy strategy) {
        return auditColumn(strategy == null ? null : strategy.getCreateTimeColumn(), "create_time");
    }

    private String auditCreateDeptColumn(LowcodeAuditStrategy strategy) {
        return auditColumn(strategy == null ? null : strategy.getCreateDeptColumn(), "create_dept");
    }

    private String auditUpdateByColumn(LowcodeAuditStrategy strategy) {
        return auditColumn(strategy == null ? null : strategy.getUpdateByColumn(), "update_by");
    }

    private String auditUpdateTimeColumn(LowcodeAuditStrategy strategy) {
        return auditColumn(strategy == null ? null : strategy.getUpdateTimeColumn(), "update_time");
    }

    private String auditColumn(String configuredColumn, String defaultColumn) {
        String column = StringUtils.defaultIfBlank(configuredColumn, defaultColumn);
        identifierValidator.accept(column);
        return column;
    }

    private LowcodeLogicDeleteStrategy logicDeleteStrategy() {
        LowcodeRuntimeDataSourceContext context = LowcodeRuntimeDataSourceContextHolder.get();
        return context == null ? null : context.getLogicDeleteStrategy();
    }

    private boolean isNoneMode(String mode) {
        return "NONE".equalsIgnoreCase(StringUtils.defaultString(mode));
    }

    private void putIfColumnExists(Map<String, Object> data,
                                   Set<String> columns,
                                   String column,
                                   Object value) {
        if (value == null || StringUtils.isBlank(column)) {
            return;
        }
        if (columns != null && !columns.isEmpty() && !columns.contains(column)) {
            return;
        }
        if (!data.containsKey(column) || data.get(column) == null) {
            data.put(column, value);
        }
    }
}
