package com.mdframe.forge.plugin.external.strategy.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.external.strategy.AuthStrategy;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;

@Component
public class BearerTokenAuthStrategy implements AuthStrategy {

    @Override
    public String getAuthType() {
        return "BearerToken";
    }

    @Override
    public void applyAuth(HttpRequest.Builder requestBuilder, String authConfig) {
        JSONObject config = JSON.parseObject(authConfig);
        String token = config.getString("token");
        String header = config.getString("tokenHeader");
        String prefix = config.getString("tokenPrefix");

        if (header == null || header.isEmpty()) {
            header = "Authorization";
        }
        if (prefix == null || prefix.isEmpty()) {
            prefix = "Bearer";
        }

        requestBuilder.header(header, prefix + " " + token);
    }

    @Override
    public boolean validateConfig(String authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return false;
        }
        try {
            JSONObject config = JSON.parseObject(authConfig);
            return config.containsKey("token") && config.getString("token") != null;
        } catch (Exception e) {
            return false;
        }
    }
}