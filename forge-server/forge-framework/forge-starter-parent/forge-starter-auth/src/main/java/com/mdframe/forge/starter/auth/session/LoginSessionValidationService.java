package com.mdframe.forge.starter.auth.session;

import com.mdframe.forge.starter.core.session.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies every registered session validator as a fail-closed validation chain.
 */
@Component
@RequiredArgsConstructor
public class LoginSessionValidationService {

    private final List<LoginSessionValidator> validators;

    public boolean isValid(LoginUser loginUser) {
        if (loginUser == null) {
            return false;
        }
        return validators.stream().allMatch(validator -> validator.isValid(loginUser));
    }
}
