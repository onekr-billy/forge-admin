package com.mdframe.forge.plugin.ai.session.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 对话体验指标（阶段四#3/#4）。
 *
 * <p>基于 {@code ai_chat_record} 中 assistant 回复的落库字段聚合而来：
 * 首字延迟（first_token_ms）、总耗时（total_ms）、token 用量（token_usage）、
 * 状态（done/error/aborted）。用于后台观察对话体验优化前后的差异。</p>
 */
@Data
public class AiExperienceMetricsVO {

    /** 统计窗口内的 assistant 回复总数 */
    private Long totalReplies;
    /** 成功完成（status=done） */
    private Long completedCount;
    /** 出错（status=error） */
    private Long errorCount;
    /** 用户中断（status=aborted） */
    private Long abortedCount;

    /** 完成率 = completed / total（0~1，服务层计算） */
    private Double completionRate;
    /** 错误率 = error / total */
    private Double errorRate;
    /** 中断率 = aborted / total */
    private Double abortRate;

    /** 平均首字延迟（毫秒） */
    private Long avgFirstTokenMs;
    /** 最大首字延迟（毫秒） */
    private Long maxFirstTokenMs;
    /** 平均总耗时（毫秒） */
    private Long avgTotalMs;
    /** 最大总耗时（毫秒） */
    private Long maxTotalMs;
    /** 平均单次回复 token 用量 */
    private Long avgTokenUsage;

    /** 近 N 天体验日趋势 */
    private List<ExperienceTrendItem> dailyTrend;
}
