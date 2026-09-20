package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessDocumentConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentConfigVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentRuntimeVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("BusinessDocumentRuntimeService")
class BusinessDocumentRuntimeServiceTest {

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "IN_PROCESS,100,true,true", "RUNNING,100,true,true", "NEED_MODIFY,100,true,true",
            "APPROVED,100,true,false", "CANCELED,100,true,false", "REJECTED,100,true,false",
            "IN_PROCESS,200,true,false", "IN_PROCESS,100,false,false"
    })
    void applicationWithdrawDoesNotDependOnDocumentModeOrMyTask(
            String status, Long userId, boolean permission, boolean expected) {
        BusinessFlowInstanceLinkMapper links = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessDocumentRuntimeService service = new BusinessDocumentRuntimeService(
                mock(BusinessDocumentConfigService.class), links, mock(BusinessProcessRunMapper.class),
                mock(AiCrudConfigMapper.class), mock(BusinessObjectMapper.class),
                mock(BusinessPermissionService.class), mock(DynamicCrudService.class));
        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setTenantId(1L);
        link.setObjectCode("order");
        link.setRecordId(9001L);
        link.setBusinessKey("order:9001");
        link.setProcessInstanceId("flow-instance-1");
        link.setStartUserId(100L);
        link.setFlowStatus(status);
        when(links.selectLatestByBusinessKey(1L, "order:9001")).thenReturn(link);
        when(links.selectLatestByBusinessKeys(1L, List.of("order:9001"))).thenReturn(List.of(link));
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(userId);
            session.when(() -> SessionHelper.hasPermission("ai:businessDocument:withdraw")).thenReturn(permission);
            for (BusinessDocumentRuntimeVO runtime : List.of(service.getRuntime("order", 9001L),
                    service.getRuntimeBatch("order", List.of(9001L)).get(9001L))) {
                assertFalse(Boolean.TRUE.equals(runtime.getDocumentEnabled()));
                assertNull(runtime.getMyTask());
                assertEquals(expected, runtime.getRuntimeActions().stream().anyMatch(action ->
                        "WITHDRAW_FLOW".equals(action.getKey()) && "order".equals(action.getObjectCode())
                                && Long.valueOf(9001L).equals(action.getRecordId())));
            }
        }
    }

    @Test
    @DisplayName("preloaded start validation avoids full runtime queries")
    void preloadedStartValidationAvoidsFullRuntimeQueries() {
        StartValidationFixture fixture = startValidationFixture(List.of("START_FLOW"));

        fixture.service().validateStartAllowed(
                "order", 9001L, fixture.configVO(), fixture.recordData(), null, true);

        verifyNoInteractions(
                fixture.documentConfigService(),
                fixture.linkMapper(),
                fixture.processRunMapper(),
                fixture.crudConfigMapper(),
                fixture.objectMapper());
    }

    @Test
    @DisplayName("preloaded start validation preserves status and existing-instance guards")
    void preloadedStartValidationPreservesStatusAndExistingInstanceGuards() {
        StartValidationFixture fixture = startValidationFixture(List.of("START_FLOW"));
        Map<String, Object> approvedRecord = Map.of("id", 9001L, "flowStatus", "APPROVED");

        BusinessException statusError = assertThrows(BusinessException.class,
                () -> fixture.service().validateStartAllowed(
                        "order", 9001L, fixture.configVO(), approvedRecord, null, false));
        assertTrue(statusError.getMessage().contains("不可发起主流程"));

        AiBusinessFlowInstanceLink existing = new AiBusinessFlowInstanceLink();
        existing.setProcessInstanceId("flow-instance-1");
        existing.setFlowStatus("IN_PROCESS");
        BusinessException instanceError = assertThrows(BusinessException.class,
                () -> fixture.service().validateStartAllowed(
                        "order", 9001L, fixture.configVO(), fixture.recordData(), existing, false));
        assertTrue(instanceError.getMessage().contains("已有主流程实例"));
    }

    @Test
    @DisplayName("preloaded start validation checks manual permission but lets internal triggers skip it")
    void preloadedStartValidationChecksManualPermissionOnly() {
        StartValidationFixture fixture = startValidationFixture(List.of("VIEW"));

        BusinessException permissionError = assertThrows(BusinessException.class,
                () -> fixture.service().validateStartAllowed(
                        "order", 9001L, fixture.configVO(), fixture.recordData(), null, true));
        assertTrue(permissionError.getMessage().contains("缺少发起主流程权限"));

        fixture.service().validateStartAllowed(
                "order", 9001L, fixture.configVO(), fixture.recordData(), null, false);
    }

    @Test
    @DisplayName("application-level flow history remains available without document mode")
    void returnsFlowInstanceWithoutDocumentMode() {
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessProcessRunMapper processRunMapper = mock(BusinessProcessRunMapper.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessDocumentRuntimeService service = new BusinessDocumentRuntimeService(
                documentConfigService,
                linkMapper,
                processRunMapper,
                crudConfigMapper,
                objectMapper,
                mock(BusinessPermissionService.class),
                mock(DynamicCrudService.class));

        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setConfigKey("order_runtime");
        runtimeConfig.setObjectCode("order");
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order"))
                .thenReturn(runtimeConfig);
        when(processRunMapper.selectActiveByBusinessKeys(1L, List.of("order:9001")))
                .thenReturn(List.of());
        AiBusinessProcessRun run = new AiBusinessProcessRun();
        run.setTenantId(1L);
        run.setBusinessKey("order:9001");
        run.setStatus("WAITING");
        run.setFlowProcessInstanceId("flow-instance-1");
        when(processRunMapper.selectLatestByBusinessKey(1L, "order:9001")).thenReturn(run);

        BusinessDocumentRuntimeVO runtime = service.getRuntime("order", 9001L);

        assertFalse(Boolean.TRUE.equals(runtime.getDocumentEnabled()));
        assertEquals("flow-instance-1", runtime.getProcessInstanceId());
        assertEquals("IN_PROCESS", runtime.getFlowStatus());
        assertTrue(Boolean.TRUE.equals(runtime.getDetailFlowTimelineVisible()));
        assertTrue(Boolean.TRUE.equals(runtime.getDetailFlowDiagramVisible()));
    }

    @Test
    @DisplayName("completed application process is returned as started for the matching process code")
    void completedApplicationProcessIsReturnedAsStarted() {
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessProcessRunMapper processRunMapper = mock(BusinessProcessRunMapper.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessDocumentRuntimeService service = new BusinessDocumentRuntimeService(
                documentConfigService,
                linkMapper,
                processRunMapper,
                crudConfigMapper,
                objectMapper,
                mock(BusinessPermissionService.class),
                mock(DynamicCrudService.class));

        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setConfigKey("order_runtime");
        runtimeConfig.setObjectCode("order");
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order"))
                .thenReturn(runtimeConfig);
        when(linkMapper.selectLatestByBusinessKeys(1L, List.of("order:9001")))
                .thenReturn(List.of());
        when(processRunMapper.selectActiveByBusinessKeys(1L, List.of("order:9001")))
                .thenReturn(List.of());
        AiBusinessProcessRun completedRun = new AiBusinessProcessRun();
        completedRun.setTenantId(1L);
        completedRun.setBusinessKey("order:9001");
        completedRun.setProcessCode("submit_approval");
        completedRun.setStatus("SUCCESS");
        when(processRunMapper.selectStartedByBusinessKeys(1L, List.of("order:9001")))
                .thenReturn(List.of(completedRun));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);

            BusinessDocumentRuntimeVO runtime = service.getRuntimeBatch("order", List.of(9001L)).get(9001L);

            assertEquals(List.of("submit_approval"), runtime.getStartedProcessCodes());
            assertTrue(runtime.getActiveProcessCodes().isEmpty());
        }
    }

    @Test
    @DisplayName("application-level modify task exposes resubmit without document mode")
    void applicationLevelModifyTaskExposesResubmitWithoutDocumentMode() throws Exception {
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessProcessRunMapper processRunMapper = mock(BusinessProcessRunMapper.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessDocumentRuntimeService service = new BusinessDocumentRuntimeService(
                documentConfigService,
                linkMapper,
                processRunMapper,
                crudConfigMapper,
                objectMapper,
                mock(BusinessPermissionService.class),
                mock(DynamicCrudService.class));

        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setConfigKey("order_runtime");
        runtimeConfig.setObjectCode("order");
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order"))
                .thenReturn(runtimeConfig);

        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setTenantId(1L);
        link.setObjectCode("order");
        link.setRecordId(9001L);
        link.setBusinessKey("order:9001");
        link.setProcessInstanceId("flow-instance-1");
        link.setFlowStatus("NEED_MODIFY");
        link.setStartUserId(100L);
        recordModifyTask(link, "100");
        when(linkMapper.selectLatestByBusinessKeys(1L, List.of("order:9001")))
                .thenReturn(List.of(link));
        when(processRunMapper.selectActiveByBusinessKeys(1L, List.of("order:9001")))
                .thenReturn(List.of());

        FlowClient flowClient = mock(FlowClient.class);
        injectFlowClient(service, flowClient);
        when(flowClient.getActiveTasksByProcessInstances(List.of("flow-instance-1"), "100"))
                .thenReturn(FlowResult.success(List.of()));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(100L);

            BusinessDocumentRuntimeVO runtime = service.getRuntimeBatch("order", List.of(9001L)).get(9001L);

            assertFalse(Boolean.TRUE.equals(runtime.getDocumentEnabled()));
            assertEquals("modify-task-1", runtime.getMyTask().getTaskId());
            assertEquals("RESUBMIT_FLOW", runtime.getNextAction());
            assertTrue(runtime.getRuntimeActions().stream()
                    .anyMatch(action -> "RESUBMIT_FLOW".equals(action.getKey())
                            && "order".equals(action.getObjectCode())
                            && Long.valueOf(9001L).equals(action.getRecordId())));
        }
    }

    @Test
    @DisplayName("need-modify remains active and exposes resubmit instead of start")
    void needModifyExposesResubmitInsteadOfStart() throws Exception {
        // 单据状态回调可以稍晚于待办生成；此处故意保留 IN_PROCESS，验证任务定义键兜底。
        RuntimeFixture fixture = runtimeFixture("NEED_MODIFY", "IN_PROCESS");
        FlowClient flowClient = mock(FlowClient.class);
        injectFlowClient(fixture.service(), flowClient);
        when(flowClient.getActiveTasksByProcessInstances(List.of("flow-instance-1"), "100"))
                .thenReturn(FlowResult.success(List.of(Map.of(
                        "processInstanceId", "flow-instance-1",
                        "taskId", "modify-task-1",
                        "taskDefKey", "Forge_InitiatorModify",
                        "taskName", "发起人修改"))));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(100L);

            BusinessDocumentRuntimeVO runtime = fixture.service().getRuntime("order", 9001L);

            assertEquals("RESUBMIT_FLOW", runtime.getNextAction());
            assertTrue(Boolean.TRUE.equals(runtime.getMyTask().getInitiatorModify()));
            assertTrue(runtime.getRuntimeActions().stream()
                    .anyMatch(action -> "RESUBMIT_FLOW".equals(action.getKey())));
            assertFalse(runtime.getRuntimeActions().stream()
                    .anyMatch(action -> "START_FLOW".equals(action.getKey())
                            && Boolean.TRUE.equals(action.getVisible())));
        }
    }

    @Test
    @DisplayName("recorded modify task exposes resubmit when active-task query is temporarily empty")
    void recordedModifyTaskFallsBackWhenActiveTaskQueryIsEmpty() throws Exception {
        RuntimeFixture fixture = runtimeFixture("NEED_MODIFY", "NEED_MODIFY");
        recordModifyTask(fixture.link(), "100");
        FlowClient flowClient = mock(FlowClient.class);
        injectFlowClient(fixture.service(), flowClient);
        when(flowClient.getActiveTasksByProcessInstances(List.of("flow-instance-1"), "100"))
                .thenReturn(FlowResult.success(List.of()));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(100L);

            BusinessDocumentRuntimeVO runtime = fixture.service().getRuntime("order", 9001L);

            assertEquals("modify-task-1", runtime.getMyTask().getTaskId());
            assertEquals("flow-instance-1", runtime.getMyTask().getProcessInstanceId());
            assertTrue(Boolean.TRUE.equals(runtime.getMyTask().getInitiatorModify()));
            assertEquals("RESUBMIT_FLOW", runtime.getNextAction());
            assertTrue(runtime.getRuntimeActions().stream()
                    .anyMatch(action -> "RESUBMIT_FLOW".equals(action.getKey())
                            && Boolean.TRUE.equals(action.getVisible())));
            assertFalse(runtime.getRuntimeActions().stream()
                    .anyMatch(action -> "START_FLOW".equals(action.getKey())
                            && Boolean.TRUE.equals(action.getVisible())));
        }
    }

    @Test
    @DisplayName("recorded modify task is not exposed to another user")
    void recordedModifyTaskIsNotExposedToAnotherUser() throws Exception {
        RuntimeFixture fixture = runtimeFixture("NEED_MODIFY", "NEED_MODIFY");
        recordModifyTask(fixture.link(), "100");
        FlowClient flowClient = mock(FlowClient.class);
        injectFlowClient(fixture.service(), flowClient);
        when(flowClient.getActiveTasksByProcessInstances(List.of("flow-instance-1"), "200"))
                .thenReturn(FlowResult.success(List.of()));

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(200L);

            BusinessDocumentRuntimeVO runtime = fixture.service().getRuntime("order", 9001L);

            assertNull(runtime.getMyTask());
            assertFalse(runtime.getRuntimeActions().stream()
                    .anyMatch(action -> "RESUBMIT_FLOW".equals(action.getKey())));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED", "CANCELED"})
    @DisplayName("terminal flow exposes history and never exposes start")
    void terminalFlowHidesStartAction(String terminalStatus) {
        RuntimeFixture fixture = runtimeFixture(terminalStatus, terminalStatus);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getTenantId).thenReturn(1L);
            session.when(SessionHelper::getUserId).thenReturn(100L);

            BusinessDocumentRuntimeVO runtime = fixture.service().getRuntime("order", 9001L);

            assertEquals("VIEW_FLOW", runtime.getNextAction());
            assertFalse(runtime.getRuntimeActions().stream()
                    .anyMatch(action -> "START_FLOW".equals(action.getKey())
                            && Boolean.TRUE.equals(action.getVisible())));
        }
    }

    private RuntimeFixture runtimeFixture(String linkStatus, String documentStatus) {
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessProcessRunMapper processRunMapper = mock(BusinessProcessRunMapper.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessPermissionService permissionService = mock(BusinessPermissionService.class);
        DynamicCrudService dynamicCrudService = mock(DynamicCrudService.class);
        BusinessDocumentRuntimeService service = new BusinessDocumentRuntimeService(
                documentConfigService,
                linkMapper,
                processRunMapper,
                crudConfigMapper,
                objectMapper,
                permissionService,
                dynamicCrudService);

        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setConfigKey("order_runtime");
        runtimeConfig.setObjectCode("order");
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order"))
                .thenReturn(runtimeConfig);

        AiBusinessDocumentConfig documentConfig = new AiBusinessDocumentConfig();
        documentConfig.setTenantId(1L);
        documentConfig.setObjectCode("order");
        documentConfig.setConfigKey("order_runtime");
        documentConfig.setStatusField("flowStatus");
        when(documentConfigService.selectEnabledByConfigKey(1L, "order_runtime"))
                .thenReturn(documentConfig);

        BusinessDocumentConfigVO configVO = new BusinessDocumentConfigVO();
        configVO.setDocumentEnabled(true);
        configVO.setObjectCode("order");
        configVO.setConfigKey("order_runtime");
        configVO.setStatusField("flowStatus");
        configVO.setMainFlowSummary(new LinkedHashMap<>(Map.of(
                "configured", true,
                "flowModelKey", "order_approval",
                "startMode", "MANUAL")));
        configVO.setStatusMappingRows(List.of(
                statusRow("DRAFT", true),
                statusRow("NEED_MODIFY", false),
                statusRow("APPROVED", false)));
        when(documentConfigService.toVO(documentConfig, runtimeConfig)).thenReturn(configVO);
        when(dynamicCrudService.selectById("order_runtime", 9001L))
                .thenReturn(Map.of("id", 9001L, "flowStatus", documentStatus));
        when(permissionService.resolveAvailableActions("order", 9001L, Map.of(
                "id", 9001L, "flowStatus", documentStatus)))
                .thenReturn(List.of("VIEW", "SAVE", "START_FLOW", "VIEW_FLOW", "WITHDRAW"));

        AiBusinessFlowInstanceLink link = new AiBusinessFlowInstanceLink();
        link.setId(7001L);
        link.setTenantId(1L);
        link.setObjectCode("order");
        link.setRecordId(9001L);
        link.setBusinessKey("order:9001");
        link.setProcessInstanceId("flow-instance-1");
        link.setFlowStatus(linkStatus);
        link.setStartUserId(100L);
        link.setStartTime(LocalDateTime.now());
        when(linkMapper.selectLatestByBusinessKey(1L, "order:9001")).thenReturn(link);
        when(linkMapper.selectByBusinessKey(1L, "order:9001")).thenReturn(List.of(link));
        when(processRunMapper.selectActiveByBusinessKeys(1L, List.of("order:9001"))).thenReturn(List.of());
        return new RuntimeFixture(service, link);
    }

    private StartValidationFixture startValidationFixture(List<String> actions) {
        BusinessDocumentConfigService documentConfigService = mock(BusinessDocumentConfigService.class);
        BusinessFlowInstanceLinkMapper linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        BusinessProcessRunMapper processRunMapper = mock(BusinessProcessRunMapper.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        BusinessObjectMapper objectMapper = mock(BusinessObjectMapper.class);
        BusinessPermissionService permissionService = mock(BusinessPermissionService.class);
        BusinessDocumentRuntimeService service = new BusinessDocumentRuntimeService(
                documentConfigService,
                linkMapper,
                processRunMapper,
                crudConfigMapper,
                objectMapper,
                permissionService,
                null);

        BusinessDocumentConfigVO configVO = new BusinessDocumentConfigVO();
        configVO.setDocumentEnabled(true);
        configVO.setObjectCode("order");
        configVO.setConfigKey("order_runtime");
        configVO.setStatusField("flowStatus");
        configVO.setMainFlowSummary(new LinkedHashMap<>(Map.of(
                "configured", true,
                "flowModelKey", "order_approval",
                "startMode", "MANUAL")));
        configVO.setStatusMappingRows(List.of(
                statusRow("DRAFT", true),
                statusRow("APPROVED", false)));
        Map<String, Object> recordData = Map.of("id", 9001L, "flowStatus", "DRAFT");
        when(permissionService.resolveAvailableActions("order", 9001L, recordData)).thenReturn(actions);
        return new StartValidationFixture(
                service,
                configVO,
                recordData,
                documentConfigService,
                linkMapper,
                processRunMapper,
                crudConfigMapper,
                objectMapper);
    }

    private BusinessDocumentConfigVO.StatusMappingRowVO statusRow(String status, boolean allowStart) {
        BusinessDocumentConfigVO.StatusMappingRowVO row = new BusinessDocumentConfigVO.StatusMappingRowVO();
        row.setStandardStatus(status);
        row.setStatusValue(status);
        row.setDisplayName(status);
        row.setAllowStartFlow(allowStart);
        return row;
    }

    private void injectFlowClient(BusinessDocumentRuntimeService service, FlowClient flowClient) throws Exception {
        Field field = BusinessDocumentRuntimeService.class.getDeclaredField("flowClient");
        field.setAccessible(true);
        field.set(service, flowClient);
    }

    private void recordModifyTask(AiBusinessFlowInstanceLink link, String assigneeId) {
        link.setVariablesSnapshot("""
                {
                  "forgeFlowRuntime": {
                    "modifyTask": {
                      "taskId": "modify-task-1",
                      "taskDefKey": "Forge_InitiatorModify",
                      "taskName": "发起人修改",
                      "assigneeId": "%s"
                    }
                  }
                }
                """.formatted(assigneeId));
    }

    private record RuntimeFixture(BusinessDocumentRuntimeService service,
                                  AiBusinessFlowInstanceLink link) {
    }

    private record StartValidationFixture(
            BusinessDocumentRuntimeService service,
            BusinessDocumentConfigVO configVO,
            Map<String, Object> recordData,
            BusinessDocumentConfigService documentConfigService,
            BusinessFlowInstanceLinkMapper linkMapper,
            BusinessProcessRunMapper processRunMapper,
            AiCrudConfigMapper crudConfigMapper,
            BusinessObjectMapper objectMapper) {
    }
}
