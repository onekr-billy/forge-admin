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
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowRemoteCommandService.java"));
        int start = source.indexOf("case BusinessFlowRemoteTaskEnvelope.COMMAND_RETURN -> flowClient.returnTask(");
        int end = source.indexOf("case BusinessFlowRemoteTaskEnvelope.COMMAND_RESUBMIT", start);

        assertTrue(start >= 0 && end > start);
        String returnBranch = source.substring(start, end);
        assertTrue(returnBranch.contains("request.getTenantId()"));
        assertTrue(returnBranch.contains("request.getIdempotencyKey()"));
        assertTrue(returnBranch.contains("request.getActionRequestDigest()"));
        assertTrue(returnBranch.contains("request.getTargetActivityId()"));
    }
}
