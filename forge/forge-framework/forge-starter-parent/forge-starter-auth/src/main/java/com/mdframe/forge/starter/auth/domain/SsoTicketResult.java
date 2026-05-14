package com.mdframe.forge.starter.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * SSO 票据响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoTicketResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 一次性票据
     */
    private String ticket;

    /**
     * 票据有效期（秒）
     */
    private Long expiresIn;

    /**
     * 目标客户端编码
     */
    private String targetClient;

    /**
     * 目标系统跳转路径
     */
    private String redirectPath;
}
