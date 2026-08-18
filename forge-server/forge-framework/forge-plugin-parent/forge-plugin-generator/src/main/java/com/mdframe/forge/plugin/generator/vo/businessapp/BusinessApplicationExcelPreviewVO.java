package com.mdframe.forge.plugin.generator.vo.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationExcelFieldDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Excel 首个 Sheet 的建模预览。 */
@Data
public class BusinessApplicationExcelPreviewVO {

    private String fileName;

    private String sheetName;

    private Integer sampledRowCount;

    private List<BusinessApplicationExcelFieldDTO> fields = new ArrayList<>();
}
