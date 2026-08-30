package com.mdframe.forge.plugin.capability.identity.external;

import com.mdframe.forge.plugin.capability.identity.domain.AiCapabilityExternalIdentity;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityExternalIdentityMapper;
import com.mdframe.forge.plugin.capability.identity.security.CapabilityIdentityInfrastructureException;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class ExternalIdentityMappingService {

    private final OidcExternalIdentityVerifier verifier;
    private final AiCapabilityExternalIdentityMapper identityMapper;
    private final IUserLoadService userLoadService;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public ResolvedExternalIdentity authenticate(String rawSubjectToken) {
        ExternalIdentityClaims claims = verifier.verify(rawSubjectToken);
        String issuerHash = ExternalIdentityFingerprint.sha256(claims.issuer());
        String subjectHash = ExternalIdentityFingerprint.sha256(claims.subject());
        AiCapabilityExternalIdentity mapping = identityMapper.selectActive(
                claims.tenantId(), claims.providerCode(), issuerHash, subjectHash);
        LoginUser user;
        if (mapping == null) {
            user = resolveFirstLogin(claims);
            mapping = insertMapping(claims, issuerHash, subjectHash, user.getUserId());
        }
        else {
            user = loadById(mapping.getUserId(), claims);
        }
        validateUser(user, claims.tenantId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (identityMapper.touchAuthenticated(claims.tenantId(), mapping.getId(), now) == 0) {
            throw new CapabilityIdentityInfrastructureException(
                    "外部身份映射状态已变化",
                    new IllegalStateException("external identity mapping is no longer active"));
        }
        return new ResolvedExternalIdentity(claims.providerCode(), claims.subject(), user);
    }

    /**
     * 客户端签名用户断言只接受管理员预绑定的身份，不允许首次认证自动映射。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResolvedExternalIdentity authenticatePreBound(ExternalIdentityClaims claims) {
        if (claims == null) {
            throw new BusinessException(400, "invalid_grant");
        }
        String issuerHash = ExternalIdentityFingerprint.sha256(claims.issuer());
        String subjectHash = ExternalIdentityFingerprint.sha256(claims.subject());
        AiCapabilityExternalIdentity mapping = identityMapper.selectActive(
                claims.tenantId(), claims.providerCode(), issuerHash, subjectHash);
        if (mapping == null) {
            throw new BusinessException(400, "invalid_grant");
        }
        LoginUser user = loadById(mapping.getUserId(), claims);
        validatePreBoundUser(user, claims.tenantId());
        touchAuthenticated(claims.tenantId(), mapping.getId());
        return new ResolvedExternalIdentity(claims.providerCode(), claims.subject(), user);
    }

    /**
     * 客户端签名断言默认要求预绑定。管理员为具体客户端显式启用可信手机号规则后，
     * 才允许使用验签通过的 phone_number 在当前租户内唯一匹配普通用户，并固化映射。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResolvedExternalIdentity authenticateClientAssertion(
            AiCapabilityClient client,
            ExternalIdentityClaims claims) {
        if (client == null || claims == null
                || !client.getTenantId().equals(claims.tenantId())) {
            throw new BusinessException(400, "invalid_grant");
        }
        String issuerHash = ExternalIdentityFingerprint.sha256(claims.issuer());
        String subjectHash = ExternalIdentityFingerprint.sha256(claims.subject());
        AiCapabilityExternalIdentity mapping = identityMapper.selectActive(
                claims.tenantId(), claims.providerCode(), issuerHash, subjectHash);
        if (mapping != null) {
            LoginUser user = loadById(mapping.getUserId(), claims);
            validatePreBoundUser(user, claims.tenantId());
            touchAuthenticated(claims.tenantId(), mapping.getId());
            return new ResolvedExternalIdentity(claims.providerCode(), claims.subject(), user);
        }
        if (!"VERIFIED_PHONE".equals(client.getUserAssertionMappingMode())
                || StringUtils.isBlank(claims.phone())) {
            throw new BusinessException(400, "invalid_grant");
        }
        LoginUser user;
        try {
            user = userLoadService.loadUniqueUserByVerifiedPhone(
                    claims.phone(), claims.tenantId(), claims.preferredOrganizationId());
        }
        catch (RuntimeException exception) {
            throw directoryError(exception);
        }
        validatePreBoundUser(user, claims.tenantId());
        insertMapping(claims, issuerHash, subjectHash, user.getUserId());
        return new ResolvedExternalIdentity(claims.providerCode(), claims.subject(), user);
    }

    private LoginUser resolveFirstLogin(ExternalIdentityClaims claims) {
        if (StringUtils.isBlank(claims.phone())) {
            throw new BusinessException(400, "invalid_grant");
        }
        LoginUser user;
        try {
            user = userLoadService.loadUniqueUserByVerifiedPhone(
                    claims.phone(), claims.tenantId(), claims.preferredOrganizationId());
        }
        catch (RuntimeException exception) {
            throw directoryError(exception);
        }
        validateUser(user, claims.tenantId());
        if (StringUtils.isNotBlank(claims.name())
                && !StringUtils.equals(
                StringUtils.trim(claims.name()), StringUtils.trim(user.getRealName()))) {
            throw new BusinessException(400, "invalid_grant");
        }
        return user;
    }

    private LoginUser loadById(Long userId, ExternalIdentityClaims claims) {
        try {
            return userLoadService.loadUserByUserId(
                    userId, claims.tenantId(), claims.preferredOrganizationId());
        }
        catch (RuntimeException exception) {
            throw directoryError(exception);
        }
    }

    private AiCapabilityExternalIdentity insertMapping(
            ExternalIdentityClaims claims,
            String issuerHash,
            String subjectHash,
            Long userId) {
        AiCapabilityExternalIdentity mapping = new AiCapabilityExternalIdentity();
        mapping.setTenantId(claims.tenantId());
        mapping.setProviderCode(claims.providerCode());
        mapping.setIssuerHash(issuerHash);
        mapping.setSubjectHash(subjectHash);
        mapping.setSubjectHint(ExternalIdentityFingerprint.subjectHint(claims.subject()));
        mapping.setUserId(userId);
        mapping.setStatus("ENABLED");
        mapping.setLastAuthenticatedAt(LocalDateTime.now(clock));
        mapping.setDelFlag(0L);
        try {
            identityMapper.insert(mapping);
            return mapping;
        }
        catch (DuplicateKeyException exception) {
            AiCapabilityExternalIdentity concurrent = identityMapper.selectActive(
                    claims.tenantId(), claims.providerCode(), issuerHash, subjectHash);
            if (concurrent == null || !userId.equals(concurrent.getUserId())) {
                throw new CapabilityIdentityInfrastructureException("外部身份映射发生冲突", exception);
            }
            return concurrent;
        }
    }

    private void validateUser(LoginUser user, Long tenantId) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0
                || !tenantId.equals(user.getTenantId())
                || user.getActiveOrgId() == null || user.getActiveOrgId() <= 0
                || user.getRoleIds() == null || user.getRoleIds().isEmpty()
                || !EnableStatus.ENABLED.matches(user.getUserStatus())
                || Boolean.TRUE.equals(user.getForcePasswordChange())) {
            throw new BusinessException(400, "invalid_grant");
        }
    }

    private void validatePreBoundUser(LoginUser user, Long tenantId) {
        validateUser(user, tenantId);
        if (user.isAdmin() || user.isTenantAdmin()) {
            throw new BusinessException(400, "invalid_grant");
        }
    }

    private void touchAuthenticated(Long tenantId, Long mappingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (identityMapper.touchAuthenticated(tenantId, mappingId, now) == 0) {
            throw new CapabilityIdentityInfrastructureException(
                    "外部身份映射状态已变化",
                    new IllegalStateException("external identity mapping is no longer active"));
        }
    }

    private RuntimeException directoryError(RuntimeException exception) {
        if (exception instanceof CapabilityIdentityInfrastructureException infrastructureException) {
            return infrastructureException;
        }
        Throwable current = exception;
        while (current != null) {
            if (current instanceof DataAccessException || current instanceof SQLException) {
                return new CapabilityIdentityInfrastructureException(
                        "Forge 用户目录暂不可用", exception);
            }
            current = current.getCause();
        }
        return new BusinessException(400, "invalid_grant");
    }

}
