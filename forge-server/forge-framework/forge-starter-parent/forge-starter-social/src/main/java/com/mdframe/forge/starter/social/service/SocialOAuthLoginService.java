package com.mdframe.forge.starter.social.service;

import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.factory.SocialAuthRequestFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * OAuth 授权码换取服务端已验证身份。
 * <p>
 * Secret 解析优先使用连接下 LOGIN 能力应用的密文凭据，兼容期回退连接旧明文字段；
 * 换取结果只以 {@link VerifiedSocialIdentity} 输出，AuthUser 明细不出本服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialOAuthLoginService {

    private final SocialAuthRequestFactory authRequestFactory;
    private final ISocialAppConfigService appConfigService;

    /**
     * 使用授权码向平台换取身份并映射为已验证身份
     */
    public VerifiedSocialIdentity exchange(SysSocialConfig connection, String code, String state) {
        if (connection == null || !EnableStatus.ENABLED.matches(connection.getStatus())) {
            throw new BusinessException("连接不存在或已停用");
        }
        if (StrUtil.isBlank(code)) {
            throw new BusinessException("授权码不能为空");
        }

        AuthRequest authRequest = buildAuthRequest(connection);
        AuthCallback callback = AuthCallback.builder()
                .code(code)
                .state(state)
                .build();

        AuthResponse<AuthUser> response = authRequest.login(callback);
        if (!response.ok() || response.getData() == null) {
            log.error("三方授权码换取失败: connectionId={}, platform={}, msg={}",
                    connection.getId(), connection.getPlatform(), response.getMsg());
            throw new BusinessException(StrUtil.blankToDefault(response.getMsg(), "三方登录失败"));
        }

        AuthUser authUser = response.getData();
        if (StrUtil.isBlank(authUser.getUuid())) {
            throw new BusinessException("平台未返回用户唯一标识");
        }

        return new VerifiedSocialIdentity(
                connection.getTenantId(),
                connection.getId(),
                connection.getConnectionCode(),
                connection.getPlatform(),
                authUser.getUuid(),
                authUser.getNickname(),
                authUser.getAvatar(),
                authUser.getEmail(),
                null,
                Instant.now());
    }

    /**
     * 构建平台授权跳转地址。
     * <p>
     * 与授权码换取共用同一套「LOGIN 应用优先、连接回退」参数解析，避免授权地址与换取阶段
     * 使用不同的 clientId/redirectUri 导致平台校验失败。
     */
    public String buildAuthorizeUrl(SysSocialConfig connection, String state) {
        if (connection == null || !EnableStatus.ENABLED.matches(connection.getStatus())) {
            throw new BusinessException("连接不存在或已停用");
        }
        return buildAuthRequest(connection).authorize(state);
    }

    /**
     * 构建 AuthRequest：LOGIN 应用密文凭据优先，无 LOGIN 绑定时回退旧明文配置
     */
    private AuthRequest buildAuthRequest(SysSocialConfig connection) {
        SysSocialAppConfig loginApp = resolveLoginApp(connection);
        if (loginApp != null) {
            char[] secret = appConfigService.decryptAppSecret(loginApp);
            if (secret != null && secret.length > 0) {
                try {
                    return authRequestFactory.createRequest(connection, loginApp, secret);
                } finally {
                    java.util.Arrays.fill(secret, '\0');
                }
            }
        }
        if (StrUtil.isBlank(connection.getClientSecret())) {
            throw new BusinessException("连接未配置登录凭据，请在连接下绑定 LOGIN 能力应用");
        }
        return authRequestFactory.createRequest(connection, loginApp, null);
    }

    /**
     * 解析连接下已启用的 LOGIN 能力应用；兼容期未绑定时返回 null 由调用方回退连接旧字段
     */
    private SysSocialAppConfig resolveLoginApp(SysSocialConfig connection) {
        try {
            return appConfigService.requireEnabledApp(
                    connection.getTenantId(), connection.getId(), CollaborationCapability.LOGIN);
        } catch (BusinessException e) {
            // 兼容期：连接尚未迁移到 LOGIN 应用时回退旧明文字段
            log.debug("连接无可用 LOGIN 应用凭据，回退旧配置: connectionId={}, reason={}",
                    connection.getId(), e.getMessage());
            return null;
        }
    }
}
