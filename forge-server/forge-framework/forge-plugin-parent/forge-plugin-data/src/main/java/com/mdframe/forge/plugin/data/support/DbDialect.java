package com.mdframe.forge.plugin.data.support;

public interface DbDialect {

    String quoteIdentifier(String identifier);

    String buildLimitSql(String sql, int limit);

    String buildPageSql(String sql, long offset, int limit);

    String buildCountSql(String sql);

    String getTableQuerySql(String schemaName, String keyword);

    String getColumnQuerySql(String schemaName, String tableName);
}
