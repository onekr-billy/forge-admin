package com.mdframe.forge.plugin.ai.provider.adapter;

import com.mdframe.forge.plugin.ai.provider.domain.AiProvider;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI Compatible 供应商适配器。
 */
@Slf4j
@Component
public class OpenAiCompatibleProviderAdapter implements AiProviderAdapter {

    @Override
    public String adapterCode() {
        return AiProviderAdapterCode.OPENAI_COMPATIBLE.getCode();
    }

    @Override
    public void validate(AiProvider provider, AiModelRuntimeOptions options) {
        validateCommon(provider, options);
        AiProviderBaseUrlPolicy.normalizeAndValidate(adapterCode(), provider.getBaseUrl());
    }

    @Override
    public ChatModel createChatModel(AiProvider provider, AiModelRuntimeOptions options) {
        String baseUrl = AiProviderBaseUrlPolicy.normalizeAndValidate(adapterCode(), provider.getBaseUrl());
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .restClientBuilder(buildLoggingRestClientBuilder())
                .webClientBuilder(buildLoggingWebClientBuilder())
                .build();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(options.model());
        if (options.temperature() != null) {
            optionsBuilder.temperature(options.temperature());
        }
        if (options.maxTokens() != null) {
            optionsBuilder.maxTokens(options.maxTokens());
        }
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(AiProvider provider, String model) {
        validateCommon(provider, new AiModelRuntimeOptions(model, null, null));
        String baseUrl = AiProviderBaseUrlPolicy.normalizeAndValidate(adapterCode(), provider.getBaseUrl());
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(model).build());
    }

    private RestClient.Builder buildLoggingRestClientBuilder() {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
            try {
                log.info("[OpenAI HTTP] 上游同步请求, method={}, url={}, headers={}, body={}",
                        request.getMethod(), request.getURI(),
                        maskHeaders(request.getHeaders()),
                        truncate(new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8), 2000));
            } catch (Exception logEx) {
                log.warn("[OpenAI HTTP] 同步请求日志失败", logEx);
            }
            ClientHttpResponse response = execution.execute(request, body);
            try {
                byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
                log.info("[OpenAI HTTP] 上游同步响应, status={}, headers={}, body={}",
                        response.getStatusCode(),
                        maskHeaders(response.getHeaders()),
                        truncate(new String(responseBody, StandardCharsets.UTF_8), 2000));
                return new BufferedClientHttpResponse(response, responseBody);
            } catch (Exception e) {
                log.warn("[OpenAI HTTP] 同步响应读取失败", e);
                return response;
            }
        });
    }

    private WebClient.Builder buildLoggingWebClientBuilder() {
        return WebClient.builder()
                .filter((request, next) -> {
                    try {
                        log.info("[OpenAI HTTP] 上游流式请求, method={}, url={}, headers={}",
                                request.method(), request.url(), maskHeaders(request.headers()));
                    } catch (Exception logEx) {
                        log.warn("[OpenAI HTTP] 流式请求日志失败", logEx);
                    }
                    return next.exchange(request).doOnNext(response -> {
                        try {
                            log.info("[OpenAI HTTP] 上游流式响应, status={}, contentType={}",
                                    response.statusCode().value(),
                                    response.headers().contentType() == null ? null : response.headers().contentType());
                        } catch (Exception logEx) {
                            log.warn("[OpenAI HTTP] 流式响应日志失败", logEx);
                        }
                    });
                });
    }

    private Map<String, List<String>> maskHeaders(HttpHeaders headers) {
        Map<String, List<String>> masked = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (isSensitiveHeader(name)) {
                masked.put(name, entry.getValue().stream()
                        .map(OpenAiCompatibleProviderAdapter::maskSecret).collect(Collectors.toList()));
            } else {
                masked.put(name, entry.getValue());
            }
        }
        return masked;
    }

    private static boolean isSensitiveHeader(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("authorization") || lower.contains("api-key") || lower.contains("apikey")
                || lower.contains("token") || lower.contains("secret") || lower.contains("cookie");
    }

    private static String maskSecret(String value) {
        if (value == null) return null;
        if (value.length() <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private static final class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        private BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }

    private void validateCommon(AiProvider provider, AiModelRuntimeOptions options) {
        if (provider == null) {
            throw new BusinessException("AI供应商配置不能为空");
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new BusinessException("API Key不能为空");
        }
        if (options == null || !StringUtils.hasText(options.model())) {
            throw new BusinessException("模型标识不能为空");
        }
    }
}
