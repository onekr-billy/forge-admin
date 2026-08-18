package com.mdframe.forge.plugin.external.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mdframe.forge.plugin.external.dto.ExternalSystemDTO;
import com.mdframe.forge.plugin.external.dto.ExternalSystemQuery;
import com.mdframe.forge.plugin.external.entity.ExternalSystem;
import com.mdframe.forge.plugin.external.service.ExternalSystemService;
import com.mdframe.forge.plugin.external.support.ExternalSensitiveDataMasker;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/external/system")
@RequiredArgsConstructor
@ApiEncrypt
@ApiDecrypt
public class ExternalSystemController {

    private final ExternalSystemService systemService;

    @GetMapping("/page")
    public RespInfo<IPage<ExternalSystem>> page(ExternalSystemQuery query) {
        return RespInfo.success(systemService.page(query));
    }

    @GetMapping("/{id}")
    public RespInfo<ExternalSystem> getById(@PathVariable Long id) {
        return RespInfo.success(systemService.getManagementById(id));
    }

    @PostMapping
    public RespInfo<Void> add(@Validated @RequestBody ExternalSystemDTO dto) {
        validateSystem(dto);
        ExternalSystem entity = convertDtoToEntity(dto);
        systemService.saveSystem(entity);
        return RespInfo.success();
    }

    @PutMapping
    public RespInfo<Void> edit(@Validated @RequestBody ExternalSystemDTO dto) {
        validateSystem(dto);
        ExternalSystem entity = convertDtoToEntity(dto);
        systemService.updateSystem(entity);
        return RespInfo.success();
    }

    @DeleteMapping("/{id}")
    public RespInfo<Void> remove(@PathVariable Long id) {
        systemService.removeById(id);
        return RespInfo.success();
    }

    @GetMapping("/list")
    public RespInfo<List<ExternalSystem>> list() {
        return RespInfo.success(systemService.listAll());
    }

    private ExternalSystem convertDtoToEntity(ExternalSystemDTO dto) {
        ExternalSystem entity = new ExternalSystem();
        entity.setId(dto.getId());
        entity.setSystemCode(dto.getSystemCode());
        entity.setSystemName(dto.getSystemName());
        entity.setSystemDesc(dto.getSystemDesc());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setAuthType(dto.getAuthType());
        entity.setBasicUsername(dto.getBasicUsername());
        entity.setBasicPassword(dto.getBasicPassword());
        entity.setTokenValue(dto.getTokenValue());
        entity.setTokenHeaderName(dto.getTokenHeaderName());
        entity.setTokenPrefix(dto.getTokenPrefix());
        entity.setOauth2TokenUrl(dto.getOauth2TokenUrl());
        entity.setOauth2ClientId(dto.getOauth2ClientId());
        entity.setOauth2ClientSecret(dto.getOauth2ClientSecret());
        entity.setOauth2GrantType(dto.getOauth2GrantType());
        entity.setOauth2Scope(dto.getOauth2Scope());
        entity.setApiKeyName(dto.getApiKeyName());
        entity.setApiKeyValue(dto.getApiKeyValue());
        entity.setApiKeyPosition(dto.getApiKeyPosition());
        entity.setCustomAuthAdapter(isCustomAuth(dto.getAuthType()) ? dto.getCustomAuthAdapter() : null);
        entity.setCustomAuthConfig(normalizeCustomAuthConfig(dto));
        entity.setTrustedInternal(Boolean.TRUE.equals(dto.getTrustedInternal()));
        entity.setProxyEnabled(dto.getProxyEnabled());
        entity.setProxyHost(dto.getProxyHost());
        entity.setProxyPort(dto.getProxyPort());
        entity.setProxyUsername(dto.getProxyUsername());
        entity.setProxyPassword(dto.getProxyPassword());
        entity.setRetryEnabled(dto.getRetryEnabled());
        entity.setRetryMaxAttempts(dto.getRetryMaxAttempts());
        entity.setRetryBackoffInterval(dto.getRetryBackoffInterval());
        entity.setConnectTimeout(dto.getConnectTimeout());
        entity.setReadTimeout(dto.getReadTimeout());
        entity.setWriteTimeout(dto.getWriteTimeout());
        entity.setSslVerifyEnabled(dto.getSslVerifyEnabled());
        entity.setRequestLoggingEnabled(dto.getRequestLoggingEnabled());
        entity.setSystemStatus(dto.getSystemStatus());
        entity.setRemark(dto.getRemark());
        return entity;
    }

