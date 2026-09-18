package com.mdframe.forge.starter.excel.core;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ZIP 兼容性回归；结构断言不代替目标 WPS 的视觉验收。 */
class ExcelWatermarkWriteHandlerTest {
    private static final int ROW_COUNT = 300;
    private static final int ZIP_LOCAL_SIGNATURE = 0x04034b50;
    private static final int ZIP_VERSION_20 = 20;
    private static final List<List<String>> HEAD = List.of(List.of("测试数据"));

    @Test
    void shouldUseAsNeededAndShareUnchangedBackgroundAcrossStreamingSheets() throws Exception {
        byte[] png = png(0xffa0a0a0);
        ExportResult result = export(handler("测试水印", png), false, null);

        assertInstanceOf(SXSSFWorkbook.class, result.workbook());
        assertEquals(Zip64Mode.AsNeeded, ReflectionTestUtils.getField(result.workbook(), "zip64Mode"));
        ByteBuffer header = ByteBuffer.wrap(result.bytes()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(ZIP_LOCAL_SIGNATURE, header.getInt());
        assertEquals(ZIP_VERSION_20, Short.toUnsignedInt(header.getShort()));
        assertReadableZip(result.bytes());

        try (XSSFWorkbook workbook = read(result)) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals(1, workbook.getAllPictures().size());
            PackagePart first = assertBackground(workbook.getSheetAt(0), png);
            PackagePart second = assertBackground(workbook.getSheetAt(1), png);
            assertEquals(first.getPartName(), second.getPartName());
            assertEquals(ROW_COUNT, workbook.getSheetAt(0).getLastRowNum());
            assertEquals("合成数据299", workbook.getSheetAt(0).getRow(ROW_COUNT).getCell(0).getStringCellValue());
            assertEquals(0, workbook.getSheetAt(1).getLastRowNum());
        }
    }

    @Test
    void shouldKeepBackgroundSupportForInMemoryWorkbook() throws Exception {
        byte[] png = png(0xffa0a0a0);
        ExportResult result = export(handler("测试水印", png), true, null);

        assertInstanceOf(XSSFWorkbook.class, result.workbook());
        try (XSSFWorkbook workbook = read(result)) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertBackground(workbook.getSheetAt(0), png);
            assertBackground(workbook.getSheetAt(1), png);
        }
    }

    @Test
    void shouldPreserveExistingDrawingPictureAndBackgroundRelationships() throws Exception {
        byte[] background = png(0xffa0a0a0);
        byte[] picture = png(0xff306090);
        ExportResult result = export(handler("测试水印", background), false, picture);

        assertEquals(Zip64Mode.AsNeeded, ReflectionTestUtils.getField(result.workbook(), "zip64Mode"));
        assertReadableZip(result.bytes());
        try (XSSFWorkbook workbook = read(result)) {
            assertEquals(2, workbook.getAllPictures().size());
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertBackground(sheet, background);
            assertBackground(workbook.getSheetAt(1), background);
            assertNotNull(sheet.getDrawingPatriarch());
            assertEquals(1, sheet.getDrawingPatriarch().getShapes().size());
            XSSFPicture drawingPicture = assertInstanceOf(XSSFPicture.class,
                    sheet.getDrawingPatriarch().getShapes().get(0));
            assertArrayEquals(picture, drawingPicture.getPictureData().getData());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \n "})
    void shouldNotChangeZipModeOrAddImagesForBlankWatermark(String text) throws Exception {
        ExportResult result = export(handler(text, png(0xffa0a0a0)), false, null);
        assertNoWatermarkAndDefaultZipMode(result);
    }

    @Test
    void shouldLeaveExportWithoutWatermarkHandlerUnchanged() throws Exception {
        assertNoWatermarkAndDefaultZipMode(export(null, false, null));
    }

    private static void assertNoWatermarkAndDefaultZipMode(ExportResult result) throws Exception {
        assertEquals(Zip64Mode.Always, ReflectionTestUtils.getField(result.workbook(), "zip64Mode"));
        try (XSSFWorkbook workbook = read(result)) {
            assertTrue(workbook.getAllPictures().isEmpty());
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                assertFalse(workbook.getSheetAt(index).getCTWorksheet().isSetPicture());
            }
        }
    }

    private static ExcelWatermarkWriteHandler handler(String text, byte[] png) {
        ExcelWatermarkWriteHandler handler = new ExcelWatermarkWriteHandler(
                text, 24, "#a0a0a0", 1.0f, -20, 100, 80);
        // 固定图片以隔离 ZIP 改动，不触发生产图片生成器的调试落盘。
        ReflectionTestUtils.setField(handler, "watermarkImageBytes", png);
        return handler;
    }

    private static ExportResult export(ExcelWatermarkWriteHandler watermark, boolean inMemory,
                                       byte[] picture) throws Exception {
        AtomicReference<Workbook> workbookRef = new AtomicReference<>();
        SheetWriteHandler observer = new SheetWriteHandler() {
            @Override
            public int order() {
                return Integer.MIN_VALUE;
            }

            @Override
            public void afterSheetCreate(WriteWorkbookHolder holder, WriteSheetHolder sheetHolder) {
                Workbook workbook = holder.getWorkbook();
                workbookRef.set(workbook);
                if (picture != null && workbook.getSheetIndex(sheetHolder.getSheet()) == 0) {
                    int index = workbook.addPicture(picture, Workbook.PICTURE_TYPE_PNG);
                    ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
                    anchor.setCol1(1);
                    anchor.setCol2(2);
                    anchor.setRow1(1);
                    anchor.setRow2(2);
                    sheetHolder.getSheet().createDrawingPatriarch().createPicture(anchor, index);
                }
            }
        };
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < ROW_COUNT; row++) {
            rows.add(List.of("合成数据" + row));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var builder = EasyExcel.write(output).head(HEAD).inMemory(inMemory).registerWriteHandler(observer);
        if (watermark != null) {
            builder.registerWriteHandler(watermark);
        }
        try (ExcelWriter writer = builder.build()) {
            writer.write(rows, EasyExcel.writerSheet(0, "测试数据").build());
            writer.write(List.of(), EasyExcel.writerSheet(1, "空表").build());
        }
        return new ExportResult(output.toByteArray(), workbookRef.get());
    }

    private static PackagePart assertBackground(XSSFSheet sheet, byte[] expected) throws Exception {
        assertTrue(sheet.getCTWorksheet().isSetPicture());
        String id = sheet.getCTWorksheet().getPicture().getId();
        assertNotNull(id);
        PackageRelationship relationship = sheet.getPackagePart().getRelationship(id);
        assertNotNull(relationship);
        assertEquals(XSSFRelation.IMAGES.getRelation(), relationship.getRelationshipType());
        PackagePart part = sheet.getPackagePart().getRelatedPart(relationship);
        try (var input = part.getInputStream()) {
            assertArrayEquals(expected, input.readAllBytes());
        }
        return part;
    }

    private static void assertReadableZip(byte[] bytes) throws Exception {
        int count = 0;
        // 标准 ZIP 流读取器需完整读到 EOF，包含所有图片/XML 成员及 CRC 校验。
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                assertFalse(entry.getName().isBlank());
                input.readAllBytes();
                input.closeEntry();
                count++;
            }
        }
        assertTrue(count >= 13);
    }

    private static XSSFWorkbook read(ExportResult result) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(result.bytes()));
    }

    private static byte[] png(int color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private record ExportResult(byte[] bytes, Workbook workbook) {
    }
}
