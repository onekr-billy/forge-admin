package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessActionExecutionLog;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectDesignVersion;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessActionExecutionLogMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectActionVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@DisplayName("BusinessActionExecutionService")
class BusinessActionExecutionServiceTest {

    private static final String IDEMPOTENCY_KEY = "order-confirm-1001";
    private static final int PUBLISHED_VERSION = 3;

    private final DynamicCrudService dynamicCrudService = Mockito.mock(DynamicCrudService.class);
    private final BusinessObjectActionService actionService = Mockito.mock(BusinessObjectActionService.class);
    private final BusinessActionExecutionLogMapper logMapper = Mockito.mock(BusinessActionExecutionLogMapper.class);
    private final BusinessActionStepExecutor stepExecutor = Mockito.mock(BusinessActionStepExecutor.class);
    private final BusinessActionExecutionService service = new BusinessActionExecutionService(
            new ObjectMapper(),
            dynamicCrudService,
            actionService,
            logMapper,
            new TestTransactionManager(),
            List.of(stepExecutor));

    @Test
    @DisplayName("duplicate running idempotency key does not execute action steps")
    void duplicateRunningIdempotencyKeyDoesNotExecuteSteps() {
        AiBusinessObject object = businessObject();
        BusinessObjectActionVO action = actionWithNoopStep();
        when(actionService.resolvePublishedAction(null, "order", "confirm", null))
                .thenReturn(publishedAction(object, action));
        AiBusinessActionExecutionLog concurrentLog = runningLog();
        Mockito.doAnswer(invocation -> {
                    AiBusinessActionExecutionLog reserved = invocation.getArgument(0);
                    concurrentLog.setRequestDigest(reserved.getRequestDigest());
                    throw new DuplicateKeyException("duplicate idempotency key");
                })
                .when(logMapper).insert(any(AiBusinessActionExecutionLog.class));
        when(logMapper.selectLatestByIdempotencyKey(
                eq(1L), eq("order"), eq("1001"), eq("confirm"),
                eq(PUBLISHED_VERSION), eq(IDEMPOTENCY_KEY)))
                .thenReturn(null, concurrentLog);
        when(stepExecutor.supportType()).thenReturn("NOOP");

        BusinessActionExecuteDTO dto = new BusinessActionExecuteDTO();
        dto.setObjectCode("order");
        dto.setRecordId("1001");
        dto.setActionCode("confirm");
        dto.setIdempotencyKey(IDEMPOTENCY_KEY);

        BusinessException error = assertThrows(BusinessException.class, () -> service.execute(dto));

        assertEquals("业务动作正在执行，请稍候", error.getMessage());
        verify(stepExecutor, never()).execute(any(), any());
        verify(logMapper, times(1)).insert(any(AiBusinessActionExecutionLog.class));
    }

