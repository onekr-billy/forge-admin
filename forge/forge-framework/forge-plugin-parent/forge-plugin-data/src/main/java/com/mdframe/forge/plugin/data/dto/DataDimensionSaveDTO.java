package com.mdframe.forge.plugin.data.dto;

import lombok.Data;

@Data
public class DataDimensionSaveDTO {

    private Long id;

    private String dimensionCode;

    private String dimensionName;

    private String sourceType;

    private Long connectionId;

    private String sqlText;

    private String valueColumn;

    private String labelColumn;

    private Integer status;

    private String description;
}
