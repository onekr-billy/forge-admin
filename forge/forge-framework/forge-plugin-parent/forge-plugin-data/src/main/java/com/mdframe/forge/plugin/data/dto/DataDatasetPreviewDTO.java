package com.mdframe.forge.plugin.data.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DataDatasetPreviewDTO {

    private Long datasetId;

    private Map<String, Object> params;

    private Integer maxRows;
}