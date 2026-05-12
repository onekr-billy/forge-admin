package com.mdframe.forge.plugin.data.enums;

public enum DataTypeEnum {

    STRING("STRING"),

    NUMBER("NUMBER"),

    DATE("DATE"),

    DATETIME("DATETIME"),

    BOOLEAN("BOOLEAN");

    private final String code;

    DataTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}