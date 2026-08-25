package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Excel 建模初始化结果。 */
@Data
@AllArgsConstructor
public class BusinessApplicationExcelImportResultVO {

    private Long applicationId;

    private Long objectId;

    private String objectCode;
}
