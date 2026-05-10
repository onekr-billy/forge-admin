package com.mdframe.forge.plugin.external.strategy;

import java.net.http.HttpRequest;

public interface AuthStrategy {

    String getAuthType();

    void applyAuth(HttpRequest.Builder requestBuilder, String authConfig);

    boolean validateConfig(String authConfig);
}