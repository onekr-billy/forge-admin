package com.mdframe.forge.plugin.capability.identity.config;

import com.mdframe.forge.plugin.capability.controlplane.config.CapabilityControlPlaneProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilityIdentityStartupGuardTest {

    @Test
    void shouldAcceptIndependentNonPlaceholderPeppers() {
        CapabilityControlPlaneProperties controlPlane = new CapabilityControlPlaneProperties();
        controlPlane.setClientPepper("C7p!6Qz2#Lm9@Rx4");
        CapabilityIdentityProperties identity = new CapabilityIdentityProperties();
        identity.setTokenPepper("T9@aK2!mP7#xR4$vN8&qW5*eY1%uD6+sH");
        identity.setAuthorizationCodePepper("A4#zM8!qL2$vB7&pX5*wC9@rN3%tF6+yK");

        assertThatCode(() -> new CapabilityIdentityStartupGuard(identity, controlPlane))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectBlankWeakAndReusedPeppers() {
        assertThatThrownBy(() -> guard(null, strongToken(), strongCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Client Pepper");
        assertThatThrownBy(() -> guard(
                "forge-dev-client-pepper-1234567890", strongToken(), strongCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("弱值");
        assertThatThrownBy(() -> guard(
                "aaaaaaaaaaaaaaaa", strongToken(), strongCode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("弱值");
        assertThatThrownBy(() -> guard(
                "C7p!6Qz2#Lm9@Rx4", strongToken(), strongToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("互不相同");
    }

    private CapabilityIdentityStartupGuard guard(
            String clientPepper, String tokenPepper, String authorizationCodePepper) {
        CapabilityControlPlaneProperties controlPlane = new CapabilityControlPlaneProperties();
        controlPlane.setClientPepper(clientPepper);
        CapabilityIdentityProperties identity = new CapabilityIdentityProperties();
        identity.setTokenPepper(tokenPepper);
        identity.setAuthorizationCodePepper(authorizationCodePepper);
        return new CapabilityIdentityStartupGuard(identity, controlPlane);
    }

    private String strongToken() {
        return "T9@aK2!mP7#xR4$vN8&qW5*eY1%uD6+sH";
    }

    private String strongCode() {
        return "A4#zM8!qL2$vB7&pX5*wC9@rN3%tF6+yK";
    }
}
