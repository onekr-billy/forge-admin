package com.mdframe.forge.starter.flow.dto;

import lombok.Data;

/**
 * 流程表单字段目录项。
 */
@Data
public class FormFieldCatalogItemDTO {

    private String field;

    private String label;

    private String componentType;

    private String dataType;

    private Boolean required;

    private String optionSource;

    private String source;

    /** main / array；未设置时按 main 兼容。 */
    private String scope;

    /** 数组父字段，仅 scope=array 时有值。 */
    private String arrayKey;

    /** 数组父字段展示名。 */
    private String arrayLabel;

    /** 数组行字段，仅 scope=array 时有值。 */
    private String itemField;
}
