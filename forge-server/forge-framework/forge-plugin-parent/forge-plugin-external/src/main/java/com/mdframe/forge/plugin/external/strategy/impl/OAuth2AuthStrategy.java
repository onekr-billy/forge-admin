package com.mdframe.forge.plugin.external.strategy.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.external.strategy.ExternalAuthStrategy;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.outbound.client.SecureOutboundClient;
import com.mdframe.forge.starter.outbound.constant.OutboundScenes;
import com.mdframe.forge.starter.outbound.model.OutboundRequest;
import com.mdframe.forge.starter.outbound.model.OutboundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OAuth2AuthStrategy implements ExternalAuthStrategy {

    private final SecureOutboundClient outboundClient;

    @Override
    public String getAuthType() {
        return "OAuth2";
    }

    @Override
    public void applyAuth(HttpRequest.Builder requestBuilder, String authConfig) {
        JSONObject config = JSON.parseObject(authConfig);
        String accessToken = requestAccessToken(config);
        String tokenType = config.getString("tokenType");
        if (tokenType == null || tokenType.isEmpty()) {
            tokenType = "Bearer";
        }
        requestBuilder.header("Authorization", tokenType + " " + accessToken);
    }

    @Override
    public boolean validateConfig(String authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return false;
        }
        try {
            JSONObject config = JSON.parseObject(authConfig);
            return config.getString("tokenUrl") != null
                    && config.getString("clientId") != null
                    && config.getString("clientSecret") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String requestAccessToken(JSONObject config) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", defaultValue(config.getString("grantType"), "client_credentials"));
        form.put("client_id", config.getString("clientId"));
        form.put("client_secret", config.getString("clientSecret"));
        if (config.getString("scope") != null && !config.getString("scope").isEmpty()) {
            form.put("scope", config.getString("scope"));
        }

        OutboundResponse response = outboundClient.execute(OutboundRequest.builder()
                .scene(OutboundScenes.EXTERNAL_CONNECTOR)
                .url(config.getString("tokenUrl"))
                .method("POST")
                .contentType("application/x-www-form-urlencoded")
                .body(encodeForm(form).getBytes(StandardCharsets.UTF_8))
                .build());
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new BusinessException("OAuth2获取Token失败，HTTP状态码: " + response.getStatusCode());
        }
        JSONObject body = JSON.parseObject(response.bodyAsUtf8());
        String tokenType = body.getString("token_type");
        if (tokenType != null && !tokenType.isEmpty()) {
            config.put("tokenType", tokenType);
        }
        String accessToken = body.getString("access_token");
        if (accessToken == null || accessToken.isEmpty()) {
            throw new BusinessException("OAuth2响应缺少access_token");
        }
        return accessToken;
    }

    private String encodeForm(Map<String, String> form) {
        return form.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String defaultValue(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
