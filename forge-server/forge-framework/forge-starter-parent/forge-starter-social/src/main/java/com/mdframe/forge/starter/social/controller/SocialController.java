package com.mdframe.forge.starter.social.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.core.annotation.tenant.IgnoreTenant;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.social.domain.dto.LoginClientContext;
import com.mdframe.forge.starter.social.domain.dto.SocialAuthUrl;
import com.mdframe.forge.starter.social.domain.dto.SocialLoginRequest;
import com.mdframe.forge.starter.social.domain.dto.SocialOAuthIntent;
import com.mdframe.forge.starter.social.domain.dto.SocialPlatformInfo;
import com.mdframe.forge.starter.social.domain.dto.SocialTicketResponse;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import com.mdframe.forge.starter.social.service.SocialOAuthLoginService;
import com.mdframe.forge.starter.social.service.SocialOAuthStateService;
import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 三方登录控制器
 * <p>
 * state 由服务端签发并绑定授权意图；回调只返回一次性登录票据，
 * AuthUser 明细与身份要素不再出前端。
 */
@Slf4j
@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final ISocialConfigService socialConfigService;
    private final SocialOAuthStateService oauthStateService;
    private final SocialOAuthLoginService oauthLoginService;

    /**
     * 获取已启用的三方登录平台/连接列表
     */
    @GetMapping("/platforms")
    @IgnoreTenant
    @SaIgnore
    public RespInfo<List<SocialPlatformInfo>> getPlatforms(@RequestParam(required = false) Long tenantId) {
        List<SocialPlatformInfo> platforms = socialConfigService.selectEnabledPlatforms(tenantId);
        return RespInfo.success(platforms);
    }

    /**
     * 获取三方登录授权链接
     * <p>
     * 路径参数优先按连接编码解析，兼容旧平台编码调用（平台下唯一启用连接时）。
     *
     * @param action 操作类型：bind-绑定账号，不传则为登录
     */
    @GetMapping("/authUrl/{platform}")
    @IgnoreTenant
    @SaIgnore
    public RespInfo<SocialAuthUrl> getAuthUrl(@PathVariable String platform,
                                                @RequestParam(required = false) Long tenantId,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(required = false) String userClient) {
        SysSocialConfig config = resolveConnection(platform, tenantId);
        if (config == null || !EnableStatus.ENABLED.matches(config.getStatus())) {
            return RespInfo.error("该平台登录未启用");
        }

        // 服务端签发 state 并绑定授权意图，回调时以意图为权威
        SocialOAuthIntent intent = new SocialOAuthIntent();
        boolean bind = "bind".equals(action);
        intent.setAction(bind ? SocialOAuthIntent.ACTION_BIND : SocialOAuthIntent.ACTION_LOGIN);
        intent.setTenantId(config.getTenantId());
        intent.setConnectionId(config.getId());
        intent.setConnectionCode(config.getConnectionCode());
        intent.setPlatform(config.getPlatform());
        intent.setUserClient(userClient);
        if (bind) {
            Long userId = SessionHelper.getUserId();
            if (userId == null) {
                return RespInfo.error("绑定账号需要先登录");
            }
            intent.setUserId(userId);
        }
        String state = oauthStateService.issueState(intent);

        String authUrl = oauthLoginService.buildAuthorizeUrl(config, state);

        SocialAuthUrl result = SocialAuthUrl.builder()
                .platform(config.getPlatform())
                .connectionCode(config.getConnectionCode())
                .platformName(config.getPlatformName())
                .authUrl(authUrl)
                .state(state)
                .build();

        return RespInfo.success(result);
    }

    /**
     * 三方登录回调：消费 state，服务端换取身份后只返回一次性登录票据
     */
    @PostMapping("/callback")
    @IgnoreTenant
    @SaIgnore
    public RespInfo<SocialTicketResponse> callback(@RequestBody SocialLoginRequest request) {
        SocialOAuthIntent intent = oauthStateService.consumeState(request.getState());
        if (!SocialOAuthIntent.ACTION_LOGIN.equals(intent.getAction())) {
            return RespInfo.error("该授权凭据不适用于登录，请通过绑定入口完成操作");
        }

        SysSocialConfig config = socialConfigService.selectConfigById(intent.getConnectionId());
        if (config == null || !EnableStatus.ENABLED.matches(config.getStatus())) {
            return RespInfo.error("该平台登录未启用");
        }

        VerifiedSocialIdentity identity = oauthLoginService.exchange(config, request.getCode(), request.getState());

        LoginClientContext client = new LoginClientContext(config.getTenantId(),
                StrUtil.blankToDefault(request.getUserClient(), intent.getUserClient()));
        String ticket = oauthStateService.issueLoginTicket(identity, client);
        log.info("三方登录回调换票成功: connectionId={}, platform={}", config.getId(), config.getPlatform());

        return RespInfo.success(SocialTicketResponse.builder()
                .socialTicket(ticket)
                .connectionCode(config.getConnectionCode())
                .platform(config.getPlatform())
                .tenantId(config.getTenantId())
                .expiresIn(SocialOAuthStateService.TICKET_TTL_SECONDS)
                .build());
    }

    /**
     * 解析连接：优先连接编码，回退平台编码（要求平台下唯一启用连接）
     */
    private SysSocialConfig resolveConnection(String codeOrPlatform, Long tenantId) {
        try {
            SysSocialConfig byCode = socialConfigService.selectConnectionByCode(codeOrPlatform);
            if (byCode != null) {
                // 声明了租户时必须与连接归属一致，防止跨租户借用连接编码
                if (tenantId != null && !tenantId.equals(byCode.getTenantId())) {
                    return null;
                }
                return byCode;
            }
        } catch (BusinessException e) {
            log.debug("按连接编码解析失败，回退平台编码: {}", codeOrPlatform);
        }
        return socialConfigService.selectByPlatformAndTenant(codeOrPlatform, tenantId);
    }
}
