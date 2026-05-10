package com.mdframe.forge.plugin.external.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ExternalAuthStrategyFactory {

    private final Map<String, ExternalAuthStrategy> strategies;

    public ExternalAuthStrategyFactory(List<ExternalAuthStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(ExternalAuthStrategy::getAuthType, Function.identity()));
    }

    public ExternalAuthStrategy getStrategy(String authType) {
        return strategies.getOrDefault(authType, strategies.get("None"));
    }

    public List<String> getSupportedTypes() {
        return new ArrayList<>(strategies.keySet());
    }
}
