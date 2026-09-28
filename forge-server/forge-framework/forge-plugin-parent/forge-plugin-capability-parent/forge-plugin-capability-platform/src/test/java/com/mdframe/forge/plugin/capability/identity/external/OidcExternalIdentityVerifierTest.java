package com.mdframe.forge.plugin.capability.identity.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityProperties;
import com.mdframe.forge.plugin.capability.identity.security.CapabilityIdentityInfrastructureException;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcExternalIdentityVerifierTest {

    private HttpServer jwksServer;
    private RSAKey signingKey;
    private String issuer;
    private OidcExternalIdentityVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] jwks = new ObjectMapper().writeValueAsBytes(
                new JWKSet(signingKey.toPublicJWK()).toJSONObject());
        jwksServer.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        jwksServer.start();
        issuer = "http://127.0.0.1:" + jwksServer.getAddress().getPort();
        CapabilityIdentityProperties properties = new CapabilityIdentityProperties();
        CapabilityIdentityProperties.ExternalProvider provider =
                new CapabilityIdentityProperties.ExternalProvider();
        provider.setIssuer(issuer);
        provider.setJwkSetUri(issuer + "/jwks");
        provider.setAudience("forge-capability");
        provider.setTenantId(1L);
        properties.setExternalProviders(Map.of("partner", provider));
        verifier = new OidcExternalIdentityVerifier(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void shouldVerifyRs256IssuerAudienceAndIdentityClaims() throws Exception {
        ExternalIdentityClaims claims = verifier.verify(token("forge-capability"));

        assertThat(claims.providerCode()).isEqualTo("partner");
        assertThat(claims.issuer()).isEqualTo(issuer);
        assertThat(claims.subject()).isEqualTo("subject-101");
        assertThat(claims.phone()).isEqualTo("13800000000");
        assertThat(claims.name()).isEqualTo("张三");
        assertThat(claims.preferredOrganizationId()).isEqualTo(201L);
    }

    @Test
    void shouldRejectJwtWithWrongAudience() throws Exception {
        assertThatThrownBy(() -> verifier.verify(token("another-audience")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("invalid_grant");
    }

    @Test
    void shouldRejectJwtFromUnknownIssuer() throws Exception {
        String token = token(
                "forge-capability", "https://unknown-idp.example.com",
                Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("invalid_grant");
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {
        String token = token("forge-capability", issuer, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage("invalid_grant");
    }

    @Test
    void shouldFailClosedWhenJwkEndpointIsUnavailable() throws Exception {
        String token = token("forge-capability");
        jwksServer.stop(0);
        jwksServer = null;

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(CapabilityIdentityInfrastructureException.class)
                .hasMessage("外部身份提供方 JWK 暂不可用");
    }

    private String token(String audience) throws Exception {
        return token(audience, issuer, Instant.now().plusSeconds(300));
    }

    private String token(String audience, String tokenIssuer, Instant expiration) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(tokenIssuer)
                .subject("subject-101")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(expiration))
                .claim("phone_number", "13800000000")
                .claim("name", "张三")
                .claim("forge_org_id", "201")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(signingKey.getKeyID())
                .build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return new String(jwt.serialize().getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII);
    }
}
