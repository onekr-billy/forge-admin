package com.mdframe.forge.starter.social.controller;

import com.mdframe.forge.starter.collaboration.model.VerifiedSocialIdentity;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.social.domain.dto.SocialBindRequest;
import com.mdframe.forge.starter.social.domain.dto.SocialOAuthIntent;
import com.mdframe.forge.starter.social.domain.dto.SocialPlatformInfo;
import com.mdframe.forge.starter.social.domain.dto.UserSocialBinding;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.domain.entity.SysUserSocial;
import com.mdframe.forge.starter.social.service.ISocialConfigService;
import com.mdframe.forge.starter.social.service.ISocialUserService;
import com.mdframe.forge.starter.social.service.SocialOAuthLoginService;
import com.mdframe.forge.starter.social.service.SocialOAuthStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import com.mdframe.forge.starter.core.enums.EnableStatus;

@Slf4j
@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialUserController {

    private final ISocialUserService socialUserService;
    private final ISocialConfigService socialConfigService;
    private final SocialOAuthStateService oauthStateService;
    private final SocialOAuthLoginService oauthLoginService;

    @GetMapping("/user/bindings")
    public RespInfo<List<UserSocialBinding>> getUserBindings(@RequestParam(required = false) Long tenantId) {
        Long userId = SessionHelper.getUserId();
        Long currentTenantId = tenantId != null ? tenantId : SessionHelper.getTenantId();

        List<SocialPlatformInfo> platforms = socialConfigService.selectEnabledPlatforms(currentTenantId);
        List<SysUserSocial> bindings = socialUserService.selectByUserId(userId);

        List<UserSocialBinding> result = platforms.stream().map(p -> {
            SysUserSocial binding = bindings.stream()
                    .filter(b -> b.getPlatform().equalsIgnoreCase(p.getPlatform()))
                    .findFirst().orElse(null);

            return UserSocialBinding.builder()
                    .platform(p.getPlatform())
                    .platformName(p.getPlatformName())
                    .platformLogo(p.getPlatformLogo())
                    .bound(binding != null)
                    .nickname(binding != null ? binding.getNickname() : null)
                    .email(binding != null ? binding.getEmail() : null)
                    .avatar(binding != null ? binding.getAvatar() : null)
                    .bindTime(binding != null ? binding.getBindTime() : null)
                    .build();
        }).collect(Collectors.toList());

        return RespInfo.success(result);
    }

    @PostMapping("/bind")
    public RespInfo<Void> bind(@RequestBody SocialBindRequest request) {
        Long userId = SessionHelper.getUserId();
        Long tenantId = SessionHelper.getTenantId();

        // 消费服务端签发的 state，绑定动作与发起用户必须一致
        SocialOAuthIntent intent = oauthStateService.consumeState(request.getState());
        if (!SocialOAuthIntent.ACTION_BIND.equals(intent.getAction())) {
            return RespInfo.error("该授权凭据不适用于绑定，请重新发起绑定");
        }
        if (intent.getUserId() == null || !intent.getUserId().equals(userId)) {
            return RespInfo.error("绑定发起用户与当前用户不一致，请重新发起绑定");
        }

        SysSocialConfig config = socialConfigService.selectConfigById(intent.getConnectionId());
        if (config == null || !EnableStatus.ENABLED.matches(config.getStatus())) {
            return RespInfo.error("该平台登录未启用");
        }
        if (tenantId != null && !tenantId.equals(config.getTenantId())) {
            return RespInfo.error("连接归属租户与当前租户不一致");
        }

        VerifiedSocialIdentity identity = oauthLoginService.exchange(config, request.getCode(), request.getState());

        boolean bound = socialUserService.bindVerifiedIdentity(identity, userId);
        if (!bound) {
            return RespInfo.error("该平台已绑定，请勿重复操作");
        }

        log.info("绑定三方账号成功: userId={}, connectionId={}, platform={}", userId, config.getId(), config.getPlatform());
        return RespInfo.success();
    }

    @DeleteMapping("/unbind/{platform}")
    public RespInfo<Void> unbind(@PathVariable String platform) {
        Long userId = SessionHelper.getUserId();
        socialUserService.unbindSocialUser(userId, platform);
        log.info("解绑三方账号成功: userId={}, platform={}", userId, platform);
        return RespInfo.success();
    }
}
