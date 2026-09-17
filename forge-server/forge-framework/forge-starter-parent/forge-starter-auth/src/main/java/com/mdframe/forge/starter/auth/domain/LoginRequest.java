package com.mdframe.forge.starter.auth.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求参数
 */
@Data
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 验证码
     */
    private String code;

    /**
     * 验证码key（用于从缓存中获取验证码）
     */
    private String codeKey;

    /**
     * 租户ID（可选，用于多租户登录）
     */
    private Long tenantId;

    /**
     * 认证方式（password-用户名密码，password_captcha-用户名密码验证码，phone_captcha-手机号验证码）
     * 默认为password
     */
    private String authType = "password";

    /**
     * 用户客户端类型（pc, app, h5, wechat）
     * 用于区分不同客户端的认证策略
     */
    private String userClient;

    /**
     * 客户端AppId（可选，用于客户端验证）
     */
    private String appId;
    
    /**
     * 客户端秘钥
     */
    private String appSecret;

    /**
     * 手机号（手机号登录时使用）
     */
    private String phone;

    /**
     * 前端选择的验证码类型（可选，用于群二维码等可选验证码切换）
     * 当用户在登录页选择了不同于默认配置的验证码方式时，前端传入此字段告知后端
     * 可选值：graphical / slider / sms / group
     */
    private String captchaType;

    /**
     * 邮箱（邮箱登录时使用）
     */
    private String email;

    /**
     * 第三方授权码（OAuth2、微信登录时使用）
     */
    private String authCode;

    /**
     * 一次性三方登录票据（OAuth2 登录唯一入参，由 /social/callback 签发）
     */
    private String socialTicket;

    /**
     * 连接编码（OAuth2 登录时携带，用于审计与前端展示）
     */
    private String connectionCode;

    /**
     * 三方平台类型
     *
     * @deprecated 登录身份改由 socialTicket 服务端取回，禁止信任前端自报
     */
    @Deprecated
    private String socialPlatform;

    /**
     * 三方用户唯一标识
     *
     * @deprecated 登录身份改由 socialTicket 服务端取回，禁止信任前端自报
     */
    @Deprecated
    private String socialUuid;

    /**
     * 三方用户昵称
     *
     * @deprecated 同 socialUuid，改由票据取回
     */
    @Deprecated
    private String socialNickname;

    /**
     * 三方用户头像
     *
     * @deprecated 同 socialUuid，改由票据取回
     */
    @Deprecated
    private String socialAvatar;

    /**
     * 三方用户邮箱
     *
     * @deprecated 同 socialUuid，改由票据取回
     */
    @Deprecated
    private String socialEmail;

    /**
     * 认证方式常量
     */
    public static final String AUTH_TYPE_PASSWORD = "password";
    public static final String AUTH_TYPE_PASSWORD_CAPTCHA = "password_captcha";
    public static final String AUTH_TYPE_PHONE_CAPTCHA = "phone_captcha";
    public static final String AUTH_TYPE_WECHAT = "wechat";
    public static final String AUTH_TYPE_EMAIL_CAPTCHA = "email_captcha";
    public static final String AUTH_TYPE_OAUTH2 = "oauth2";
    public static final String AUTH_TYPE_SOCIAL = "social";
}
