package com.mdframe.forge.starter.flow.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowModelVersionCleanupContractTest {

    @Test
    void cleanupMustLockTenantVersionsAndProtectRuntimeReferences() throws IOException {
        String mapper = Files.readString(Path.of(
                "src/main/resources/mapper/FlowModelVersionMapper.xml"));
        String service = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/starter/flow/service/impl/FlowModelVersionServiceImpl.java"));
        String controller = Files.readString(Path.of(
                "../../../forge-flow/forge-flow-server/src/main/java/com/mdframe/forge/flow/controller/FlowModelVersionController.java"));
        String dto = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/starter/flow/dto/VersionCleanupDTO.java"));

        String cleanupStatement = statement(mapper, "selectCleanupCandidates");
        assertThat(cleanupStatement).contains("tenant_id = #{tenantId}",
                "del_flag = 0", "FOR UPDATE");
        assertThat(cleanupStatement).doesNotContain("ORDER BY", "LIMIT");
        assertThat(service).contains("cleanupVersions(VersionCleanupDTO dto)",
                "selectCleanupCandidates(dto.getModelId(), tenantId)",
                "candidates.sort(Comparator.comparing(FlowModelVersion::getVersion",
                "thenComparing(FlowModelVersion::getCreateTime",
                "thenComparing(FlowModelVersion::getId",
                "isReferencedByRunningInstance(version)",
                "logicalDeleteByIdAndTenant(version.getId(), tenantId)");
        assertThat(controller).contains("@PostMapping(\"/cleanup\")",
                "@RequestBody VersionCleanupDTO dto");
        assertThat(dto).contains("private String modelId", "private Integer retainLatest");
    }

    private static String statement(String xml, String id) {
        int start = xml.indexOf("<select id=\"" + id + "\"");
        int end = xml.indexOf("</select>", start);
        return start >= 0 && end > start ? xml.substring(start, end) : "";
    }
}
