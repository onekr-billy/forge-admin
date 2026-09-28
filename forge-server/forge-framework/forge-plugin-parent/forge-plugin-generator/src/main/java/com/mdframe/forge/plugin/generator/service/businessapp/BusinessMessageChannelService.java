package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessMessageChannel;
import com.mdframe.forge.plugin.generator.mapper.BusinessMessageChannelMapper;
import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;
import com.mdframe.forge.plugin.message.service.MessageService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.plugin.generator.enums.BusinessMessageChannelType;

/**
 * 业务消息通道服务。
 * <p>
 * 站内信、已绑定企业协同走消息中心；其它旧第三方占位通道继续返回 TODO。
 */
@Service
@RequiredArgsConstructor
public class BusinessMessageChannelService {

    private static final String DEFAULT_INTERNAL_CHANNEL_CODE = "internal_websocket";
    private static final Set<String> THIRD_PARTY_TYPES = Set.of("WECHAT_WORK", "FEISHU", "DINGTALK", "WEBHOOK");

    private final BusinessMessageChannelMapper channelMapper;

    @Autowired(required = false)
    private MessageService messageService;

    public BusinessMessageChannelStatus resolveChannel(String channelCode) {
        String normalizedCode = normalizeChannelCode(channelCode);
        if (isInternalAlias(normalizedCode)) {
            return internalStatus(DEFAULT_INTERNAL_CHANNEL_CODE, "站内信");
        }

        boolean applicationChannel = normalizedCode.startsWith("app_") && normalizedCode.endsWith("_collaboration");
        Long tenantId = applicationChannel ? BusinessMessageTenantContext.requireTenantId() : resolveTenantId();
        AiBusinessMessageChannel channel = channelMapper.selectByChannelCode(tenantId, normalizedCode);
        if (channel == null) {
            if (normalizedCode.startsWith("app_") && normalizedCode.endsWith("_collaboration")) {
                throw new BusinessException("应用协同消息通道不存在，请重新绑定企业协同连接");
            }
            if (isThirdPartyAlias(normalizedCode)) {
                return thirdPartyTodoStatus(normalizedCode.toUpperCase(Locale.ROOT), normalizedCode);
            }
            return internalStatus(DEFAULT_INTERNAL_CHANNEL_CODE, "站内信");
        }
        String channelType = StringUtils.defaultIfBlank(channel.getChannelType(), "INTERNAL")
                .toUpperCase(Locale.ROOT);
        if (applicationChannel && !BusinessMessageChannelType.COLLABORATION.matches(channelType)) {
            throw new BusinessException("应用协同消息通道类型异常，请重新绑定企业协同连接");
        }
        if (BusinessMessageChannelType.COLLABORATION.matches(channelType)) {
            if (!BusinessMessageTenantContext.requireTenantId().equals(channel.getTenantId())
                    || !EnableStatus.ENABLED.matches(channel.getStatus())) {
                throw new BusinessException("应用协同消息通道已停用或租户不匹配");
            }
            Long connectionId;
            try { connectionId = Long.valueOf(channel.getChannelConfigRef()); }
            catch (RuntimeException invalid) { throw new BusinessException("应用协同消息通道缺少有效连接"); }
            if (connectionId <= 0) throw new BusinessException("应用协同消息连接无效");
            BusinessMessageChannelStatus status = new BusinessMessageChannelStatus();
            status.setChannelCode(channel.getChannelCode());
            status.setChannelName(channel.getChannelName());
            status.setChannelType(channelType);
            status.setSendChannel(channelType);
            status.setConnectionId(connectionId);
            status.setEnabled(true);
            status.setInternalChannel(false);
            status.setThirdPartyChannel(true);
            status.setTodo(false);
            status.setMessage("企业协同通道已绑定，投递结果在消息中心查看");
            return status;
        }
        if ("INTERNAL".equals(channelType)) {
            BusinessMessageChannelStatus status = internalStatus(channel.getChannelCode(), channel.getChannelName());
            status.setEnabled(EnableStatus.ENABLED.matches(channel.getStatus()));
            if (!Boolean.TRUE.equals(status.getEnabled())) {
                status.setTodo(true);
                status.setTodoCode("INTERNAL_CHANNEL_DISABLED");
                status.setMessage("站内信通道未启用");
            }
            return status;
        }
        return thirdPartyTodoStatus(channelType, channel.getChannelCode());
    }

