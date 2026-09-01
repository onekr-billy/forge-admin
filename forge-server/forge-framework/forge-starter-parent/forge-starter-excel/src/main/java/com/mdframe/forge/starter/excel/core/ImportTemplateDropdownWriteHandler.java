package com.mdframe.forge.starter.excel.core;

import com.alibaba.excel.write.handler.WorkbookWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import com.mdframe.forge.starter.excel.model.ImportTemplateColumn;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 为导入模板中的字典列写入隐藏选项表和 Excel 下拉校验。
 */
final class ImportTemplateDropdownWriteHandler implements WorkbookWriteHandler {

    static final String OPTIONS_SHEET_NAME = "字典选项";
    static final int DROPDOWN_LAST_ROW = 5000;

    private final List<ImportTemplateColumn> columns;

    ImportTemplateDropdownWriteHandler(List<ImportTemplateColumn> columns) {
        this.columns = columns == null ? List.of() : columns;
    }

    @Override
    public void afterWorkbookDispose(WriteWorkbookHolder writeWorkbookHolder) {
        List<DropdownColumn> dropdownColumns = collectDropdownColumns();
        if (dropdownColumns.isEmpty()) {
            return;
        }

        Workbook workbook = writeWorkbookHolder.getWorkbook();
        if (workbook == null || workbook.getNumberOfSheets() == 0) {
            return;
        }

        Sheet optionsSheet = workbook.getSheet(OPTIONS_SHEET_NAME);
        if (optionsSheet == null) {
            optionsSheet = workbook.createSheet(OPTIONS_SHEET_NAME);
        }

        for (DropdownColumn dropdownColumn : dropdownColumns) {
            writeOptions(optionsSheet, dropdownColumn.sourceColumnIndex(), dropdownColumn.options());
            addNamedRange(workbook, dropdownColumn);
            addValidation(workbook.getSheetAt(0), dropdownColumn);
        }

        int optionsSheetIndex = workbook.getSheetIndex(optionsSheet);
        if (optionsSheetIndex >= 0) {
            workbook.setSheetHidden(optionsSheetIndex, true);
        }
    }

    private List<DropdownColumn> collectDropdownColumns() {
        List<DropdownColumn> dropdownColumns = new ArrayList<>();
        int sourceColumnIndex = 0;
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            List<String> options = normalizeOptions(columns.get(columnIndex).dropdownOptions());
            if (options.isEmpty()) {
                continue;
            }
            dropdownColumns.add(new DropdownColumn(columnIndex, sourceColumnIndex++, options));
        }
        return dropdownColumns;
    }

    private List<String> normalizeOptions(List<String> rawOptions) {
        if (rawOptions == null || rawOptions.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> options = new LinkedHashSet<>();
        for (String option : rawOptions) {
            if (option == null) {
                continue;
            }
            String text = option.trim();
            if (!text.isEmpty()) {
                options.add(text);
            }
        }
        return List.copyOf(options);
    }

    private void writeOptions(Sheet optionsSheet, int sourceColumnIndex, List<String> options) {
        for (int rowIndex = 0; rowIndex < options.size(); rowIndex++) {
            Row row = optionsSheet.getRow(rowIndex);
            if (row == null) {
                row = optionsSheet.createRow(rowIndex);
            }
            row.createCell(sourceColumnIndex).setCellValue(options.get(rowIndex));
        }
    }

    private void addNamedRange(Workbook workbook, DropdownColumn dropdownColumn) {
        String rangeName = dropdownColumn.rangeName();
        Name existing = workbook.getName(rangeName);
        if (existing != null) {
            workbook.removeName(existing);
        }
        Name namedRange = workbook.createName();
        namedRange.setNameName(rangeName);
        namedRange.setRefersToFormula(dropdownColumn.formula());
    }

    private void addValidation(Sheet dataSheet, DropdownColumn dropdownColumn) {
        DataValidationHelper helper = dataSheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(dropdownColumn.rangeName());
        CellRangeAddressList addressList = new CellRangeAddressList(
                1,
                DROPDOWN_LAST_ROW,
                dropdownColumn.columnIndex(),
                dropdownColumn.columnIndex()
        );
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setEmptyCellAllowed(true);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("无效选项", "请从下拉列表中选择。");
        validation.setShowPromptBox(true);
        validation.createPromptBox("可选值", "请从下拉列表中选择字典选项。");
        if (validation instanceof XSSFDataValidation) {
            validation.setSuppressDropDownArrow(true);
        } else {
            validation.setSuppressDropDownArrow(false);
        }
        dataSheet.addValidationData(validation);
    }

    private record DropdownColumn(int columnIndex, int sourceColumnIndex, List<String> options) {
        String rangeName() {
            return "forge_dict_col_" + columnIndex;
        }

        String formula() {
            String columnLetter = toColumnLetter(sourceColumnIndex);
            return "'" + OPTIONS_SHEET_NAME + "'!$" + columnLetter + "$1:$" + columnLetter + "$" + options.size();
        }
    }

    static String toColumnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        int current = index + 1;
        while (current > 0) {
            int remainder = (current - 1) % 26;
            letters.insert(0, (char) ('A' + remainder));
            current = (current - 1) / 26;
        }
        return letters.toString();
    }
}