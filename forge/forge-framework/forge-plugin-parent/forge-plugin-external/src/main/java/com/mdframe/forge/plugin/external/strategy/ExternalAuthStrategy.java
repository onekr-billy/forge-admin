package com.mdframe.forge.plugin.external.strategy;

import java.net.http.HttpRequest;

public interface ExternalAuthStrategy {

    String getAuthType();

    void applyAuth(HttpRequest.Builder requestBuilder, String authConfig);

    boolean validateConfig(String authConfig);
}
