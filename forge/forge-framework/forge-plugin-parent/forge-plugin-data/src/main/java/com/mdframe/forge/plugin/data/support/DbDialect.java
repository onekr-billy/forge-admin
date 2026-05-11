package com.mdframe.forge.plugin.data.support;

public interface DbDialect {

    String quoteIdentifier(String identifier);

    String buildLimitSql(String sql, int limit);

    String getTableQuerySql(String schemaName, String keyword);

    String getColumnQuerySql(String schemaName, String tableName);
}