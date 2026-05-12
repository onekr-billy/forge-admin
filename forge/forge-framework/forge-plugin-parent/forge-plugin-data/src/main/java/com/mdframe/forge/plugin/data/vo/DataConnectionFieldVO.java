package com.mdframe.forge.plugin.data.vo;

import lombok.Data;

@Data
public class DataConnectionFieldVO {

    private String columnName;

    private String columnType;

    private String columnComment;

    private Boolean nullable;

    private Boolean primaryKey;
}