    private void validateSystem(ExternalSystemDTO dto) {
        if (isBlank(dto.getSystemName())) {
            throw new BusinessException("系统名称不能为空");
        }
        if (isBlank(dto.getSystemCode())) {
            throw new BusinessException("系统编码不能为空");
        }
        if (isBlank(dto.getBaseUrl())) {
            throw new BusinessException("基础URL不能为空");
        }
        validateHttpUrl(dto.getBaseUrl(), "基础URL");
        if (isBlank(dto.getAuthType())) {
            dto.setAuthType("none");
        }
        String authType = dto.getAuthType();
        if ("basic".equalsIgnoreCase(authType)) {
            requireNotBlank(dto.getBasicUsername(), "Basic用户名不能为空");
            requireSecretOnCreate(dto.getBasicPassword(), dto.getId(), "Basic密码不能为空");
        }
        if ("token".equalsIgnoreCase(authType)) {
            requireSecretOnCreate(dto.getTokenValue(), dto.getId(), "Token值不能为空");
        }
        if ("current_token".equalsIgnoreCase(authType)) {
            if (isBlank(dto.getTokenHeaderName())) {
                dto.setTokenHeaderName("Authorization");
            }
            if (dto.getTokenPrefix() == null) {
                dto.setTokenPrefix("Bearer");
            }
        }
        if ("api_key".equalsIgnoreCase(authType)) {
            requireNotBlank(dto.getApiKeyName(), "API Key名称不能为空");
            requireSecretOnCreate(dto.getApiKeyValue(), dto.getId(), "API Key值不能为空");
            if (isBlank(dto.getApiKeyPosition())) {
                dto.setApiKeyPosition("header");
            }
        }
        if ("oauth2".equalsIgnoreCase(authType)) {
            requireNotBlank(dto.getOauth2TokenUrl(), "OAuth2 Token URL不能为空");
            validateHttpUrl(dto.getOauth2TokenUrl(), "OAuth2 Token URL");
            requireNotBlank(dto.getOauth2ClientId(), "OAuth2 Client ID不能为空");
            requireSecretOnCreate(dto.getOauth2ClientSecret(), dto.getId(), "OAuth2 Client Secret不能为空");
        }
        if (Boolean.TRUE.equals(dto.getProxyEnabled())) {
            throw new BusinessException("统一安全出站客户端暂不支持连接器代理，请使用网关出口");
        }
        if (Boolean.FALSE.equals(dto.getSslVerifyEnabled())) {
            throw new BusinessException("外部连接器禁止关闭SSL证书校验");
        }
        if (Boolean.TRUE.equals(dto.getRetryEnabled())) {
            validateRange(dto.getRetryMaxAttempts(), 1, 5, "最大重试次数");
            validateRange(dto.getRetryBackoffInterval(), 0, 5000, "重试间隔");
        }
        validateRange(dto.getConnectTimeout(), 100, 120000, "连接超时");
        validateRange(dto.getReadTimeout(), 100, 120000, "读取超时");
        validateRange(dto.getWriteTimeout(), 100, 120000, "写入超时");
    }

    private String normalizeCustomAuthConfig(ExternalSystemDTO dto) {
        if (!isCustomAuth(dto.getAuthType())) {
            return null;
        }
        if (isBlank(dto.getCustomAuthAdapter())) {
            throw new BusinessException("请选择认证适配器");
        }
        if (isBlank(dto.getCustomAuthConfig())) {
            return "{}";
        }
        if (dto.getId() != null && ExternalSensitiveDataMasker.MASK.equals(dto.getCustomAuthConfig())) {
            return ExternalSensitiveDataMasker.MASK;
        }
        try {
            JSONObject config = JSON.parseObject(dto.getCustomAuthConfig());
            if (config == null) {
                throw new BusinessException("自定义认证配置必须是JSON对象");
            }
            return config.toJSONString();
        } catch (Exception e) {
            throw new BusinessException("自定义认证配置必须是JSON对象");
        }
    }

    private boolean isCustomAuth(String authType) {
        return authType != null && "custom".equalsIgnoreCase(authType);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throw new BusinessException(message);
        }
    }

    private void requireSecretOnCreate(String value, Long id, String message) {
        if (id == null && (isBlank(value) || "******".equals(value))) {
            throw new BusinessException(message);
        }
    }

    private void validateRange(Integer value, int minimum, int maximum, String label) {
        if (value != null && (value < minimum || value > maximum)) {
            throw new BusinessException(label + "必须在" + minimum + "到" + maximum + "之间");
        }
    }

    private void validateHttpUrl(String value, String label) {
        try {
            URI uri = URI.create(value.trim());
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new BusinessException(label + "必须是有效的HTTP或HTTPS地址，且不能包含用户凭据");
        }
    }
}
