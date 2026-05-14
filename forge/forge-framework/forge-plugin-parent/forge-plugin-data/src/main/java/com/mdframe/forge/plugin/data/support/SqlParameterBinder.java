package com.mdframe.forge.plugin.data.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SqlParameterBinder {

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    public List<String> extractNamedParams(String sql) {
        List<String> params = new java.util.ArrayList<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }

    public String convertToPreparedStatement(String sql) {
        return sql.replaceAll(":([a-zA-Z_][a-zA-Z0-9_]*)", "?");
    }

    public Map<Integer, Object> buildParamIndexMap(String sql, Map<String, Object> params) {
        Map<Integer, Object> indexMap = new LinkedHashMap<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        int index = 1;
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);
            indexMap.put(index, value);
            index++;
        }
        return indexMap;
    }

    public String renderDebugSql(String sql, Map<String, Object> params) {
        if (sql == null || sql.isEmpty() || params == null || params.isEmpty()) {
            return sql;
        }

        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(formatSqlLiteral(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String formatSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value).replace("'", "''");
        return "'" + text + "'";
    }
}
