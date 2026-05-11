package com.mdframe.forge.plugin.data.enums;

public enum SensitiveLevelEnum {

    NONE("NONE"),

    MASK("MASK"),

    HIDDEN("HIDDEN");

    private final String code;

    SensitiveLevelEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}