package com.mdframe.forge.plugin.capability.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityGrant;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityVersion;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityCallGuideCheckVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCallContractPolicyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CapabilityCallContractPolicy policy = new CapabilityCallContractPolicy(objectMapper);

    @Test
    void shouldReadOnlyObjectSchemaAndResolveActionCodeFallback() {
        AiCapabilityVersion version = new AiCapabilityVersion();
        version.setSourceKey("FLOW_ACTION/invoice/START");
        version.setPolicySnapshot("[]");

        assertThat(policy.readVersionSchema("[]").isObject()).isTrue();
        assertThat(policy.readVersionSchema("{\"type\":\"object\"}").path("type").asText())
                .isEqualTo("object");
        assertThat(policy.actionCode(version)).isEqualTo("START");
    }

    @Test
    void shouldPreferPublishedOperationAndAppendFlowRequestNotes() {
        AiCapabilityVersion version = flowVersion("1.0.0", "START", 10);
        version.setSourceKey("FLOW_ACTION/invoice/REJECT");

        assertThat(policy.actionCode(version)).isEqualTo("START");
        assertThat(policy.requestNotes(version, "START"))
                .anyMatch(note -> note.contains("真实记录主键"))
                .anyMatch(note -> note.contains("不会创建业务记录"));
    }

    @Test
    void shouldRestrictSubmitExampleToGrantedFieldsAndPassRequiredFieldCheck() throws Exception {
        AiCapabilityVersion version = flowVersion("1.0.0", "SUBMIT", 10);
        version.setPolicySnapshot("""
                {"operation":"SUBMIT","allowedFields":["amount","remark"],"requiredFields":["amount"]}
                """);
        AiCapabilityGrant grant = new AiCapabilityGrant();
        grant.setFieldPolicy("{\"allowedFields\":[\"amount\"]}");
        JsonNode example = objectMapper.readTree("""
                {"data":{"amount":100,"remark":"internal","tenantId":9}}
                """);
        List<CapabilityCallGuideCheckVO> checks = new ArrayList<>();

        JsonNode prepared = policy.prepareRequestExample(
                version, "SUBMIT", grant.getFieldPolicy(), example);
        policy.addSubmissionGrantCheck(checks, version, "SUBMIT", grant);

        List<String> fields = new ArrayList<>();
        prepared.path("data").fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("amount");
        assertThat(checks).singleElement().satisfies(check -> {
            assertThat(check.code()).isEqualTo("FIELD_POLICY");
            assertThat(check.status()).isEqualTo("PASSED");
        });
    }

    @Test
    void shouldBlockPinnedFlowVersionWhenBindingSnapshotChanged() {
        AiCapability capability = new AiCapability();
        capability.setSourceType("FLOW_ACTION");
        AiCapabilityVersion pinned = flowVersion("1.0.0", "START", 10);
        AiCapabilityVersion current = flowVersion("1.0.1", "START", 11);
        List<CapabilityCallGuideCheckVO> checks = new ArrayList<>();

        policy.addFlowBindingCheck(checks, capability, pinned, current, "1.0.0", "1.0.1");

        assertThat(checks).singleElement().satisfies(check -> {
            assertThat(check.code()).isEqualTo("FLOW_BINDING");
            assertThat(check.status()).isEqualTo("FAILED");
            assertThat(check.message()).contains("FLOW_BINDING_MISMATCH");
        });
    }

    private AiCapabilityVersion flowVersion(String versionNumber, String operation, int objectVersion) {
        AiCapabilityVersion version = new AiCapabilityVersion();
        version.setVersion(versionNumber);
        version.setSourceType("FLOW_ACTION");
        version.setStatus("PUBLISHED");
        version.setPolicySnapshot("""
                {"bindingId":100,"flowModelKey":"invoice_flow","publishedObjectVersion":%d,
                 "operation":"%s"}
                """.formatted(objectVersion, operation));
        return version;
    }
}
