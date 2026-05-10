package com.mdframe.forge.plugin.external.strategy.impl;

import com.mdframe.forge.plugin.external.strategy.AuthStrategy;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;

@Component
public class NoneAuthStrategy implements AuthStrategy {

    @Override
    public String getAuthType() {
        return "None";
    }

    @Override
    public void applyAuth(HttpRequest.Builder requestBuilder, String authConfig) {
    }

    @Override
    public boolean validateConfig(String authConfig) {
        return true;
    }
}