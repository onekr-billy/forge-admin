package com.mdframe.forge.plugin.generator.constant;

import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 业务扩展治理状态。
 */
@Getter
public enum BusinessExtensionStatus {

    DRAFT("DRAFT"),
    TESTED("TESTED"),
    ENABLED("ENABLED"),
    DISABLED("DISABLED");

    public static final Set<String> ALL = Stream.of(values())
            .map(BusinessExtensionStatus::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final String code;

    BusinessExtensionStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }
}
