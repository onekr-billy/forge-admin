package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessNodeRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessManualStartDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessNodeRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEvent;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@DisplayName("BusinessProcessOrchestrator")
class BusinessProcessOrchestratorTest {

    private final BusinessApplicationMapper applicationMapper = mock(BusinessApplicationMapper.class);
    private final BusinessProcessMapper processMapper = mock(BusinessProcessMapper.class);
    private final BusinessProcessVersionMapper versionMapper = mock(BusinessProcessVersionMapper.class);
    private final BusinessProcessRunMapper runMapper = mock(BusinessProcessRunMapper.class);
    private final BusinessProcessNodeRunMapper nodeRunMapper = mock(BusinessProcessNodeRunMapper.class);
    private final BusinessFlowService flowService = mock(BusinessFlowService.class);
    private final BusinessProcessActionExecutor actionExecutor = mock(BusinessProcessActionExecutor.class);
    private final BusinessProcessOrchestrator orchestrator = new BusinessProcessOrchestrator(
            applicationMapper,
            processMapper,
            versionMapper,
            runMapper,
            nodeRunMapper,
            new BusinessProcessSchemaValidator(new ObjectMapper()),
            flowService,
            actionExecutor);

    private ExecutionIdentityContextHolder.Scope identityScope;
    private final AtomicReference<AiBusinessProcessRun> storedRun = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser();
        user.setUserId(101L);
        user.setTenantId(1L);
        user.setActiveOrgId(201L);
        user.setPermissions(Set.of("ai:businessProcess:start"));
        identityScope = ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                user, "USER", 101L, null, 301L,
                "business_process_runtime_test", "token-test", Set.of()));
        storedRun.set(null);
        when(runMapper.insert(any(AiBusinessProcessRun.class))).thenAnswer(invocation -> {
            AiBusinessProcessRun run = invocation.getArgument(0);
            storedRun.set(run);
            return 1;
        });
        when(runMapper.selectRunById(eq(1L), anyLong())).thenAnswer(invocation -> copy(storedRun.get()));
        when(runMapper.compareAndSetStatus(eq(1L), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    AiBusinessProcessRun run = storedRun.get();
                    if (run == null) {
                        return 0;
                    }
                    run.setStatus(invocation.getArgument(5));
                    run.setCurrentNodeId(invocation.getArgument(6));
                    run.setFlowProcessInstanceId(invocation.getArgument(7));
                    run.setErrorCode(invocation.getArgument(9));
                    run.setErrorSummary(invocation.getArgument(10));
                    storedRun.set(run);
                    return 1;
                });
        when(nodeRunMapper.selectMaxAttemptNo(anyLong(), anyLong(), any())).thenReturn(0);
        when(nodeRunMapper.insertAttempt(any())).thenReturn(1);
        when(nodeRunMapper.claimAttempt(anyLong(), anyLong())).thenReturn(1);
        when(nodeRunMapper.selectLatestAttempt(anyLong(), anyLong(), any())).thenReturn(null);
        when(nodeRunMapper.completeAttempt(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(runMapper.selectByIdempotencyKey(eq(1L), anyLong(), any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        identityScope.close();
    }

    @Test
    @DisplayName("manual start walks start to end and marks the run success")
    void startCompletesLinearManualProcess() {
        stubPublishedProcess(manualSchema());
        BusinessProcessManualStartDTO dto = new BusinessProcessManualStartDTO();
        dto.setRecordId("9001");
        dto.setObjectCode("order");

        BusinessProcessRunVO result = orchestrator.start("CRM_APP", "submit_approval", dto);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("order:9001", result.getBusinessKey());
        assertEquals("submit_approval", result.getProcessCode());
    }

    @Test
    @DisplayName("client cannot start with a mismatched object code")
    void rejectsMismatchedObjectCode() {
        stubPublishedProcess(manualSchema());
        BusinessProcessManualStartDTO dto = new BusinessProcessManualStartDTO();
        dto.setRecordId("9001");
        dto.setObjectCode("other");

        assertThrows(com.mdframe.forge.starter.core.exception.BusinessException.class,
                () -> orchestrator.start("CRM_APP", "submit_approval", dto));
    }

    @Test
    @DisplayName("record-created event matches main-record conditions and starts the published process")
    void recordCreatedEventMatchesMainRecordCondition() {
        AiBusinessProcessVersion version = publishedEventVersion(eventSchema());
        when(versionMapper.selectCurrentPublishedBySubjectObjectCode(1L, "order"))
                .thenReturn(java.util.List.of(version));
        BusinessEvent event = BusinessEvent.builder()
                .eventType(BusinessEvent.RECORD_CREATED)
                .objectCode("order")
                .recordId("9001")
                .recordData(Map.of(
                        "main", Map.of("id", 9001L, "approval_status", "DRAFT"),
                        "children", Map.of()))
                .operatorId(101L)
                .operatorName("operator")
                .tenantId(1L)
                .build();

        orchestrator.startEvent(event);

        assertEquals("SUCCESS", storedRun.get().getStatus());
        assertEquals("EVENT", storedRun.get().getTriggerType());
        assertEquals("order:9001", storedRun.get().getBusinessKey());
    }

    @Test
    @DisplayName("approval passes the independent flow status field to Flowable runtime")
    void approvalPassesIndependentFlowStatusField() {
        stubPublishedProcess(approvalSchema("flowStatus"));
        BusinessFlowRuntimeVO flowRuntime = new BusinessFlowRuntimeVO();
        flowRuntime.setProcessInstanceId("flow-instance-1");
        when(flowService.startFromBusinessProcess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(flowRuntime);
        BusinessProcessManualStartDTO dto = new BusinessProcessManualStartDTO();
        dto.setRecordId("9001");
        dto.setObjectCode("order");

        BusinessProcessRunVO result = orchestrator.start("CRM_APP", "submit_approval", dto);

        assertEquals("WAITING", result.getStatus());
        ArgumentCaptor<JSONObject> variables = ArgumentCaptor.forClass(JSONObject.class);
        verify(flowService).startFromBusinessProcess(
                eq("order_approval"), eq("order:9001"), eq("订单审批"), eq(101L),
                any(), eq(1L), variables.capture());
        assertEquals("flowStatus", variables.getValue().getString("flowStatusField"));
        assertEquals("app_10_page_order_apply_form_asset_order",
                variables.getValue().getString("businessFormKey"));
        JSONObject formRef = variables.getValue().getJSONObject("businessFormRef");
        assertEquals("10", formRef.getString("applicationId"));
        assertEquals("page_order_apply", formRef.getString("pageId"));
        assertEquals("订单申请页", formRef.getString("pageName"));
    }

    @Test
    @DisplayName("approval rejects a generic business status target before starting Flowable")
    void approvalRejectsGenericBusinessStatusField() {
        stubPublishedProcess(approvalSchema("status"));
        BusinessProcessManualStartDTO dto = new BusinessProcessManualStartDTO();
        dto.setRecordId("9001");
        dto.setObjectCode("order");

        BusinessException error = assertThrows(BusinessException.class,
                () -> orchestrator.start("CRM_APP", "submit_approval", dto));

        assertEquals("审批节点只能使用独立流程状态字段 flowStatus", error.getMessage());
        verify(flowService, never()).startFromBusinessProcess(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approved callback resumes the outer process and executes its next action once")
    void approvedCallbackResumesAndExecutesNextActionOnce() {
        stubPublishedProcess(approvalActionSchema());
        BusinessFlowRuntimeVO flowRuntime = new BusinessFlowRuntimeVO();
        flowRuntime.setProcessInstanceId("flow-instance-1");
        when(flowService.startFromBusinessProcess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(flowRuntime);
        when(actionExecutor.execute(any(), any(), any())).thenReturn("已更新 1 个字段");
        BusinessProcessManualStartDTO dto = new BusinessProcessManualStartDTO();
        dto.setRecordId("9001");
        dto.setObjectCode("order");
        orchestrator.start("CRM_APP", "submit_approval", dto);

        AiBusinessProcessNodeRun waitingAttempt = new AiBusinessProcessNodeRun();
        waitingAttempt.setId(7001L);
        waitingAttempt.setTenantId(1L);
        waitingAttempt.setRunId(storedRun.get().getId());
        waitingAttempt.setNodeId("approval");
        waitingAttempt.setStatus("WAITING");
        waitingAttempt.setCorrelationId("flow-instance-1");
        when(runMapper.selectWaitingByProcessInstanceId(1L, "flow-instance-1"))
                .thenAnswer(invocation -> "WAITING".equals(storedRun.get().getStatus()) ? copy(storedRun.get()) : null);
        when(nodeRunMapper.selectWaitingByCorrelation(
                eq(1L), anyLong(), eq("approval"), eq("flow-instance-1")))
                .thenReturn(waitingAttempt)
                .thenReturn(null);

        orchestrator.resumeApprovalResult(1L, "flow-instance-1", "APPROVED");
        orchestrator.resumeApprovalResult(1L, "flow-instance-1", "APPROVED");

        assertEquals("SUCCESS", storedRun.get().getStatus());
        verify(actionExecutor, times(1)).execute(
                any(AiBusinessProcessRun.class), any(),
                org.mockito.ArgumentMatchers.argThat(node -> "update-score".equals(node.getId())));
    }

    private void stubPublishedProcess(String schemaJson) {
        AiBusinessApplication application = new AiBusinessApplication();
        application.setId(10L);
        application.setApplicationCode("CRM_APP");
        application.setStatus(1);
        when(applicationMapper.selectEntityByCode(1L, "CRM_APP")).thenReturn(application);

        AiBusinessProcess process = new AiBusinessProcess();
        process.setId(1001L);
        process.setTenantId(1L);
        process.setApplicationId(10L);
        process.setProcessCode("submit_approval");
        process.setProcessName("提交审批");
        process.setSubjectObjectCode("order");
        process.setPublishedVersion(1);
        process.setStatus(1);
        when(processMapper.selectActiveByCode(1L, 10L, "submit_approval")).thenReturn(process);

        AiBusinessProcessVersion version = new AiBusinessProcessVersion();
        version.setId(2001L);
        version.setProcessId(1001L);
        version.setProcessCode("submit_approval");
        version.setVersionNo(1);
        version.setSchemaJson(schemaJson);
        when(versionMapper.selectPublishedVersion(1L, 1001L, 1)).thenReturn(version);
        when(versionMapper.selectPublishedVersionById(1L, 2001L)).thenReturn(version);
    }

    private AiBusinessProcessVersion publishedEventVersion(String schemaJson) {
        AiBusinessProcessVersion version = new AiBusinessProcessVersion();
        version.setId(2002L);
        version.setTenantId(1L);
        version.setApplicationId(10L);
        version.setProcessId(1002L);
        version.setProcessCode("created_approval");
        version.setVersionNo(1);
        version.setSchemaJson(schemaJson);
        when(versionMapper.selectPublishedVersionById(1L, 2002L)).thenReturn(version);
        return version;
    }

    private AiBusinessProcessRun copy(AiBusinessProcessRun source) {
        if (source == null) {
            return null;
        }
        AiBusinessProcessRun copy = new AiBusinessProcessRun();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setApplicationId(source.getApplicationId());
        copy.setProcessId(source.getProcessId());
        copy.setProcessVersionId(source.getProcessVersionId());
        copy.setProcessCode(source.getProcessCode());
        copy.setSubjectObjectCode(source.getSubjectObjectCode());
        copy.setSubjectRecordId(source.getSubjectRecordId());
        copy.setBusinessKey(source.getBusinessKey());
        copy.setTriggerType(source.getTriggerType());
        copy.setIdempotencyKey(source.getIdempotencyKey());
        copy.setActorType(source.getActorType());
        copy.setActorUserId(source.getActorUserId());
        copy.setActiveOrgId(source.getActiveOrgId());
        copy.setStatus(source.getStatus());
        copy.setCurrentNodeId(source.getCurrentNodeId());
        copy.setFlowProcessInstanceId(source.getFlowProcessInstanceId());
        copy.setContextSnapshot(source.getContextSnapshot());
        copy.setRetryCount(source.getRetryCount());
        copy.setErrorCode(source.getErrorCode());
        copy.setErrorSummary(source.getErrorSummary());
        return copy;
    }

    private String manualSchema() {
        return """
                {
                  "schemaVersion":"1.0",
                  "processCode":"submit_approval",
                  "subject":{"objectId":"20","objectCode":"order","recordIdSource":"RUNTIME_RECORD"},
                  "nodes":[
                    {"id":"start","type":"START_MANUAL","name":"提交","config":{"positions":["ROW"],"permission":"ai:businessProcess:start"}},
                    {"id":"end","type":"END","name":"完成","config":{"result":"SUCCESS"}}
                  ],
                  "edges":[
                    {"id":"e1","source":"start","target":"end","sourcePort":"NEXT"}
                  ]
                }
                """;
    }

    private String eventSchema() {
        return """
                {
                  "schemaVersion":"1.0",
                  "processCode":"created_approval",
                  "subject":{"objectId":"20","objectCode":"order","recordIdSource":"RUNTIME_RECORD"},
                  "nodes":[
                    {"id":"start","type":"START_EVENT","name":"新增后触发","config":{"eventType":"RECORD_CREATED","condition":{"logic":"AND","rules":[{"field":"approvalStatus","operator":"EQ","value":"DRAFT"}]}}},
                    {"id":"end","type":"END","name":"完成","config":{"result":"SUCCESS"}}
                  ],
                  "edges":[
                    {"id":"e1","source":"start","target":"end","sourcePort":"NEXT"}
                  ]
                }
                """;
    }

    private String approvalSchema(String statusField) {
        return """
                {
                  "schemaVersion":"1.0",
                  "processCode":"submit_approval",
                  "subject":{"objectId":"20","objectCode":"order","recordIdSource":"RUNTIME_RECORD"},
                  "nodes":[
                    {"id":"start","type":"START_MANUAL","name":"提交","config":{"positions":["ROW"],"permission":"ai:businessProcess:start"}},
                    {"id":"approval","type":"APPROVAL","name":"订单审批","ports":["APPROVED","REJECTED","CANCELED","FAILED"],"config":{"flowModelKey":"order_approval","statusField":"%s","formAsset":{"formKey":"app_10_page_order_apply_form_asset_order","formName":"订单申请表","formMode":"BUSINESS_OBJECT_FORM","applicationId":"10","pageId":"page_order_apply","pageName":"订单申请页"}}},
                    {"id":"end","type":"END","name":"完成","config":{"result":"SUCCESS"}}
                  ],
                  "edges":[
                    {"id":"e1","source":"start","target":"approval","sourcePort":"NEXT"},
                    {"id":"e2","source":"approval","target":"end","sourcePort":"APPROVED"}
                  ]
                }
                """.formatted(statusField);
    }

    private String approvalActionSchema() {
        return """
                {
                  "schemaVersion":"1.0",
                  "processCode":"submit_approval",
                  "subject":{"objectId":"20","objectCode":"order","recordIdSource":"RUNTIME_RECORD"},
                  "nodes":[
                    {"id":"start","type":"START_MANUAL","name":"提交","config":{"positions":["ROW"],"permission":"ai:businessProcess:start"}},
                    {"id":"approval","type":"APPROVAL","name":"订单审批","ports":["APPROVED","REJECTED","CANCELED","FAILED"],"config":{"flowModelKey":"order_approval","statusField":"flowStatus","formAsset":{"formKey":"order_form","formMode":"BUSINESS_OBJECT_FORM"}}},
                    {"id":"update-score","type":"ACTION","name":"更新评分","config":{"actionType":"UPDATE_RECORD","objectCode":"order","fieldMappings":[{"field":"score","valueSource":"CONSTANT","value":"2222"}]}},
                    {"id":"end","type":"END","name":"完成","config":{"result":"SUCCESS"}}
                  ],
                  "edges":[
                    {"id":"e1","source":"start","target":"approval","sourcePort":"NEXT"},
                    {"id":"e2","source":"approval","target":"update-score","sourcePort":"APPROVED"},
                    {"id":"e3","source":"update-score","target":"end","sourcePort":"NEXT"}
                  ]
                }
                """;
    }
}
