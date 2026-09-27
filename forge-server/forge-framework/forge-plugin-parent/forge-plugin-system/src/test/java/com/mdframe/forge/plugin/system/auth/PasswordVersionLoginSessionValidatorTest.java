package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordVersionLoginSessionValidatorTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final PasswordVersionLoginSessionValidator validator =
            new PasswordVersionLoginSessionValidator(userMapper);

    @Test
    void shouldAcceptMatchingDatabaseCredentialVersion() {
        LoginUser loginUser = loginUser(7L);
        when(userMapper.selectActivePasswordVersion(11L, 1L)).thenReturn(7L);

        assertThat(validator.isValid(loginUser)).isTrue();
        verify(userMapper).selectActivePasswordVersion(11L, 1L);
    }

    @Test
    void shouldTreatLegacySessionWithoutVersionAsInitialVersion() {
        LoginUser loginUser = loginUser(null);
        when(userMapper.selectActivePasswordVersion(11L, 1L)).thenReturn(0L);

        assertThat(validator.isValid(loginUser)).isTrue();
    }

    @Test
    void shouldRejectOldSessionAfterPasswordChangesOnAnotherInstance() {
        LoginUser loginUser = loginUser(3L);
        when(userMapper.selectActivePasswordVersion(11L, 1L)).thenReturn(3L, 4L);

        assertThat(validator.isValid(loginUser)).isTrue();
        assertThat(validator.isValid(loginUser)).isFalse();
    }

    @Test
    void shouldFailClosedWhenAuthoritativeVersionCannotBeRead() {
        LoginUser loginUser = loginUser(3L);
        when(userMapper.selectActivePasswordVersion(11L, 1L))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(validator.isValid(loginUser)).isFalse();
    }

    @Test
    void shouldRejectDeletedDisabledOrTenantRevokedUser() {
        LoginUser loginUser = loginUser(3L);
        when(userMapper.selectActivePasswordVersion(11L, 1L)).thenReturn(null);

        assertThat(validator.isValid(loginUser)).isFalse();
    }

    private LoginUser loginUser(Long passwordVersion) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(11L);
        loginUser.setTenantId(1L);
        loginUser.setPasswordVersion(passwordVersion);
        return loginUser;
    }
}
