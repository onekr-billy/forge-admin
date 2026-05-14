package com.mdframe.forge.starter.auth.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * SSO 票据缓存载荷
 */
@Data
public class SsoTicketPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 票据值
     */
    private String ticket;

    /**
     * 来源会话 Token
     */
    private String sourceToken;

    /**
     * 来源客户端编码
     */
    private String sourceClient;

    /**
     * 目标客户端编码
     */
    private String targetClient;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 目标系统跳转路径
     */
    private String redirectPath;

    /**
     * 签发时间
     */
    private Long issuedAt;
}
