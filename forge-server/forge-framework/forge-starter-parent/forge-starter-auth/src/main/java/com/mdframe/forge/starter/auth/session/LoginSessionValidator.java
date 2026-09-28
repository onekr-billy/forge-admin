package com.mdframe.forge.starter.auth.session;

import com.mdframe.forge.starter.core.session.LoginUser;

/**
 * Extension point for validating an authenticated token session against authoritative state.
 */
@FunctionalInterface
public interface LoginSessionValidator {

    /**
     * @return {@code true} when the session can continue serving authenticated requests
     */
    boolean isValid(LoginUser loginUser);
}
