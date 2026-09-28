package com.mdframe.forge.plugin.data.support;

import org.springframework.stereotype.Component;

@Component
public class MySqlDialect implements DbDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    public String buildLimitSql(String sql, int limit) {
        return sql + " LIMIT " + limit;
    }

    @Override
    public String getTableQuerySql(String schemaName, String keyword) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT TABLE_NAME AS tableName, TABLE_TYPE AS tableType, TABLE_COMMENT AS tableComment ");
        sql.append("FROM information_schema.TABLES WHERE TABLE_SCHEMA = '").append(schemaName).append("' ");
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (TABLE_NAME LIKE '%").append(keyword).append("%' ");
            sql.append("OR TABLE_COMMENT LIKE '%").append(keyword).append("%') ");
        }
        sql.append("ORDER BY TABLE_NAME");
        return sql.toString();
    }

    @Override
    public String getColumnQuerySql(String schemaName, String tableName) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COLUMN_NAME AS columnName, COLUMN_TYPE AS columnType, ");
        sql.append("COLUMN_COMMENT AS columnComment, IS_NULLABLE AS nullable, COLUMN_KEY AS primaryKey ");
        sql.append("FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '").append(schemaName).append("' ");
        sql.append("AND TABLE_NAME = '").append(tableName).append("' ORDER BY ORDINAL_POSITION");
        return sql.toString();
    }
}