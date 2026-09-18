package com.mdframe.forge.plugin.system.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 登录验证码最终生效策略。
 */
@Data
@Builder
public class LoginCaptchaPolicy {

    private Boolean enableCaptcha;

    private String captchaType;

    private String userClient;

    private String captchaTypeSource;

    private String globalCaptchaType;

    private String clientCaptchaType;

    /**
     * 是否启用群二维码引流验证码
     */
    private Boolean groupQrcodeEnabled;
}
