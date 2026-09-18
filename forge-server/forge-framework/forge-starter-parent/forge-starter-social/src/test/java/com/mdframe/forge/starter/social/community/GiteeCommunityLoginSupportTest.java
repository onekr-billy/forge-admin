package com.mdframe.forge.starter.social.community;

import com.mdframe.forge.starter.config.config.LoginConfig;
import com.mdframe.forge.starter.config.service.ConfigManagerService;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GiteeCommunityLoginSupportTest {

    @Test
    void disabledDoesNotApplyToGitee() {
        GiteeCommunityLoginSupport support = support(loginConfig(false));
        assertThat(support.isEnabled()).isFalse();
        assertThat(support.appliesTo(giteeConnection())).isFalse();
    }

    @Test
    void enabledAppliesToGiteeOnly() {
        GiteeCommunityLoginSupport support = support(loginConfig(true));
        assertThat(support.appliesTo(giteeConnection())).isTrue();
        SysSocialConfig wecom = new SysSocialConfig();
        wecom.setPlatform("WECHAT_ENTERPRISE");
        assertThat(support.appliesTo(wecom)).isFalse();
    }

    private GiteeCommunityLoginSupport support(LoginConfig loginConfig) {
        ConfigManagerService configManagerService = mock(ConfigManagerService.class);
        when(configManagerService.getLoginConfig()).thenReturn(loginConfig);
        return new GiteeCommunityLoginSupport(configManagerService);
    }

    private LoginConfig loginConfig(boolean enabled) {
        LoginConfig config = new LoginConfig();
        config.setGiteeCommunityEnabled(enabled);
        return config;
    }

    private SysSocialConfig giteeConnection() {
        SysSocialConfig connection = new SysSocialConfig();
        connection.setPlatform("GITEE");
        connection.setConnectionCode("any");
        return connection;
    }
}
