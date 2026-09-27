package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessFlowReturnIdempotencyContractTest {

    @Test
    void businessReturnMustForwardTrustedRetryIdentity() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskCommandCoordinator.java"));
        int start = source.indexOf("case \"return\" -> flowClient.returnTask(");
        int end = source.indexOf("default -> flowClient.approve(", start);

        assertTrue(start >= 0 && end > start);
        String returnBranch = source.substring(start, end);
        assertTrue(returnBranch.contains("resolveTrustedTaskTenant(dto)"));
        assertTrue(returnBranch.contains("dto.getIdempotencyKey()"));
        assertTrue(returnBranch.contains("dto.getRequestDigest()"));
    }
}
