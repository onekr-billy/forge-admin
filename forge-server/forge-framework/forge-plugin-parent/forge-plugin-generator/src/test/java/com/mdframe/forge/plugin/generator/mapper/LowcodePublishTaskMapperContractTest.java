package com.mdframe.forge.plugin.generator.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodePublishTaskMapperContractTest {

    @Test
    void taskTransitionsUseTenantLeaseCasAndBoundedRetryStates() throws Exception {
        String xml = Files.readString(resolveMapper(), StandardCharsets.UTF_8);

        assertTrue(xml.contains("WHERE tenant_id = #{tenantId}"));
        assertTrue(xml.contains("task_status = 'PROCESSING'"));
        assertTrue(xml.contains("lock_owner = #{task.lockOwner}"));
        assertTrue(xml.contains("retry_count = retry_count + 1"));
        assertTrue(xml.contains("task_status = 'RETRY'"));
        assertTrue(xml.contains("task_status = 'PENDING'"));
        assertTrue(xml.contains("current_stage = #{expectedStage}"));
        assertTrue(xml.contains("current_stage = #{nextStage}"));
        assertTrue(xml.contains("replay_count = replay_count + 1"));
        assertTrue(xml.contains("replay_reason = #{replayReason}"));
        assertTrue(xml.contains("AND task_status = 'DEAD'"));
        assertFalse(xml.contains("${"));
    }

    private Path resolveMapper() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(
                    "forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/"
                            + "src/main/resources/mapper/LowcodePublishTaskMapper.xml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            candidate = current.resolve(
                    "forge-framework/forge-plugin-parent/forge-plugin-generator/"
                            + "src/main/resources/mapper/LowcodePublishTaskMapper.xml");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return Path.of("src/main/resources/mapper/LowcodePublishTaskMapper.xml");
    }
}
