package com.mdframe.forge.starter.auth.session;

import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LoginSessionValidationServiceTest {

    @Test
    void shouldRejectMissingLoginUser() {
        LoginSessionValidationService service = new LoginSessionValidationService(List.of(user -> true));

        assertThat(service.isValid(null)).isFalse();
    }

    @Test
    void shouldRequireEveryRegisteredValidatorToPass() {
        AtomicInteger calls = new AtomicInteger();
        LoginSessionValidationService service = new LoginSessionValidationService(List.of(
                user -> true,
                user -> {
                    calls.incrementAndGet();
                    return false;
                },
                user -> {
                    calls.incrementAndGet();
                    return true;
                }));

        assertThat(service.isValid(new LoginUser())).isFalse();
        assertThat(calls).hasValue(1);
    }

    @Test
    void shouldAllowStandaloneAuthStarterWhenNoDomainValidatorIsRegistered() {
        LoginSessionValidationService service = new LoginSessionValidationService(List.of());

        assertThat(service.isValid(new LoginUser())).isTrue();
    }
}
