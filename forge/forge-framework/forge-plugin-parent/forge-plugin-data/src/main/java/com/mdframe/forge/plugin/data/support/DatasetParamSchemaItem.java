package com.mdframe.forge.plugin.data.support;

import lombok.Data;

@Data
public class DatasetParamSchemaItem {

    private String paramName;

    private String label;

    private String dataType;

    private Boolean required;

    private Object defaultValue;

    private String operator;

    private String fieldName;
}
