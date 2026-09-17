package com.mdframe.forge.starter.auth.session;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Sa-Token 1.38 的 autoRenew 只续 last-active，不续 token Redis TTL。
 * 活跃请求在剩余寿命不超过配置 timeout 一半时重置 TTL，形成滑动会话。
 */
@Slf4j
public final class LoginSessionRenewal {

    private LoginSessionRenewal() {
    }

    public static void renewIfHalfwayExpired() {
        try {
            long remainingTimeout = StpUtil.getTokenTimeout();
            long configuredTimeout = SaManager.getConfig().getTimeout();
            if (!shouldRenew(remainingTimeout, configuredTimeout)) {
                return;
            }
            StpUtil.renewTimeout(configuredTimeout);
        } catch (Exception exception) {
            log.warn("登录会话滑动续期失败: {}", exception.getMessage());
        }
    }

    static boolean shouldRenew(long remainingTimeout, long configuredTimeout) {
        if (configuredTimeout <= 0 || configuredTimeout == SaTokenDao.NEVER_EXPIRE) {
            return false;
        }
        if (remainingTimeout < 0 || remainingTimeout == SaTokenDao.NEVER_EXPIRE) {
            return false;
        }
        return remainingTimeout <= configuredTimeout / 2;
    }
}
