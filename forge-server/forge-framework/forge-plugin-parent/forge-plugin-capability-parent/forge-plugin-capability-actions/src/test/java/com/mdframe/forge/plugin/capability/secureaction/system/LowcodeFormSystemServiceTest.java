package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.capability.secureaction.mapper.LowcodeFormReceiptMapper;
import com.mdframe.forge.plugin.generator.domain.entity.*;
import com.mdframe.forge.plugin.generator.mapper.BusinessDocumentConfigMapper;
import com.mdframe.forge.plugin.generator.service.*;
import com.mdframe.forge.plugin.generator.service.businessapp.*;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LowcodeFormSystemServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final BusinessObjectActionService actions = mock(BusinessObjectActionService.class);
    private final AiCrudConfigService configs = mock(AiCrudConfigService.class);
    private final DynamicCrudService records = mock(DynamicCrudService.class);
    private final BusinessEventPublisher events = mock(BusinessEventPublisher.class);
    private final LowcodeFormReceiptMapper receipts = mock(LowcodeFormReceiptMapper.class);
    private final BusinessDocumentConfigMapper documents = mock(BusinessDocumentConfigMapper.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final LowcodeFormSystemService service = new LowcodeFormSystemService(
            mock(BusinessObjectService.class), actions, configs, records, events, receipts, mapper,
            new CapabilitySchemaValidator(), transactions, documents);
    private final AiBusinessObjectDesignVersion version = new AiBusinessObjectDesignVersion();
    private final AiCrudConfig config = new AiCrudConfig();

    @BeforeEach
    void source() {
        AiBusinessObject object = new AiBusinessObject();
        object.setId(5L); object.setTenantId(1L); object.setObjectName("登记");
        version.setConfigKey("case_form"); version.setPublishVersion(2);
        version.setModelSnapshot("""
                {"appType":"SINGLE","fields":[
                  {"field":"title","label":"标题","required":true,"dataType":"varchar","length":40},
                  {"field":"note","label":"说明","dataType":"varchar"},
                  {"field":"tenantId","dataType":"bigint"},
                  {"field":"internalStage","dataType":"varchar"},
                  {"field":"computed","dataType":"varchar","readonly":true}
                ]}
                """);
        when(actions.resolvePublishedActions("law", "case", null)).thenReturn(
                new BusinessObjectActionService.ResolvedPublishedBusinessActions(object, List.of(), version));
        config.setTenantId(1L); config.setPublishStatus("PUBLISHED"); config.setPublishedVersion(3);
        when(configs.getByConfigKey("case_form")).thenReturn(config);
        when(configs.resolvePublishedRuntimeConfig(config)).thenReturn(config);
        AiBusinessDocumentConfig document = new AiBusinessDocumentConfig(); document.setStatusField("internalStage");
        when(documents.selectByObjectId(1L, 5L)).thenReturn(document);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void enforcesRequiredAndManagedFieldWhitelistAtPublication() {
        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            assertThatThrownBy(() -> publish(List.of("title", "tenantId"))).hasMessageContaining("允许字段");
            assertThatThrownBy(() -> publish(List.of("title", "internalStage"))).hasMessageContaining("允许字段");
            assertThatThrownBy(() -> publish(List.of("note"))).hasMessageContaining("必填");
            var published = publish(List.of("title", "note"));
            new CapabilitySchemaValidator().validateDefinition(published.inputSchema());
            assertThat(published.inputSchema().at("/properties/data/required").toString()).contains("title");
        }
    }

    @Test
    void refusesChangedSourceAndUnallowedData() {
        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            var descriptor = descriptor();
            assertThatThrownBy(() -> service.validate(descriptor, Map.of("data", Map.of("title", "t", "tenantId", 2)))).isInstanceOf(RuntimeException.class);
            config.setPublishedVersion(4);
            assertThatThrownBy(() -> service.validate(descriptor, Map.of("data", Map.of("title", "t")))).hasMessageContaining("FORM_SOURCE_CHANGED");
            verifyNoInteractions(records);
        }
    }

    @Test
    void duplicateRequestReusesReceiptAndDoesNotCreateOrPublishEventTwice() {
        Map<String, Object> receipt = new HashMap<>(); receipt.put("recordId", "");
        when(receipts.reserve(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyLong()))
                .thenAnswer(call -> { receipt.putIfAbsent("requestDigest", call.getArgument(5)); return 1; });
        when(receipts.lock(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(receipt);
        when(receipts.complete(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyLong()))
                .thenAnswer(call -> { receipt.put("recordId", call.getArgument(4)); return 1; });
        when(records.insert(eq("case_form"), anyMap())).thenReturn(Map.of("id", 99L, "title", "example"));
        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            var descriptor = descriptor();
            var input = Map.<String, Object>of("data", Map.of("title", "example"), "idempotencyKey", "test-key");
            assertThat(service.execute(descriptor, input, "req-1").get("recordId")).isEqualTo("99");
            assertThat(service.execute(descriptor, input, "req-2").get("idempotentHit")).isEqualTo(true);
            assertThatThrownBy(() -> service.execute(descriptor, Map.of("data", Map.of("title", "changed"), "idempotencyKey", "test-key"), "req-3"))
                    .hasMessageContaining("IDEMPOTENCY_CONFLICT");
        }
        verify(records, times(1)).insert(eq("case_form"), anyMap());
        verify(events, times(1)).publishRecordCreated(eq("case_form"), anyMap());
    }

    private SystemServicePublication publish(List<String> fields) {
        return service.preparePublication(1L, mapper.valueToTree(Map.of("suiteCode", "law", "objectCode", "case", "allowedFields", fields, "requiredFields", List.of())));
    }
    @Test
    void rejectsExternalDatasourceSnapshotEvenWithoutFlattenedDatasourceFields() {
        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            config.setRuntimeDatasourceSnapshot("{\"datasourceId\":\"9007199254740993\"}");
            assertThatThrownBy(() -> descriptor()).hasMessageContaining("外部数据源");
            config.setRuntimeDatasourceSnapshot("invalid");
            assertThatThrownBy(() -> descriptor()).hasMessageContaining("快照不可解析");
            verifyNoInteractions(records);
        }
    }

    @Test
    void refusesAFieldThatBecomesDocumentManagedAfterPublication() {
        try (var ignored = ExecutionIdentityContextHolder.open(identity())) {
            var descriptor = descriptor();
            AiBusinessDocumentConfig changed = new AiBusinessDocumentConfig(); changed.setStatusField("note");
            when(documents.selectByObjectId(1L, 5L)).thenReturn(changed);
            assertThatThrownBy(() -> service.validate(descriptor, Map.of("data", Map.of("title", "t", "note", "value"))))
                    .hasMessageContaining("FORM_FIELD_POLICY_CHANGED");
            verifyNoInteractions(records);
        }
    }
    private SecureActionDescriptor descriptor() {
        var publication = publish(List.of("title", "note"));
        return new SecureActionDescriptor(1L, "form.case.create", "填报", "", "1.0.0", "SYSTEM_SERVICE", service.serviceCode(), "1", "ACTION", "MEDIUM",
                "system", service.serviceCode(), service.serviceCode(), null, "ai:business:case:add", Set.of(), Set.of(),
                publication.policySnapshot(), publication.inputSchema(), publication.outputSchema());
    }
    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser(); user.setUserId(7L); user.setTenantId(1L); user.setActiveOrgId(2L); user.setPermissions(Set.of("ai:business:case:add"));
        return new ExecutionIdentity(user, "USER", 7L, null, 3L, "test", "test-token", Set.of());
    }
}
