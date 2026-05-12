package com.mdframe.forge.plugin.data.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SqlSafetyValidator {

    private static final List<String> FORBIDDEN_KEYWORDS = Arrays.asList(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", 
            "TRUNCATE", "MERGE", "CALL", "EXEC", "EXECUTE"
    );

    private static final List<String> FORBIDDEN_FUNCTIONS = Arrays.asList(
            "LOAD_FILE", "INTO OUTFILE", "INTO DUMPFILE"
    );

    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(";.*;");
    private static final Pattern UNION_ATTACK_PATTERN = Pattern.compile("UNION\\s+SELECT", Pattern.CASE_INSENSITIVE);

    public void validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL不能为空");
        }

        String normalizedSql = sql.toUpperCase().trim();

        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (normalizedSql.startsWith(keyword) || normalizedSql.contains(" " + keyword + " ")) {
                log.warn("SQL contains forbidden keyword: {}", keyword);
                throw new IllegalArgumentException("SQL仅允许单条查询语句");
            }
        }

        for (String function : FORBIDDEN_FUNCTIONS) {
            if (normalizedSql.contains(function.toUpperCase())) {
                log.warn("SQL contains forbidden function: {}", function);
                throw new IllegalArgumentException("SQL包含不允许的危险函数");
            }
        }

        if (MULTI_STATEMENT_PATTERN.matcher(sql).find()) {
            log.warn("SQL contains multiple statements");
            throw new IllegalArgumentException("SQL仅允许单条查询语句");
        }

        if (!normalizedSql.startsWith("SELECT") && !normalizedSql.startsWith("WITH")) {
            log.warn("SQL is not a SELECT or WITH statement");
            throw new IllegalArgumentException("SQL仅允许SELECT或WITH查询语句");
        }

        if (UNION_ATTACK_PATTERN.matcher(sql).find()) {
            log.warn("SQL contains UNION SELECT");
            throw new IllegalArgumentException("SQL不允许UNION注入攻击");
        }
    }

    public boolean isSafe(String sql) {
        try {
            validate(sql);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}