package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalSystem;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalSecretService {

    private static final String VERSION_PREFIX = "FPC1:";

    private final PersistentCryptoService persistentCryptoService;

    public ExternalSystem prepareForPersistence(ExternalSystem incoming, ExternalSystem existing) {
        ExternalSystem target = copy(incoming);
        boolean basic = "basic".equalsIgnoreCase(target.getAuthType());
        boolean token = "token".equalsIgnoreCase(target.getAuthType())
                || "BearerToken".equalsIgnoreCase(target.getAuthType());
        boolean oauth2 = "oauth2".equalsIgnoreCase(target.getAuthType());
        boolean apiKey = "api_key".equalsIgnoreCase(target.getAuthType());
        boolean custom = "custom".equalsIgnoreCase(target.getAuthType());

        target.setBasicPassword(resolveRequiredForWrite(basic, target.getBasicPassword(),
                existingValue(existing, ExternalSystem::getBasicPassword), "Basic密码不能为空"));
        target.setTokenValue(resolveRequiredForWrite(token, target.getTokenValue(),
                existingValue(existing, ExternalSystem::getTokenValue), "Token值不能为空"));
        target.setOauth2ClientSecret(resolveRequiredForWrite(oauth2, target.getOauth2ClientSecret(),
                existingValue(existing, ExternalSystem::getOauth2ClientSecret), "OAuth2 Client Secret不能为空"));
        target.setApiKeyValue(resolveRequiredForWrite(apiKey, target.getApiKeyValue(),
                existingValue(existing, ExternalSystem::getApiKeyValue), "API Key值不能为空"));
        target.setCustomAuthConfig(resolveForWrite(custom, normalizeCustomInput(target.getCustomAuthConfig()),
                existingValue(existing, ExternalSystem::getCustomAuthConfig)));
        target.setProxyPassword(resolveForWrite(Boolean.TRUE.equals(target.getProxyEnabled()), target.getProxyPassword(),
                existingValue(existing, ExternalSystem::getProxyPassword)));
        return target;
    }

    public ExternalSystem forRuntime(ExternalSystem stored) {
        if (stored == null) {
            return null;
        }
        ExternalSystem runtime = copy(stored);
        decryptField(runtime, ExternalSystem::getBasicPassword, ExternalSystem::setBasicPassword, "basicPassword");
        decryptField(runtime, ExternalSystem::getTokenValue, ExternalSystem::setTokenValue, "tokenValue");
        decryptField(runtime, ExternalSystem::getOauth2ClientSecret, ExternalSystem::setOauth2ClientSecret, "oauth2ClientSecret");
        decryptField(runtime, ExternalSystem::getApiKeyValue, ExternalSystem::setApiKeyValue, "apiKeyValue");
        decryptField(runtime, ExternalSystem::getCustomAuthConfig, ExternalSystem::setCustomAuthConfig, "customAuthConfig");
        decryptField(runtime, ExternalSystem::getProxyPassword, ExternalSystem::setProxyPassword, "proxyPassword");
        return runtime;
    }

    public ExternalSystem forManagement(ExternalSystem stored) {
        if (stored == null) {
            return null;
        }
        ExternalSystem safe = copy(stored);
        maskField(safe, ExternalSystem::getBasicPassword, ExternalSystem::setBasicPassword);
        maskField(safe, ExternalSystem::getTokenValue, ExternalSystem::setTokenValue);
        maskField(safe, ExternalSystem::getOauth2ClientSecret, ExternalSystem::setOauth2ClientSecret);
        maskField(safe, ExternalSystem::getApiKeyValue, ExternalSystem::setApiKeyValue);
        maskField(safe, ExternalSystem::getCustomAuthConfig, ExternalSystem::setCustomAuthConfig);
        maskField(safe, ExternalSystem::getProxyPassword, ExternalSystem::setProxyPassword);
        return safe;
    }

    private String resolveForWrite(boolean active, String incoming, String existing) {
        if (!active) {
            return null;
        }
        if (!hasText(incoming) || ExternalSensitiveDataMasker.MASK.equals(incoming)) {
            return existing;
        }
        if (incoming.startsWith(VERSION_PREFIX)) {
            throw new BusinessException("外部系统凭据不能直接提交密文");
        }
        return persistentCryptoService.encrypt(incoming, null);
    }

    private String resolveRequiredForWrite(boolean active, String incoming, String existing, String message) {
        String resolved = resolveForWrite(active, incoming, existing);
        if (active && !hasText(resolved)) {
            throw new BusinessException(message);
        }
        return resolved;
    }

    private String normalizeCustomInput(String value) {
        return "{}".equals(value) ? null : value;
    }

    private void decryptField(ExternalSystem target, Function<ExternalSystem, String> getter,
                              BiConsumer<ExternalSystem, String> setter, String fieldName) {
        String value = getter.apply(target);
        if (!hasText(value)) {
            return;
        }
        if (!value.startsWith(VERSION_PREFIX)) {
            log.warn("检测到外部系统存量明文凭据，建议执行迁移，systemId={}, field={}", target.getId(), fieldName);
            return;
        }
        try {
            setter.accept(target, persistentCryptoService.decrypt(value, null));
        } catch (Exception exception) {
            throw new BusinessException("外部系统凭据解密失败，请重新配置认证信息");
        }
    }

    private void maskField(ExternalSystem target, Function<ExternalSystem, String> getter,
                           BiConsumer<ExternalSystem, String> setter) {
        setter.accept(target, hasText(getter.apply(target)) ? ExternalSensitiveDataMasker.MASK : null);
    }

    private String existingValue(ExternalSystem existing, Function<ExternalSystem, String> getter) {
        return existing == null ? null : getter.apply(existing);
    }

    private ExternalSystem copy(ExternalSystem source) {
        ExternalSystem target = new ExternalSystem();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
