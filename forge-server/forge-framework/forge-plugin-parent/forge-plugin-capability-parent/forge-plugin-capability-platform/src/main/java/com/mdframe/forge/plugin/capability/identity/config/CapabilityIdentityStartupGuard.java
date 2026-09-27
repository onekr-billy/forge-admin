package com.mdframe.forge.plugin.capability.identity.config;

import com.mdframe.forge.plugin.capability.controlplane.config.CapabilityControlPlaneProperties;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CapabilityIdentityStartupGuard {

    private static final Set<String> WEAK_SECRET_MARKERS = Set.of(
            "changeme", "change_me", "change-me", "default", "password",
            "placeholder", "example", "forge-dev", "development-only");

    public CapabilityIdentityStartupGuard(
            CapabilityIdentityProperties identityProperties,
            CapabilityControlPlaneProperties controlPlaneProperties) {
        identityProperties.validatedIssuer();
        identityProperties.validatedResource();
        identityProperties.validatedAccessTokenTtl();
        identityProperties.validatedAuthorizationCodeTtl();
        identityProperties.validatedAccessTokenRetention();
        identityProperties.validatedLastUsedTouchInterval();
        identityProperties.validatedUserAssertionMaxTtl();
        identityProperties.validatedUserAssertionClockSkew();

        String clientPepper = validateSecret(
                controlPlaneProperties.getClientPepper(), 16, "Forge Capability Client Pepper");
        String tokenPepper = validateSecret(
                identityProperties.getTokenPepper(), 32, "Forge MCP Token Pepper");
        String codePepper = validateSecret(
                identityProperties.getAuthorizationCodePepper(), 32,
                "Forge MCP Authorization Code Pepper");
        Set<String> peppers = new HashSet<>();
        peppers.add(clientPepper);
        peppers.add(tokenPepper);
        peppers.add(codePepper);
        if (peppers.size() != 3) {
            throw new IllegalStateException("Forge MCP 三类 Pepper 必须互不相同");
        }
    }

    private String validateSecret(String value, int minLength, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < minLength) {
            throw new IllegalStateException(name + " 未配置或长度不足 " + minLength + " 位");
        }
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        boolean containsWeakMarker = WEAK_SECRET_MARKERS.stream().anyMatch(lowerCase::contains);
        long distinctCharacters = normalized.chars().distinct().count();
        if (containsWeakMarker || distinctCharacters < 8) {
            throw new IllegalStateException(name + " 使用了弱值或占位值");
        }
        return normalized;
    }
}
