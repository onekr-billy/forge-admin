package com.mdframe.forge.plugin.ai.session.vo;

import lombok.Data;

/**
 * AI 对话体验日趋势项（阶段四#4）。
 * 每天一条：回复数、平均首字延迟、平均总耗时、错误数、中断数。
 */
@Data
public class ExperienceTrendItem {

    private String date;
    /** 当日 assistant 回复数 */
    private Long replyCount;
    /** 当日平均首字延迟（毫秒） */
    private Long avgFirstTokenMs;
    /** 当日平均总耗时（毫秒） */
    private Long avgTotalMs;
    /** 当日错误数 */
    private Long errorCount;
    /** 当日中断数 */
    private Long abortedCount;
}
