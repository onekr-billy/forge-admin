package com.mdframe.forge.starter.social.service;

import com.mdframe.forge.starter.collaboration.CollaborationCapability;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.domain.entity.SysSocialConfig;
import com.mdframe.forge.starter.social.factory.SocialAuthRequestFactory;
import me.zhyd.oauth.request.AuthRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth 登录应用参数与凭据解析测试。
 */
@ExtendWith(MockitoExtension.class)
class SocialOAuthLoginServiceTest {

    @Mock
    private SocialAuthRequestFactory authRequestFactory;

    @Mock
    private ISocialAppConfigService appConfigService;

    @Mock
    private AuthRequest authRequest;

    @InjectMocks
    private SocialOAuthLoginService service;

    @Test
    void buildAuthorizeUrlUsesDecryptedLoginAppSecretWhenLegacySecretIsBlank() {
        SysSocialConfig connection = enabledConnection();
        SysSocialAppConfig loginApp = enabledLoginApp();
        char[] decryptedSecret = "application-secret".toCharArray();
        String state = "WECHAT_ENTERPRISE_state";
        String authorizeUrl = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

        when(appConfigService.requireEnabledApp(1L, 10L, CollaborationCapability.LOGIN))
                .thenReturn(loginApp);
        when(appConfigService.decryptAppSecret(loginApp)).thenReturn(decryptedSecret);
        when(authRequestFactory.createRequest(same(connection), same(loginApp), same(decryptedSecret)))
                .thenReturn(authRequest);
        when(authRequest.authorize(state)).thenReturn(authorizeUrl);

        assertThat(service.buildAuthorizeUrl(connection, state)).isEqualTo(authorizeUrl);
        verify(authRequestFactory).createRequest(same(connection), same(loginApp), same(decryptedSecret));
        assertThat(decryptedSecret).containsOnly('\0');
    }

    private SysSocialConfig enabledConnection() {
        SysSocialConfig connection = new SysSocialConfig();
        connection.setId(10L);
        connection.setTenantId(1L);
        connection.setPlatform("WECHAT_ENTERPRISE");
        connection.setStatus(1);
        connection.setClientSecret(null);
        return connection;
    }

    private SysSocialAppConfig enabledLoginApp() {
        SysSocialAppConfig app = new SysSocialAppConfig();
        app.setId(100L);
        app.setTenantId(1L);
        app.setConnectionId(10L);
        app.setStatus(1);
        app.setSecretCipher("FPC1:AES_GCM:k1:synthetic-ciphertext");
        return app;
    }
}
