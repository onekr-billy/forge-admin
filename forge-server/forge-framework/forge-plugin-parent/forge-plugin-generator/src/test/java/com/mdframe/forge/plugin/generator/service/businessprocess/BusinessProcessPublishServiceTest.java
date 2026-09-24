package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessValidationContext;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessValidationContextResolver;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectDesignVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessProcessPublishService")
class BusinessProcessPublishServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final BusinessProcessMapper processMapper = mock(BusinessProcessMapper.class);
    private final BusinessProcessVersionMapper versionMapper = mock(BusinessProcessVersionMapper.class);
    private final BusinessObjectDesignVersionMapper objectVersionMapper
            = mock(BusinessObjectDesignVersionMapper.class);
    private final BusinessProcessValidationContextResolver contextResolver
            = mock(BusinessProcessValidationContextResolver.class);
    private final FlowClient flowClient = mock(FlowClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<FlowClient> flowClientProvider = mock(ObjectProvider.class);

    private BusinessProcessPublishService service;
    private String draftHash;

    @BeforeEach
    void setUp() {
        BusinessProcessSchemaValidator validator = new BusinessProcessSchemaValidator(objectMapper);
        draftHash = validator.schemaHash(validator.normalize(schemaJson()));
        service = new BusinessProcessPublishService(
                objectMapper,
                processMapper,
                versionMapper,
                objectVersionMapper,
                validator,
                contextResolver,
                flowClientProvider);
    }

    @Test
    @DisplayName("retrying one application version reuses the immutable process version")
    void retryReusesApplicationVersion() {
        AiBusinessProcess process = process();
        process.setPublishedVersion(6);
        process.setDesignStatus("PUBLISHED");
        AiBusinessProcessVersion existing = version(801L, draftHash, 6, 3);
        when(processMapper.selectForPublish(1L, 10L, 20L)).thenReturn(process);
        when(versionMapper.selectPublishedForApplicationVersion(1L, 20L, 3)).thenReturn(existing);

        BusinessProcessPublishResult result = service.publishForApplication(
                10L, 3, List.of(20L), Map.of(20L, draftHash), 300L);

        assertEquals(1, result.snapshots().size());
        assertEquals("801", result.snapshots().get(0).processVersionId());
        verify(versionMapper, never()).insertImmutable(any());
        verify(processMapper, never()).updatePublishedProjection(
                any(), any(), any(), any(), any(), any());
        verify(processMapper).clearPublishedProjectionExcept(1L, 10L, List.of(20L), null);
    }

    @Test
    @DisplayName("reuse still refreshes projection when published version pointer drifted")
    void retryRefreshesStaleProjection() {
        AiBusinessProcess process = process();
        process.setPublishedVersion(5);
        process.setDesignStatus("CHANGED");
        AiBusinessProcessVersion existing = version(801L, draftHash, 6, 3);
        when(processMapper.selectForPublish(1L, 10L, 20L)).thenReturn(process);
        when(versionMapper.selectPublishedForApplicationVersion(1L, 20L, 3)).thenReturn(existing);
        when(processMapper.updatePublishedProjection(1L, 10L, 20L, 6, draftHash, null)).thenReturn(1);

        BusinessProcessPublishResult result = service.publishForApplication(
                10L, 3, List.of(20L), Map.of(20L, draftHash), 300L);

        assertEquals("801", result.snapshots().get(0).processVersionId());
        verify(processMapper).updatePublishedProjection(1L, 10L, 20L, 6, draftHash, null);
    }

    @Test
    @DisplayName("same application version rejects a different process draft")
    void sameApplicationVersionRejectsDifferentDraft() {
        AiBusinessProcess process = process();
        AiBusinessProcessVersion existing = version(801L, "b".repeat(64), 6, 3);
        when(processMapper.selectForPublish(1L, 10L, 20L)).thenReturn(process);
        when(versionMapper.selectPublishedForApplicationVersion(1L, 20L, 3)).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.publishForApplication(
                10L, 3, List.of(20L), Map.of(20L, draftHash), 300L));
        verify(processMapper, never()).updatePublishedProjection(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("coordinated publish rejects a process missing from the frozen candidate hashes")
    void coordinatedPublishRejectsMissingCandidateHash() {
        when(processMapper.selectForPublish(1L, 10L, 20L)).thenReturn(process());

        assertThrows(BusinessException.class, () -> service.publishForApplication(
                10L, 3, List.of(20L), Map.of(), 300L));

        verify(versionMapper, never()).selectPublishedForApplicationVersion(any(), any(), any());
        verify(versionMapper, never()).insertImmutable(any());
    }

    @Test
    @DisplayName("new process version pins object and deployed Flowable model dependencies")
    void publishesPinnedDependencies() throws Exception {
        stubNewVersionDependencies(7);

        BusinessProcessPublishResult result = service.publishForApplication(
                10L, 3, List.of(20L), Map.of(20L, draftHash), 300L);

        ArgumentCaptor<AiBusinessProcessVersion> inserted
                = ArgumentCaptor.forClass(AiBusinessProcessVersion.class);
        verify(versionMapper).insertImmutable(inserted.capture());
        Map<?, ?> dependencySnapshot = objectMapper.readValue(
                inserted.getValue().getDependencySnapshotJson(), Map.class);
        assertEquals("501", ((Map<?, ?>) ((List<?>) dependencySnapshot.get("objects")).get(0))
                .get("designVersionId"));
        Map<?, ?> flowModel = (Map<?, ?>) ((List<?>) dependencySnapshot.get("flowModels")).get(0);
        assertEquals(7, flowModel.get("modelVersion"));
        assertEquals("order_approval:7:9001", flowModel.get("processDefinitionId"));
        assertEquals("deployment-7", flowModel.get("deploymentId"));
        assertEquals("801", result.snapshots().get(0).processVersionId());
        assertTrue(result.snapshots().get(0).dependencies().containsKey("objects"));
    }

    @Test
    @DisplayName("process publish rejects a non-positive Flowable model version")
    void publishRejectsNonPositiveFlowableVersion() {
        stubNewVersionDependencies(0);

        assertThrows(BusinessException.class, () -> service.publishForApplication(
                10L, 3, List.of(20L), Map.of(20L, draftHash), 300L));

        verify(versionMapper, never()).insertImmutable(any());
    }

    @Test
    @DisplayName("Flow service failures return an actionable business error without exposing transport details")
    void flowServiceFailureReturnsSafeBusinessError() {
        stubNewVersionDependencies(7);
        when(flowClient.getModelByKey("order_approval"))
                .thenThrow(new IllegalStateException("connect refused at 10.0.0.8:8581"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.publishForApplication(
                        10L, 3, List.of(20L), Map.of(20L, draftHash), 300L));

        assertTrue(exception.getMessage().contains("请确认 Flow 服务可用后重试"));
        assertTrue(exception.getMessage().contains("order_approval"));
        assertFalse(exception.getMessage().contains("10.0.0.8"));
        verify(versionMapper, never()).insertImmutable(any());
    }

    @Test
    @DisplayName("rollback restores only the process projection and reuses the historical version")
    void rollbackRestoresHistoricalProjection() {
        AiBusinessProcess process = process();
        process.setStatus(0);
        AiBusinessProcessVersion historical = version(701L, draftHash, 4, 2);
        when(versionMapper.selectPublishedVersionById(1L, 701L)).thenReturn(historical);
        when(processMapper.selectForProjection(1L, 10L, 20L)).thenReturn(process);
        when(processMapper.updatePublishedProjection(1L, 10L, 20L, 4, draftHash, null)).thenReturn(1);

        List<BusinessProcessSnapshot> restored
                = service.restorePublishedProjection(10L, List.of(701L));

        assertEquals(1, restored.size());
        assertEquals("701", restored.get(0).processVersionId());
        verify(versionMapper, never()).insertImmutable(any());
        verify(processMapper).clearPublishedProjectionExcept(1L, 10L, List.of(20L), null);
    }

    private void stubNewVersionDependencies(Integer flowModelVersion) {
        AiBusinessProcess process = process();
        BusinessProcessSchema schema = new BusinessProcessSchemaValidator(objectMapper)
                .normalize(process.getDraftSchemaJson());
        BusinessProcessValidationContext context = new BusinessProcessValidationContext()
                .setExpectedProcessCode("order_submit")
                .setObjectIdsByCode(Map.of("order", "30"))
                .setPublishedObjectVersionIdsByCode(Map.of("order", "501"))
                .setFieldsByObjectCode(Map.of("order", Set.of("id", "status")))
                .setAvailableFlowModelKeys(Set.of("order_approval"))
                .setKnownPermissions(Set.of("ai:businessProcess:start"));
        AiBusinessObjectDesignVersion objectVersion = new AiBusinessObjectDesignVersion();
        objectVersion.setId(501L);
        objectVersion.setObjectId(30L);
        objectVersion.setObjectCode("order");
        objectVersion.setVersionNo(4);
        objectVersion.setPublishVersion(4);
        objectVersion.setPublishStatus("PUBLISHED");

        when(processMapper.selectForPublish(1L, 10L, 20L)).thenReturn(process);
        when(versionMapper.selectPublishedForApplicationVersion(1L, 20L, 3)).thenReturn(null);
        when(versionMapper.selectMaxVersionNo(1L, 20L)).thenReturn(5);
        when(contextResolver.resolve(1L, 10L, "order_submit", schema)).thenReturn(context);
        when(objectVersionMapper.selectLatestPublishedVersions(1L, List.of(30L)))
                .thenReturn(List.of(objectVersion));
        when(flowClientProvider.getIfAvailable()).thenReturn(flowClient);
        when(flowClient.getModelByKey("order_approval")).thenReturn(FlowResult.success(Map.of(
                "id", "flow-model-1",
                "modelKey", "order_approval",
                "version", flowModelVersion,
                "status", 1,
                "processDefinitionId", "order_approval:" + flowModelVersion + ":9001",
                "deploymentId", "deployment-" + flowModelVersion)));
        when(versionMapper.insertImmutable(any())).thenAnswer(invocation -> {
            ((AiBusinessProcessVersion) invocation.getArgument(0)).setId(801L);
            return 1;
        });
        when(processMapper.updatePublishedProjection(1L, 10L, 20L, 6, draftHash, null)).thenReturn(1);
    }

    private AiBusinessProcess process() {
        AiBusinessProcess process = new AiBusinessProcess();
        process.setId(20L);
        process.setTenantId(1L);
        process.setApplicationId(10L);
        process.setProcessCode("order_submit");
        process.setProcessName("订单提交");
        process.setSubjectObjectId(30L);
        process.setSubjectObjectCode("order");
        process.setDraftSchemaHash(draftHash);
        process.setDraftSchemaJson(schemaJson());
        process.setStatus(1);
        process.setDesignStatus("VALIDATED");
        return process;
    }

    private String schemaJson() {
        return """
                {
                  "schemaVersion":"1.0",
                  "processCode":"order_submit",
                  "subject":{"objectId":"30","objectCode":"order","recordIdSource":"RUNTIME_RECORD"},
                  "nodes":[
                    {"id":"start","type":"START_MANUAL","name":"提交","config":{"permission":"ai:businessProcess:start"}},
                    {"id":"end","type":"END","name":"完成","config":{"result":"SUCCESS"}}
                  ],
                  "edges":[{"id":"e1","source":"start","target":"end","sourcePort":"NEXT"}],
                  "policies":{"approvalConcurrency":"ONE_ACTIVE_PER_BUSINESS_KEY","maxSubProcessDepth":5,
                    "retry":{"mode":"LIMITED","maxAttempts":1,"backoffSeconds":[30]}},
                  "dependencies":{"objects":["order"],"flowModels":["order_approval"],"formAssets":[],
                    "businessActions":[],"messageTemplates":[],"capabilities":[],"subProcesses":[]}
                }
                """;
    }

    private AiBusinessProcessVersion version(Long id, String hash, Integer versionNo, Integer applicationVersion) {
        AiBusinessProcessVersion version = new AiBusinessProcessVersion();
        version.setId(id);
        version.setTenantId(1L);
        version.setApplicationId(10L);
        version.setProcessId(20L);
        version.setProcessCode("order_submit");
        version.setVersionNo(versionNo);
        version.setApplicationVersion(applicationVersion);
        version.setSchemaVersion("1.0");
        version.setSchemaJson(process().getDraftSchemaJson());
        version.setSchemaHash(hash);
        version.setDependencySnapshotJson("{}");
        version.setStatus(1);
        return version;
    }
}
