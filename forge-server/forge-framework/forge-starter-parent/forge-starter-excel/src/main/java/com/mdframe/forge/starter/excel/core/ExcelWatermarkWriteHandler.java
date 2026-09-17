package com.mdframe.forge.starter.excel.core;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;

/**
 * Excel 导出文字水印处理器（工作表背景图方案）。
 * <p>
 * 通过 worksheet 的 {@code <picture>} 元素将水印图设为工作表背景：
 * Excel 原生将背景图平铺到整表、天然位于单元格数据下层、无需锚点像素换算。
 * 背景图显示还受查看器与 ZIP 写出方式的兼容性影响。
 * <p>
 * 注意：背景图在部分查看器中显示、打印时不输出，满足屏幕水印场景。
 */
@Slf4j
public class ExcelWatermarkWriteHandler implements SheetWriteHandler {

    /** 背景图单元尺寸：图片即平铺单元，尺寸越大水印越稀疏 */
    private static final int IMG_WIDTH = 500;
    private static final int IMG_HEIGHT = 400;

    /** 背景图底色：近白浅灰，沿用已验证样本的 RGB 图片设置 */
    private static final Color NEAR_WHITE_BG = new Color(245, 245, 245);

    /** 文字等效亮度上限：与实测可见值一致（160 灰 @ 245 底） */
    private static final int MAX_TEXT_BRIGHTNESS = 160;

    private final String watermarkText;
    private final int fontSize;
    private final Color fontColor;
    private final float opacity;
    private final int rotate;
    private final int gapX;
    private final int gapY;

    private byte[] watermarkImageBytes;
    /** 多 Sheet 共享同一份图片数据，只 addPicture 一次 */
    private int pictureIdx = -1;

