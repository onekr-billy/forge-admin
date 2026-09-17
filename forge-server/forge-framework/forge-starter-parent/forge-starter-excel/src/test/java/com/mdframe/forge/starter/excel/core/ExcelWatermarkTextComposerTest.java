package com.mdframe.forge.starter.excel.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Excel 水印内容项组装规则测试
 */
class ExcelWatermarkTextComposerTest {

    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.of(2026, 9, 16, 10, 30, 0);

    @Test
    void shouldComposeAllSelectedSegmentsInOrder() {
        String text = ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                true, "亚信运营平台", "机密", true, "zhangsan",
                true, "张三", true, "13812345678",
                true, FIXED_TIME, "yyyy-MM-dd HH:mm:ss"));

        assertEquals("亚信运营平台 机密 zhangsan 张三 13812345678 2026-09-16 10:30:00", text);
    }

    @Test
    void shouldNotRenderNullLiteralWhenCustomTextMissing() {
        // 复现线上 "null 超级管理员"：content 为 null 时不得拼出 "null" 字面量
        String text = ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                false, null, null, false, null,
                true, "超级管理员", false, null,
                false, FIXED_TIME, null));

        assertEquals("超级管理员", text);
    }

    @Test
    void shouldFilterNullAndUndefinedLiterals() {
        String text = ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                true, " null ", "undefined", true, "  ",
                true, null, true, "13900001234",
                false, null, null));

        assertEquals("13900001234", text);
    }

    @Test
    void shouldSkipUnselectedUserSegmentsEvenWithValue() {
        String text = ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                false, "不应出现", "内部资料", false, "zhangsan",
                false, "张三", false, "13812345678",
                false, FIXED_TIME, null));

        assertEquals("内部资料", text);
    }

    @Test
    void shouldFallbackToDefaultPatternWhenFormatInvalid() {
        String text = ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                true, "系统", "文本", true, "admin",
                true, "管理员", true, "13812345678",
                true, FIXED_TIME, "not-a-pattern"));

        assertEquals("系统 文本 admin 管理员 13812345678 2026-09-16 10:30:00", text);
    }

    @Test
    void shouldSupportCustomTimePattern() {
        String text = ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                false, null, null, false, null,
                false, null, false, null,
                true, FIXED_TIME, "yyyy/MM/dd"));

        assertEquals("2026/09/16", text);
    }

    @Test
    void shouldReturnNullWhenAllSegmentsEmpty() {
        assertNull(ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                true, null, " ", true, null,
                true, null, true, null,
                true, null, null)));
    }

    @Test
    void shouldReturnNullWhenNothingSelectedAndNoCustomText() {
        assertNull(ExcelWatermarkTextComposer.compose(new ExcelWatermarkTextComposer.Parts(
                false, "系统", null, false, "admin",
                false, "张三", false, "13812345678",
                false, FIXED_TIME, null)));
    }
}
