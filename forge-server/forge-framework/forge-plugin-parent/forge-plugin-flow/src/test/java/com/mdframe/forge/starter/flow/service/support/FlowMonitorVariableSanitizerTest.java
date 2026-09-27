package com.mdframe.forge.starter.flow.service.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FlowMonitorVariableSanitizerTest {

    @Test
    void exposesOnlyAllowlistedOperationalScalars() {
        Map<String, Object> sanitized = FlowMonitorVariableSanitizer.sanitize(Map.of(
                "businessKey", "order:1",
                "approved", true,
                "candidateUsers", List.of("1", "2"),
                "password", "secret",
                "formData", Map.of("idCard", "123456")));

        assertEquals("order:1", sanitized.get("businessKey"));
        assertEquals(true, sanitized.get("approved"));
        assertFalse(sanitized.containsKey("candidateUsers"));
        assertFalse(sanitized.containsKey("password"));
        assertFalse(sanitized.containsKey("formData"));
    }

    @Test
    void removesComplexValuesEvenWhenTheirKeyIsAllowlisted() {
        Map<String, Object> sanitized = FlowMonitorVariableSanitizer.sanitize(Map.of(
                "status", Map.of("raw", "approved"),
                "recordId", new Object()));

        assertEquals(Map.of(), sanitized);
    }
}
