package com.mdframe.forge.plugin.external.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalSensitiveDataMaskerTest {

    private final ExternalSensitiveDataMasker masker = new ExternalSensitiveDataMasker();

    @Test
    void shouldMaskNestedCredentialsBusinessIdentifiersAndUrlValues() {
        String json = "{\"mobile\":\"13800138000\",\"items\":[{\"orderNo\":\"P20260810001\"}],"
                + "\"profile\":{\"token\":\"secret-token\"},\"message\":\"call 13900139000\"}";

        String masked = masker.maskJson(json);
        String maskedUrl = masker.maskUrl("https://api.example.com/member?mobile=13800138000&token=abc");

        assertFalse(masked.contains("13800138000"));
        assertFalse(masked.contains("13900139000"));
        assertFalse(masked.contains("P20260810001"));
        assertFalse(masked.contains("secret-token"));
        assertTrue(maskedUrl.contains("mobile=******"));
        assertFalse(maskedUrl.contains("13800138000"));
        assertFalse(maskedUrl.contains("abc"));
    }

    @Test
    void shouldMaskSensitiveAssignmentsInPlainErrorText() {
        String masked = masker.maskText(
                "request failed token=opaque-token orderNo=P20260810001 password:plain-secret");

        assertFalse(masked.contains("opaque-token"));
        assertFalse(masked.contains("P20260810001"));
        assertFalse(masked.contains("plain-secret"));
    }
}
