package com.mdframe.forge.plugin.generator.service.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DataAuditChildRowSummarizer")
class DataAuditChildRowSummarizerTest {

    @Test
    @DisplayName("子表只汇总行数并保留删除行主键")
    void summarizesRowCountsAndDeletedIds() {
        Map<String, Map<String, Object>> before = new LinkedHashMap<>();
        before.put("1", Map.of("id", 1, "qty", 10));
        before.put("2", Map.of("id", 2, "qty", 5));
        before.put("3", Map.of("id", 3, "qty", 8));
        Map<String, Map<String, Object>> after = new LinkedHashMap<>();
        after.put("1", Map.of("id", 1, "qty", 12));
        after.put("4", Map.of("id", 4, "qty", 1));

        DataAuditChildRowSummarizer.Summary summary = DataAuditChildRowSummarizer.summarize("items", before, after);

        assertEquals(1, summary.added());
        assertEquals(1, summary.updated());
        assertEquals(2, summary.deleted());
        assertEquals("2", summary.deletedIds().get(0));
        assertEquals("3", summary.deletedIds().get(1));
        assertTrue(DataAuditChildRowSummarizer.display(summary).contains("删除 2 行（2、3）"));
    }
}
