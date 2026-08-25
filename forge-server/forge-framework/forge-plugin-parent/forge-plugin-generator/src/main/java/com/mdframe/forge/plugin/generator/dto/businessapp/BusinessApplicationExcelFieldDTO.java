package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Excel 建模向导中的字段识别与确认参数。 */
@Data
public class BusinessApplicationExcelFieldDTO {

    private Integer columnIndex;

    private String headerName;

    private String fieldCode;

    private String columnName;

    /** TEXT、NUMBER、DATE、DATETIME、SWITCH、SELECT。 */
    private String fieldType;

    private String dataType;

    private String componentType;

    private Integer length;

    private Integer precision;

    private Boolean required;

    private Boolean searchable;

    private Boolean listVisible;

    private Boolean formVisible;

    private List<String> suggestedOptions = new ArrayList<>();
}
