package com.mdframe.forge.plugin.data.dto;

import lombok.Data;

@Data
public class DataDimensionItemDTO {

    private Long id;

    private String itemValue;

    private String itemLabel;

    private Integer sort;

    private Integer status;

    private String extraJson;
}
