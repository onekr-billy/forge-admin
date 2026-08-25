package com.mdframe.forge.plugin.capability.identity.external;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.controlplane.security.CapabilityClientActorMode;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.domain.AiCapabilityExternalIdentity;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityExternalIdentityMapper;
import com.mdframe.forge.plugin.capability.identity.mapper.model.ClientUserAssertionMappingRow;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * 无统一 OIDC 场景下的客户端用户断言密钥和预绑定身份管理。
 */
public class ClientUserAssertionAdminService {

    private static final int RSA_KEY_SIZE = 2048;
    private static final int KEY_ID_RANDOM_BYTES = 18;
    private static final Set<String> MAPPING_MODES = Set.of("PREBOUND", "VERIFIED_PHONE");

    private final AiCapabilityClientMapper clientMapper;
    private final AiCapabilityExternalIdentityMapper identityMapper;
    private final IUserLoadService userLoadService;
    private final CapabilityIdentityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ClientUserAssertionAdminService(
            AiCapabilityClientMapper clientMapper,
            AiCapabilityExternalIdentityMapper identityMapper,
            IUserLoadService userLoadService,
            CapabilityIdentityProperties properties,
            Clock clock) {
        this.clientMapper = clientMapper;
        this.identityMapper = identityMapper;
        this.userLoadService = userLoadService;
        this.properties = properties;
        this.clock = clock;
    }

