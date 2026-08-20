package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessManualStartDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessNodeRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowService;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.when;

@DisplayName("BusinessProcessOrchestrator")
class BusinessProcessOrchestratorTest {

    private final BusinessApplicationMapper applicationMapper = mock(BusinessApplicationMapper.class);
    private final BusinessProcessMapper processMapper = mock(BusinessProcessMapper.class);
    private final BusinessProcessVersionMapper versionMapper = mock(BusinessProcessVersionMapper.class);
    private final BusinessProcessRunMapper runMapper = mock(BusinessProcessRunMapper.class);
    private final BusinessProcessNodeRunMapper nodeRunMapper = mock(BusinessProcessNodeRunMapper.class);
    private final BusinessFlowService flowService = mock(BusinessFlowService.class);
    private final BusinessProcessOrchestrator orchestrator = new BusinessProcessOrchestrator(
            applicationMapper,
            processMapper,
            versionMapper,
            runMapper,
            nodeRunMapper,
            new BusinessProcessSchemaValidator(new ObjectMapper()),
            flowService);

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
}