    @Test
    @DisplayName("same idempotency key cannot be reused with different arguments")
    void sameIdempotencyKeyCannotBeReusedWithDifferentArguments() {
        AiBusinessObject object = businessObject();
        BusinessObjectActionVO action = actionWithNoopStep();
        when(actionService.resolvePublishedAction(null, "order", "confirm", null))
                .thenReturn(publishedAction(object, action));
        AiBusinessActionExecutionLog existing = runningLog();
        existing.setExecuteStatus("SUCCESS");
        existing.setRequestDigest("sha256:different");
        when(logMapper.selectLatestByIdempotencyKey(
                eq(1L), eq("order"), eq("1001"), eq("confirm"),
                eq(PUBLISHED_VERSION), eq(IDEMPOTENCY_KEY)))
                .thenReturn(existing);

        BusinessActionExecuteDTO dto = new BusinessActionExecuteDTO();
        dto.setObjectCode("order");
        dto.setRecordId("1001");
        dto.setActionCode("confirm");
        dto.setIdempotencyKey(IDEMPOTENCY_KEY);
        dto.setFormData(Map.of("status", "CONFIRMED"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.execute(dto));

        assertEquals("幂等键已被不同业务动作参数使用，请更换幂等键", error.getMessage());
        verify(stepExecutor, never()).execute(any(), any());
    }

    @Test
    @DisplayName("resolves only explicit object code aliases")
    void resolvesOnlyExplicitObjectCodeAliases() {
        BusinessActionExecuteDTO dto = new BusinessActionExecuteDTO();
        dto.setBusinessObjectCode("purchase_order");
        assertEquals("purchase_order", service.resolveObjectCode(dto));

        dto = new BusinessActionExecuteDTO();
        dto.setTargetObjectCode("supplier");
        assertEquals("supplier", service.resolveObjectCode(dto));

        dto = new BusinessActionExecuteDTO();
        dto.setContext(Map.of("row", Map.of("_runtimeObjectCode", "outbound_order")));
        assertNull(service.resolveObjectCode(dto));

        dto = new BusinessActionExecuteDTO();
        dto.setFormData(Map.of("businessObjectCode", "transfer_order"));
        assertNull(service.resolveObjectCode(dto));
    }

    @Test
    @DisplayName("published execution log carries trusted delegated identity")
    void publishedExecutionLogCarriesTrustedDelegatedIdentity() {
        AiBusinessObject object = businessObject();
        BusinessObjectActionVO action = actionWithNoopStep();
        when(actionService.resolvePublishedAction(null, "order", "confirm", PUBLISHED_VERSION))
                .thenReturn(publishedAction(object, action));
        when(stepExecutor.supportType()).thenReturn("NOOP");
        BusinessActionStepResultVO stepResult = new BusinessActionStepResultVO();
        stepResult.setStepCode("noop");
        stepResult.setStepType("NOOP");
        stepResult.setStatus("SUCCESS");
        when(stepExecutor.execute(any(), any())).thenReturn(stepResult);
        Mockito.doAnswer(invocation -> {
            AiBusinessActionExecutionLog log = invocation.getArgument(0);
            log.setId(100L);
            return 1;
        }).when(logMapper).insert(any(AiBusinessActionExecutionLog.class));
        LoginUser user = new LoginUser();
        user.setUserId(101L);
        user.setTenantId(1L);
        user.setActiveOrgId(201L);
        user.setPermissions(Set.of());
        BusinessActionExecuteDTO dto = new BusinessActionExecuteDTO();
        dto.setObjectCode("order");
        dto.setActionCode("confirm");
        dto.setIdempotencyKey("order-confirm-1001");

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(
                new ExecutionIdentity(user, "USER", 101L, 999L, 301L,
                        "agent_client", "token-1", Set.of("capability:invoke")))) {
            service.executePublished(dto, PUBLISHED_VERSION, "req-1");
        }

        ArgumentCaptor<AiBusinessActionExecutionLog> captor =
                ArgumentCaptor.forClass(AiBusinessActionExecutionLog.class);
        verify(logMapper).insert(captor.capture());
        AiBusinessActionExecutionLog log = captor.getValue();
        assertEquals("req-1", log.getCapabilityRequestId());
        assertEquals(301L, log.getClientId());
        assertEquals(999L, log.getServiceUserId());
        assertEquals("USER", log.getActorType());
        assertEquals(PUBLISHED_VERSION, log.getActionVersion());
        assertEquals("ORCHESTRATION", log.getExecutionMode());
    }

