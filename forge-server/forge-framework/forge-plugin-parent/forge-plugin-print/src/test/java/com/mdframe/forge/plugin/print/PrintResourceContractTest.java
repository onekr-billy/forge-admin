package com.mdframe.forge.plugin.print;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.mdframe.forge.plugin.print.entity.*;
import com.mdframe.forge.plugin.print.enums.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PrintResourceContractTest {

    static Path migrationDirectory() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("db/migration"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("找不到 forge-server/db/migration");
        }
        return dir.resolve("db/migration");
    }

    @Test
    void tablesKeepAuditColumnsAndActiveOnlyKeysWhileVersionsKeepHistory() throws Exception {
        String sql = Files.readString(migrationDirectory().resolve("V1.0.168__add_native_print_tables.sql"));
        for (String table : List.of("sys_print_template", "sys_print_template_version", "sys_print_binding", "sys_print_execution")) {
            String block = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS " + table + " ("));
            block = block.substring(0, block.indexOf(";"));
            assertThat(block).contains("tenant_id BIGINT NOT NULL", "create_by", "create_time", "create_dept", "update_by", "update_time", "del_flag", "ENGINE=InnoDB", "utf8mb4");
        }
        assertThat(sql).contains("(tenant_id, application_id, template_code, del_flag)", "(tenant_id, template_id, version_no)", "(tenant_id, application_id, source_key, template_id, scene, del_flag)").doesNotContain("${", "FOREIGN KEY", "logic_delete_active");
        for (Class<?> entity : List.of(PrintTemplate.class, PrintBinding.class)) {
            var field = entity.getDeclaredField("delFlag");
            assertThat(field.getType()).isEqualTo(Long.class);
            assertThat(field.getAnnotation(TableLogic.class).delval()).isEqualTo("id");
        }
        assertThat(PrintTemplateVersion.class.getDeclaredField("delFlag").getAnnotation(TableLogic.class)).isNotNull();
        assertThat(PrintExecution.class.getDeclaredField("delFlag").getAnnotation(TableLogic.class)).isNotNull();
    }

    @Test
    void seedsMatchEnumsAndNeverGrantRolesOrReportPhysicalSuccess() throws Exception {
        String sql = Files.readString(migrationDirectory().resolve("V1.0.169__add_native_print_resources.sql"));
        for (Class<? extends Enum<?>> type : List.of(PrintDesignStatus.class, PrintExecutionResult.class, PrintScene.class, PrintSourceType.class, PrintDataMode.class)) {
            for (Enum<?> value : type.getEnumConstants()) {
                assertThat(sql).contains("'" + value.name() + "'");
            }
        }
        assertThat(sql).contains("print:template:view", "print:template:manage", "print:template:publish", "print:execute").doesNotContain("INSERT INTO sys_role_resource", "${", "'SUCCESS'");
        for (String statement : sql.split(";")) {
            if (statement.contains("INSERT INTO")) {
                assertThat(statement).contains("NOT EXISTS", "SELECT 1");
            }
        }
    }
}
