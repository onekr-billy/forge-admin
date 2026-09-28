package com.mdframe.forge.plugin.data.support;

import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SqlSafetyValidator {

    private static final List<Pattern> FORBIDDEN_SELECT_FEATURES = List.of(
            tokenPattern("UNION"),
            tokenPattern("FOR\\s+UPDATE"),
            tokenPattern("LOCK\\s+IN\\s+SHARE\\s+MODE"),
            tokenPattern("INTO\\s+OUTFILE"),
            tokenPattern("INTO\\s+DUMPFILE"),
            functionPattern("LOAD_FILE"),
            functionPattern("SLEEP"),
            functionPattern("BENCHMARK"),
            Pattern.compile("(?<![A-Z0-9_])@@?[A-Z0-9_]+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<![A-Z0-9_])(INFORMATION_SCHEMA|MYSQL|PERFORMANCE_SCHEMA|SYS|PG_CATALOG)\\s*\\.",
                    Pattern.CASE_INSENSITIVE));

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL不能为空");
        }

        Statement statement;
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements == null || statements.getStatements().size() != 1) {
                throw new IllegalArgumentException("SQL仅允许单条查询语句");
            }
            statement = statements.getStatements().get(0);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("SQL AST parse failed: {}", exception.getClass().getSimpleName());
            throw new IllegalArgumentException("SQL语法无效或不是受支持的只读查询");
        }

        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException("SQL仅允许单条SELECT查询语句");
        }

        String canonicalSql = statement.toString().toUpperCase(Locale.ROOT);
        for (Pattern forbiddenPattern : FORBIDDEN_SELECT_FEATURES) {
            if (forbiddenPattern.matcher(canonicalSql).find()) {
                log.warn("SQL AST contains forbidden read feature: {}", forbiddenPattern.pattern());
                throw new IllegalArgumentException("SQL包含不允许的查询特性");
            }
        }
    }

    public boolean isSafe(String sql) {
        try {
            validate(sql);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Pattern tokenPattern(String tokenExpression) {
        return Pattern.compile("(?<![A-Z0-9_])" + tokenExpression + "(?![A-Z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    private static Pattern functionPattern(String functionName) {
        return Pattern.compile("(?<![A-Z0-9_])" + functionName + "\\s*\\(", Pattern.CASE_INSENSITIVE);
    }
}
