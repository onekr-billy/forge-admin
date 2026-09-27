package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;

import java.util.Map;

public interface SystemServiceCapabilityDefinition {

    String serviceCode();

    String definitionVersion();

    String platformPermission();

    SystemServiceRegistrationSource registrationSource(Long tenantId);

    default SystemServiceRegistrationSource registrationSource(Long tenantId, SystemServiceRegistrationContext context) {
        return registrationSource(tenantId);
    }

    SystemServicePublication preparePublication(Long tenantId, JsonNode parameters);

    Map<String, Object> prepareInput(Map<String, Object> payload);

    void validate(SecureActionDescriptor descriptor, Map<String, Object> input);

    Map<String, Object> execute(
            SecureActionDescriptor descriptor,
            Map<String, Object> input,
            String requestId);
}
