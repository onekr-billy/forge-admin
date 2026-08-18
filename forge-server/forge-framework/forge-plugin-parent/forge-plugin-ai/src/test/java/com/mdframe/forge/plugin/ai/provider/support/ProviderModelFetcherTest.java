package com.mdframe.forge.plugin.ai.provider.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderModelFetcherTest {

    private final ProviderModelFetcher fetcher = new ProviderModelFetcher();

    @Test
    void dashScopeNativeRootShouldAppendCompatibleModelsEndpoint() {
        List<String> candidates = fetcher.buildCandidates("https://dashscope.aliyuncs.com");
        // 原生根地址先按通用规则拼 /v1/models，再补 OpenAI 兼容端点兜底
        assertEquals(List.of(
                "https://dashscope.aliyuncs.com/v1/models",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/models"), candidates);
    }

    @Test
    void dashScopeNativeRootWithTrailingSlashShouldNormalize() {
        List<String> candidates = fetcher.buildCandidates("https://dashscope.aliyuncs.com/");
        assertEquals(List.of(
                "https://dashscope.aliyuncs.com/v1/models",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/models"), candidates);
    }

    @Test
    void dashScopeCompatibleShouldKeepSingleModelsEndpoint() {
        List<String> candidates = fetcher.buildCandidates("https://dashscope.aliyuncs.com/compatible-mode");
        assertEquals(List.of("https://dashscope.aliyuncs.com/compatible-mode/v1/models"), candidates);
    }

    @Test
    void dashScopeCompatibleWithVersionSegmentShouldKeepBaseModels() {
        List<String> candidates = fetcher.buildCandidates("https://dashscope.aliyuncs.com/compatible-mode/v1");
        // 已含版本段：拼 {base}/models，DashScope 兼容兜底与之去重
        assertEquals(List.of("https://dashscope.aliyuncs.com/compatible-mode/v1/models"), candidates);
    }

    @Test
    void openAiBaseWithVersionShouldUseBaseModels() {
        List<String> candidates = fetcher.buildCandidates("https://api.openai.com/v1");
        assertEquals(List.of("https://api.openai.com/v1/models"), candidates);
    }

    @Test
    void nonDashScopeRootShouldUseV1Models() {
        List<String> candidates = fetcher.buildCandidates("https://api.example.com");
        assertTrue(candidates.contains("https://api.example.com/v1/models"));
        assertTrue(candidates.stream().noneMatch(url -> url.contains("compatible-mode")));
    }
}
