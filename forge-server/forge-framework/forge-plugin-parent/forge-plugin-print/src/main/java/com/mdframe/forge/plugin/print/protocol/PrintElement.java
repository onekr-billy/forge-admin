package com.mdframe.forge.plugin.print.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 可打印元素；常量保留 JSON 标量类型，不执行表达式。
 */
public record PrintElement(String id, String type, Double xMm, Double yMm, Double widthMm, Double heightMm, Binding binding, Format format, Style style, StaticTable table, String barcodeFormat, String pageNumberFormat, Double rotationDeg, Boolean flipX, Boolean flipY, Boolean locked) {

    public record Binding(String source, String path, JsonNode value) {
    }

    public record Format(String type, Integer scale, String emptyText, String trueText, String falseText, String datePattern) {
    }

    public record Style(String fontFamily, Double fontSizePt, Integer fontWeight, String fontStyle, String textAlign, Double lineHeight, String color, String backgroundColor, String borderColor, Double borderWidthMm, String borderStyle, Double borderRadiusMm, Double paddingMm, String textDecoration, String objectFit) {
    }

    public record StaticTable(java.util.List<StaticColumn> columns, java.util.List<StaticRow> rows, java.util.List<StaticCell> cells) {
    }

    public record StaticColumn(String id, Double widthMm) {
    }

    public record StaticRow(String id, Double heightMm) {
    }

    public record StaticCell(String id, Integer row, Integer column, Integer rowSpan, Integer colSpan, Binding binding, Format format, Style style) {
    }
}
