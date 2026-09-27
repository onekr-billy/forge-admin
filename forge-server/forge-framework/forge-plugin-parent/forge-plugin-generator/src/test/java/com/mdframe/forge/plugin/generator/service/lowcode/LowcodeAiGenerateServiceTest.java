package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateRequest;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeAiAppGenerateResult;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRuntimeConfig;
import com.mdframe.forge.plugin.generator.service.AiClientAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LowcodeAiGenerateServiceTest {

    @Test
    void ruleFallbackKeepsDomainModelPageAndRequestedTablePrefixConnected() {
        LowcodeRuntimeConfigBuilder runtimeConfigBuilder = mock(LowcodeRuntimeConfigBuilder.class);
        LowcodeRuntimeConfig runtimeConfig = new LowcodeRuntimeConfig();
        runtimeConfig.setConfigKey("crm_customer");
        when(runtimeConfigBuilder.buildRuntimeConfig(anyString(), any(), any())).thenReturn(runtimeConfig);
        LowcodeAiGenerateService service = new LowcodeAiGenerateService(
            new ObjectMapper(),
            mock(LowcodeDomainService.class),
            runtimeConfigBuilder,
            mock(AiClientAdapter.class),
            mock(LowcodeModelSchemaNormalizer.class),
            mock(LowcodePolicyService.class));
        LowcodeAiAppGenerateRequest request = new LowcodeAiAppGenerateRequest();
        request.setDescription("生成客户管理应用，数据表统一以 acme_ 开头");

        LowcodeAiAppGenerateResult result = service.generateAppDraft(request);

        assertTrue(result.getFallback());
        assertFalse(result.getDomains().isEmpty());
        assertFalse(result.getModels().isEmpty());
        assertFalse(result.getApps().isEmpty());
        assertEquals("acme_", result.getDomains().get(0).getTablePrefix());
        assertTrue(result.getModels().get(0).getModelSchema().getTableName().startsWith("acme_"));
        assertEquals(result.getModels().get(0).getModelCode(), result.getApps().get(0).getObjectCode());
    }
}
