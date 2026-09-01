package com.mdframe.forge.starter.excel.core;

import com.mdframe.forge.starter.excel.model.ImportTemplateColumn;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelImportTemplateWriterTest {

    @Test
    void shouldWriteSampleDataAndFieldInstructions() throws Exception {
        byte[] workbookBytes = ExcelImportTemplateWriter.write(
                "导入数据",
                List.of(
                        new ImportTemplateColumn("username", "用户名", true, "zhangsan", "账号唯一"),
                        new ImportTemplateColumn("status", "状态", false, "启用", "填写启用或停用")
                )
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("导入数据"));
            assertNotNull(workbook.getSheet("填写说明"));
            assertEquals("用户名", workbook.getSheet("导入数据").getRow(0).getCell(0).getStringCellValue());
            assertEquals("zhangsan", workbook.getSheet("导入数据").getRow(1).getCell(0).getStringCellValue());
            assertEquals("字段编码", workbook.getSheet("填写说明").getRow(0).getCell(2).getStringCellValue());
            assertEquals("username", workbook.getSheet("填写说明").getRow(1).getCell(2).getStringCellValue());
            assertTrue(workbook.getSheet("填写说明").getRow(1).getCell(5).getStringCellValue()
                    .contains("必填。账号唯一"));
        }
    }

    @Test
    void shouldWriteHiddenDictDropdownForDictColumns() throws Exception {
        byte[] workbookBytes = ExcelImportTemplateWriter.write(
                "导入数据",
                List.of(
                        new ImportTemplateColumn("username", "用户名", true, "zhangsan", "账号唯一"),
                        new ImportTemplateColumn(
                                "status",
                                "状态",
                                false,
                                "启用",
                                "请从下拉列表中选择",
                                List.of("启用", "停用", " 启用 ", "")
                        )
                )
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet optionsSheet = workbook.getSheet(ImportTemplateDropdownWriteHandler.OPTIONS_SHEET_NAME);
            assertNotNull(optionsSheet);
            int optionsIndex = workbook.getSheetIndex(optionsSheet);
            assertTrue(workbook.isSheetHidden(optionsIndex) || workbook.isSheetVeryHidden(optionsIndex));
            assertEquals("启用", optionsSheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("停用", optionsSheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(2, optionsSheet.getLastRowNum() + 1);

            Name namedRange = workbook.getName("forge_dict_col_1");
            assertNotNull(namedRange);
            assertTrue(namedRange.getRefersToFormula().contains("字典选项"));
            assertTrue(namedRange.getRefersToFormula().contains("$A$1:$A$2"));

            XSSFSheet dataSheet = workbook.getSheet("导入数据");
            List<? extends DataValidation> validations = dataSheet.getDataValidations();
            assertFalse(validations.isEmpty());
            DataValidation validation = validations.get(0);
            assertEquals("forge_dict_col_1", validation.getValidationConstraint().getFormula1());
            assertEquals(1, validation.getRegions().getCellRangeAddress(0).getFirstColumn());
            assertEquals(1, validation.getRegions().getCellRangeAddress(0).getLastColumn());
            assertEquals(1, validation.getRegions().getCellRangeAddress(0).getFirstRow());
        }
    }
}
