package com.mdframe.forge.starter.excel.model;

import java.util.List;

/**
 * 导入模板列说明。
 *
 * @param fieldName        业务字段编码
 * @param columnName       Excel 表头名称
 * @param required         是否必填
 * @param exampleValue     样例值
 * @param description      填写说明
 * @param dropdownOptions  下拉选项（字典标签等），空列表表示不生成下拉
 */
public record ImportTemplateColumn(
        String fieldName,
        String columnName,
        boolean required,
        String exampleValue,
        String description,
        List<String> dropdownOptions
) {
    public ImportTemplateColumn {
        dropdownOptions = dropdownOptions == null ? List.of() : List.copyOf(dropdownOptions);
    }

    public ImportTemplateColumn(String fieldName,
                                String columnName,
                                boolean required,
                                String exampleValue,
                                String description) {
        this(fieldName, columnName, required, exampleValue, description, List.of());
    }
}
