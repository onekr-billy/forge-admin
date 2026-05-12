package com.mdframe.forge.plugin.data.dto;

import lombok.Data;

import java.util.List;

@Data
public class DataDatasetSaveDTO {

    private Long id;

    private String datasetCode;

    private String datasetName;

    private Long connectionId;

    private String datasetType;

    private String tableName;

    private String sqlText;

    private String paramSchemaJson;

    private String defaultOrderJson;

    private Integer maxRows;

    private Integer timeoutSeconds;

    private Integer cacheEnabled;

    private Integer cacheTtlSeconds;

    private Integer status;

    private String description;

    private List<DataDatasetFieldDTO> fields;
}