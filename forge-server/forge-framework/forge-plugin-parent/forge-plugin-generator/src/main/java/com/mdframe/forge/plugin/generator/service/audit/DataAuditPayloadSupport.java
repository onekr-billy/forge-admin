package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.dto.audit.DataAuditWriteContextDTO;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 从动态请求体剥离保留元数据节点，固定字段立即转为 DTO。
 */
public final class DataAuditPayloadSupport {

    public static final String PAYLOAD_KEY = "_dataAudit";
    public static final String CONTEXT_KEY = "auditContext";

    private DataAuditPayloadSupport() {
    }

    public static DataAuditWriteContextDTO extractAndStrip(Map<String, Object> payload) {
        DataAuditWriteContextDTO context = new DataAuditWriteContextDTO();
        if (payload == null || payload.isEmpty()) {
            return context;
        }
        merge(context, payload.remove(PAYLOAD_KEY));
        merge(context, payload.remove(CONTEXT_KEY));
        Object main = payload.get("main");
        if (main instanceof Map<?, ?> mainMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) mainMap;
            merge(context, typed.remove(PAYLOAD_KEY));
            merge(context, typed.remove(CONTEXT_KEY));
        }
        return context;
    }

    public static void stripReservedKeys(Map<String, Object> payload) {
        extractAndStrip(payload);
    }

    private static void merge(DataAuditWriteContextDTO target, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        Object reason = first(map, "reason", "changeReason");
        if (reason != null && StringUtils.isBlank(target.getReason())) {
            target.setReason(String.valueOf(reason));
        }
        Object revision = first(map, "expectedRevision", "revision");
        if (revision != null && target.getExpectedRevision() == null) {
            target.setExpectedRevision(toLong(revision));
        }
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
