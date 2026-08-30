package com.mdframe.forge.plugin.generator.constant;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 业务对象设计状态。
 */
@Getter
public enum BusinessObjectDesignStatus {

    DRAFT("DRAFT"),
    DESIGNING("DESIGNING"),
    READY("READY"),
    PUBLISHED("PUBLISHED"),
    CHANGED("CHANGED"),
    DISABLED("DISABLED");

    private static final Set<String> VALUES = Stream.of(values())
            .map(BusinessObjectDesignStatus::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private final String code;

    BusinessObjectDesignStatus(String code) {
        this.code = code;
    }

    public boolean matches(String value) {
        return value != null && this.code.equalsIgnoreCase(value.trim());
    }

    public static boolean isValid(String status) {
        return VALUES.contains(normalize(status));
    }

    public static String normalize(String status) {
        return StringUtils.defaultIfBlank(status, DRAFT.code).trim().toUpperCase(Locale.ROOT);
    }
}
