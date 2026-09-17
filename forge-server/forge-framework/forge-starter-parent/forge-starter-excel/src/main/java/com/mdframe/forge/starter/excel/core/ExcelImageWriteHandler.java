package com.mdframe.forge.starter.excel.core;

import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.mdframe.forge.starter.excel.model.ExcelColumnConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 图片列导出处理器。
 * <p>
 * 检测 columnType=IMAGE 的列，将逗号分隔的 fileId 列表渲染为嵌入图片。
 * 多张图片在同一单元格内垂直排列。
 */
@Slf4j
public class ExcelImageWriteHandler implements CellWriteHandler {

    private final List<ExcelColumnConfig> columnConfigs;
    private final Object fileManager;
    private final Map<Integer, ExcelColumnConfig> imageColumnIndexMap = new HashMap<>();

    public ExcelImageWriteHandler(List<ExcelColumnConfig> columnConfigs, Object fileManager) {
        this.columnConfigs = columnConfigs;
        this.fileManager = fileManager;
        for (int i = 0; i < columnConfigs.size(); i++) {
            ExcelColumnConfig config = columnConfigs.get(i);
            if ("IMAGE".equalsIgnoreCase(config.getColumnType())) {
                imageColumnIndexMap.put(i, config);
            }
        }
    }

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        if (context.getHead()) {
            return;
        }
        int columnIndex = context.getColumnIndex();
        ExcelColumnConfig imageConfig = imageColumnIndexMap.get(columnIndex);
        if (imageConfig == null) {
            return;
        }

        Cell cell = context.getCell();
        String cellValue = getCellStringValue(cell);
        if (cellValue == null || cellValue.isBlank()) {
            return;
        }

        String[] fileIds = cellValue.split(",");
        int maxWidth = imageConfig.getImageWidth() != null ? imageConfig.getImageWidth() : 120;
        int maxHeight = imageConfig.getImageHeight() != null ? imageConfig.getImageHeight() : 90;
        int maxCount = imageConfig.getImageMaxCount() != null ? imageConfig.getImageMaxCount() : 5;

        Sheet sheet = cell.getSheet();
        Workbook workbook = sheet.getWorkbook();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        int rowCount = 0;
        for (String fileId : fileIds) {
            if (rowCount >= maxCount) {
                break;
            }
            String trimmedId = fileId.trim();
            if (trimmedId.isEmpty()) {
                continue;
            }

            byte[] imageBytes = downloadFileBytes(trimmedId);
            if (imageBytes == null || imageBytes.length == 0) {
                continue;
            }

            try {
                int pictureIdx = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_PNG);

                // 计算锚点位置（EMU）
                int col1 = cell.getColumnIndex();
                int row1 = cell.getRowIndex() + rowCount;
                int emuW = maxWidth * 9525;
                int emuH = maxHeight * 9525;

                XSSFClientAnchor anchor = new XSSFClientAnchor(
                        0, 0, emuW, emuH,
                        col1, row1, col1 + 1, row1 + 1);

                if (drawing instanceof XSSFDrawing xssfDrawing) {
                    xssfDrawing.createPicture(anchor, pictureIdx);
                }

                // 设置行高以容纳图片
                sheet.getRow(row1).setHeightInPoints((float) (maxHeight * 0.75));

                rowCount++;
            } catch (Exception e) {
                log.warn("嵌入图片失败: fileId={}, error={}", trimmedId, e.getMessage());
            }
        }

        // 清空单元格文本（图片已替代文本展示）
        cell.setBlank();
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    /**
     * 通过反射调用 FileManager.getFileBytes(fileId) 获取文件字节。
     */
    private byte[] downloadFileBytes(String fileId) {
        if (fileManager == null) {
            return null;
        }
        try {
            Method getFileBytes = fileManager.getClass().getMethod("getFileBytes", String.class);
            return (byte[]) getFileBytes.invoke(fileManager, fileId);
        } catch (Exception e) {
            log.warn("下载文件失败: fileId={}, error={}", fileId, e.getMessage());
            return null;
        }
    }
}
