package com.mdframe.forge.plugin.system.auth;

import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.plugin.system.entity.SysClient;
import com.mdframe.forge.plugin.system.service.IClientService;
import com.mdframe.forge.starter.config.config.LoginConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一解析登录验证码配置：客户端配置为空时继承全局登录配置。
 */
@Component
@RequiredArgsConstructor
public class LoginCaptchaPolicyResolver {

    public static final String DEFAULT_USER_CLIENT = "pc";
    public static final String DEFAULT_CAPTCHA_TYPE = "graphical";
    public static final String SOURCE_GLOBAL = "GLOBAL";
    public static final String SOURCE_CLIENT = "CLIENT";

    private final ConfigManagerService configManagerService;
    private final IClientService clientService;

    public LoginCaptchaPolicy resolve(String userClient) {
        LoginConfig loginConfig = configManagerService.getLoginConfig();
        if (loginConfig == null) {
            loginConfig = new LoginConfig();
        }

        String resolvedUserClient = StrUtil.blankToDefault(userClient, DEFAULT_USER_CLIENT);
        String globalCaptchaType = StrUtil.blankToDefault(loginConfig.getCaptchaType(), DEFAULT_CAPTCHA_TYPE);
        String clientCaptchaType = null;

        SysClient client = clientService.getByCode(resolvedUserClient);
        if (client != null && StrUtil.isNotBlank(client.getCaptchaType())) {
            clientCaptchaType = client.getCaptchaType();
        }

        boolean useClientCaptchaType = StrUtil.isNotBlank(clientCaptchaType);
        return LoginCaptchaPolicy.builder()
                .enableCaptcha(!Boolean.FALSE.equals(loginConfig.getEnableCaptcha()))
                .captchaType(useClientCaptchaType ? clientCaptchaType : globalCaptchaType)
                .userClient(resolvedUserClient)
                .captchaTypeSource(useClientCaptchaType ? SOURCE_CLIENT : SOURCE_GLOBAL)
                .globalCaptchaType(globalCaptchaType)
                .clientCaptchaType(clientCaptchaType)
                .groupQrcodeEnabled(Boolean.TRUE.equals(loginConfig.getGroupQrcodeEnabled()))
                .build();
    }
}