    @Test
    @DisplayName("structured status audit keeps retention metadata without record identifiers")
    void structuredStatusAuditKeepsRetentionWithoutRecordIdentifiers() {
        AiBusinessObject object = businessObject();
        BusinessObjectActionVO action = actionWithNoopStep();
        when(actionService.resolvePublishedAction(null, "order", "confirm", null))
                .thenReturn(publishedAction(object, action));
        when(stepExecutor.supportType()).thenReturn("NOOP");
        when(stepExecutor.execute(any(), any())).thenAnswer(invocation -> {
            BusinessActionExecutionContext context = invocation.getArgument(0);
            context.getAuditTransitions().add(Map.of(
                    "eventType", "STATUS_TRANSITION",
                    "statusField", "status",
                    "from", "DRAFT",
                    "to", "SUBMITTED",
                    "targetConfigKey", "runtime_order",
                    "targetRecordId", "13800138000",
                    "outcome", "SUCCESS"));
            BusinessActionStepResultVO result = new BusinessActionStepResultVO();
            result.setStatus("SUCCESS");
            return result;
        });
        Mockito.doAnswer(invocation -> {
            AiBusinessActionExecutionLog log = invocation.getArgument(0);
            log.setId(102L);
            return 1;
        }).when(logMapper).insert(any(AiBusinessActionExecutionLog.class));
        BusinessActionExecuteDTO dto = new BusinessActionExecuteDTO();
        dto.setObjectCode("order");
        dto.setActionCode("confirm");
        dto.setIdempotencyKey("order-confirm-audit");

        service.execute(dto);

        ArgumentCaptor<AiBusinessActionExecutionLog> captor =
                ArgumentCaptor.forClass(AiBusinessActionExecutionLog.class);
        verify(logMapper).insert(captor.capture());
        AiBusinessActionExecutionLog log = captor.getValue();
        assertEquals("STATUS_TRANSITION", log.getAuditEventType());
        assertEquals("status", log.getStatusField());
        assertEquals("DRAFT", log.getStatusFrom());
        assertEquals("SUBMITTED", log.getStatusTo());
        assertNotNull(log.getRetentionUntil());
        assertTrue(log.getRetentionUntil().isAfter(LocalDateTime.now().plusYears(6)));
        assertTrue(log.getChangeSummary().contains("\"outcome\":\"SUCCESS\""));
        assertFalse(log.getChangeSummary().contains("13800138000"));
    }

    @Test
    @DisplayName("child-row execution rebuilds trusted parent and child records")
    void childRowExecutionBuildsTrustedParentAndChildContext() throws Exception {
        AiBusinessObject object = businessObject();
        BusinessObjectActionVO action = childRowAction();
        when(actionService.resolvePublishedAction(null, "order", "confirm_detail", null))
                .thenReturn(childPublishedAction(object, action));
        when(dynamicCrudService.selectById("runtime_order", "1001")).thenReturn(Map.of(
                "main", Map.of("id", 1001L, "status", "OPEN"),
                "children", Map.of("order_item", List.of(
                        Map.of("id", 2001L, "quantity", 3),
                        Map.of("id", 2002L, "quantity", 5)))));
        when(stepExecutor.supportType()).thenReturn("NOOP");
        BusinessActionStepResultVO stepResult = new BusinessActionStepResultVO();
        stepResult.setStatus("SUCCESS");
        when(stepExecutor.execute(any(), any())).thenReturn(stepResult);
        Mockito.doAnswer(invocation -> {
            AiBusinessActionExecutionLog log = invocation.getArgument(0);
            log.setId(201L);
            return 1;
        }).when(logMapper).insert(any(AiBusinessActionExecutionLog.class));

        BusinessActionExecuteDTO dto = childRowRequest("2001");
        service.execute(dto);

        ArgumentCaptor<BusinessActionExecutionContext> contextCaptor =
                ArgumentCaptor.forClass(BusinessActionExecutionContext.class);
        verify(stepExecutor).execute(contextCaptor.capture(), any());
        BusinessActionExecutionContext context = contextCaptor.getValue();
        assertEquals(2001L, context.getRecordData().get("id"));
        assertEquals(1001L, context.getParentRecordData().get("id"));
        assertEquals("OPEN", BusinessActionStepConfigHelper.resolvePath("parentRecord.status", context));
        assertEquals(3, BusinessActionStepConfigHelper.resolvePath("record.quantity", context));
        assertEquals("1001", context.getSystemContext().get("parentRecordId"));
        assertEquals("2001", context.getSystemContext().get("childRecordId"));
        assertEquals("order_item", context.getSystemContext().get("relationKey"));
        assertTrue(context.getExtraContext().isEmpty());
    }

    @Test
    @DisplayName("child-row execution rejects a child that does not belong to the parent")
    void childRowExecutionRejectsCrossParentChild() throws Exception {
        AiBusinessObject object = businessObject();
        BusinessObjectActionVO action = childRowAction();
        when(actionService.resolvePublishedAction(null, "order", "confirm_detail", null))
                .thenReturn(childPublishedAction(object, action));
        when(dynamicCrudService.selectById("runtime_order", "1001")).thenReturn(Map.of(
                "main", Map.of("id", 1001L),
                "children", Map.of("order_item", List.of(Map.of("id", 2002L)))));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.execute(childRowRequest("2001")));

