package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodeOnlineDdlPolicyTest {

    @Test
    void additiveAndCommentStatementsKeepOnlineWhitelist() {
        for (String ddl : List.of(
                "CREATE TABLE IF NOT EXISTS t_order (id bigint)",
                "ALTER TABLE t_order ADD COLUMN memo varchar(100)",
                "ALTER TABLE t_order ADD (memo varchar(100))",
                "ALTER TABLE t_order ADD KEY idx_memo (memo)",
                "ALTER TABLE t_order ADD UNIQUE KEY uk_code (code)",
                "CREATE INDEX idx_code ON t_order(code)",
                "CREATE UNIQUE INDEX uk_code ON t_order(code)",
                "COMMENT ON TABLE t_order IS '订单'")) {
            assertTrue(LowcodeOnlineDdlPolicy.isSafeOnlineDdl(ddl), ddl);
            assertDoesNotThrow(() -> LowcodeOnlineDdlPolicy.assertSafeDdl(ddl));
        }
    }

    @Test
    void destructiveOrUnknownStatementsAreRejected() {
        for (String ddl : List.of(
                "ALTER TABLE t_order MODIFY COLUMN memo text",
                "ALTER TABLE t_order DROP COLUMN memo",
                "DROP TABLE t_order",
                " ")) {
            assertFalse(LowcodeOnlineDdlPolicy.isSafeOnlineDdl(ddl), ddl);
            assertThrows(BusinessException.class, () -> LowcodeOnlineDdlPolicy.assertSafeDdl(ddl));
        }
    }

    @Test
    void previewCheckUsesTheSamePredicate() {
        assertFalse(LowcodeOnlineDdlPolicy.containsUnsafeOnlineDdl(null));
        assertFalse(LowcodeOnlineDdlPolicy.containsUnsafeOnlineDdl(List.of(" ", "CREATE TABLE t_order (id bigint)")));
        assertTrue(LowcodeOnlineDdlPolicy.containsUnsafeOnlineDdl(List.of(
                "CREATE TABLE t_order (id bigint)", "ALTER TABLE t_order DROP COLUMN memo")));
    }
}
