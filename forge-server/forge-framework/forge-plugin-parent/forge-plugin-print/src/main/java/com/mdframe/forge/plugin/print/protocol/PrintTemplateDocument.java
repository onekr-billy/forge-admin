package com.mdframe.forge.plugin.print.protocol;

import java.util.List;

/**
 * forge-print v1；仅由白名单验证后的 JSON 构造，不承担读取业务数据。
 */
public record PrintTemplateDocument(String protocol, Integer schemaVersion, Paper paper, Band header, List<PrintSection> body, Band footer, List<Resource> resources) {

    public record Paper(Double widthMm, Double heightMm, String orientation, Margins marginMm) {
    }

    public record Margins(Double top, Double right, Double bottom, Double left) {
    }

    public record Band(Double heightMm, Boolean repeat, List<PrintElement> elements) {
    }

    public record Resource(String id, String fileId) {
    }
}
