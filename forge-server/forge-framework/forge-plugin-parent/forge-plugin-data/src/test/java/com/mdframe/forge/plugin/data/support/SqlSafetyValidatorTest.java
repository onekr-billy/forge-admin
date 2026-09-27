package com.mdframe.forge.plugin.data.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlSafetyValidatorTest {

    private final SqlSafetyValidator validator = new SqlSafetyValidator();

    @Test
    void shouldAllowOneReadOnlySelectIncludingCte() {
        assertThat(validator.isSafe("SELECT id, name FROM customer WHERE id = 1")).isTrue();
        assertThat(validator.isSafe("WITH active AS (SELECT id FROM customer WHERE status = 1) SELECT * FROM active"))
                .isTrue();
    }

    @Test
    void shouldRejectMultipleOrNonSelectStatements() {
        assertThatThrownBy(() -> validator.validate("SELECT 1; DELETE FROM customer"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("UPDATE customer SET name = 'x'"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDangerousSelectFeatures() {
        assertThat(validator.isSafe("SELECT SLEEP(5)")).isFalse();
        assertThat(validator.isSafe("SELECT LOAD_FILE('/etc/passwd')")).isFalse();
        assertThat(validator.isSafe("SELECT * FROM information_schema.tables")).isFalse();
        assertThat(validator.isSafe("SELECT @secret")).isFalse();
        assertThat(validator.isSafe("SELECT id FROM a UNION SELECT id FROM b")).isFalse();
        assertThat(validator.isSafe("SELECT * FROM customer FOR UPDATE")).isFalse();
    }
}
