package com.mdframe.forge.plugin.ai.provider.support;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 从供应商拉取可用模型列表。
 * <p>
 * 调用 OpenAI 兼容的 {@code GET /v1/models} 端点（参考 cc-switch 的 model_fetch 实现），
 * 按候选端点列表依次尝试，兼容各家 baseUrl 形态差异：
 * <ul>
 *   <li>baseUrl 以版本段 {@code /v{N}} 结尾（如 {@code /v1}、智谱 {@code /api/coding/paas/v4}）
 *       → 拼 {@code {base}/models}（版本号已在路径里，不能再补 /v1）</li>
 *   <li>baseUrl 命中已知的「Anthropic 协议兼容子路径」（如 {@code /anthropic}、{@code /api/anthropic}）
 *       → 剥离后缀后再拼 {@code /v1/models}、{@code /models} 兜底</li>
 * </ul>
 */
@Slf4j
@Component
public class ProviderModelFetcher {

    /** 单个候选端点的请求超时（秒） */
    private static final int FETCH_TIMEOUT_SECS = 15;

    /** 响应体截断长度，避免把整页 HTML 404 带进错误信息 */
    private static final int ERROR_BODY_MAX_CHARS = 512;

    /** 已知的「Anthropic 协议兼容子路径」后缀，按长度降序（最长前缀优先匹配）。 */
    private static final String[] KNOWN_COMPAT_SUFFIXES = {
            "/api/claudecode", "/api/anthropic", "/apps/anthropic", "/api/coding",
            "/claudecode", "/anthropic", "/step_plan", "/coding", "/claude",
    };

    /** DashScope 主机：原生根地址无模型列表端点，模型列表需走 OpenAI 兼容路径 */
    private static final String DASHSCOPE_HOST = "dashscope.aliyuncs.com";
    private static final String DASHSCOPE_COMPATIBLE_BASE = "https://dashscope.aliyuncs.com/compatible-mode";

    private final HttpClient httpClient;

    public ProviderModelFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(FETCH_TIMEOUT_SECS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 拉取模型列表。
     *
     * @param baseUrl 供应商 Base URL
     * @param apiKey 明文 API Key
     * @return 模型列表（已按 id 升序）
     */
    public List<FetchedModel> fetch(String baseUrl, String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("API Key不能为空，无法获取模型");
        }
        List<String> candidates = buildCandidates(baseUrl);
        if (candidates.isEmpty()) {
            throw new BusinessException("Base URL为空，无法获取模型");
        }

        String lastError = null;
        for (String url : candidates) {
            try {
                return fetchFrom(url, apiKey);
            } catch (NotFoundException e) {
                lastError = e.getMessage();
                log.debug("[ProviderModelFetcher] 候选端点不可用: {} ({})", url, e.getMessage());
            }
        }
        throw new BusinessException("获取模型失败: " + (lastError != null ? lastError : "所有候选端点均不可用"));
    }

    private List<FetchedModel> fetchFrom(String url, String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(FETCH_TIMEOUT_SECS))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404 || status == 405) {
                // 该候选端点不可用，尝试下一个
                throw new NotFoundException("HTTP " + status + ": " + truncateBody(response.body()));
            }
            if (status < 200 || status >= 300) {
                throw new BusinessException("HTTP " + status + ": " + truncateBody(response.body()));
            }
            return parseModels(response.body());
        } catch (NotFoundException | BusinessException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new BusinessException("请求失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("请求被中断");
        }
    }

    /**
     * 构造模型列表端点的候选 URL，保持出现顺序、去重。
     */
    List<String> buildCandidates(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (!StringUtils.hasText(trimmed)) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();

        if (endsWithVersionSegment(trimmed)) {
            candidates.add(trimmed + "/models");
            if (!trimmed.endsWith("/v1")) {
                candidates.add(trimmed + "/v1/models");
            }
        } else {
            candidates.add(trimmed + "/v1/models");
        }

        String stripped = stripCompatSuffix(trimmed);
        if (stripped != null) {
            String root = stripped.replaceAll("/+$", "");
            if (StringUtils.hasText(root) && root.contains("://")) {
                candidates.add(root + "/v1/models");
                candidates.add(root + "/models");
            }
        }

        // DashScope 原生根地址（无 /compatible-mode 路径）的模型列表在 OpenAI 兼容端点下
        String dashScopeCompat = toDashScopeCompatible(trimmed);
        if (dashScopeCompat != null && !trimmed.endsWith("/compatible-mode")) {
            candidates.add(dashScopeCompat + "/v1/models");
        }

        List<String> unique = new ArrayList<>();
        for (String c : candidates) {
            if (!unique.contains(c)) {
                unique.add(c);
            }
        }
        return unique;
    }

    /**
     * 命中 DashScope 官方主机时返回其 OpenAI 兼容端点根地址，否则返回 {@code null}。
     */
    private String toDashScopeCompatible(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            if (DASHSCOPE_HOST.equalsIgnoreCase(uri.getHost())) {
                return DASHSCOPE_COMPATIBLE_BASE;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean endsWithVersionSegment(String url) {
        int slashIdx = url.lastIndexOf('/');
        String last = slashIdx >= 0 ? url.substring(slashIdx + 1) : url;
        if (!last.startsWith("v") || last.length() == 1) {
            return false;
        }
        for (int i = 1; i < last.length(); i++) {
            if (!Character.isDigit(last.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String stripCompatSuffix(String baseUrl) {
        for (String suffix : KNOWN_COMPAT_SUFFIXES) {
            if (baseUrl.endsWith(suffix)) {
                return baseUrl.substring(0, baseUrl.length() - suffix.length());
            }
        }
        return null;
    }

    private List<FetchedModel> parseModels(String body) {
        try {
            com.alibaba.fastjson2.JSONObject root = com.alibaba.fastjson2.JSON.parseObject(body);
            if (root == null) {
                return List.of();
            }
            var data = root.getJSONArray("data");
            if (data == null) {
                return List.of();
            }
            List<FetchedModel> models = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                com.alibaba.fastjson2.JSONObject item = data.getJSONObject(i);
                String id = item.getString("id");
                if (StringUtils.hasText(id)) {
                    models.add(new FetchedModel(id, item.getString("owned_by")));
                }
            }
            models.sort((a, b) -> a.id().compareTo(b.id()));
            return models;
        } catch (Exception e) {
            log.warn("[ProviderModelFetcher] 模型列表响应解析失败", e);
            throw new BusinessException("模型列表响应解析失败");
        }
    }

    private static String truncateBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= ERROR_BODY_MAX_CHARS ? body : body.substring(0, ERROR_BODY_MAX_CHARS) + "…";
    }

    /** 候选端点返回 404/405，尝试下一个。 */
    private static class NotFoundException extends RuntimeException {
        NotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 拉取到的模型信息。
     */
    public record FetchedModel(String id, String ownedBy) {
    }
}
