package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessFlowRetryIdentityContractTest {

    @Test
    void resubmitAndWithdrawMustUseStableServerDerivedCredentials() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowTaskCommandCoordinator.java"));

        assertThat(source)
                .contains("BusinessFlowCommandIdentity.forTaskAction(",
                        "tenantId, credentials.idempotencyKey(), credentials.requestDigest()",
                        "BusinessFlowCommandIdentity.forProcessAction(",
                        "comment, tenantId,\n                credentials.idempotencyKey(), credentials.requestDigest()")
                .doesNotContain("String.valueOf(userIdSupplier.get()),\n                StringUtils.defaultIfBlank(dto.getComment(), \"修改后重提\"), variables");
    }
}
