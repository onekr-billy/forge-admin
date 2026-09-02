package com.mdframe.forge.starter.auth.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送找回密码验证码。
 */
@Data
public class SendResetPasswordCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** sms / email */
    private String channel;

    /** 手机号或邮箱 */
    private String account;

    /** 可选租户，多租户登录页传入 */
    private Long tenantId;
}
