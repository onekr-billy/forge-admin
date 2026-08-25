package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationExcelPreviewVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BusinessApplicationExcelImportService")
class BusinessApplicationExcelImportServiceTest {

    @Test
    @DisplayName("preview rejects empty and non-Excel files")
    void previewRejectsInvalidFiles() {
        BusinessApplicationExcelImportService service = previewService();

        assertThrows(BusinessException.class, () -> service.preview(
                new MockMultipartFile("file", "empty.xlsx", "application/octet-stream", new byte[0])));
        BusinessException nonExcel = assertThrows(BusinessException.class, () -> service.preview(
                new MockMultipartFile("file", "data.csv", "text/csv", "name".getBytes())));

        assertTrue(nonExcel.getMessage().contains("仅支持"));
    }

    @Test
    @DisplayName("preview reads only the first sheet and infers stable unique fields")
    void previewInfersFirstSheetFields() throws Exception {
        BusinessApplicationExcelImportService service = previewService();
        MockMultipartFile workbook = workbook(List.of(
                List.of("分类", "分类", "发生日期", "金额", "是否启用"),
                List.of("A", "甲", "2026-08-01", "12.50", "是"),
                List.of("B", "乙", "2026-08-02", "30.00", "否"),
                List.of("A", "甲", "2026-08-03", "8.25", "是")));

        BusinessApplicationExcelPreviewVO preview = service.preview(workbook);

        assertEquals("客户台账", preview.getSheetName());
        assertEquals(3, preview.getSampledRowCount());
        assertEquals(5, preview.getFields().size());
        assertNotEquals(preview.getFields().get(0).getFieldCode(), preview.getFields().get(1).getFieldCode());
        assertEquals("SELECT", preview.getFields().get(0).getFieldType());
        assertEquals("DATE", preview.getFields().get(2).getFieldType());
        assertEquals("NUMBER", preview.getFields().get(3).getFieldType());
        assertEquals("SWITCH", preview.getFields().get(4).getFieldType());
    }

    @Test
    @DisplayName("confirmed duplicate field codes are rejected before object creation")
    void initializeRejectsDuplicateConfirmedFields() throws Exception {
        BusinessApplicationService applicationService = mock(BusinessApplicationService.class);
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        AiBusinessApplication application = new AiBusinessApplication();
        application.setId(10L);
        application.setApplicationCode("crm_app");
        application.setApplicationName("客户应用");
        application.setSuiteCode("crm");
        when(applicationService.requireEntity(10L)).thenReturn(application);
        when(applicationObjectService.list(10L)).thenReturn(List.of());
        BusinessApplicationExcelImportService service = new BusinessApplicationExcelImportService(
                new ObjectMapper(), applicationService, applicationObjectService,
                mock(BusinessObjectCreateService.class), mock(BusinessObjectDesignerService.class),
                new BusinessNamingService());
        String fields = "["
                + "{\"headerName\":\"客户名称\",\"fieldCode\":\"sameCode\",\"fieldType\":\"TEXT\"},"
                + "{\"headerName\":\"联系电话\",\"fieldCode\":\"sameCode\",\"fieldType\":\"TEXT\"}"
                + "]";

        BusinessException error = assertThrows(BusinessException.class, () -> service.initialize(
                10L, workbook(List.of(List.of("客户名称", "联系电话"), List.of("甲", "13800000000"))),
                null, null, fields));

        assertTrue(error.getMessage().contains("不能重复"));
    }

    private BusinessApplicationExcelImportService previewService() {
        return new BusinessApplicationExcelImportService(
                new ObjectMapper(), null, null, null, null, new BusinessNamingService());
    }

    private MockMultipartFile workbook(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("客户台账");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex);
                List<String> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values.get(columnIndex));
                }
            }
            var ignoredSheet = workbook.createSheet("不应读取");
            ignoredSheet.createRow(0).createCell(0).setCellValue("忽略字段");
            workbook.write(output);
            return new MockMultipartFile("file", "customers.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
