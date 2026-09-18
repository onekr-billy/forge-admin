package com.mdframe.forge.starter.excel.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 水印文本组装器。
 * 按固定顺序拼接勾选的内容项：系统名称、自定义文本、账号、姓名、手机号、导出时间。
 * 空值与 "null" 字面量自动跳过，全部为空时返回 null。
 */
public final class ExcelWatermarkTextComposer {

    /**
     * 时间格式为空或非法时的回退格式
     */
    private static final String DEFAULT_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private ExcelWatermarkTextComposer() {
    }

    /**
     * 水印内容片段（取值来源由调用方解析，本类只负责组装）
     *
     * @param showSystemName 是否拼接系统名称
     * @param systemName     系统名称（租户配置 sys_tenant.system_name）
     * @param customText     自定义文本（水印内容输入框，无需开关，非空即拼接）
     * @param showAccount    是否拼接登录账号
     * @param account        登录账号（username）
     * @param showRealName   是否拼接用户姓名
     * @param realName       用户姓名（realName）
     * @param showPhone      是否拼接手机号（完整显示）
     * @param phone          手机号
     * @param showTime       是否拼接导出时间
     * @param time           导出时间
     * @param timePattern    时间格式，空或非法时回退默认格式
     */
    public record Parts(boolean showSystemName, String systemName,
                        String customText,
                        boolean showAccount, String account,
                        boolean showRealName, String realName,
                        boolean showPhone, String phone,
                        boolean showTime, LocalDateTime time, String timePattern) {
    }

    /**
     * 组装水印文本。
     *
     * @return 拼接后的文本；全部内容项为空时返回 null
     */
    public static String compose(Parts parts) {
        List<String> segments = new ArrayList<>();
        if (parts.showSystemName()) {
            addSegment(segments, parts.systemName());
        }
        addSegment(segments, parts.customText());
        if (parts.showAccount()) {
            addSegment(segments, parts.account());
        }
        if (parts.showRealName()) {
            addSegment(segments, parts.realName());
        }
        if (parts.showPhone()) {
            addSegment(segments, parts.phone());
        }
        if (parts.showTime()) {
            addSegment(segments, formatTime(parts.time(), parts.timePattern()));
        }
        return segments.isEmpty() ? null : String.join(" ", segments);
    }

    private static void addSegment(List<String> segments, String value) {
        String cleaned = cleanText(value);
        if (cleaned != null) {
            segments.add(cleaned);
        }
    }

    /**
     * 过滤 null、空白与序列化残留的 "null"/"undefined" 字面量
     */
    private static String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()
                || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String formatTime(LocalDateTime time, String pattern) {
        if (time == null) {
            return null;
        }
        String effective = cleanText(pattern);
        try {
            return time.format(DateTimeFormatter.ofPattern(
                    effective != null ? effective : DEFAULT_TIME_PATTERN));
        } catch (IllegalArgumentException e) {
            return time.format(DateTimeFormatter.ofPattern(DEFAULT_TIME_PATTERN));
        }
    }
}
