package com.mdframe.forge.plugin.collaboration.message;

import com.mdframe.forge.plugin.collaboration.message.CollaborationRecipientResolver.RecipientResolution;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.connector.MessageConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.ProviderError;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageRequest;
import com.mdframe.forge.starter.collaboration.model.ProviderMessageResult;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.message.channel.ChannelType;
import com.mdframe.forge.starter.message.channel.MessageChannel;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 企业协同消息渠道（Task 13）。
 * <p>
 * 实现消息核心的逐人投递合同：按连接解析平台与 MESSAGE 应用构建执行上下文，
 * 把 Forge 接收人映射为平台侧用户后交给对应平台的 {@link MessageConnector}；
 * 未映射/停用接收人转为 SKIPPED，模板非法整批拒绝，平台部分失败逐人回传，
 * 任何前置失败都返回稳定的逐人结果而不抛异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationMessageChannel implements MessageChannel {

    /** 接收人无连接内身份绑定 */
    public static final String ERROR_NO_BINDING = "NO_BINDING";
    /** 接收人绑定的外部账号已停用/删除 */
    public static final String ERROR_BINDING_DISABLED = "BINDING_DISABLED";
    /** 连接缺失或不可用 */
    public static final String ERROR_CONNECTION_UNAVAILABLE = "CONNECTION_UNAVAILABLE";
    /** 平台不支持消息能力或无可用应用 */
    public static final String ERROR_CAPABILITY_UNAVAILABLE = "CAPABILITY_UNAVAILABLE";
    /** 模板校验失败 */
    public static final String ERROR_TEMPLATE_INVALID = "TEMPLATE_INVALID";
    /** 平台调用异常 */
    public static final String ERROR_PROVIDER_ERROR = "PROVIDER_ERROR";

    private final ISocialConfigService socialConfigService;
    private final ISocialAppConfigService appConfigService;
    private final CollaborationProviderRegistry providerRegistry;
    private final CollaborationRecipientResolver recipientResolver;
    private final CollaborationMessageTemplatePolicy templatePolicy;

    @Override
    public ChannelType key() {
        return ChannelType.COLLABORATION;
    }

    @Override
    public void init(Map<String, String> config) {
        // 渠道配置来自连接与应用配置表，无需静态初始化
    }

    @Override
    public SendResult send(SendRequest request) {
        return SendResult.fail("企业协同渠道仅支持逐人投递，请使用 sendToRecipients");
    }

    @Override
    public boolean supportsRecipientDelivery() {
        return true;
    }

    @Override
    public ChannelSendResult sendToRecipients(ChannelSendRequest request) {
        List<Long> userIds = request.recipients() == null
                ? List.of()
                : request.recipients().stream().map(ChannelRecipient::userId).toList();
        if (userIds.isEmpty()) {
            return new ChannelSendResult(null, List.of(), null);
        }
        SysSocialConfig connection;
        if (request.connectionId() == null) {
            // 未指定连接时自动解析租户下唯一可用的消息连接（启用 + 平台支持 MESSAGE + 已绑定启用应用）
            connection = resolveDefaultConnection(request.tenantId());
            if (connection == null) {
                return allFailed(userIds, ERROR_CONNECTION_UNAVAILABLE, "租户下无可用的企业协同消息连接", null);
            }
        } else {
            connection = socialConfigService.selectConfigById(request.connectionId());
            if (connection == null || !EnableStatus.ENABLED.matches(connection.getStatus())) {
                return allFailed(userIds, ERROR_CONNECTION_UNAVAILABLE, "企业协同连接不存在或已停用", null);
            }
        }
        String platform = connection.getPlatform();
        if (!providerRegistry.supports(platform, CollaborationCapability.MESSAGE)) {
            return allFailed(userIds, ERROR_CAPABILITY_UNAVAILABLE, "该平台未启用消息能力", platform);
        }
        MessageConnector connector = providerRegistry.requireConnector(
                platform, CollaborationCapability.MESSAGE, MessageConnector.class);

        CollaborationExecutionContext context;
        try {
            context = buildContext(connection);
        } catch (BusinessException e) {
            return allFailed(userIds, ERROR_CAPABILITY_UNAVAILABLE, e.getMessage(), platform);
        }

        // 模板校验：非法/超长发送前整批拒绝
        String msgType = templatePolicy.resolveMsgType(request.params());
        String url = templatePolicy.resolveUrl(request.params());
        String rejectReason = templatePolicy.validate(msgType, request.title(), request.content(), url);
        if (rejectReason != null) {
            log.warn("企业协同消息模板校验拒绝: connectionId={}, messageId={}, reason={}",
                    connection.getId(), request.messageId(), rejectReason);
            return allFailed(userIds, ERROR_TEMPLATE_INVALID, rejectReason, platform);
        }

        // 接收人映射：未映射/停用明确 SKIPPED，可发送人交给平台 Connector
        RecipientResolution resolution = recipientResolver.resolve(
                request.tenantId(), connection.getId(), userIds);
        List<RecipientDeliveryResult> deliveries = new ArrayList<>(userIds.size());
        for (Long userId : resolution.unmappedUserIds()) {
            deliveries.add(RecipientDeliveryResult.skipped(userId, ERROR_NO_BINDING, "用户未绑定该连接的外部账号"));
        }
        for (Long userId : resolution.disabledUserIds()) {
            deliveries.add(RecipientDeliveryResult.skipped(userId, ERROR_BINDING_DISABLED, "用户外部账号已停用或删除"));
        }
        if (resolution.sendable().isEmpty()) {
            return new ChannelSendResult(null, deliveries, platform);
        }

        Map<String, Long> externalToUser = new LinkedHashMap<>();
        resolution.sendable().forEach((userId, externalUserId) -> externalToUser.putIfAbsent(externalUserId, userId));
        ProviderMessageRequest providerRequest = new ProviderMessageRequest(
                request.messageId(), request.idempotencyKey(), msgType,
                request.title(), request.content(), url,
                List.copyOf(externalToUser.keySet()), request.params());
        ProviderMessageResult result;
        try {
            result = connector.send(providerRequest, context);
        } catch (RuntimeException e) {
            log.warn("企业协同消息平台调用异常: connectionId={}, messageId={}, error={}",
                    connection.getId(), request.messageId(), e.getMessage());
            for (Long userId : resolution.sendable().keySet()) {
                deliveries.add(RecipientDeliveryResult.failed(userId, ERROR_PROVIDER_ERROR, e.getMessage()));
            }
            return new ChannelSendResult(null, deliveries, platform);
        }

        for (ProviderMessageResult.RecipientDelivery delivery : result.deliveries()) {
            Long userId = externalToUser.get(delivery.externalUserId());
            if (userId == null) {
                continue;
            }
            if (delivery.success()) {
                deliveries.add(RecipientDeliveryResult.sent(userId, result.providerRequestId()));
            } else {
                ProviderError error = delivery.error();
                deliveries.add(RecipientDeliveryResult.failed(userId,
                        error != null && error.providerCode() != null ? error.providerCode() : ERROR_PROVIDER_ERROR,
                        error != null ? error.message() : null));
            }
        }
        return new ChannelSendResult(result.providerRequestId(), deliveries, platform);
    }

    /**
     * 自动解析租户默认消息连接：启用且平台支持 MESSAGE 能力、已绑定启用应用的连接；
     * 命中多个时取第一个并告警，建议发送方显式指定 connectionId。
     */
    private SysSocialConfig resolveDefaultConnection(Long tenantId) {
        SysSocialConfig query = new SysSocialConfig();
        query.setTenantId(tenantId);
        query.setStatus(com.mdframe.forge.starter.core.enums.EnableStatus.ENABLED.getCode());
        List<SysSocialConfig> candidates = socialConfigService.selectConfigList(query).stream()
                .filter(conn -> providerRegistry.supports(conn.getPlatform(), CollaborationCapability.MESSAGE))
                .filter(conn -> {
                    try {
                        appConfigService.requireEnabledApp(conn.getTenantId(), conn.getId(), CollaborationCapability.MESSAGE);
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                })
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() > 1) {
            log.warn("租户存在多个可用消息连接，默认选用第一个: tenantId={}, connectionId={}",
                    tenantId, candidates.get(0).getId());
        }
        return candidates.get(0);
    }

    /**
     * 按 MESSAGE 能力绑定的应用构建执行上下文（与目录同步编排保持一致）
     */
    private CollaborationExecutionContext buildContext(SysSocialConfig connection) {
        SysSocialAppConfig app = appConfigService.requireEnabledApp(connection.getTenantId(),
                connection.getId(), CollaborationCapability.MESSAGE);
        return new CollaborationExecutionContext(connection.getTenantId(), connection.getId(),
                connection.getConnectionCode(), connection.getPlatform(), connection.getEnterpriseId(),
                app.getId(), app.getAppCode(), app.getAgentId(), Map.of());
    }

    private ChannelSendResult allFailed(List<Long> userIds, String errorCode, String errorMessage, String platform) {
        List<RecipientDeliveryResult> deliveries = userIds.stream()
                .map(userId -> RecipientDeliveryResult.failed(userId, errorCode, errorMessage))
                .toList();
        return new ChannelSendResult(null, deliveries, platform);
    }
}
