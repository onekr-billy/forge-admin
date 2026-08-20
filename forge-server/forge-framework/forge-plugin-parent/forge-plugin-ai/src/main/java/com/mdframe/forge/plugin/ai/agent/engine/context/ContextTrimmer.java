package com.mdframe.forge.plugin.ai.agent.engine.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 长会话上下文裁剪器（阶段三#3）。
 *
 * <p>在把历史消息拼进 prompt 之前，按「消息条数上限 + token 预算 + 最近优先」对历史做裁剪，
 * 避免长会话直接撑爆模型上下文窗口。</p>
 *
 * <p>这里只做<b>裁剪</b>（trim），不做二次模型摘要——摘要需要额外的同步模型调用，
 * 会显著增加首字延迟与成本；裁剪已能满足「长会话上下文可控」的目标，且无副作用。
 * token 估算走保守的按字符估算（宁可多裁，不可少裁——高估只会多丢历史，低估会撑爆窗口）。</p>
 *
 * <p>本类是无状态工具组件：纯内存计算，不访问数据库、不注入任何业务 Service。</p>
 */
@Slf4j
@Component
public class ContextTrimmer {

    /** 历史消息条数上限（转成 Message 后、进入 token 预算前的第一道闸） */
    @Value("${forge.agent.context.max-history-messages:20}")
    private int maxHistoryMessages;

    /** 历史消息 token 预算（按保守字符估算） */
    @Value("${forge.agent.context.max-history-tokens:6000}")
    private int maxHistoryTokens;

    /** 即使超预算也至少保留的最近条数（避免连最近一轮上下文都被裁掉） */
    @Value("${forge.agent.context.min-keep-messages:2}")
    private int minKeepMessages;

    /**
     * 历史抓取条数上限——调用方据此决定从库里取多少条，再交给 {@link #trim} 按 token 预算收敛。
     */
    public int getMaxHistoryMessages() {
        return maxHistoryMessages > 0 ? maxHistoryMessages : 20;
    }

    /**
     * 裁剪历史消息。
     *
     * @param history        历史消息（时间升序：最旧在前、最新在后）
     * @param reservedTokens 本轮 system + user 消息预计占用的 token（先从预算里扣除）
     * @return 裁剪后的历史（仍为时间升序）；不修改入参
     */
    public List<Message> trim(List<Message> history, int reservedTokens) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        // 第一道闸：条数上限（只保留最近 maxHistoryMessages 条）
        int max = getMaxHistoryMessages();
        List<Message> bounded = history;
        int droppedByCount = 0;
        if (history.size() > max) {
            droppedByCount = history.size() - max;
            bounded = new ArrayList<>(history.subList(droppedByCount, history.size()));
        }

        // 第二道闸：token 预算（从最新往回累加，保留能放下的最近若干条）
        int budget = maxHistoryTokens - Math.max(0, reservedTokens);
        int minKeep = Math.max(1, minKeepMessages);

        List<Message> kept = new ArrayList<>();
        int used = 0;
        int droppedByToken = 0;
        for (int i = bounded.size() - 1; i >= 0; i--) {
            int cost = estimateTokens(text(bounded.get(i)));
            boolean mustKeep = kept.size() < minKeep;
            if (!mustKeep && budget > 0 && used + cost > budget) {
                droppedByToken = i + 1; // 前面 i+1 条因预算不足被裁
                break;
            }
            kept.add(0, bounded.get(i)); // 头插，保持时间升序
            used += cost;
        }

        if (droppedByCount > 0 || droppedByToken > 0) {
            log.debug("[ContextTrimmer] 历史裁剪: 原始={} 条, 条数裁剪={}, token裁剪={}, 最终保留={} 条, 估算token={}, 预算={}",
                    history.size(), droppedByCount, droppedByToken, kept.size(), used, budget);
        }
        return kept;
    }

    private String text(Message message) {
        if (message == null) {
            return "";
        }
        String t = message.getText();
        return t == null ? "" : t;
    }

    /**
     * 保守的 token 估算：CJK 表意/东亚字符按 1 token/字（宁可高估），其余可见字符按 ~4 字符/token。
     * 这不是精确分词，只用于「够不够放进窗口」的相对判断——高估比低估安全。
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else if (!Character.isWhitespace(c)) {
                other++;
            }
        }
        return cjk + (int) Math.ceil(other / 4.0);
    }

    private boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)   // CJK 统一表意文字
                || (c >= 0x3400 && c <= 0x4DBF)   // 扩展 A
                || (c >= 0xF900 && c <= 0xFAFF)   // 兼容表意文字
                || (c >= 0x3000 && c <= 0x303F)   // CJK 标点
                || (c >= 0xFF00 && c <= 0xFFEF)   // 全角字符
                || (c >= 0x3040 && c <= 0x30FF);  // 平假名/片假名
    }
}
