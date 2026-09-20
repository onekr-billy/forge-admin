package com.mdframe.forge.plugin.generator.service.audit;

/**
 * 记录主键规范化为字符串，禁止前端 Number 化。
 */
public final class DataAuditRecordIds {

    public static final int MAX_LENGTH = 128;

    private DataAuditRecordIds() {
    }

    public static String normalize(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("记录主键长度超过 " + MAX_LENGTH);
        }
        return text;
    }
}
