package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.List;

/**
 * 应用页面表单自动准备数据存储参数。
 */
@Data
public class BusinessApplicationFormDataProvisionDTO {

    private String formAssetId;

    private String formName;

    private List<BusinessFieldDTO> fields;

    private FormDesignerSchemaDTO formDesignerSchema;

    /** 低代码运行数据源 ID。空值时使用默认可写运行数据源。 */
    private Long runtimeDatasourceId;

    /** BLANK / DB_IMPORT */
    private String createMode;

    private Long importDatasourceId;

    private String importTableName;
}
