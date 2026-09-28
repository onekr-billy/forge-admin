package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.keyexchange.RsaKeyPairHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 登录密码统一解码器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginPasswordDecoder {

    private final LoginPasswordEncryptionPolicy encryptionPolicy;
    private final ObjectProvider<RsaKeyPairHolder> rsaKeyPairHolderProvider;

    /**
     * 按服务端登录策略解析密码。
     *
     * <p>RSA 开启时禁止把解密失败的密文降级成明文密码。</p>
     */
    public String decode(String password) {
        if (!encryptionPolicy.isEnabled()) {
            return password;
        }

        RsaKeyPairHolder rsaKeyPairHolder = rsaKeyPairHolderProvider.getIfAvailable();
        if (rsaKeyPairHolder == null) {
            // 服务端未就绪：客户端应刷新后重试，不是系统崩溃
            throw new BusinessException(503, "密码加密服务不可用，请稍后重试");
        }

        try {
            String decryptedPassword = rsaKeyPairHolder.decryptByPrivateKey(password);
            if (decryptedPassword != null && !decryptedPassword.isBlank()) {
                return decryptedPassword;
            }
        } catch (Exception exception) {
            log.warn("登录密码 RSA 解密失败，已拒绝明文降级: exceptionType={}",
                    exception.getClass().getSimpleName());
            // 常见于：服务端重启后密钥轮换，或前端缓存了旧公钥
            throw new BusinessException(400, "密码加密校验失败，请刷新后重试");
        }

        throw new BusinessException(400, "密码加密校验失败，请刷新后重试");
    }
}