    public ClientUserAssertionConfigVO getConfig(Long tenantId, Long clientId) {
        AiCapabilityClient client = requireManageableClient(tenantId, clientId);
        return config(client);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClientUserAssertionKeyVO rotateKey(Long tenantId, Long clientId) {
        AiCapabilityClient client = requireManageableClient(tenantId, clientId);
        KeyPair keyPair = generateKeyPair();
        String keyId = generateKeyId();
        String publicKeyPem = pem("PUBLIC KEY", ((RSAPublicKey) keyPair.getPublic()).getEncoded());
        String privateKeyPem = pem("PRIVATE KEY", ((RSAPrivateKey) keyPair.getPrivate()).getEncoded());
        Integer credentialVersion = requireCredentialVersion(client);
        int currentKeyVersion = currentKeyVersion(client);
        if (clientMapper.rotateUserAssertionKey(
                client.getTenantId(), client.getId(), credentialVersion, currentKeyVersion,
                keyId, publicKeyPem) == 0) {
            throw new BusinessException("客户端用户断言密钥已发生变化，请刷新后重试");
        }
        return new ClientUserAssertionKeyVO(
                client.getId(), client.getClientCode(), keyId, currentKeyVersion + 1,
                privateKeyPem, publicKeyPem, ClientUserAssertionProtocol.issuer(client),
                properties.validatedIssuer(), ClientUserAssertionProtocol.SUBJECT_TOKEN_TYPE,
                properties.validatedUserAssertionMaxTtl().toSeconds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long tenantId, Long clientId) {
        AiCapabilityClient client = requireManageableClient(tenantId, clientId);
        if (!EnableStatus.ENABLED.matches(client.getUserAssertionEnabled())) {
            return;
        }
        if (clientMapper.disableUserAssertion(
                client.getTenantId(), client.getId(), requireCredentialVersion(client),
                currentKeyVersion(client)) == 0) {
            throw new BusinessException("客户端用户断言配置已发生变化，请刷新后重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ClientUserAssertionMappingVO addMapping(
            Long tenantId,
            Long clientId,
            ClientUserAssertionMappingCreateDTO dto) {
        AiCapabilityClient client = requireManageableClient(tenantId, clientId);
        String subject;
        try {
            subject = ExternalIdentityFingerprint.requireSubject(dto.externalSubject());
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException("外围用户标识不能为空且长度不能超过512个字符");
        }
        LoginUser user = loadDelegatedUser(dto.userId(), client.getTenantId());
        String providerCode = ClientUserAssertionProtocol.providerCode(client.getId());
        String issuerHash = ExternalIdentityFingerprint.sha256(
                ClientUserAssertionProtocol.issuer(client));
        String subjectHash = ExternalIdentityFingerprint.sha256(subject);
        AiCapabilityExternalIdentity existing = identityMapper.selectActive(
                client.getTenantId(), providerCode, issuerHash, subjectHash);
        if (existing != null) {
            if (!user.getUserId().equals(existing.getUserId())) {
                throw new BusinessException("该外围用户标识已绑定其他 Forge 用户");
            }
            return mapping(existing, user);
        }

        AiCapabilityExternalIdentity mapping = new AiCapabilityExternalIdentity();
        mapping.setTenantId(client.getTenantId());
        mapping.setProviderCode(providerCode);
        mapping.setIssuerHash(issuerHash);
        mapping.setSubjectHash(subjectHash);
        mapping.setSubjectHint(ExternalIdentityFingerprint.subjectHint(subject));
        mapping.setUserId(user.getUserId());
        mapping.setStatus("ENABLED");
        mapping.setDelFlag(0L);
        try {
            identityMapper.insert(mapping);
        }
        catch (DuplicateKeyException exception) {
            AiCapabilityExternalIdentity concurrent = identityMapper.selectActive(
                    client.getTenantId(), providerCode, issuerHash, subjectHash);
            if (concurrent == null || !user.getUserId().equals(concurrent.getUserId())) {
                throw new BusinessException("该外围用户标识绑定发生冲突，请刷新后重试");
            }
            mapping = concurrent;
        }
        return mapping(mapping, user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeMapping(Long tenantId, Long clientId, Long mappingId) {
        AiCapabilityClient client = requireManageableClient(tenantId, clientId);
        String providerCode = ClientUserAssertionProtocol.providerCode(client.getId());
        String issuerHash = ExternalIdentityFingerprint.sha256(
                ClientUserAssertionProtocol.issuer(client));
        if (identityMapper.disableClientMapping(
                client.getTenantId(), mappingId, providerCode, issuerHash) == 0) {
            throw new BusinessException("用户映射不存在或已解除");
        }
    }

    public Page<ClientUserAssertionMappingVO> mappingPage(
            Long tenantId,
            Long clientId,
            PageQuery pageQuery,
            String keyword) {
        AiCapabilityClient client = requireManageableClient(tenantId, clientId);
        String providerCode = ClientUserAssertionProtocol.providerCode(client.getId());
        String issuerHash = ExternalIdentityFingerprint.sha256(
                ClientUserAssertionProtocol.issuer(client));
        Page<ClientUserAssertionMappingRow> source = identityMapper.selectClientMappingPage(
                pageQuery.toPage(), client.getTenantId(), providerCode, issuerHash,
                StringUtils.trimToNull(keyword));
        Page<ClientUserAssertionMappingVO> result = new Page<>(
                source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(this::mapping).toList());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMappingRule(
            Long tenantId,
            Long clientId,
            ClientUserAssertionMappingRuleDTO dto) {
        AiCapabilityClient client = requireManageableClient(tenantId, clientId);
        String mappingMode = StringUtils.upperCase(StringUtils.trim(dto.mappingMode()));
        if (!MAPPING_MODES.contains(mappingMode)) {
            throw new BusinessException("用户映射规则只支持预绑定或已验签手机号唯一匹配");
        }
        if (clientMapper.updateUserAssertionMappingMode(
                client.getTenantId(), client.getId(), requireCredentialVersion(client), mappingMode) == 0) {
            throw new BusinessException("客户端用户映射规则已发生变化，请刷新后重试");
        }
    }

    private ClientUserAssertionConfigVO config(AiCapabilityClient client) {
        return new ClientUserAssertionConfigVO(
                client.getId(), client.getClientCode(), client.getClientName(),
                EnableStatus.ENABLED.matches(client.getUserAssertionEnabled()),
                client.getUserAssertionKeyId(), client.getUserAssertionKeyVersion(),
                ClientUserAssertionProtocol.issuer(client), properties.validatedIssuer(),
                ClientUserAssertionProtocol.SUBJECT_TOKEN_TYPE,
                properties.validatedUserAssertionMaxTtl().toSeconds(),
                StringUtils.defaultIfBlank(client.getUserAssertionMappingMode(), "PREBOUND"),
                List.of());
    }

    private AiCapabilityClient requireManageableClient(Long tenantId, Long clientId) {
        if (tenantId == null || tenantId <= 0 || clientId == null || clientId <= 0) {
            throw new BusinessException("未获取到有效租户或客户端上下文");
        }
        AiCapabilityClient client = clientMapper.selectTenantById(tenantId, clientId);
        if (client == null) {
            throw new BusinessException("客户端不存在或无权访问");
        }
        CapabilityClientActorMode actorMode;
        try {
            actorMode = CapabilityClientActorMode.valueOf(client.getActorMode());
        }
        catch (Exception exception) {
            throw new BusinessException("客户端主体模式无效");
        }
        boolean oauthMode = StringUtils.contains(
                "," + StringUtils.defaultString(client.getAuthModes()) + ",", ",OAUTH,");
        if (!"ENABLED".equals(client.getStatus())
                || !actorMode.allowsUserDelegation()
                || !EnableStatus.ENABLED.matches(client.getOauthEnabled())
                || !oauthMode) {
            throw new BusinessException("只有启用 OAuth 的用户委托或混合模式客户端可配置用户断言");
        }
        if (client.getExpiresAt() != null
                && !client.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException("客户端已过期，不能配置用户断言");
        }
        requireCredentialVersion(client);
        return client;
    }

    private LoginUser loadDelegatedUser(Long userId, Long tenantId) {
        LoginUser user;
        try {
            user = userLoadService.loadUserByUserId(userId, tenantId);
        }
        catch (RuntimeException exception) {
            throw new BusinessException("Forge 用户不存在、未加入当前租户或目录不可用");
        }
        if (user == null || user.getUserId() == null || !tenantId.equals(user.getTenantId())
                || user.isAdmin() || user.isTenantAdmin()
                || !EnableStatus.ENABLED.matches(user.getUserStatus())
                || Boolean.TRUE.equals(user.getForcePasswordChange())
                || user.getActiveOrgId() == null || user.getActiveOrgId() <= 0
                || user.getRoleIds() == null || user.getRoleIds().isEmpty()) {
            throw new BusinessException("只能绑定当前租户内已启用、已分配组织和角色的普通用户");
        }
        return user;
    }

    private ClientUserAssertionMappingVO mapping(ClientUserAssertionMappingRow row) {
        return new ClientUserAssertionMappingVO(
                row.getId(), row.getSubjectHint(), hashPrefix(row.getSubjectHash()),
                row.getUserId(), row.getUsername(), row.getRealName(),
                row.getLastAuthenticatedAt(), row.getCreateTime());
    }

    private ClientUserAssertionMappingVO mapping(
            AiCapabilityExternalIdentity mapping,
            LoginUser user) {
        return new ClientUserAssertionMappingVO(
                mapping.getId(), mapping.getSubjectHint(), hashPrefix(mapping.getSubjectHash()),
                mapping.getUserId(), user.getUsername(), user.getRealName(),
                mapping.getLastAuthenticatedAt(), mapping.getCreateTime());
    }

    private String hashPrefix(String hash) {
        return hash == null ? null : hash.substring(0, Math.min(12, hash.length()));
    }

    private int currentKeyVersion(AiCapabilityClient client) {
        Integer value = client.getUserAssertionKeyVersion();
        return value == null ? 0 : value;
    }

    private Integer requireCredentialVersion(AiCapabilityClient client) {
        if (client.getCredentialVersion() == null || client.getCredentialVersion() <= 0) {
            throw new BusinessException("客户端凭据版本无效");
        }
        return client.getCredentialVersion();
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(
                    new RSAKeyGenParameterSpec(RSA_KEY_SIZE, RSAKeyGenParameterSpec.F4),
                    secureRandom);
            return generator.generateKeyPair();
        }
        catch (GeneralSecurityException exception) {
            throw new IllegalStateException("当前 JDK 无法生成 RSA 用户断言密钥", exception);
        }
    }

    private String generateKeyId() {
        byte[] bytes = new byte[KEY_ID_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return "ua_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String pem(String type, byte[] encoded) {
        String content = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + content
                + "\n-----END " + type + "-----\n";
    }
}
