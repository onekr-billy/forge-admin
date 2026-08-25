package com.mdframe.forge.plugin.generator.constant;

import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 应用设计状态。
 */
@Getter
public enum BusinessApplicationDesignStatus {

    DRAFT("DRAFT"),
    READY("READY"),
    PUBLISHED("PUBLISHED"),
    CHANGED("CHANGED");

    private final String code;

    BusinessApplicationDesignStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static Set<String> supportedStatuses() {
        return Stream.of(values())
                .map(BusinessApplicationDesignStatus::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
