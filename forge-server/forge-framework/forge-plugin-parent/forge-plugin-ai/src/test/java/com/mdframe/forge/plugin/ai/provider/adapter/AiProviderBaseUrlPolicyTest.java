package com.mdframe.forge.plugin.ai.provider.adapter;

import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProviderBaseUrlPolicyTest {

    @Test
    void normalizeShouldRejectUnsafeUris() {
        assertThrows(BusinessException.class, () -> normalize("ftp://example.com"));
        assertThrows(BusinessException.class, () -> normalize("https://example.com/path?key=value"));
        assertThrows(BusinessException.class, () -> normalize("https://example.com/path#fragment"));
        assertThrows(BusinessException.class, () -> normalize("https://user@example.com/path"));
    }

    @Test
    void nativeShouldUseOfficialRootAndRejectCompatiblePath() {
        assertEquals("https://dashscope.aliyuncs.com",
                AiProviderBaseUrlPolicy.normalizeAndValidate("dashscope_native", null));
        assertEquals("https://dashscope.aliyuncs.com",
                AiProviderBaseUrlPolicy.normalizeAndValidate("dashscope_native",
                        "https://dashscope.aliyuncs.com/"));
        assertThrows(BusinessException.class,
                () -> AiProviderBaseUrlPolicy.normalizeAndValidate("dashscope_native",
                        "https://dashscope.aliyuncs.com/compatible-mode"));
    }

    @Test
    void compatibleShouldRequireCompatibleModeOnOfficialDashScopeHost() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible",
                        "https://dashscope.aliyuncs.com/compatible-mode/"));
        assertThrows(BusinessException.class,
                () -> AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible",
                        "https://dashscope.aliyuncs.com"));
        assertThrows(BusinessException.class,
                () -> AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible",
                        "https://dashscope.aliyuncs.com/compatible-mode/v1"));
    }

    @Test
    void customProxyShouldOnlyApplyGenericValidation() {
        assertEquals("https://proxy.example.com/dashscope",
                AiProviderBaseUrlPolicy.normalizeAndValidate("dashscope_native",
                        "https://proxy.example.com/dashscope/"));
        assertEquals("http://proxy.example.com/openai/v1",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible",
                        "http://proxy.example.com/openai/v1/"));
    }

    @Test
    void blankBaseUrlShouldFallBackToProviderTypeDefault() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "alibaba", null));
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "alibaba", " "));
        assertEquals("https://api.openai.com/v1",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "openai", null));
        assertEquals("https://open.bigmodel.cn/api/paas/v4",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "zhipu", null));
        assertEquals("https://api.moonshot.cn/v1",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "moonshot", null));
        assertEquals("https://api.deepseek.com/v1",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "deepseek", null));
        assertEquals("http://localhost:11434/v1",
                AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "ollama", null));
        // DashScope 原生协议优先使用官方根地址，不受供应商类型影响
        assertEquals("https://dashscope.aliyuncs.com",
                AiProviderBaseUrlPolicy.normalizeAndValidate("dashscope_native", "alibaba", null));
    }

    @Test
    void blankBaseUrlWithoutDefaultShouldFail() {
        // azure/custom 无固定官方端点，空地址仍需手动填写
        assertThrows(BusinessException.class,
                () -> AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "azure", null));
        assertThrows(BusinessException.class,
                () -> AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "custom", null));
        // 未知供应商类型不补默认，保持原行为
        assertThrows(BusinessException.class,
                () -> AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", "unknown", null));
    }

    private String normalize(String baseUrl) {
        return AiProviderBaseUrlPolicy.normalizeAndValidate("openai_compatible", baseUrl);
    }
}
