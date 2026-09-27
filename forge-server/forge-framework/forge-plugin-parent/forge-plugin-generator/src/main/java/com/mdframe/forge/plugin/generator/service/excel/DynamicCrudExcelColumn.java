package com.mdframe.forge.plugin.generator.service.excel;

import lombok.Data;

/**
 * Excel column metadata shared by schema resolution and cell value adaptation.
 */
@Data
public class DynamicCrudExcelColumn {

    private String field;
    private String label;
    private String type;
    private String dataType;
    private String dictType;
    private String targetField;
    private boolean required;
    private String exampleValue;
    private String description;
}
