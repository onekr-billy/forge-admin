package com.mdframe.forge.starter.auth.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * SSO 票据申请请求
 */
@Data
public class SsoTicketRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标客户端编码
     */
    private String targetClient;

    /**
     * 目标系统跳转路径
     */
    private String redirectPath;
}
