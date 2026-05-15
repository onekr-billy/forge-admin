package com.mdframe.forge.plugin.data.vo;

import lombok.Data;

@Data
public class DataDatasetFieldVO {

    private Long id;

    private String fieldName;

    private String fieldLabel;

    private String sourceColumn;

    private String dbType;

    private String dataType;

    private String fieldRole;

    private String defaultAgg;

    private Integer queryEnabled;

    private Integer displayEnabled;

    private String sensitiveLevel;

    private String maskRule;

    private String dictType;

    private String dateFormat;

    private String dataUnit;

    private Long dimensionId;

    private String dimensionCode;

    private String dimensionName;

    private Integer sort;

    private String description;
}
