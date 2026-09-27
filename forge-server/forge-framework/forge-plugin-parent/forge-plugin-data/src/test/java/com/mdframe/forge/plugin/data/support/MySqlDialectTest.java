package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlDialectTest {

    private final MySqlDialect dialect = new MySqlDialect();

    @Test
    void shouldBuildOffsetPaginationAndCountSql() {
        assertThat(dialect.buildPageSql("SELECT id FROM members;", 20, 10))
                .isEqualTo("SELECT id FROM members LIMIT 20, 10");
        assertThat(dialect.buildCountSql("SELECT id FROM members;"))
                .isEqualTo("SELECT COUNT(*) FROM (SELECT id FROM members) forge_dataset_count");
    }

    @Test
    void shouldRejectUnsafeIdentifiers() {
        assertThat(dialect.quoteIdentifier("member_name")).isEqualTo("`member_name`");
        assertThatThrownBy(() -> dialect.quoteIdentifier("member`; DROP TABLE members--"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> dialect.quoteIdentifier("member.name"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> dialect.quoteIdentifier("member\nname"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void metadataQueriesShouldUseBindParameters() {
        String maliciousSchema = "forge' OR 1=1 --";
        String maliciousTable = "members' UNION SELECT secret --";
        String maliciousKeyword = "%' OR 1=1 --";

        assertThat(dialect.getTableQuerySql(maliciousSchema, maliciousKeyword))
                .contains("TABLE_SCHEMA = ?")
                .contains("TABLE_NAME LIKE ?")
                .doesNotContain(maliciousSchema)
                .doesNotContain(maliciousKeyword);
        assertThat(dialect.getColumnQuerySql(maliciousSchema, maliciousTable))
                .contains("TABLE_SCHEMA = ?")
                .contains("TABLE_NAME = ?")
                .doesNotContain(maliciousSchema)
                .doesNotContain(maliciousTable);
    }
}
