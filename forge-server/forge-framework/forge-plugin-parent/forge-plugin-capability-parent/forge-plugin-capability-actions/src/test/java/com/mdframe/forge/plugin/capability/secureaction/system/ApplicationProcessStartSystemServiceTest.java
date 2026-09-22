package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.plugin.capability.execution.SecureActionDescriptor;
import com.mdframe.forge.plugin.capability.schema.CapabilitySchemaValidator;
import com.mdframe.forge.plugin.generator.domain.entity.*;
import com.mdframe.forge.plugin.generator.mapper.*;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.*;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessOrchestrator;
import com.mdframe.forge.plugin.generator.vo.businessapp.*;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.*;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ApplicationProcessStartSystemServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BusinessApplicationRuntimeService runtime = mock(BusinessApplicationRuntimeService.class);
    private final BusinessApplicationVersionService appVersions = mock(BusinessApplicationVersionService.class);
    private final BusinessObjectMapper objects = mock(BusinessObjectMapper.class);
    private final BusinessProcessMapper processes = mock(BusinessProcessMapper.class);
    private final BusinessProcessVersionMapper versions = mock(BusinessProcessVersionMapper.class);
    private final BusinessProcessOrchestrator orchestrator = mock(BusinessProcessOrchestrator.class);
    private final DynamicCrudService records = mock(DynamicCrudService.class);
    private final ApplicationProcessCapabilitySource sources = new ApplicationProcessCapabilitySource(runtime, appVersions, objects, processes, versions, json);
    private final ApplicationProcessStartSystemService service = new ApplicationProcessStartSystemService(sources, orchestrator, records, json, new CapabilitySchemaValidator());
    private final AiBusinessApplicationVersion appVersion = new AiBusinessApplicationVersion();
    private final AiBusinessProcess process = new AiBusinessProcess();
    private final AiBusinessProcessVersion version = new AiBusinessProcessVersion();
    private final BusinessApplicationRuntimeVO published = new BusinessApplicationRuntimeVO();
    private ExecutionIdentityContextHolder.Scope scope;

    @BeforeEach void setup() throws Exception {
        scope = ExecutionIdentityContextHolder.open(identity(Set.of("ai:businessProcess:start", "case:submit")));
        BusinessObjectVO object = new BusinessObjectVO(); object.setId(5L); object.setSuiteCode("law");
        object.setObjectCode("case"); object.setConfigKey("case_form"); object.setStatus(1);
        when(objects.selectObjectDetail(1L, 5L)).thenReturn(object);
        BusinessApplicationObjectVO member = new BusinessApplicationObjectVO(); member.setObjectId(5L);
        member.setObjectCode("case"); member.setConfigKey("case_form"); // Also covers legacy missing suiteCode.
        BusinessApplicationVO app = new BusinessApplicationVO(); app.setApplicationCode("legal");
        published.setApplication(app); published.setVersionNo(3); published.setObjects(List.of(member));
        when(runtime.runtimeById(11L)).thenReturn(published);
        when(appVersions.requireVersion(11L, 3)).thenReturn(appVersion);
        version.setId(31L); version.setApplicationId(11L); version.setProcessId(21L); version.setVersionNo(2);
        version.setSchemaHash("fixed-schema-hash"); version.setSchemaJson("""
            {"subject":{"objectId":"5","objectCode":"case"},"nodes":[
              {"id":"start","type":"START_MANUAL","config":{"permission":"case:submit"}},
              {"id":"approval","type":"APPROVAL","config":{"flowModelKey":"approval-model"}}
            ]}
            """);
        appVersion.setSnapshotJson(json.writeValueAsString(Map.of("publishedProcessVersions", List.of(Map.of(
                "processId", "21", "processVersionId", "31", "processCode", "case_approval", "versionNo", 2,
                "schemaHash", version.getSchemaHash(), "businessProcessJson", json.readTree(version.getSchemaJson()))))));
        process.setId(21L); process.setApplicationId(11L); process.setProcessCode("case_approval");
        process.setProcessName("合同审批"); process.setStatus(1); process.setPublishedVersion(2);
        when(processes.selectActiveById(1L, 21L)).thenReturn(process);
        when(versions.selectPublishedVersion(1L, 21L, 2)).thenReturn(version);
        when(records.selectById("case_form", "9001")).thenReturn(Map.of("id", "9001"));
    }

    @AfterEach void close() { scope.close(); }

    @Test void exposesPublishedApplicationProcessWithoutLegacyObjectFlowBinding() {
        var options = service.registrationSource(1L, new SystemServiceRegistrationContext(11L, 5L)).options();
        assertThat(options.path("processes").get(0).path("name").asText()).isEqualTo("合同审批");
        assertThat(options.path("processes").get(0).path("available").asBoolean()).isTrue();
        var publication = publication();
        new CapabilitySchemaValidator().validateDefinition(publication.inputSchema());
        new CapabilitySchemaValidator().validateDefinition(publication.outputSchema());
        assertThat(publication.policySnapshot().path("processVersionId").asLong()).isEqualTo(31L);
    }

    @Test void executesOriginalOrchestratorWithPinnedVersionAndServerOwnedObject() {
        BusinessProcessRunVO run = new BusinessProcessRunVO(); run.setId("51"); run.setProcessCode("case_approval");
        run.setProcessVersionId("31"); run.setSubjectRecordId("9001"); run.setStatus("WAITING_APPROVAL");
        when(orchestrator.startPublished(eq("legal"), eq("case_approval"), any(), eq(31L))).thenReturn(run);
        var result = service.execute(descriptor(), Map.of("recordId", "9001", "idempotencyKey", "k1"), "r1");
        assertThat(result.get("runId")).isEqualTo("51");
        verify(orchestrator).startPublished(eq("legal"), eq("case_approval"), argThat(dto ->
                "9001".equals(dto.getRecordId()) && "case".equals(dto.getObjectCode()) && dto.getVariables().isEmpty()), eq(31L));
        verify(records).selectById("case_form", "9001");
        verify(orchestrator, never()).start(anyString(), anyString(), any());
    }

    @Test void refusesRecordOutsideUserDataScopeBeforeAnyStart() {
        when(records.selectById(anyString(), any())).thenReturn(null);
        assertThatThrownBy(() -> service.execute(descriptor(), Map.of("recordId", "9001", "idempotencyKey", "k"), "r"))
                .hasMessageContaining("可访问范围");
        verifyNoInteractions(orchestrator);
    }

    @Test void refusesClientIdentityModelAndVariableOverrides() {
        for (String key : List.of("variables", "modelId", "processCode", "userId", "tenantId", "applicationId")) {
            assertThatThrownBy(() -> service.prepareInput(Map.of("recordId", "9001", key, "override"))).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> service.validate(descriptor(), Map.of("recordId", "9001", key, "override"))).isInstanceOf(RuntimeException.class);
        }
        verifyNoInteractions(orchestrator, records);
    }

    @Test void rejectsMissingIdempotencyKey() {
        assertThatThrownBy(() -> service.execute(descriptor(), Map.of("recordId", "9001"), "r")).hasMessageContaining("missing_idempotency_key");
        verifyNoInteractions(orchestrator);
    }

    @Test void rejectsMissingNodePermission() {
        var descriptor = descriptor();
        try (var ignored = ExecutionIdentityContextHolder.open(identity(Set.of("ai:businessProcess:start")))) {
            assertThatThrownBy(() -> service.validate(descriptor, Map.of("recordId", "9001"))).hasMessageContaining("没有权限");
        }
        verifyNoInteractions(records, orchestrator);
    }

    @Test void rejectsMachineIdentity() {
        var descriptor = descriptor();
        var user = identity(Set.of()).loginUser();
        try (var ignored = ExecutionIdentityContextHolder.open(new ExecutionIdentity(user, "SERVICE", 2L, 2L, 3L, "test", "t", Set.of()))) {
            assertThatThrownBy(() -> service.validate(descriptor, Map.of("recordId", "9001"))).hasMessageContaining("USER_DELEGATION_REQUIRED");
        }
    }

    @Test void rejectsCrossTenantBeforeApplicationRead() {
        clearInvocations(runtime);
        assertThatThrownBy(() -> sources.options(2L, 11L, 5L)).hasMessageContaining("租户");
        verifyNoInteractions(runtime);
    }

    @Test void rejectsDraftOnlyObject() {
        published.setObjects(List.of());
        assertThatThrownBy(this::publication).hasMessageContaining("已发布版本");
    }

    @Test void respectsApplicationPortalAccessCheck() {
        when(runtime.runtimeById(11L)).thenThrow(new com.mdframe.forge.starter.core.exception.BusinessException("无门户权限"));
        assertThatThrownBy(this::publication).hasMessageContaining("无门户权限");
    }

    @Test void refusesProcessFromAnotherApplication() {
        process.setApplicationId(12L);
        assertThatThrownBy(this::publication).hasMessageContaining("版本已变化");
    }

    @Test void marksDisabledOrUnpublishedProcessUnavailable() {
        process.setStatus(0);
        var option = sources.options(1L, 11L, 5L).get(0);
        assertThat(option.available()).isFalse();
        assertThatThrownBy(this::publication).hasMessageContaining("停用");
        process.setStatus(1); process.setPublishedVersion(null);
        assertThatThrownBy(this::publication).hasMessageContaining("版本已变化");
    }

    @Test void rejectsEventOnlyProcess() {
        version.setSchemaJson(version.getSchemaJson().replace("START_MANUAL", "START_EVENT"));
        assertThat(sources.options(1L, 11L, 5L).get(0).available()).isFalse();
        assertThatThrownBy(this::publication).hasMessageContaining("手动发起");
    }

    @Test void rejectsProcessSubjectMismatch() {
        version.setSchemaJson(version.getSchemaJson().replace("\"objectId\":\"5\"", "\"objectId\":\"6\""));
        assertThatThrownBy(this::publication).hasMessageContaining("主对象");
    }

    @Test void rejectsSnapshotHashOrVersionIdentityMismatch() {
        version.setSchemaHash("changed");
        assertThatThrownBy(this::publication).hasMessageContaining("快照不一致");
        version.setSchemaHash("fixed-schema-hash"); version.setId(32L);
        assertThatThrownBy(this::publication).hasMessageContaining("快照不一致");
    }

    @Test void refusesCapabilityPolicyDriftAndChangedPermission() {
        var descriptor = descriptor();
        ((ObjectNode) descriptor.policySnapshot()).put("processVersionId", 32L);
        assertThatThrownBy(() -> service.validate(descriptor, Map.of("recordId", "9001"))).hasMessageContaining("SOURCE_CHANGED");
        verifyNoInteractions(records, orchestrator);
    }

    @Test void contextFreeCatalogDoesNotScanAllApplications() {
        clearInvocations(runtime, objects, processes, versions);
        assertThat(service.registrationSource(1L).options().path("registrationKind").asText()).isEqualTo("APPLICATION_PROCESS");
        verifyNoInteractions(runtime, objects, processes, versions);
    }

    private SystemServicePublication publication() {
        return service.preparePublication(1L, json.valueToTree(Map.of("applicationId", "11", "objectId", "5", "processCode", "case_approval")));
    }

    private SecureActionDescriptor descriptor() {
        var publication = publication();
        return new SecureActionDescriptor(8L, "app.start", "发起", "", "1.0.0", "SYSTEM_SERVICE", service.serviceCode(),
                "1", "ACTION", "HIGH", "system", service.serviceCode(), service.serviceCode(), null, "case:submit",
                Set.of(), Set.of(), publication.policySnapshot(), publication.inputSchema(), publication.outputSchema());
    }

    private ExecutionIdentity identity(Set<String> permissions) {
        LoginUser user = new LoginUser(); user.setTenantId(1L); user.setUserId(2L); user.setActiveOrgId(4L); user.setPermissions(permissions);
        return new ExecutionIdentity(user, "USER", 2L, null, 3L, "test", "t", Set.of());
    }
}
