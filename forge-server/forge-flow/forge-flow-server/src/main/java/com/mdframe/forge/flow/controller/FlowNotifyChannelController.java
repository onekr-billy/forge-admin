package com.mdframe.forge.flow.controller;

import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程通知可用渠道查询接口
 *
 * <p>流程模型「通知与推送」配置（事件×渠道矩阵）的渠道下拉数据源：
 * 消息中心内置渠道（站内信/邮件/短信）+ 当前租户已启用的企业协同连接平台（企微/钉钉/飞书…）。
 * 未接入适配器的平台不会返回，前端勾选项随连接配置动态变化。</p>
 */
@RestController
@RequestMapping("/api/flow/notify-channels")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class FlowNotifyChannelController {

    /** 协同平台编码 → 展示名 */
    private static final Map<String, String> PLATFORM_NAMES = Map.of(
            "WECOM", "企业微信",
            "WECHAT_ENTERPRISE", "企业微信",
            "DINGTALK", "钉钉",
            "FEISHU", "飞书");

    private final ObjectProvider<ISocialConfigService> socialConfigServiceProvider;
    private final ObjectProvider<CollaborationProviderRegistry> collaborationProviderRegistryProvider;

    /**
     * 当前租户可用的通知渠道列表
     */
    @GetMapping
    public RespInfo<List<Map<String, Object>>> availableChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();

        // 内置渠道：站内信为基础渠道（幂等去重、消息留痕），始终返回
        channels.add(channel("WEB", "站内信", "builtin", null, true, false));
        channels.add(channel("EMAIL", "邮件", "builtin", null, false, false));
        // 短信按条计费，标记成本提示
        channels.add(channel("SMS", "短信", "builtin", null, false, true));

        // 企业协同渠道：已启用连接的平台（同一平台多连接只展示一次）
        Set<String> platforms = new LinkedHashSet<>();
        try {
            ISocialConfigService socialConfigService = socialConfigServiceProvider.getIfAvailable();
            CollaborationProviderRegistry providerRegistry = collaborationProviderRegistryProvider.getIfAvailable();
            if (socialConfigService != null) {
                SysSocialConfig query = new SysSocialConfig();
                query.setStatus(1);
                List<SysSocialConfig> connections = socialConfigService.selectConfigList(query);
                if (connections != null) {
                    for (SysSocialConfig connection : connections) {
                        if (connection.getPlatform() != null && !connection.getPlatform().isBlank()
                                && providerRegistry != null
                                && providerRegistry.supports(connection.getPlatform(), CollaborationCapability.MESSAGE)) {
                            platforms.add(connection.getPlatform().trim().toUpperCase());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 协同模块未启用或查询失败时仅返回内置渠道，不影响接口可用性
        }

        if (!platforms.isEmpty()) {
            List<String> platformNames = new ArrayList<>();
            for (String platform : platforms) {
                platformNames.add(PLATFORM_NAMES.getOrDefault(platform, platform));
            }
            channels.add(channel("COLLABORATION", "企业协同（" + String.join("、", platformNames) + "）",
                    "connection", platforms, false, false));
        }

        return RespInfo.success(channels);
    }

    private Map<String, Object> channel(String channel, String name, String type,
                                        Set<String> platforms, boolean alwaysOn, boolean costWarning) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("channel", channel);
        item.put("name", name);
        item.put("type", type);
        if (platforms != null) {
            item.put("platforms", platforms);
        }
        item.put("alwaysOn", alwaysOn);
        item.put("costWarning", costWarning);
        return item;
    }
}
