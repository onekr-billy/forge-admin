package com.mdframe.forge.plugin.generator.service.excel;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw row read from an Excel workbook.
 */
@Data
public class DynamicCrudExcelRow {

    private Integer rowNum;
    private Map<Integer, Object> values = new LinkedHashMap<>();
}
