package com.mdframe.forge.plugin.data.enums;

public enum DatasetTypeEnum {

    TABLE("TABLE", "单表数据集"),

    SQL("SQL", "SQL数据集");

    private final String code;

    private final String description;

    DatasetTypeEnum(String code, String description) {
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