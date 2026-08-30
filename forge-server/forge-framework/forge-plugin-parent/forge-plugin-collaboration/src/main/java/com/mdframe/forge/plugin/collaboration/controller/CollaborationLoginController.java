package com.mdframe.forge.plugin.collaboration.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.collaboration.connector.LoginConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.collaboration.provider.CollaborationProviderRegistry;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.social.domain.dto.LoginClientContext;
import com.mdframe.forge.starter.social.domain.dto.SocialAuthUrl;
import com.mdframe.forge.starter.social.domain.dto.SocialLoginRequest;
import com.mdframe.forge.starter.social.domain.dto.SocialOAuthIntent;
import com.mdframe.forge.starter.social.domain.dto.SocialTicketResponse;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import com.mdframe.forge.starter.social.service.SocialOAuthStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 企业协同免登控制器（企业客户端内工作台点击应用自动登录）。
 * <p>
 * 授权走平台 OAuth2 网页授权：前端在企业客户端内取授权地址跳转，回跳带 code；
 * 回调由服务端携带 code 调用平台官方接口换取已验证身份，只返回一次性登录票据，
 * 身份要素不出前端。手机号在平台授权 snsapi_privateinfo 后随身份增量补齐。
 */
@Slf4j
@RestController
@RequestMapping("/collaboration/login")
@RequiredArgsConstructor
public class CollaborationLoginController {

    private final ISocialConfigService socialConfigService;
    private final ISocialAppConfigService appConfigService;
    private final CollaborationProviderRegistry providerRegistry;
    private final SocialOAuthStateService oauthStateService;

    /**
     * 获取免登授权地址：服务端签发 state 并绑定登录意图，返回平台 OAuth2 授权页地址
     *
     * @param connectionCode 连接编码
     * @param redirectUri    平台授权后回跳地址（须在应用可信域名内，前端当前免登页地址）
     * @param userClient     发起客户端标识（app/pc 等），用于票据跨端防挪用
     */
    @GetMapping("/authorize")
    @IgnoreTenant
    @SaIgnore
    public RespInfo<SocialAuthUrl> authorize(@RequestParam String connectionCode,
                                             @RequestParam String redirectUri,
                                             @RequestParam(required = false) String userClient) {
        if (StrUtil.isBlank(connectionCode) || StrUtil.isBlank(redirectUri)) {
            return RespInfo.error("连接编码与回跳地址不能为空");
        }
        SysSocialConfig connection = requireEnabledConnection(connectionCode);
        LoginConnector connector = requireLoginConnector(connection);
        CollaborationExecutionContext context = buildContext(connection);

        SocialOAuthIntent intent = new SocialOAuthIntent();
        intent.setAction(SocialOAuthIntent.ACTION_LOGIN);
        intent.setTenantId(connection.getTenantId());
        intent.setConnectionId(connection.getId());
        intent.setConnectionCode(connection.getConnectionCode());
        intent.setPlatform(connection.getPlatform());
        intent.setUserClient(userClient);
        String state = oauthStateService.issueState(intent);

        String authUrl = connector.buildAuthorizeUrl(context, state, redirectUri);

        SocialAuthUrl result = SocialAuthUrl.builder()
                .platform(connection.getPlatform())
                .connectionCode(connection.getConnectionCode())
                .platformName(connection.getPlatformName())
                .authUrl(authUrl)
                .state(state)
                .build();
        return RespInfo.success(result);
    }

    /**
     * 免登回调：消费 state，服务端携带 code 换取已验证身份，只返回一次性登录票据
     */
    @PostMapping("/callback")
    @IgnoreTenant
    @SaIgnore
    public RespInfo<SocialTicketResponse> callback(@RequestBody SocialLoginRequest request) {
        SocialOAuthIntent intent = oauthStateService.consumeState(request.getState());
        if (!SocialOAuthIntent.ACTION_LOGIN.equals(intent.getAction())) {
            return RespInfo.error("该授权凭据不适用于登录，请重新发起免登");
        }
        SysSocialConfig connection = socialConfigService.selectConfigById(intent.getConnectionId());
        if (connection == null || !EnableStatus.ENABLED.matches(connection.getStatus())) {
            return RespInfo.error("该连接未启用");
        }

        LoginConnector connector = requireLoginConnector(connection);
        CollaborationExecutionContext context = buildContext(connection);
        VerifiedSocialIdentity identity = connector.exchangeIdentity(context, request.getCode());

        LoginClientContext client = new LoginClientContext(connection.getTenantId(),
                StrUtil.blankToDefault(request.getUserClient(), intent.getUserClient()));
        String ticket = oauthStateService.issueLoginTicket(identity, client);
        log.info("企业协同免登换票成功: connectionId={}, platform={}", connection.getId(), connection.getPlatform());

        return RespInfo.success(SocialTicketResponse.builder()
                .socialTicket(ticket)
                .connectionCode(connection.getConnectionCode())
                .platform(connection.getPlatform())
                .tenantId(connection.getTenantId())
                .expiresIn(SocialOAuthStateService.TICKET_TTL_SECONDS)
                .build());
    }

    /**
     * 工作台免登发现：前端启动时查询指定平台是否开启免登及其 connectionCode，
     * 替代前端写死的 VITE_WECOM_CONNECTION_CODE。未登录无租户上下文，故公开且忽略租户。
     *
     * @param platform 平台编码，默认企业微信
     */
    @GetMapping("/sso-connection")
    @IgnoreTenant
    @SaIgnore
    public RespInfo<Map<String, Object>> ssoConnection(
            @RequestParam(required = false, defaultValue = "WECHAT_ENTERPRISE") String platform) {
        SysSocialConfig connection = socialConfigService.selectSsoWorkbenchConnection(platform);
        Map<String, Object> result = new java.util.HashMap<>(4);
        if (connection == null) {
            result.put("enabled", false);
            return RespInfo.success(result);
        }
        result.put("enabled", true);
        result.put("connectionCode", connection.getConnectionCode());
        result.put("platform", connection.getPlatform());
        result.put("platformName", connection.getPlatformName());
        return RespInfo.success(result);
    }

    private SysSocialConfig requireEnabledConnection(String connectionCode) {
        SysSocialConfig connection = socialConfigService.selectConnectionByCode(connectionCode);
        if (connection == null || !EnableStatus.ENABLED.matches(connection.getStatus())) {
            throw new com.mdframe.forge.starter.core.exception.BusinessException("该连接不存在或未启用");
        }
        return connection;
    }

    private LoginConnector requireLoginConnector(SysSocialConfig connection) {
        return providerRegistry.requireConnector(
                connection.getPlatform(), CollaborationCapability.LOGIN, LoginConnector.class);
    }

    private CollaborationExecutionContext buildContext(SysSocialConfig connection) {
        SysSocialAppConfig app = appConfigService.requireEnabledApp(connection.getTenantId(),
                connection.getId(), CollaborationCapability.LOGIN);
        return new CollaborationExecutionContext(connection.getTenantId(), connection.getId(),
                connection.getConnectionCode(), connection.getPlatform(), connection.getEnterpriseId(),
                app.getId(), app.getAppCode(), app.getAgentId(), Map.of());
    }
}
