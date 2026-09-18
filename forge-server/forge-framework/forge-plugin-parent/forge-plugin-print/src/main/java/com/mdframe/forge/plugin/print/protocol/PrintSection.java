package com.mdframe.forge.plugin.print.protocol;

import java.util.List;
import com.mdframe.forge.plugin.print.protocol.PrintElement.Binding;
import com.mdframe.forge.plugin.print.protocol.PrintElement.Format;
import com.mdframe.forge.plugin.print.protocol.PrintElement.Style;

/**
 * 固定区块、流式文本与明细表格的明确协议模型。
 */
public record PrintSection(String id, String kind, Double heightMm, List<PrintElement> elements, Binding binding, Format format, Style style, Double gapAfterMm, Boolean keepWithNext, String collectionPath, List<Column> columns, List<HeaderRow> headerRows, Boolean repeatHeader, Footer footer, String emptyText) {

    public record Column(String id, String field, String title, Double widthMm, Format format, Style style) {
    }

    public record HeaderRow(List<HeaderCell> cells) {
    }

    public record HeaderCell(String text, Integer span, Style style) {
    }

    public record Footer(List<FooterCell> cells) {
    }

    public record FooterCell(Binding binding, Integer span, Format format, Style style) {
    }
}
