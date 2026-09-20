package com.mdframe.forge.plugin.generator.service.audit;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 子表不记字段级前后值，只汇总新增/修改/删除行数，删除时保留行主键。
 */
public final class DataAuditChildRowSummarizer {

    public static final String FIELD_CODE = "__childRows";
    public static final String FIELD_TYPE = "CHILD_SUMMARY";

    private DataAuditChildRowSummarizer() {
    }

    public static Summary summarize(String relationKey,
                                    Map<String, Map<String, Object>> beforeById,
                                    Map<String, Map<String, Object>> afterById) {
        Map<String, Map<String, Object>> before = beforeById == null ? Map.of() : beforeById;
        Map<String, Map<String, Object>> after = afterById == null ? Map.of() : afterById;
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(before.keySet());
        ids.addAll(after.keySet());
        int added = 0;
        int updated = 0;
        List<String> deletedIds = new ArrayList<>();
        for (String id : ids) {
            boolean hasBefore = before.containsKey(id) && before.get(id) != null;
            boolean hasAfter = after.containsKey(id) && after.get(id) != null;
            if (!hasBefore && hasAfter) {
                added++;
                continue;
            }
            if (hasBefore && !hasAfter) {
                deletedIds.add(id);
                continue;
            }
            if (hasBefore && hasAfter && !sameRow(before.get(id), after.get(id))) {
                updated++;
            }
        }
        return new Summary(relationKey == null ? "" : relationKey, added, updated, deletedIds.size(), deletedIds);
    }

    public static String display(Summary summary) {
        if (summary == null || !summary.hasChange()) {
            return "子表无变化";
        }
        List<String> parts = new ArrayList<>();
        if (summary.added() > 0) {
            parts.add("新增 " + summary.added() + " 行");
        }
        if (summary.updated() > 0) {
            parts.add("修改 " + summary.updated() + " 行");
        }
        if (summary.deleted() > 0) {
            String ids = String.join("、", summary.deletedIds());
            parts.add("删除 " + summary.deleted() + " 行（" + ids + "）");
        }
        return String.join("，", parts);
    }

    private static boolean sameRow(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> a = normalize(left);
        Map<String, Object> b = normalize(right);
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : a.entrySet()) {
            if (!Objects.equals(stringify(entry.getValue()), stringify(b.get(entry.getKey())))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (row == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            if (key.isEmpty() || isSystem(key)) {
                continue;
            }
            result.put(key.toLowerCase(), entry.getValue());
        }
        return result;
    }

    private static boolean isSystem(String key) {
        String column = key.trim().toLowerCase();
        return "id".equals(column)
                || "tenant_id".equals(column) || "tenantid".equals(column)
                || "create_by".equals(column) || "createby".equals(column)
                || "create_time".equals(column) || "createtime".equals(column)
                || "create_dept".equals(column) || "createdept".equals(column)
                || "update_by".equals(column) || "updateby".equals(column)
                || "update_time".equals(column) || "updatetime".equals(column)
                || "del_flag".equals(column) || "delflag".equals(column);
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record Summary(String relationKey, int added, int updated, int deleted, List<String> deletedIds) {
        public boolean hasChange() {
            return added > 0 || updated > 0 || deleted > 0;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("added", added);
            map.put("updated", updated);
            map.put("deleted", deleted);
            map.put("deletedIds", deletedIds);
            if (StringUtils.isNotBlank(relationKey)) {
                map.put("relationKey", relationKey);
            }
            return map;
        }
    }
}
