package com.mdframe.forge.starter.auth.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 找回密码请求。channel 只能是 sms 或 email。
 */
@Data
public class ResetPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** sms / email */
    private String channel;

    /** 手机号或邮箱 */
    private String account;

    /** 通道验证码 */
    private String code;

    /** 新密码 */
    private String newPassword;

    /** 可选租户，多租户登录页传入 */
    private Long tenantId;
}
