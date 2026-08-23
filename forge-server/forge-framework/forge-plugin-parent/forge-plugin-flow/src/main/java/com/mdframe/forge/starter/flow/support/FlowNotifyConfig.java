package com.mdframe.forge.starter.flow.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 流程模型通知配置（notify_config JSON）解析结果。
 *
 * <p>结构：事件 key（todo-新待办 / result-审批结果 / cc-抄送）→ 渠道配置。
 * 渠道取值：WEB-站内信 / EMAIL-邮件 / SMS-短信 / COLLABORATION-企业协同（按连接平台路由）。</p>
 *
 * <p>解析失败或未配置时返回 {@code null}，调用方回退默认通知行为，保证不回归。</p>
 */
@Slf4j
public final class FlowNotifyConfig {

    public static final String EVENT_TODO = "todo";
    public static final String EVENT_RESULT = "result";
    public static final String EVENT_CC = "cc";

    public static final String CHANNEL_WEB = "WEB";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_COLLABORATION = "COLLABORATION";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_CHANNELS = Set.of(
            CHANNEL_WEB, CHANNEL_EMAIL, CHANNEL_SMS, CHANNEL_COLLABORATION);

    private final Map<String, ChannelConfig> events;

    private FlowNotifyConfig(Map<String, ChannelConfig> events) {
        this.events = events == null ? Collections.emptyMap() : events;
    }

    /**
     * 解析 notify_config JSON（顶层扁平结构：{"todo":{...},"result":{...},"cc":{...}}）；
     * 空/非法返回 null（回退默认行为）
     */
    public static FlowNotifyConfig parse(String notifyConfigJson) {
        if (notifyConfigJson == null || notifyConfigJson.isBlank()) {
            return null;
        }
        try {
            Map<String, ChannelConfig> events = MAPPER.readValue(
                    notifyConfigJson,
                    MAPPER.getTypeFactory().constructMapType(Map.class, String.class, ChannelConfig.class));
            if (events == null || events.isEmpty()) {
                return null;
            }
            events.forEach((eventKey, config) -> normalize(eventKey, config));
            return new FlowNotifyConfig(events);
        } catch (Exception e) {
            log.warn("流程通知配置解析失败，回退默认通知行为: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取事件渠道配置；未配置返回 null
     */
    public ChannelConfig channelConfigOf(String eventKey) {
        ChannelConfig config = events.get(eventKey);
        if (config == null || config.getChannels() == null || config.getChannels().isEmpty()) {
            return null;
        }
        return config;
    }

    /** 是否配置了指定事件 */
    public boolean hasEvent(String eventKey) {
        return channelConfigOf(eventKey) != null;
    }

    private static void normalize(String eventKey, ChannelConfig config) {
        if (config == null || config.getChannels() == null) {
            return;
        }
        LinkedHashSet<String> channels = new LinkedHashSet<>();
        for (String channel : config.getChannels()) {
            if (channel == null) {
                continue;
            }
            String normalized = channel.trim().toUpperCase(Locale.ROOT);
            if (ALLOWED_CHANNELS.contains(normalized)) {
                channels.add(normalized);
            }
        }
        // WEB 是新待办必选基础渠道；即使手写 API 载荷遗漏也会补齐。
        // 抄送不支持短信，与前端矩阵和产品约定保持一致。
        if (EVENT_TODO.equals(eventKey)) {
            LinkedHashSet<String> todoChannels = new LinkedHashSet<>();
            todoChannels.add(CHANNEL_WEB);
            todoChannels.addAll(channels);
            channels = todoChannels;
        }
        if (EVENT_CC.equals(eventKey)) {
            channels.remove(CHANNEL_SMS);
        }
        config.setChannels(List.copyOf(channels));
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChannelConfig {
        /** 通知渠道列表：WEB/EMAIL/SMS/COLLABORATION */
        private List<String> channels;
        /** 消息模板编码覆盖（为空时按事件使用默认模板编码） */
        private String templateCode;
    }
}
