package com.mdframe.forge.plugin.data.enums;

public enum FieldRoleEnum {

    DIMENSION("DIMENSION", "维度"),

    MEASURE("MEASURE", "指标");

    private final String code;

    private final String description;

    FieldRoleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}