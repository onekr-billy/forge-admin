package com.mdframe.forge.plugin.external.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AuthStrategyFactory {

    private final Map<String, AuthStrategy> strategies;

    public AuthStrategyFactory(List<AuthStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(AuthStrategy::getAuthType, Function.identity()));
    }

    public AuthStrategy getStrategy(String authType) {
        return strategies.getOrDefault(authType, strategies.get("None"));
    }

    public List<String> getSupportedTypes() {
        return new ArrayList<>(strategies.keySet());
    }
}