package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.capability.controlplane.dto.CapabilityPublishDTO;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityCatalogService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class SystemServiceCapabilityPublisher {

    private final SystemServiceDefinitionRegistry registry;
    private final CapabilityCatalogService catalogService;
    private final ObjectMapper objectMapper;

    public SystemServiceCapabilityPublisher(
            SystemServiceDefinitionRegistry registry,
            CapabilityCatalogService catalogService,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    public List<SystemServiceRegistrationSource> registrationSources(Long tenantId) {
        return registrationSources(tenantId, null);
    }

    public List<SystemServiceRegistrationSource> registrationSources(Long tenantId, String serviceCode) {
        // Filter before enumeration: FORM sources inspect published objects and must not delay REST selection.
        if (StringUtils.isNotBlank(serviceCode)) {
            return List.of(registry.require(serviceCode).registrationSource(tenantId));
        }
        return registry.definitions().stream()
                .map(definition -> definition.registrationSource(tenantId))
                .toList();
    }

    public Long publish(Long tenantId, SystemServiceCapabilityPublishDTO dto) {
        SystemServiceCapabilityDefinition definition = registry.require(dto.serviceCode());
        SystemServiceRegistrationSource source = definition.registrationSource(tenantId);
        SystemServicePublication publication = definition.preparePublication(tenantId, dto.parameters());
        if (publication == null || publication.inputSchema() == null
                || publication.outputSchema() == null || publication.policySnapshot() == null
                || !publication.policySnapshot().isObject()) {
            throw new BusinessException("系统服务未返回完整的受控发布契约");
        }
        ObjectNode policy = ((ObjectNode) publication.policySnapshot()).deepCopy();
        policy.put("serviceCode", definition.serviceCode());
        policy.put("definitionVersion", definition.definitionVersion());
        policy.put("platformPermission", definition.platformPermission());
        if (StringUtils.isBlank(policy.path("permission").asText())) {
            throw new BusinessException("系统服务发布契约缺少业务权限");
        }
        CapabilityPublishDTO command = new CapabilityPublishDTO(
                dto.capabilityCode(), dto.capabilityCode(), publication.capabilityName(),
                StringUtils.defaultIfBlank(dto.description(), publication.description()),
                "SYSTEM_SERVICE", definition.serviceCode(), definition.definitionVersion(),
                dto.version(), "ACTION", source.riskLevel(), "DISCOVERABLE",
                source.requiredActorType(), publication.inputSchema(), publication.outputSchema(),
                objectMapper.valueToTree(policy));
        return catalogService.publishSystemService(tenantId, command);
    }
}