    public ExcelWatermarkWriteHandler(String watermarkText, int fontSize, String fontColorHex,
                                      float opacity, int rotate, int gapX, int gapY) {
        this.watermarkText = watermarkText;
        this.fontSize = fontSize;
        this.fontColor = adjustColorForExcel(parseColor(fontColorHex));
        this.opacity = opacity;
        this.rotate = rotate != 0 ? rotate : -20;
        this.gapX = Math.max(gapX, 100);
        this.gapY = Math.max(gapY, 80);
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        try {
            if (watermarkText == null || watermarkText.isBlank()) {
                return;
            }
            byte[] imageBytes = getOrGenerateWatermarkImage();
            Workbook workbook = writeWorkbookHolder.getWorkbook();
            if (workbook instanceof SXSSFWorkbook sxssfWorkbook) {
                // 普通文件避免强制 ZIP64 写出，兼容已验证的 WPS 背景水印读取方式。
                sxssfWorkbook.setZip64Mode(Zip64Mode.AsNeeded);
            }

            // 图片只注册一次，各 Sheet 通过 relationship 共享同一份 media
            if (pictureIdx < 0) {
                pictureIdx = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_PNG);
            }
            XSSFPictureData pictureData = (XSSFPictureData) workbook.getAllPictures().get(pictureIdx);
            PackagePartName ppn = pictureData.getPackagePart().getPartName();
            String relType = XSSFRelation.IMAGES.getRelation();

            XSSFSheet xssfSheet = resolveXssfSheet(workbook, writeSheetHolder);
            if (xssfSheet == null) {
                log.warn("无法定位底层XSSFSheet，跳过水印: sheet={}", writeSheetHolder.getSheet().getSheetName());
                return;
            }
            PackageRelationship pr = xssfSheet.getPackagePart()
                    .addRelationship(ppn, TargetMode.INTERNAL, relType, null);
            // worksheet 根元素追加 <picture r:id="..."/>，Excel 识别为工作表背景图
            xssfSheet.getCTWorksheet().addNewPicture().setId(pr.getId());
            log.info("Excel水印背景图已设置: sheet={}", writeSheetHolder.getSheet().getSheetName());
        } catch (Exception e) {
            log.warn("Excel水印设置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取 Sheet 对应的底层 XSSFSheet。
     * SXSSF 流式模式需反射 {@code _sh} 字段；XSSF 直接可用。
     */
    private XSSFSheet resolveXssfSheet(Workbook workbook, WriteSheetHolder writeSheetHolder) {
        if (workbook instanceof XSSFWorkbook) {
            return (XSSFSheet) writeSheetHolder.getSheet();
        }
        if (workbook instanceof SXSSFWorkbook && writeSheetHolder.getSheet() instanceof SXSSFSheet sxssfSheet) {
            try {
                Field field = SXSSFSheet.class.getDeclaredField("_sh");
                field.setAccessible(true);
                return (XSSFSheet) field.get(sxssfSheet);
            } catch (Exception e) {
                log.warn("反射获取SXSSFSheet底层_sh失败: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    private byte[] getOrGenerateWatermarkImage() {
        if (watermarkImageBytes != null) {
            return watermarkImageBytes;
        }
        watermarkImageBytes = generateWatermarkImage(watermarkText, fontSize, fontColor, opacity, rotate, gapX, gapY);
        return watermarkImageBytes;
    }

    /**
     * 生成水印背景图单元：不透明浅灰底 + 斜排灰色多行文字。
     * <p>
     * 当前保留无 alpha 通道的 RGB 图片，用近白浅灰底与混合灰字模拟半透明感。
     * 背景图位于单元格数据下层；本次兼容性修复只调整 ZIP 写出模式，不改变绘图参数。
     */
    static byte[] generateWatermarkImage(String text, int fontSize, Color color,
                                         float opacity, int rotateDeg, int gapX, int gapY) {
        int effectiveFontSize = Math.max(fontSize, 24);
        Font font = createFont(effectiveFontSize);
        String[] lines = text.split("\n");

        int width = Math.max(IMG_WIDTH, textWidth(font, lines) + gapX);
        int height = Math.max(IMG_HEIGHT, lines.length * (effectiveFontSize + 8) + gapY);

        // 无 alpha 通道的不透明图片
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(NEAR_WHITE_BG);
        g.fillRect(0, 0, width, height);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics(font);

        // 文字颜色 = 配置色与底色按 opacity 混合；亮度强制 ≤ 160（实测可见值），保持色相等比调暗
        float blend = Math.min(1.0f, Math.max(0.0f, opacity));
        int r = (int) (color.getRed() * blend + NEAR_WHITE_BG.getRed() * (1 - blend));
        int gg = (int) (color.getGreen() * blend + NEAR_WHITE_BG.getGreen() * (1 - blend));
        int b = (int) (color.getBlue() * blend + NEAR_WHITE_BG.getBlue() * (1 - blend));
        int brightness = (r + gg + b) / 3;
        if (brightness > MAX_TEXT_BRIGHTNESS && brightness > 0) {
            double scale = (double) MAX_TEXT_BRIGHTNESS / brightness;
            r = (int) (r * scale);
            gg = (int) (gg * scale);
            b = (int) (b * scale);
        }
        g.setColor(new Color(r, gg, b));

        int x = width / 2;
        int y = height / 2 - (lines.length - 1) * effectiveFontSize / 2;
        g.rotate(Math.toRadians(rotateDeg), x, y);
        for (String line : lines) {
            int textWidth = fm.stringWidth(line);
            g.drawString(line, x - textWidth / 2, y);
            y += effectiveFontSize;
        }
        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            // 调试：落盘一份，可直接打开确认图片内容
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get("/tmp/forge-watermark-debug.png"), bytes);
                log.info("水印PNG已落盘: /tmp/forge-watermark-debug.png, {}字节, {}x{}px, 底色RGB({},{},{}), 文字RGB({},{},{})",
                        bytes.length, width, height,
                        NEAR_WHITE_BG.getRed(), NEAR_WHITE_BG.getGreen(), NEAR_WHITE_BG.getBlue(), r, gg, b);
            } catch (Exception ignored) {
            }
            return bytes;
        } catch (Exception e) {
            log.error("水印图片生成失败", e);
            return new byte[0];
        }
    }

    private static int textWidth(Font font, String[] lines) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        FontMetrics fm = probe.getGraphics().getFontMetrics(font);
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, fm.stringWidth(line));
        }
        return max;
    }

    /**
     * 如果颜色太亮（接近白色），在白色背景上不可见，自动调暗。
     */
    private static Color adjustColorForExcel(Color color) {
        double brightness = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
        if (brightness > 200) {
            return new Color(180, 180, 180);
        }
        return color;
    }

    /**
     * 按候选顺序选择可用字体，避免服务器缺少中文字体时渲染空白。
     */
    private static Font createFont(int fontSize) {
        for (String name : new String[]{"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB"}) {
            Font font = new Font(name, Font.PLAIN, fontSize);
            if (!"Dialog".equals(font.getFamily())) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
    }

    private static Color parseColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return Color.GRAY;
        }
        try {
            hex = hex.startsWith("#") ? hex.substring(1) : hex;
            if (hex.length() == 6) {
                return new Color(
                        Integer.parseInt(hex.substring(0, 2), 16),
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16));
            }
        } catch (Exception ignored) {
        }
        return Color.GRAY;
    }
}
