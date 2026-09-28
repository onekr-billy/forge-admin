package com.mdframe.forge.plugin.capability.opengateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.controlplane.config.CapabilityControlPlaneProperties;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityGrantService;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityInvocationAuditService;
import com.mdframe.forge.plugin.capability.identity.config.CapabilityIdentityAutoConfiguration;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityAccessTokenMapper;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityExternalIdentityMapper;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityOAuthRedirectUriMapper;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenService;
import com.mdframe.forge.plugin.capability.opengateway.auth.OpenGatewayAuthenticator;
import com.mdframe.forge.plugin.capability.opengateway.mapper.AiCapabilityOpenapiIdempotencyMapper;
import com.mdframe.forge.plugin.capability.opengateway.mapper.OpenGatewayCatalogMapper;
import com.mdframe.forge.plugin.capability.opengateway.service.CapabilityInvokeOrchestrator;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.starter.crypto.persistence.PersistentCryptoService;
import com.mdframe.forge.starter.openapi.security.idempotency.OpenApiIdempotencyManager;
import com.mdframe.forge.starter.openapi.security.ratelimit.OpenApiRateLimitManager;
import com.mdframe.forge.starter.openapi.security.replay.OpenApiReplayGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenGatewayAutoConfigurationTest {

    @Test
    void shouldCreateCompleteGatewayWhenGatewayForcesIdentityRuntime() {
        CapabilityControlPlaneProperties controlPlaneProperties =
                new CapabilityControlPlaneProperties();
        controlPlaneProperties.setClientPepper("client-pepper-1234567890");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CapabilityIdentityAutoConfiguration.class,
                        OpenGatewayAutoConfiguration.class))
                .withPropertyValues(
                        "forge.capability.identity.enabled=false",
                        "forge.capability.open-gateway.enabled=true",
                        "forge.capability.identity.token-pepper=token-pepper-123456789012345678901234567890",
                        "forge.capability.identity.authorization-code-pepper=code-pepper-123456789012345678901234567890")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(CapabilityControlPlaneProperties.class, () -> controlPlaneProperties)
                .withBean("capabilityClock", Clock.class, Clock::systemUTC)
                .withBean(AiCapabilityClientMapper.class, () -> mock(AiCapabilityClientMapper.class))
                .withBean(AiCapabilityAccessTokenMapper.class,
                        () -> mock(AiCapabilityAccessTokenMapper.class))
                .withBean(AiCapabilityExternalIdentityMapper.class,
                        () -> mock(AiCapabilityExternalIdentityMapper.class))
                .withBean(AiCapabilityOAuthRedirectUriMapper.class,
                        () -> mock(AiCapabilityOAuthRedirectUriMapper.class))
                .withBean(IUserLoadService.class, () -> mock(IUserLoadService.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(CapabilityGrantService.class, () -> mock(CapabilityGrantService.class))
                .withBean(OpenApiReplayGuard.class, () -> mock(OpenApiReplayGuard.class))
                .withBean(PersistentCryptoService.class, () -> mock(PersistentCryptoService.class))
                .withBean(OpenGatewayCatalogMapper.class,
                        () -> mock(OpenGatewayCatalogMapper.class))
                .withBean(OpenApiRateLimitManager.class,
                        () -> mock(OpenApiRateLimitManager.class))
                .withBean(OpenApiIdempotencyManager.class,
                        () -> mock(OpenApiIdempotencyManager.class))
                .withBean(AiCapabilityOpenapiIdempotencyMapper.class,
                        () -> mock(AiCapabilityOpenapiIdempotencyMapper.class))
                .withBean(CapabilitySchemaValidator.class,
                        () -> mock(CapabilitySchemaValidator.class))
                .withBean(CapabilityInvocationAuditService.class,
                        () -> mock(CapabilityInvocationAuditService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CapabilityAccessTokenService.class);
                    assertThat(context).hasSingleBean(OpenGatewayAuthenticator.class);
                    assertThat(context).hasSingleBean(CapabilityInvokeOrchestrator.class);
                });
    }
}
