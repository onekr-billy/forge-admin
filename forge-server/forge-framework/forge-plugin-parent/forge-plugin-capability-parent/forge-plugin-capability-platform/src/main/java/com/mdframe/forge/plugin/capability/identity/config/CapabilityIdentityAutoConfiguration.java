package com.mdframe.forge.plugin.capability.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.config.CapabilityAutoConfiguration;
import com.mdframe.forge.plugin.capability.controlplane.config.CapabilityControlPlaneAutoConfiguration;
import com.mdframe.forge.plugin.capability.controlplane.config.CapabilityControlPlaneProperties;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityClientMapper;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityGrantService;
import com.mdframe.forge.plugin.capability.identity.authorization.ForgeCapabilityAuthorizationPolicy;
import com.mdframe.forge.plugin.capability.identity.authorization.ForgeCapabilityPermissionMapper;
import com.mdframe.forge.plugin.capability.identity.external.ExternalIdentityMappingService;
import com.mdframe.forge.plugin.capability.identity.external.ClientUserAssertionAdminService;
import com.mdframe.forge.plugin.capability.identity.external.ClientUserAssertionVerifier;
import com.mdframe.forge.plugin.capability.identity.external.OidcExternalIdentityVerifier;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityAccessTokenMapper;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityExternalIdentityMapper;
import com.mdframe.forge.plugin.capability.identity.mapper.AiCapabilityOAuthRedirectUriMapper;
import com.mdframe.forge.plugin.capability.identity.oauth.DatabaseExactRedirectUriRegistry;
import com.mdframe.forge.plugin.capability.identity.oauth.DelegationAuthorizationCodeStore;
import com.mdframe.forge.plugin.capability.identity.oauth.ExactRedirectUriRegistry;
import com.mdframe.forge.plugin.capability.identity.oauth.OAuthRequestValidator;
import com.mdframe.forge.plugin.capability.identity.oauth.RedisDelegationAuthorizationCodeStore;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenCodec;
import com.mdframe.forge.plugin.capability.identity.token.CapabilityAccessTokenService;
import com.mdframe.forge.plugin.capability.spi.CapabilityAuthorizationPolicy;
import com.mdframe.forge.plugin.system.service.IUserLoadService;
import com.mdframe.forge.starter.openapi.security.config.OpenApiSecurityAutoConfiguration;
import com.mdframe.forge.starter.openapi.security.replay.OpenApiReplayGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@AutoConfiguration(
        after = {CapabilityControlPlaneAutoConfiguration.class, OpenApiSecurityAutoConfiguration.class},
        before = CapabilityAutoConfiguration.class)
@EnableConfigurationProperties(CapabilityIdentityProperties.class)
@Conditional(CapabilityIdentityRequiredCondition.class)
public class CapabilityIdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ForgeCapabilityPermissionMapper forgeCapabilityPermissionMapper() {
        return new ForgeCapabilityPermissionMapper();
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityAuthorizationPolicy.class)
    public CapabilityAuthorizationPolicy capabilityAuthorizationPolicy(
            CapabilityGrantService grantService,
            ForgeCapabilityPermissionMapper permissionMapper) {
        return new ForgeCapabilityAuthorizationPolicy(grantService, permissionMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityIdentityStartupGuard capabilityIdentityStartupGuard(
            CapabilityIdentityProperties identityProperties,
            CapabilityControlPlaneProperties controlPlaneProperties) {
        return new CapabilityIdentityStartupGuard(identityProperties, controlPlaneProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityAccessTokenCodec capabilityAccessTokenCodec(
            CapabilityIdentityProperties properties) {
        return new CapabilityAccessTokenCodec(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExactRedirectUriRegistry exactRedirectUriRegistry(
            AiCapabilityOAuthRedirectUriMapper redirectUriMapper) {
        return new DatabaseExactRedirectUriRegistry(redirectUriMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuthRequestValidator oAuthRequestValidator(
            CapabilityIdentityProperties properties,
            ExactRedirectUriRegistry redirectUriRegistry,
            @Qualifier("capabilityClock") Clock clock) {
        return new OAuthRequestValidator(properties, redirectUriRegistry, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public DelegationAuthorizationCodeStore delegationAuthorizationCodeStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CapabilityIdentityProperties properties) {
        return new RedisDelegationAuthorizationCodeStore(redisTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityAccessTokenService capabilityAccessTokenService(
            AiCapabilityAccessTokenMapper tokenMapper,
            AiCapabilityClientMapper clientMapper,
            CapabilityAccessTokenCodec tokenCodec,
            CapabilityIdentityProperties properties,
            IUserLoadService userLoadService,
            @Qualifier("capabilityClock") Clock clock) {
        return new CapabilityAccessTokenService(
                tokenMapper, clientMapper, tokenCodec, properties, userLoadService, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public OidcExternalIdentityVerifier oidcExternalIdentityVerifier(
            CapabilityIdentityProperties properties,
            ObjectMapper objectMapper) {
        return new OidcExternalIdentityVerifier(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExternalIdentityMappingService externalIdentityMappingService(
            OidcExternalIdentityVerifier verifier,
            AiCapabilityExternalIdentityMapper identityMapper,
            IUserLoadService userLoadService,
            @Qualifier("capabilityClock") Clock clock) {
        return new ExternalIdentityMappingService(
                verifier, identityMapper, userLoadService, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientUserAssertionVerifier clientUserAssertionVerifier(
            CapabilityIdentityProperties properties,
            OpenApiReplayGuard replayGuard,
            @Qualifier("capabilityClock") Clock clock) {
        return new ClientUserAssertionVerifier(properties, replayGuard, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientUserAssertionAdminService clientUserAssertionAdminService(
            AiCapabilityClientMapper clientMapper,
            AiCapabilityExternalIdentityMapper identityMapper,
            IUserLoadService userLoadService,
            CapabilityIdentityProperties properties,
            @Qualifier("capabilityClock") Clock clock) {
        return new ClientUserAssertionAdminService(
                clientMapper, identityMapper, userLoadService, properties, clock);
    }
}
