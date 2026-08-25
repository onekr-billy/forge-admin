package com.mdframe.forge.plugin.capability.identity.external;

import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityClient;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.security.CapabilityIdentityInfrastructureException;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.openapi.security.replay.OpenApiReplayGuard;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang3.StringUtils;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 验证无 OIDC 客户端使用独立 RSA 私钥签发的短期用户身份断言。
 */
public final class ClientUserAssertionVerifier {

    private static final int MAX_ASSERTION_LENGTH = 16384;
    private static final Pattern JWT_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{6,20}$");

    private final CapabilityIdentityProperties properties;
    private final OpenApiReplayGuard replayGuard;
    private final Clock clock;

    public ClientUserAssertionVerifier(
            CapabilityIdentityProperties properties,
            OpenApiReplayGuard replayGuard,
            Clock clock) {
        this.properties = properties;
        this.replayGuard = replayGuard;
        this.clock = clock;
    }

    public ExternalIdentityClaims verify(AiCapabilityClient client, String rawAssertion) {
        requireUsableClient(client);
        if (StringUtils.isBlank(rawAssertion) || rawAssertion.length() > MAX_ASSERTION_LENGTH) {
            throw invalidGrant();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(rawAssertion);
            verifyHeader(client, jwt.getHeader());
            if (!jwt.verify(new RSASSAVerifier(publicKey(client.getUserAssertionPublicKey())))) {
                throw invalidGrant();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            VerifiedClaims verified = verifyClaims(client, claims);
            assertNotReplayed(client, verified.issuedAt(), verified.jwtId());
            return new ExternalIdentityClaims(
                    ClientUserAssertionProtocol.providerCode(client.getId()),
                    ClientUserAssertionProtocol.issuer(client),
                    verified.subject(), client.getTenantId(), verified.phone(), null,
                    verified.preferredOrganizationId());
        }
        catch (BusinessException | CapabilityIdentityInfrastructureException exception) {
            throw exception;
        }
        catch (ParseException | JOSEException | RuntimeException exception) {
            throw invalidGrant();
        }
    }

    private void requireUsableClient(AiCapabilityClient client) {
        if (client == null || client.getId() == null || client.getTenantId() == null
                || !"ENABLED".equals(client.getStatus())
                || !EnableStatus.ENABLED.matches(client.getOauthEnabled())
                || !EnableStatus.ENABLED.matches(client.getUserAssertionEnabled())
                || !Set.of("USER_DELEGATION", "HYBRID").contains(client.getActorMode())
                || (client.getExpiresAt() != null
                && !client.getExpiresAt().isAfter(LocalDateTime.now(clock)))
                || StringUtils.isAnyBlank(
                        client.getClientCode(), client.getUserAssertionKeyId(),
                        client.getUserAssertionPublicKey())
                || client.getUserAssertionKeyVersion() == null
                || client.getUserAssertionKeyVersion() <= 0) {
            throw invalidGrant();
        }
    }

    private void verifyHeader(AiCapabilityClient client, JWSHeader header) {
        if (header == null || !JWSAlgorithm.RS256.equals(header.getAlgorithm())
                || !client.getUserAssertionKeyId().equals(header.getKeyID())) {
            throw invalidGrant();
        }
    }

    private VerifiedClaims verifyClaims(
            AiCapabilityClient client,
            JWTClaimsSet claims) throws ParseException {
        Instant now = clock.instant();
        Duration skew = properties.validatedUserAssertionClockSkew();
        Duration maxTtl = properties.validatedUserAssertionMaxTtl();
        Date issuedAtDate = claims.getIssueTime();
        Date expiresAtDate = claims.getExpirationTime();
        if (issuedAtDate == null || expiresAtDate == null) {
            throw invalidGrant();
        }
        Instant issuedAt = issuedAtDate.toInstant();
        Instant expiresAt = expiresAtDate.toInstant();
        if (issuedAt.isAfter(now.plus(skew))
                || !expiresAt.isAfter(now.minus(skew))
                || !expiresAt.isAfter(issuedAt)
                || Duration.between(issuedAt, expiresAt).compareTo(maxTtl) > 0) {
            throw invalidGrant();
        }
        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && notBefore.toInstant().isAfter(now.plus(skew))) {
            throw invalidGrant();
        }
        String subject = StringUtils.trimToNull(claims.getSubject());
        String jwtId = StringUtils.trimToNull(claims.getJWTID());
        List<String> audience = claims.getAudience();
        Object clientId = claims.getClaim("client_id");
        if (!ClientUserAssertionProtocol.issuer(client).equals(claims.getIssuer())
                || audience == null || !audience.contains(properties.validatedIssuer())
                || clientId == null || !client.getId().toString().equals(String.valueOf(clientId))
                || subject == null || subject.length() > 512
                || jwtId == null || !JWT_ID.matcher(jwtId).matches()) {
            throw invalidGrant();
        }
        return new VerifiedClaims(
                subject, jwtId, issuedAt, optionalPhoneClaim(claims),
                positiveLongClaim(claims, "forge_org_id"));
    }

    private String optionalPhoneClaim(JWTClaimsSet claims) {
        String phone = StringUtils.trimToNull(String.valueOf(
                claims.getClaim("phone_number") == null ? "" : claims.getClaim("phone_number")));
        if (phone == null) {
            return null;
        }
        if (!PHONE.matcher(phone).matches()) {
            throw invalidGrant();
        }
        return phone;
    }

    private Long positiveLongClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value == null) {
            return null;
        }
        try {
            long result = Long.parseLong(String.valueOf(value));
            if (result <= 0) {
                throw invalidGrant();
            }
            return result;
        }
        catch (NumberFormatException exception) {
            throw invalidGrant();
        }
    }

    private RSAPublicKey publicKey(String pem) {
        try {
            String normalized = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(normalized);
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            if (publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RSA key size is less than 2048 bits");
            }
            return publicKey;
        }
        catch (Exception exception) {
            throw new CapabilityIdentityInfrastructureException(
                    "客户端用户断言公钥不可用", exception);
        }
    }

    private void assertNotReplayed(
            AiCapabilityClient client,
            Instant issuedAt,
            String jwtId) {
        try {
            replayGuard.assertNotReplayed(
                    "user-assertion:" + client.getId(), issuedAt.toEpochMilli(), jwtId);
        }
        catch (BusinessException exception) {
            if (Integer.valueOf(503).equals(exception.getCode())) {
                throw new CapabilityIdentityInfrastructureException(
                        "客户端用户断言防重放服务暂不可用", exception);
            }
            throw invalidGrant();
        }
    }

    private BusinessException invalidGrant() {
        return new BusinessException(400, "invalid_grant");
    }

    private record VerifiedClaims(
            String subject,
            String jwtId,
            Instant issuedAt,
            String phone,
            Long preferredOrganizationId) {
    }
}
