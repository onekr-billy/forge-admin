package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.connector.AccessTokenProvider;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.ProviderError;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.outbound.client.SecureOutboundClient;
import com.mdframe.forge.starter.outbound.constant.OutboundScenes;
import com.mdframe.forge.starter.outbound.model.OutboundRequest;
import com.mdframe.forge.starter.outbound.model.OutboundResponse;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.enums.SocialPlatform;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 企业微信访问 Token 提供者。
 * <p>
 * 缓存键包含租户、连接、应用与 Token 类型维度；刷新走分布式锁 + 双重检查，
 * 并发场景只有一次真实刷新；日志不记录 URL、Secret、Token 和响应正文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComAccessTokenProvider implements AccessTokenProvider {

    private static final String TOKEN_KEY_PREFIX = "collab:token:wecom:";
    private static final String LOCK_KEY_PREFIX = "collab:token:lock:wecom:";
    /** 提前刷新窗口（秒），企微 expires_in 默认 7200 */
    private static final long REFRESH_AHEAD_SECONDS = 300;
    private static final long MIN_CACHE_SECONDS = 60;
    private static final long LOCK_WAIT_SECONDS = 10;
    private static final long LOCK_LEASE_SECONDS = 30;

    private final ICacheService cacheService;
    private final RedissonClient redissonClient;
    private final ISocialAppConfigService appConfigService;
    private final SecureOutboundClient outboundClient;
    private final WeComErrorClassifier errorClassifier;
    private final WeComEndpointResolver endpointResolver;

    @Override
    public String platform() {
        return SocialPlatform.WECHAT_ENTERPRISE.getCode();
    }

    @Override
    public String getAccessToken(CollaborationExecutionContext context, TokenType tokenType) {
        validateContext(context, tokenType);
        SysSocialAppConfig app = resolveApp(context, tokenType);
        String cacheKey = buildTokenKey(context, app.getId(), tokenType);
        String token = cacheService.get(cacheKey, String.class);
        if (StringUtils.hasText(token)) {
            return token;
        }
        return refreshWithLock(context, app, tokenType, cacheKey);
    }

    @Override
    public void invalidate(CollaborationExecutionContext context, TokenType tokenType) {
        validateContext(context, tokenType);
        SysSocialAppConfig app = resolveApp(context, tokenType);
        cacheService.delete(buildTokenKey(context, app.getId(), tokenType));
    }

    private String refreshWithLock(CollaborationExecutionContext context, SysSocialAppConfig app,
                                   TokenType tokenType, String cacheKey) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + context.tenantId()
                + ":" + context.connectionId() + ":" + app.getId() + ":" + tokenType);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("获取企业微信Token刷新锁超时");
            }
            // 双重检查：等待锁期间其他线程可能已完成刷新
            String cached = cacheService.get(cacheKey, String.class);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            return fetchAndCacheToken(context, app, cacheKey);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("企业微信Token刷新被中断");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String fetchAndCacheToken(CollaborationExecutionContext context,
                                      SysSocialAppConfig app, String cacheKey) {
        String corpId = context.enterpriseId();
        if (!StringUtils.hasText(corpId)) {
            throw new BusinessException("企业协同连接未配置企业ID，无法获取企业微信Token");
        }
        char[] secret = appConfigService.decryptAppSecret(app);
        OutboundResponse response;
        try {
            String url = endpointResolver.resolveBaseUrl(context) + "/cgi-bin/gettoken?corpid=" + encode(corpId)
                    + "&corpsecret=" + encode(new String(secret));
            response = outboundClient.execute(OutboundRequest.builder()
                    .scene(OutboundScenes.COLLABORATION_PROVIDER)
                    .url(url)
                    .method("GET")
                    .build());
        } finally {
            Arrays.fill(secret, '\0');
        }
        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            ProviderError error = errorClassifier.classify(response.getStatusCode(), 0);
            log.warn("企业微信Token获取HTTP异常: connectionId={}, appId={}, status={}, category={}",
                    context.connectionId(), app.getId(), response.getStatusCode(), error.category());
            throw new WeComApiException(error);
        }
        JSONObject json = JSON.parseObject(response.bodyAsUtf8());
        if (json == null) {
            throw new BusinessException("企业微信Token响应解析失败");
        }
        int errcode = json.getIntValue("errcode", 0);
        if (errcode != 0) {
            ProviderError error = errorClassifier.classify(response.getStatusCode(), errcode,
                    json.getString("errmsg"), null);
            log.warn("企业微信Token获取失败: connectionId={}, appId={}, errcode={}, category={}",
                    context.connectionId(), app.getId(), errcode, error.category());
            throw new WeComApiException(error);
        }
        String token = json.getString("access_token");
        if (!StringUtils.hasText(token)) {
            throw new BusinessException("企业微信Token响应缺少access_token");
        }
        long expiresIn = json.getLongValue("expires_in", 7200L);
        long cacheSeconds = Math.max(expiresIn - REFRESH_AHEAD_SECONDS, MIN_CACHE_SECONDS);
        cacheService.set(cacheKey, token, cacheSeconds, TimeUnit.SECONDS);
        return token;
    }

    /**
     * 解析 Token 对应的物理应用：APP 用上下文指定应用，CONTACT 用 DIRECTORY 能力绑定应用
     */
    private SysSocialAppConfig resolveApp(CollaborationExecutionContext context, TokenType tokenType) {
        if (tokenType == TokenType.CONTACT) {
            return appConfigService.requireEnabledApp(context.tenantId(), context.connectionId(),
                    CollaborationCapability.DIRECTORY);
        }
        Long appId = context.appId();
        if (appId == null) {
            throw new BusinessException("企业协同执行上下文缺少应用ID");
        }
        return appConfigService.listApps(context.tenantId(), context.connectionId()).stream()
                .filter(app -> appId.equals(app.getId()))
                .findFirst()
                .filter(app -> EnableStatus.ENABLED.matches(app.getStatus()))
                .orElseThrow(() -> new BusinessException("企业协同应用不存在或已停用"));
    }

    private void validateContext(CollaborationExecutionContext context, TokenType tokenType) {
        if (context == null || context.tenantId() == null || context.connectionId() == null) {
            throw new BusinessException("企业协同执行上下文不完整");
        }
        if (tokenType == null) {
            throw new BusinessException("Token类型不能为空");
        }
    }

    private String buildTokenKey(CollaborationExecutionContext context, Long appId, TokenType tokenType) {
        return TOKEN_KEY_PREFIX + context.tenantId() + ":" + context.connectionId()
                + ":" + appId + ":" + tokenType;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
