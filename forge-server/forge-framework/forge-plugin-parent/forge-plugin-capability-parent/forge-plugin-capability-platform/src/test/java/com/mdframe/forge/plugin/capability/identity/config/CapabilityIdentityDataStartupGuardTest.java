package com.mdframe.forge.plugin.capability.identity.config;

import com.mdframe.forge.plugin.capability.identity.mapper.CapabilityIdentityStartupMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityIdentityDataStartupGuardTest {

    private final CapabilityIdentityStartupMapper mapper = mock(CapabilityIdentityStartupMapper.class);
    private final CapabilityIdentityDataStartupGuard guard =
            new CapabilityIdentityDataStartupGuard(mapper);

    @Test
    void shouldPassOnlyWhenClientAndGrantInventoryIsSafe() {
        assertThatCode(guard::verify).doesNotThrowAnyException();

        verify(mapper).countUnsafeEnabledClients(
                CapabilityIdentityDataStartupGuard.RESERVED_CLIENT_CODES);
        verify(mapper).countUnsafeEnabledGrants(
                CapabilityIdentityDataStartupGuard.RESERVED_CLIENT_CODES);
    }

    @Test
    void shouldFailStartupForUnsafeClientOrGrant() {
        when(mapper.countUnsafeEnabledClients(
                CapabilityIdentityDataStartupGuard.RESERVED_CLIENT_CODES)).thenReturn(2);
        when(mapper.countUnsafeEnabledGrants(
                CapabilityIdentityDataStartupGuard.RESERVED_CLIENT_CODES)).thenReturn(1);

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 个不安全客户端")
                .hasMessageContaining("1 个不安全授权");
    }
}
