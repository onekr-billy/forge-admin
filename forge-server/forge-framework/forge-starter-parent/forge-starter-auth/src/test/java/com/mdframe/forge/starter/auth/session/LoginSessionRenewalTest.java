package com.mdframe.forge.starter.auth.session;

import cn.dev33.satoken.dao.SaTokenDao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginSessionRenewalTest {

    @Test
    void shouldRenewWhenRemainingAtMostHalfOfConfiguredTimeout() {
        assertThat(LoginSessionRenewal.shouldRenew(900, 1800)).isTrue();
        assertThat(LoginSessionRenewal.shouldRenew(0, 1800)).isTrue();
        assertThat(LoginSessionRenewal.shouldRenew(3600, 7200)).isTrue();
    }

    @Test
    void shouldSkipRenewWhenRemainingAboveHalf() {
        assertThat(LoginSessionRenewal.shouldRenew(901, 1800)).isFalse();
        assertThat(LoginSessionRenewal.shouldRenew(1800, 1800)).isFalse();
    }

    @Test
    void shouldSkipRenewWhenTimeoutNeverExpires() {
        assertThat(LoginSessionRenewal.shouldRenew(60, SaTokenDao.NEVER_EXPIRE)).isFalse();
        assertThat(LoginSessionRenewal.shouldRenew(SaTokenDao.NEVER_EXPIRE, 1800)).isFalse();
        assertThat(LoginSessionRenewal.shouldRenew(60, 0)).isFalse();
    }
}