    public SysMessage sendInternalMessage(MessageSendRequestDTO req) {
        if (messageService == null) {
            throw new BusinessException("MessageService 未注入，无法发送站内消息");
        }
        req.setChannel(StringUtils.defaultIfBlank(req.getChannel(), "WEB"));
        return messageService.send(req);
    }

    public JSONObject buildThirdPartyTodoResult(String channelType, String channelCode) {
        String type = StringUtils.defaultIfBlank(channelType, "THIRD_PARTY").toUpperCase(Locale.ROOT);
        JSONObject result = new JSONObject();
        result.put("status", "TODO");
        result.put("todoCode", type + "_NOT_IMPLEMENTED");
        result.put("channelType", type);
        result.put("channelCode", channelCode);
        result.put("message", "第三方消息通道待实现");
        return result;
    }

    public List<Long> selectUserIdsByRoleIds(List<Long> roleIds) {
        return selectUserIdsByRoleIds(roleIds, null, null);
    }

    public List<Long> selectUserIdsByRoleIds(List<Long> roleIds, Long orgId) {
        return selectUserIdsByRoleIds(roleIds, null, orgId);
    }

    public List<Long> selectUserIdsByRoleIds(List<Long> roleIds, Long tenantId, Long orgId) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Long resolvedOrgId = orgId != null ? orgId : resolveActiveOrgId();
        if (resolvedOrgId == null) {
            return List.of();
        }
        Long resolvedTenantId = tenantId != null ? tenantId : resolveTenantId();
        return channelMapper.selectUserIdsByRoleIds(resolvedTenantId, resolvedOrgId, roleIds);
    }

    public List<Long> selectUserIdsByOrgIds(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return List.of();
        }
        return channelMapper.selectUserIdsByOrgIds(resolveTenantId(), orgIds);
    }

    public Set<Long> toUserIdSet(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(userIds);
    }

    private BusinessMessageChannelStatus internalStatus(String channelCode, String channelName) {
        BusinessMessageChannelStatus status = new BusinessMessageChannelStatus();
        status.setChannelCode(channelCode);
        status.setChannelName(channelName);
        status.setChannelType("INTERNAL");
        status.setSendChannel("WEB");
        status.setEnabled(true);
        status.setInternalChannel(true);
        status.setThirdPartyChannel(false);
        status.setTodo(false);
        status.setMessage("站内信通道可用");
        return status;
    }

    private BusinessMessageChannelStatus thirdPartyTodoStatus(String channelType, String channelCode) {
        BusinessMessageChannelStatus status = new BusinessMessageChannelStatus();
        status.setChannelCode(channelCode);
        status.setChannelName(channelCode);
        status.setChannelType(channelType);
        status.setSendChannel(channelType);
        status.setEnabled(false);
        status.setInternalChannel(false);
        status.setThirdPartyChannel(true);
        status.setTodo(true);
        status.setTodoCode(channelType + "_NOT_IMPLEMENTED");
        status.setMessage("第三方消息通道待实现");
        return status;
    }

    private String normalizeChannelCode(String channelCode) {
        String normalized = StringUtils.defaultIfBlank(channelCode, DEFAULT_INTERNAL_CHANNEL_CODE)
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "web", "internal" -> DEFAULT_INTERNAL_CHANNEL_CODE;
            case "wechat", "wechatwork", "wechat_work" -> "wechat_work";
            case "ding", "ding_talk", "dingtalk" -> "dingtalk";
            default -> normalized;
        };
    }

    private boolean isInternalAlias(String channelCode) {
        return DEFAULT_INTERNAL_CHANNEL_CODE.equals(channelCode);
    }

    private boolean isThirdPartyAlias(String channelCode) {
        String type = channelCode.toUpperCase(Locale.ROOT);
        return THIRD_PARTY_TYPES.contains(type)
                || Set.of("WECHAT_WORK", "FEISHU", "DINGTALK").contains(type);
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId == null ? 1L : tenantId;
    }

    private Long resolveActiveOrgId() {
        try {
            return SessionHelper.getActiveOrgId();
        } catch (Exception e) {
            return null;
        }
    }
}
