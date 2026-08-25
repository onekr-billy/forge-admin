package com.mdframe.forge.plugin.capability.controlplane.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.controlplane.dto.CapabilityClientCreateDTO;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.controlplane.security.CapabilityClientPrincipal;
import com.mdframe.forge.plugin.capability.controlplane.security.CapabilityClientActorMode;
import com.mdframe.forge.plugin.capability.controlplane.security.CapabilityClientSecretCodec;
import com.mdframe.forge.plugin.capability.controlplane.security.IssuedClientSecret;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityClientSecretVO;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityClientVO;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilitySigningKeyVO;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CapabilityClientService {

    private static final String DUMMY_HASH = "0".repeat(64);
    private static final String AUTH_MODE_OAUTH = "OAUTH";
    private static final String AUTH_MODE_SIGNATURE = "SIGNATURE";
    private static final int SIGNING_KEY_BYTES = 32;

    private final AiCapabilityClientMapper clientMapper;
    private final CapabilityClientSecretCodec secretCodec;
    private final PersistentCryptoService persistentCryptoService;
    private final Clock capabilityClock;
    private final SecureRandom signingKeyRandom = new SecureRandom();

    public Page<CapabilityClientVO> page(
            Long tenantId,
            PageQuery pageQuery,
            String keyword,
            String status) {
        Page<AiCapabilityClient> source = clientMapper.selectPage(
                pageQuery.toPage(), requireTenant(tenantId), keyword, status);
        Page<CapabilityClientVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(CapabilityClientVO::from).toList());
        return result;
    }

    public List<CapabilityClientVO> listGrantOptions(Long tenantId) {
        return clientMapper.selectGrantOptions(requireTenant(tenantId)).stream()
                .map(CapabilityClientVO::from)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public CapabilityClientSecretVO create(Long tenantId, CapabilityClientCreateDTO dto) {
        Long safeTenantId = requireTenant(tenantId);
        if (clientMapper.selectByCode(safeTenantId, dto.clientCode()) != null) {
            throw new BusinessException("客户端编码已存在");
        }
        CapabilityClientActorMode actorMode = normalizeActorMode(dto.actorMode());
        String authModes = normalizeAuthModes(dto.authModes());
        validateIdentityConfiguration(
                actorMode, dto.serviceUserId(), dto.activeOrgId(), authModes);
        IssuedClientSecret issued = secretCodec.issue(dto.clientCode());
        String signingKey = authModes.contains(AUTH_MODE_SIGNATURE) ? generateSigningKey() : null;
        AiCapabilityClient client = new AiCapabilityClient();
        client.setTenantId(safeTenantId);
        client.setClientCode(dto.clientCode());
        client.setClientName(dto.clientName());
        client.setKeyId(issued.keyId());
        client.setKeyPrefix(issued.keyPrefix());
        client.setKeyHash(issued.keyHash());
        client.setCredentialVersion(1);
        client.setServiceUserId(dto.serviceUserId());
        client.setActiveOrgId(dto.activeOrgId());
        client.setOauthEnabled(authModes.contains(AUTH_MODE_OAUTH) ? EnableStatus.ENABLED.getCode() : EnableStatus.DISABLED.getCode());
        client.setOauthClientType("CONFIDENTIAL");
        client.setAuthModes(authModes);
        client.setActorMode(actorMode.name());
        if (signingKey != null) {
            client.setSigningKeyCipher(persistentCryptoService.encrypt(signingKey, null));
            client.setSigningKeyVersion(1);
        }
        client.setUserAssertionEnabled(EnableStatus.DISABLED.getCode());
        client.setUserAssertionMappingMode("PREBOUND");
        client.setStatus("ENABLED");
        client.setExpiresAt(dto.expiresAt());
        client.setRemark(dto.remark());
        client.setDelFlag(0L);
        clientMapper.insert(client);
        return secretResponse(client, issued.rawSecret(), signingKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public CapabilityClientSecretVO rotate(Long tenantId, Long clientId) {
        AiCapabilityClient client = requireClient(tenantId, clientId);
        if ("REVOKED".equals(client.getStatus())) {
            throw new BusinessException("已吊销客户端不能轮换密钥");
        }
        IssuedClientSecret issued = secretCodec.issue(client.getClientCode());
        Integer currentVersion = requireCredentialVersion(client);
        if (clientMapper.rotateCredential(
                client.getTenantId(), client.getId(), currentVersion,
                issued.keyId(), issued.keyPrefix(), issued.keyHash()) == 0) {
            throw new BusinessException("客户端凭据已发生变化，请刷新后重试");
        }
        client.setKeyId(issued.keyId());
        client.setKeyPrefix(issued.keyPrefix());
        client.setCredentialVersion(currentVersion + 1);
        return secretResponse(client, issued.rawSecret(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public CapabilitySigningKeyVO rotateSigningKey(Long tenantId, Long clientId) {
        AiCapabilityClient client = requireClient(tenantId, clientId);
        if ("REVOKED".equals(client.getStatus())) {
            throw new BusinessException("已吊销客户端不能轮换签名密钥");
        }
        if (client.getAuthModes() == null || !client.getAuthModes().contains(AUTH_MODE_SIGNATURE)) {
            throw new BusinessException("客户端未启用签名认证模式");
        }
        Integer currentVersion = client.getSigningKeyVersion();
        if (currentVersion == null || currentVersion <= 0) {
            throw new BusinessException("客户端签名密钥版本无效");
        }
        String signingKey = generateSigningKey();
        if (clientMapper.rotateSigningKey(
                client.getTenantId(), client.getId(), currentVersion,
                persistentCryptoService.encrypt(signingKey, null)) == 0) {
            throw new BusinessException("客户端签名密钥已发生变化，请刷新后重试");
        }
        return new CapabilitySigningKeyVO(
                client.getId(), client.getClientCode(), signingKey, currentVersion + 1);
    }

    public CapabilityClientPrincipal authenticate(String rawSecret) {
        String keyId = secretCodec.extractKeyId(rawSecret);
        AiCapabilityClient client = keyId == null ? null : clientMapper.selectCredentialByKeyId(keyId);
        boolean matches = secretCodec.matches(
                rawSecret,
                client == null ? DUMMY_HASH : client.getKeyHash());
        LocalDateTime now = LocalDateTime.now(capabilityClock);
        if (client == null || !matches || !"ENABLED".equals(client.getStatus())
                || (client.getExpiresAt() != null && !client.getExpiresAt().isAfter(now))) {
            throw new BusinessException("客户端凭据无效或已失效");
        }
        Integer credentialVersion = requireCredentialVersion(client);
        if (clientMapper.touchLastUsed(
                client.getTenantId(), client.getId(), credentialVersion, client.getKeyHash(), now) == 0) {
            throw new BusinessException("客户端凭据无效或已失效");
        }
        return new CapabilityClientPrincipal(
                client.getId(), client.getClientCode(), client.getTenantId(),
                client.getServiceUserId(), client.getActiveOrgId(), credentialVersion);
    }

    public void revoke(Long tenantId, Long clientId) {
        AiCapabilityClient client = requireClient(tenantId, clientId);
        if ("REVOKED".equals(client.getStatus())) {
            return;
        }
        Integer credentialVersion = requireCredentialVersion(client);
        if (clientMapper.revokeCredential(
                client.getTenantId(), client.getId(), credentialVersion) == 0) {
            throw new BusinessException("客户端凭据已发生变化，请刷新后重试");
        }
    }

    public AiCapabilityClient requireClient(Long tenantId, Long clientId) {
        AiCapabilityClient client = clientMapper.selectTenantById(requireTenant(tenantId), clientId);
        if (client == null) {
            throw new BusinessException("客户端不存在或无权访问");
        }
        return client;
    }

    private CapabilityClientSecretVO secretResponse(
            AiCapabilityClient client, String rawSecret, String signingKey) {
        return new CapabilityClientSecretVO(
                client.getId(), client.getClientCode(), client.getKeyPrefix(),
                rawSecret, client.getCredentialVersion(),
                signingKey, signingKey == null ? null : client.getSigningKeyVersion());
    }

    private String normalizeAuthModes(String authModes) {
        if (authModes == null || authModes.isBlank()) {
            return AUTH_MODE_OAUTH;
        }
        Set<String> modes = new LinkedHashSet<>();
        for (String mode : authModes.split(",")) {
            String normalized = mode.trim().toUpperCase();
            if (normalized.isEmpty()) {
                continue;
            }
            if (!AUTH_MODE_OAUTH.equals(normalized) && !AUTH_MODE_SIGNATURE.equals(normalized)) {
                throw new BusinessException("认证模式只支持 OAUTH 和 SIGNATURE");
            }
            modes.add(normalized);
        }
        if (modes.isEmpty()) {
            return AUTH_MODE_OAUTH;
        }
        return String.join(",", modes);
    }

    private CapabilityClientActorMode normalizeActorMode(String actorMode) {
        if (actorMode == null || actorMode.isBlank()) {
            return CapabilityClientActorMode.USER_DELEGATION;
        }
        try {
            return CapabilityClientActorMode.valueOf(actorMode.trim().toUpperCase());
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException("客户端主体模式无效");
        }
    }

    private void validateIdentityConfiguration(
            CapabilityClientActorMode actorMode,
            Long serviceUserId,
            Long activeOrgId,
            String authModes) {
        if (actorMode == CapabilityClientActorMode.USER_DELEGATION) {
            if (serviceUserId != null || activeOrgId != null) {
                throw new BusinessException("用户委托客户端不需要绑定服务账号和组织");
            }
            if (!AUTH_MODE_OAUTH.equals(authModes)) {
                throw new BusinessException("用户委托客户端只支持 OAUTH 认证模式");
            }
            return;
        }
        requireServiceIdentity(serviceUserId, activeOrgId);
        if (actorMode == CapabilityClientActorMode.HYBRID
                && !authModes.contains(AUTH_MODE_OAUTH)) {
            throw new BusinessException("混合模式客户端必须启用 OAUTH");
        }
    }

    private String generateSigningKey() {
        byte[] keyBytes = new byte[SIGNING_KEY_BYTES];
        signingKeyRandom.nextBytes(keyBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
    }

    private Long requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("未获取到有效租户上下文");
        }
        return tenantId;
    }

    private void requireServiceIdentity(Long serviceUserId, Long activeOrgId) {
        if (!hasValidServiceIdentity(serviceUserId, activeOrgId)) {
            throw new BusinessException("机器客户端必须绑定有效服务账号和活动组织");
        }
    }

    private boolean hasValidServiceIdentity(Long serviceUserId, Long activeOrgId) {
        return serviceUserId != null && serviceUserId > 0
                && activeOrgId != null && activeOrgId > 0;
    }

    private Integer requireCredentialVersion(AiCapabilityClient client) {
        if (client.getCredentialVersion() == null || client.getCredentialVersion() <= 0) {
            throw new BusinessException("客户端凭据版本无效");
        }
        return client.getCredentialVersion();
    }
}
