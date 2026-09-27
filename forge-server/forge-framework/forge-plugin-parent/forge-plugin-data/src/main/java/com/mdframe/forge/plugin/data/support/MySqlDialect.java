package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class MySqlDialect implements DbDialect {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new BusinessException("数据库标识符格式不安全");
        }
        return "`" + identifier + "`";
    }

    @Override
    public String buildLimitSql(String sql, int limit) {
        return sql + " LIMIT " + limit;
    }

    @Override
    public String buildPageSql(String sql, long offset, int limit) {
        if (offset < 0 || limit <= 0) {
            throw new BusinessException("分页参数不合法");
        }
        return trimTerminator(sql) + " LIMIT " + offset + ", " + limit;
    }

    @Override
    public String buildCountSql(String sql) {
        return "SELECT COUNT(*) FROM (" + trimTerminator(sql) + ") forge_dataset_count";
    }

    @Override
    public String getTableQuerySql(String schemaName, String keyword) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT TABLE_NAME AS tableName, TABLE_TYPE AS tableType, TABLE_COMMENT AS tableComment ");
        sql.append("FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ");
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (TABLE_NAME LIKE ? OR TABLE_COMMENT LIKE ?) ");
        }
        sql.append("ORDER BY TABLE_NAME");
        return sql.toString();
    }

    @Override
    public String getColumnQuerySql(String schemaName, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COLUMN_NAME AS columnName, COLUMN_TYPE AS columnType, ");
        sql.append("COLUMN_COMMENT AS columnComment, IS_NULLABLE AS nullable, COLUMN_KEY AS primaryKey ");
        sql.append("FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? ");
        sql.append("AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION");
        return sql.toString();
    }

    private String trimTerminator(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("查询SQL不能为空");
        }
        String normalized = sql.trim();
        return normalized.endsWith(";")
                ? normalized.substring(0, normalized.length() - 1).trim()
                : normalized;
    }
}
