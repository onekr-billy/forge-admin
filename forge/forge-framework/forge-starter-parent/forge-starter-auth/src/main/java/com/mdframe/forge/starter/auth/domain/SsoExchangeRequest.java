package com.mdframe.forge.starter.auth.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * SSO 票据交换请求
 */
@Data
public class SsoExchangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 一次性票据
     */
    private String ticket;
}
