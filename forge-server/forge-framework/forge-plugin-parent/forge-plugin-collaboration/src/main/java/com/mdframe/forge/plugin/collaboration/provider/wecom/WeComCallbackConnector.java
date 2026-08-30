package com.mdframe.forge.plugin.collaboration.provider.wecom;

import com.mdframe.forge.starter.collaboration.connector.CallbackConnector;
import com.mdframe.forge.starter.collaboration.model.CollaborationExecutionContext;
import com.mdframe.forge.starter.social.domain.entity.SysSocialAppConfig;
import com.mdframe.forge.starter.social.enums.SocialPlatform;
import com.mdframe.forge.starter.social.service.ISocialAppConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 企业微信回调 Connector。
 * <p>
 * URL 验证与事件解密统一委托 {@link WeComCallbackCrypto}；按执行上下文定位应用回调凭据，
 * 凭据仅在单次处理内短暂持有并在使用后清零，不做任何业务处理。
 */
@Component
@RequiredArgsConstructor
public class WeComCallbackConnector implements CallbackConnector {

    private final WeComCallbackCrypto callbackCrypto;
    private final ISocialAppConfigService socialAppConfigService;

    @Override
    public String platform() {
        return SocialPlatform.WECHAT_ENTERPRISE.getCode();
    }

    @Override
    public String verifyUrl(CollaborationExecutionContext context, Map<String, String> queryParams) {
        WeComCallbackRequest request = new WeComCallbackRequest(
                queryParams.get("msg_signature"),
                queryParams.get("timestamp"),
                queryParams.get("nonce"),
                queryParams.get("echostr"));
        return verify(context, request).plaintext();
    }

    @Override
    public String verifyAndDecrypt(CollaborationExecutionContext context,
                                   Map<String, String> queryParams, String body) {
        WeComCallbackRequest request = new WeComCallbackRequest(
                queryParams.get("msg_signature"),
                queryParams.get("timestamp"),
                queryParams.get("nonce"),
                callbackCrypto.extractEncrypt(body));
        return verify(context, request).plaintext();
    }

    private CallbackVerificationResult verify(CollaborationExecutionContext context, WeComCallbackRequest request) {
        SysSocialAppConfig app = resolveApp(context);
        char[] token = socialAppConfigService.decryptCallbackToken(app);
        char[] aesKey = socialAppConfigService.decryptEncodingAesKey(app);
        try {
            if (token == null || token.length == 0 || aesKey == null || aesKey.length == 0) {
                throw new WeComCallbackException("回调凭据未配置");
            }
            CallbackCredential credential = new CallbackCredential(
                    new String(token), new String(aesKey), context.enterpriseId());
            return callbackCrypto.verifyAndDecrypt(request, credential);
        } finally {
            if (token != null) {
                Arrays.fill(token, '\0');
            }
            if (aesKey != null) {
                Arrays.fill(aesKey, '\0');
            }
        }
    }

    private SysSocialAppConfig resolveApp(CollaborationExecutionContext context) {
        return socialAppConfigService.listApps(context.tenantId(), context.connectionId()).stream()
                .filter(app -> app.getId() != null && app.getId().equals(context.appId()))
                .filter(app -> EnableStatus.ENABLED.matches(app.getStatus()))
                .findFirst()
                .orElseThrow(() -> new WeComCallbackException("回调应用不存在或未启用"));
    }
}
