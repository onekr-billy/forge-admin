package com.mdframe.forge.starter.auth.email;

import java.time.Duration;

/**
 * 邮箱验证码发送通道。
 */
public interface EmailCaptchaSender {

    boolean sendVerificationCode(String email, String code, Duration duration);
}
