package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.controlplane.dto.CapabilityPublishDTO;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityCatalogService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemServiceCapabilityPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CapabilityCatalogService catalogService = mock(CapabilityCatalogService.class);

    @Test
    void shouldFreezeDefinitionAndPlatformPermissionIntoPublishedPolicy() {
        SystemServiceCapabilityDefinition definition = definition(validPublication());
        SystemServiceCapabilityPublisher publisher = publisher(definition);
        when(catalogService.publishSystemService(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(88L);

        Long capabilityId = publisher.publish(1L, publishCommand());

        assertThat(capabilityId).isEqualTo(88L);
        ArgumentCaptor<CapabilityPublishDTO> command = ArgumentCaptor.forClass(CapabilityPublishDTO.class);
        verify(catalogService).publishSystemService(eq(1L), command.capture());
        assertThat(command.getValue().sourceType()).isEqualTo("SYSTEM_SERVICE");
        assertThat(command.getValue().sourceKey()).isEqualTo("flow.process.start");
        assertThat(command.getValue().sourceVersion()).isEqualTo("1");
        assertThat(command.getValue().requiredActorType()).isEqualTo("USER");
        assertThat(command.getValue().behavior()).isEqualTo("ACTION");
        assertThat(command.getValue().riskLevel()).isEqualTo("MEDIUM");
        assertThat(command.getValue().policySnapshot().path("permission").asText())
                .isEqualTo("ai:businessFlow:start");
        assertThat(command.getValue().policySnapshot().path("platformPermission").asText())
                .isEqualTo("ai:capability:flow-action:invoke");
        assertThat(command.getValue().policySnapshot().path("serviceCode").asText())
                .isEqualTo("flow.process.start");
        assertThat(command.getValue().policySnapshot().path("definitionVersion").asText())
                .isEqualTo("1");
    }

    @Test
    void shouldRejectIncompletePublicationContract() {
        SystemServicePublication incomplete = new SystemServicePublication(
                "启动流程", "说明", null, objectMapper.createObjectNode(), objectMapper.createObjectNode());
        SystemServiceCapabilityPublisher publisher = publisher(definition(incomplete));

        assertThatThrownBy(() -> publisher.publish(1L, publishCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("完整的受控发布契约");
        verify(catalogService, never()).publishSystemService(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectPublicationWithoutBusinessPermission() {
        JsonNode schema = objectMapper.createObjectNode().put("type", "object");
        SystemServicePublication publication = new SystemServicePublication(
                "启动流程", "说明", schema, schema, objectMapper.createObjectNode());
        SystemServiceCapabilityPublisher publisher = publisher(definition(publication));

        assertThatThrownBy(() -> publisher.publish(1L, publishCommand()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少业务权限");
        verify(catalogService, never()).publishSystemService(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private SystemServiceCapabilityPublisher publisher(SystemServiceCapabilityDefinition definition) {
        return new SystemServiceCapabilityPublisher(
                new SystemServiceDefinitionRegistry(List.of(definition)), catalogService, objectMapper);
    }

    @Test
    void filtersBeforeInspectingExpensiveUnrelatedSourcesAndKeepsLegacyListing() {
        var flow = definition(validPublication());
        var rest = mock(SystemServiceCapabilityDefinition.class);
        when(rest.serviceCode()).thenReturn("system.rest.invoke");
        when(rest.definitionVersion()).thenReturn("1");
        when(rest.platformPermission()).thenReturn("ai:capability:system-service:invoke");
        when(rest.registrationSource(1L)).thenReturn(new SystemServiceRegistrationSource(
                "system.rest.invoke", "REST", "", "1", "USER", "MEDIUM",
                objectMapper.createObjectNode(), objectMapper.createObjectNode()));
        var publisher = new SystemServiceCapabilityPublisher(new SystemServiceDefinitionRegistry(List.of(flow, rest)), catalogService, objectMapper);
        assertThat(publisher.registrationSources(1L, "system.rest.invoke")).hasSize(1)
                .first().extracting(SystemServiceRegistrationSource::serviceCode).isEqualTo("system.rest.invoke");
        verify(flow, never()).registrationSource(1L);
        assertThatThrownBy(() -> publisher.registrationSources(1L, "unknown")).isInstanceOf(BusinessException.class);
        assertThat(publisher.registrationSources(1L)).hasSize(2);
        verify(flow).registrationSource(1L);
    }

    private SystemServiceCapabilityDefinition definition(SystemServicePublication publication) {
        SystemServiceCapabilityDefinition definition = mock(SystemServiceCapabilityDefinition.class);
        when(definition.serviceCode()).thenReturn("flow.process.start");
        when(definition.definitionVersion()).thenReturn("1");
        when(definition.platformPermission()).thenReturn("ai:capability:flow-action:invoke");
        when(definition.registrationSource(1L)).thenReturn(new SystemServiceRegistrationSource(
                "flow.process.start", "启动已发布流程", "说明", "1", "USER", "MEDIUM",
                objectMapper.createObjectNode(), objectMapper.createObjectNode()));
        when(definition.preparePublication(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(publication);
        return definition;
    }

    private SystemServicePublication validPublication() {
        JsonNode schema = objectMapper.createObjectNode().put("type", "object");
        JsonNode policy = objectMapper.createObjectNode()
                .put("permission", "ai:businessFlow:start")
                .put("modelKey", "invoice_approval");
        return new SystemServicePublication("启动发票审批", "启动已发布发票审批流程", schema, schema, policy);
    }

    private SystemServiceCapabilityPublishDTO publishCommand() {
        return new SystemServiceCapabilityPublishDTO(
                "flow.process.start", "system.flow.process.start.invoice_approval", "1.0.0",
                "开放发票审批流程", objectMapper.createObjectNode().put("modelId", "model-1"));
    }
}
