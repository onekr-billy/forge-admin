package com.mdframe.forge.plugin.external.strategy.impl;

import com.mdframe.forge.plugin.external.strategy.ExternalAuthStrategy;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;

@Component
public class NoneAuthStrategy implements ExternalAuthStrategy {

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
