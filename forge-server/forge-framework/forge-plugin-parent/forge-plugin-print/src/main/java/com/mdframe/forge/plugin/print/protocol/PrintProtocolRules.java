package com.mdframe.forge.plugin.print.protocol;

import static com.mdframe.forge.plugin.print.protocol.PrintProtocolLimits.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 每次校验独立实例，ID 与元素计数不跨请求共享。
 */
final class PrintProtocolRules {

    final List<PrintProtocolValidator.Issue> issues = new ArrayList<>();

    private final Set<String> ids = new HashSet<>();

    private int elements;

    private final PrintValueRules values = new PrintValueRules(this);

    private final PrintTableRules tables = new PrintTableRules(this, values);

    void issue(String path, String code, String message) {
        if (issues.size() < MAX_ISSUES) {
            issues.add(new PrintProtocolValidator.Issue(path, code, message));
        }
    }

    boolean object(JsonNode value, String path, String... keys) {
        if (value == null || !value.isObject()) {
            issue(path, "INVALID_OBJECT", "必须是 JSON 对象");
            return false;
        }
        Set<String> allowed = Set.of(keys);
        value.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) {
                issue(child(path, key), "UNKNOWN_PROPERTY", "不支持此属性");
            }
        });
        return true;
    }

    boolean array(JsonNode value, String path, int max) {
        if (value == null || !value.isArray() || value.size() > max) {
            issue(path, "INVALID_ARRAY", "数组项数超过限制或类型不正确");
            return false;
        }
        return true;
    }

    boolean number(JsonNode value, String path, double min, double max) {
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue()) || value.doubleValue() < min || value.doubleValue() > max) {
            issue(path, "INVALID_NUMBER", "数字类型或范围不正确");
            return false;
        }
        return true;
    }

    void integer(JsonNode value, String path, int min, int max) {
        if (number(value, path, min, max) && value.decimalValue().stripTrailingZeros().scale() > 0) {
            issue(path, "INVALID_NUMBER", "必须为整数");
        }
    }

    void choice(JsonNode value, String path, String... options) {
        if (value == null || !value.isTextual() || !Set.of(options).contains(value.textValue())) {
            issue(path, "UNSUPPORTED_VALUE", "不支持此配置值");
        }
    }

    void bool(JsonNode value, String path) {
        if (value == null || !value.isBoolean()) {
            issue(path, "UNSUPPORTED_VALUE", "必须是布尔值");
        }
    }

    void text(JsonNode value, String path, int max) {
        if (value == null || !value.isTextual() || value.textValue().length() > max) {
            issue(path, "INVALID_TEXT", "文本类型或长度不正确");
        }
    }

    void identifier(JsonNode value, String path) {
        if (value == null || !value.isTextual() || !value.textValue().matches("[A-Za-z0-9_-]{1,80}")) {
            issue(path, "INVALID_ID", "标识无效");
        } else if (!ids.add(value.textValue())) {
            issue(path, "DUPLICATE_ID", "标识重复");
        }
    }

    static String child(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    static double n(JsonNode value, String key) {
        return value.path(key).asDouble(Double.NaN);
    }

    void document(JsonNode doc) {
        if (!object(doc, "", "protocol", "schemaVersion", "paper", "header", "body", "footer", "resources")) {
            return;
        }
        choice(doc.get("protocol"), "protocol", "forge-print");
        JsonNode version = doc.get("schemaVersion");
        if (version == null || !version.isNumber() || version.decimalValue().compareTo(java.math.BigDecimal.valueOf(SCHEMA_VERSION)) != 0) {
            issue("schemaVersion", "UNSUPPORTED_VALUE", "仅支持协议版本 1");
        }
        JsonNode paper = doc.get("paper");
        if (!object(paper, "paper", "widthMm", "heightMm", "orientation", "marginMm")) {
            return;
        }
        number(paper.get("widthMm"), "paper.widthMm", 10, PAPER_SIZE_MM);
        number(paper.get("heightMm"), "paper.heightMm", 10, PAPER_SIZE_MM);
        choice(paper.get("orientation"), "paper.orientation", "PORTRAIT", "LANDSCAPE");
        JsonNode margins = paper.get("marginMm");
        if (!object(margins, "paper.marginMm", "top", "right", "bottom", "left")) {
            return;
        }
        for (String key : List.of("top", "right", "bottom", "left")) {
            number(margins.get(key), "paper.marginMm." + key, 0, PAPER_SIZE_MM);
        }
        boolean landscape = "LANDSCAPE".equals(paper.path("orientation").asText());
        double shortSide = Math.min(n(paper, "widthMm"), n(paper, "heightMm"));
        double longSide = Math.max(n(paper, "widthMm"), n(paper, "heightMm"));
        double width = (landscape ? longSide : shortSide) - n(margins, "left") - n(margins, "right");
        double height = (landscape ? shortSide : longSide) - n(margins, "top") - n(margins, "bottom");
        band(doc.get("header"), "header", width);
        band(doc.get("footer"), "footer", width);
        if (!(width > 0 && height - doc.path("header").path("heightMm").asDouble(Double.NaN) - doc.path("footer").path("heightMm").asDouble(Double.NaN) > 0)) {
            issue("paper", "NO_PRINTABLE_AREA", "页边距、页眉和页脚未留下正文区域");
        }
        if (array(doc.get("body"), "body", SECTIONS)) {
            for (int i = 0; i < doc.get("body").size(); i++) {
                section(doc.get("body").get(i), "body[" + i + "]", width);
            }
        }
        if (elements > ELEMENTS) {
            issue("body", "TOO_MANY_ELEMENTS", "元素总数超过 1000");
        }
        if (array(doc.get("resources"), "resources", RESOURCES)) {
            for (int i = 0; i < doc.get("resources").size(); i++) {
                JsonNode resource = doc.get("resources").get(i);
                String path = "resources[" + i + "]";
                if (!object(resource, path, "id", "fileId")) {
                    continue;
                }
                identifier(resource.get("id"), path + ".id");
                if (!PrintValueRules.fileId(resource.get("fileId"))) {
                    issue(path, "INVALID_RESOURCE", "资源必须使用文件标识");
                }
            }
        }
    }

    private void band(JsonNode band, String path, double width) {
        if (!object(band, path, "heightMm", "repeat", "elements")) {
            return;
        }
        number(band.get("heightMm"), path + ".heightMm", 0, PAPER_SIZE_MM);
        bool(band.get("repeat"), path + ".repeat");
        elements(band.get("elements"), path + ".elements", width, n(band, "heightMm"));
    }

    private void elements(JsonNode list, String path, double width, double height) {
        if (!array(list, path, ELEMENTS)) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            element(list.get(i), path + "[" + i + "]", width, height);
        }
    }

    private void element(JsonNode e, String path, double width, double height) {
        if (!object(e, path, "id", "type", "xMm", "yMm", "widthMm", "heightMm", "binding", "format", "style", "barcodeFormat", "pageNumberFormat")) {
            return;
        }
        elements++;
        identifier(e.get("id"), path + ".id");
        choice(e.get("type"), path + ".type", "TEXT", "IMAGE", "LINE", "RECTANGLE", "ELLIPSE", "BARCODE", "QRCODE", "PAGE_NUMBER");
        for (String key : List.of("xMm", "yMm", "widthMm", "heightMm")) {
            number(e.get(key), path + "." + key, (key.equals("widthMm") || key.equals("heightMm")) ? .1 : 0, PAPER_SIZE_MM);
        }
        if (n(e, "xMm") + n(e, "widthMm") > width + GEOMETRY_TOLERANCE_MM || n(e, "yMm") + n(e, "heightMm") > height + GEOMETRY_TOLERANCE_MM) {
            issue(path, "OUT_OF_BOUNDS", "元素超出所属区块");
        }
        String type = e.path("type").asText();
        if (e.has("binding") || Set.of("TEXT", "IMAGE", "BARCODE", "QRCODE").contains(type)) {
            values.binding(e.get("binding"), path + ".binding", type.equals("IMAGE"), type.equals("TEXT"));
        }
        if (e.has("barcodeFormat")) {
            choice(e.get("barcodeFormat"), path + ".barcodeFormat", "CODE128", "CODE39", "EAN13", "EAN8", "ITF14");
        }
        if (e.has("pageNumberFormat")) {
            choice(e.get("pageNumberFormat"), path + ".pageNumberFormat", "CURRENT", "CURRENT_TOTAL");
        }
        values.style(e.get("style"), path + ".style");
        values.format(e.get("format"), path + ".format");
    }

    private void section(JsonNode s, String path, double width) {
        if (!object(s, path, "id", "kind", "heightMm", "elements", "binding", "format", "style", "gapAfterMm", "keepWithNext", "collectionPath", "columns", "headerRows", "repeatHeader", "footer", "emptyText")) {
            return;
        }
        identifier(s.get("id"), path + ".id");
        choice(s.get("kind"), path + ".kind", "FIXED", "TEXT", "TABLE");
        if (s.has("gapAfterMm")) {
            number(s.get("gapAfterMm"), path + ".gapAfterMm", 0, 100);
        }
        if (s.has("keepWithNext")) {
            bool(s.get("keepWithNext"), path + ".keepWithNext");
        }
        values.style(s.get("style"), path + ".style");
        values.format(s.get("format"), path + ".format");
        String kind = s.path("kind").asText();
        if (kind.equals("FIXED") || s.has("heightMm")) {
            number(s.get("heightMm"), path + ".heightMm", .1, PAPER_SIZE_MM);
        }
        if (kind.equals("FIXED") || s.has("elements")) {
            elements(s.get("elements"), path + ".elements", width, n(s, "heightMm"));
        }
        if (kind.equals("TEXT") || s.has("binding")) {
            values.binding(s.get("binding"), path + ".binding", false, false);
        }
        if (kind.equals("TABLE") || List.of("columns", "collectionPath", "headerRows", "repeatHeader", "footer", "emptyText").stream().anyMatch(s::has)) {
            tables.table(s, path, width);
        }
    }
}
