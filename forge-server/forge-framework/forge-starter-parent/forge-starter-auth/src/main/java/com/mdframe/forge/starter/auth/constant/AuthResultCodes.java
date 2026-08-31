package com.mdframe.forge.starter.auth.constant;

/**
 * 认证接口业务码。
 */
public final class AuthResultCodes {

    private AuthResultCodes() {
    }

    /**
     * 密码已校验通过，但账号可进入多个工作区，需要调用方补齐 tenantId 后再登录。
     */
    public static final int TENANT_SELECTION_REQUIRED = 4091;
}