        assertEquals("子记录不存在或不属于当前父记录", error.getMessage());
        verify(stepExecutor, never()).execute(any(), any());
        verify(logMapper, never()).insert(any(AiBusinessActionExecutionLog.class));
    }

    private AiBusinessObject businessObject() {
        AiBusinessObject object = new AiBusinessObject();
        object.setTenantId(1L);
        object.setSuiteCode("default");
        object.setObjectCode("order");
        object.setObjectName("订单");
        object.setConfigKey("runtime_order");
        return object;
    }

    private BusinessObjectActionVO actionWithNoopStep() {
        BusinessObjectActionVO action = new BusinessObjectActionVO();
        action.setActionCode("confirm");
        action.setActionName("确认");
        action.setStatus(1);
        action.setActionConfig(Map.of("steps", List.of(Map.of(
                "stepCode", "noop",
                "stepName", "空步骤",
                "stepType", "NOOP"
        ))));
        return action;
    }

    private BusinessObjectActionVO childRowAction() {
        BusinessObjectActionVO action = actionWithNoopStep();
        action.setActionCode("confirm_detail");
        action.setActionName("确认明细");
        action.setActionPosition("CHILD_ROW");
        action.setActionType("COMMAND");
        action.setActionConfig(Map.of(
                "triggerScene", "MANUAL",
                "relationKey", "order_item",
                "inputSchema", List.of(),
                "steps", List.of(Map.of(
                        "stepCode", "noop",
                        "stepName", "空步骤",
                        "stepType", "NOOP"))));
        return action;
    }

    private BusinessActionExecuteDTO childRowRequest(String childRecordId) {
        BusinessActionExecuteDTO dto = new BusinessActionExecuteDTO();
        dto.setObjectCode("order");
        dto.setRecordId(childRecordId);
        dto.setParentRecordId("1001");
        dto.setChildRecordId(childRecordId);
        dto.setRelationKey("order_item");
        dto.setActionCode("confirm_detail");
        dto.setIdempotencyKey("order-detail-confirm-" + childRecordId);
        return dto;
    }

    private BusinessObjectActionService.ResolvedPublishedBusinessAction childPublishedAction(
            AiBusinessObject object,
            BusinessObjectActionVO action) throws Exception {
        AiBusinessObjectDesignVersion version = new AiBusinessObjectDesignVersion();
        version.setPublishVersion(PUBLISHED_VERSION);
        version.setRelationSnapshot(new ObjectMapper().writeValueAsString(List.of(Map.of(
                "sourceObjectCode", "order",
                "targetObjectCode", "ORDER_ITEM",
                "relationType", "DETAIL",
                "relationConfig", "{\"relationKey\":\"order_item\"}",
                "status", 1))));
        return new BusinessObjectActionService.ResolvedPublishedBusinessAction(object, action, version);
    }

    private AiBusinessActionExecutionLog runningLog() {
        AiBusinessActionExecutionLog log = new AiBusinessActionExecutionLog();
        log.setId(10L);
        log.setTenantId(1L);
        log.setSuiteCode("default");
        log.setObjectCode("order");
        log.setRecordId("1001");
        log.setActionCode("confirm");
        log.setActionName("确认");
        log.setExecuteStatus("RUNNING");
        log.setIdempotencyKey(IDEMPOTENCY_KEY);
        log.setActionVersion(PUBLISHED_VERSION);
        log.setResultMessage("动作执行中");
        return log;
    }

    private BusinessObjectActionService.ResolvedPublishedBusinessAction publishedAction(
            AiBusinessObject object,
            BusinessObjectActionVO action) {
        AiBusinessObjectDesignVersion version = new AiBusinessObjectDesignVersion();
        version.setPublishVersion(PUBLISHED_VERSION);
        return new BusinessObjectActionService.ResolvedPublishedBusinessAction(object, action, version);
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
