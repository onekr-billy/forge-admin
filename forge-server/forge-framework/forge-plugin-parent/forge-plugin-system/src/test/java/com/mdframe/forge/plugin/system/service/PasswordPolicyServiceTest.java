package com.mdframe.forge.plugin.system.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyServiceTest {

    @Test
    void generatedSocialCredentialSatisfiesDefaultPolicy() {
        PasswordPolicyService service = new PasswordPolicyService(null);

        String credential = service.generateSystemCredential();

        assertTrue(credential.length() >= 64);
        assertDoesNotThrow(() -> service.validate(credential));
    }
}
