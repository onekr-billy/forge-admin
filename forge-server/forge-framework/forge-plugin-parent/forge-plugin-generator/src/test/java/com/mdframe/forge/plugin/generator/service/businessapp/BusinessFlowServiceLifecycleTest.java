package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("low-code business flow lifecycle synchronization")
class BusinessFlowServiceLifecycleTest {

    private BusinessFlowService service;
    private BusinessFlowInstanceLinkMapper linkMapper;
    private DynamicCrudService dynamicCrudService;
    private AiBusinessFlowInstanceLink link;

    @BeforeEach
    void setUp() {
        linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        dynamicCrudService = mock(DynamicCrudService.class);
        AiCrudConfigMapper crudConfigMapper = mock(AiCrudConfigMapper.class);
        AiCrudConfig runtimeConfig = new AiCrudConfig();
        runtimeConfig.setConfigKey("order_runtime");
        runtimeConfig.setObjectCode("order");
        runtimeConfig.setTableName("biz_order");
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order_runtime"))
                .thenReturn(runtimeConfig);
        when(crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order"))
                .thenReturn(runtimeConfig);

        service = new BusinessFlowService(
                mock(BusinessBindingMapper.class),
                linkMapper,
                crudConfigMapper,
                mock(BusinessObjectMapper.class),
                mock(BusinessDocumentConfigService.class),
                mock(BusinessDocumentRuntimeService.class),
                dynamicCrudService,
                mock(BusinessFieldDesignService.class),
                mock(BusinessFlowVariableResolver.class),
                mock(BusinessCodeFormProviderRegistry.class),
                mock(ApplicationEventPublisher.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));

        link = new AiBusinessFlowInstanceLink();
        link.setId(7001L);
        link.setTenantId(1L);
        link.setObjectCode("order");
        link.setRecordId(9001L);
        link.setBusinessKey("order:9001");
        link.setProcessInstanceId("flow-instance-1");
        link.setFlowStatus(BusinessDocumentFlowStatus.IN_PROCESS.getCode());
        link.setStartUserId(100L);
        link.setVariablesSnapshot("{\"configKey\":\"order_runtime\",\"flowStatusField\":\"flowStatus\"}");
        when(linkMapper.selectByProcessInstanceId(1L, "flow-instance-1")).thenReturn(link);
    }

    @Test
    @DisplayName("initiator modify task repairs both link and record to NEED_MODIFY")
    void initiatorModifyTaskRepairsNeedModifyState() {
        when(dynamicCrudService.selectByIdAllowDraft("order_runtime", 9001L))
                .thenReturn(Map.of("id", 9001L, "flowStatus", "IN_PROCESS"));

        service.handleFlowEngineEvent(taskCreated("Forge_InitiatorModify", "发起人修改", "100"));

        assertEquals(BusinessDocumentFlowStatus.NEED_MODIFY.getCode(), link.getFlowStatus());
        verify(dynamicCrudService).updateInternalFieldsByIdAllowDraft(
                eq("order_runtime"), eq(9001L), eq(Map.of("flowStatus", "NEED_MODIFY")));
        verify(linkMapper).updateById(link);
    }

    @Test
    @DisplayName("approval task after resubmit repairs both link and record to IN_PROCESS")
    void approvalTaskRepairsInProcessState() {
        link.setFlowStatus(BusinessDocumentFlowStatus.NEED_MODIFY.getCode());
        when(dynamicCrudService.selectByIdAllowDraft("order_runtime", 9001L))
                .thenReturn(Map.of("id", 9001L, "flowStatus", "NEED_MODIFY"));

        service.handleFlowEngineEvent(taskCreated("managerApprove", "经理审批", "200"));

        assertEquals(BusinessDocumentFlowStatus.IN_PROCESS.getCode(), link.getFlowStatus());
        verify(dynamicCrudService).updateInternalFieldsByIdAllowDraft(
                eq("order_runtime"), eq(9001L), eq(Map.of("flowStatus", "IN_PROCESS")));
        verify(linkMapper).updateById(link);
    }

    @Test
    @DisplayName("delayed task event never overwrites a terminal link")
    void terminalLinkIgnoresDelayedTaskEvent() {
        link.setFlowStatus(BusinessDocumentFlowStatus.APPROVED.getCode());

        service.handleFlowEngineEvent(taskCreated("Forge_InitiatorModify", "发起人修改", "100"));

        assertEquals(BusinessDocumentFlowStatus.APPROVED.getCode(), link.getFlowStatus());
        verify(linkMapper, never()).updateById(any(AiBusinessFlowInstanceLink.class));
    }

    private FlowEventContext taskCreated(String taskDefKey, String taskName, String assigneeId) {
        return FlowEventContext.builder()
                .event(FlowCallback.ON_TASK_CREATED)
                .tenantId(1L)
                .processInstanceId("flow-instance-1")
                .businessKey("order:9001:R1")
                .taskId("task-" + taskDefKey)
                .taskDefKey(taskDefKey)
                .taskName(taskName)
                .assigneeId(assigneeId)
                .build();
    }

    @Test
    void canceledEventWritesRecordAndLinkWithoutDocumentConfiguration() {
        FlowEventContext event = FlowEventContext.builder().event(FlowCallback.ON_CANCELED)
                .tenantId(1L).processInstanceId("flow-instance-1").businessKey("order:9001").build();
        service.handleFlowEngineEvent(event);
        assertEquals("CANCELED", link.getFlowStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(link.getEndTime());
        verify(dynamicCrudService).updateInternalFieldsByIdAllowDraft(
                "order_runtime", 9001L, Map.of("flowStatus", "CANCELED"));
        verify(linkMapper).updateById(link);
    }

    @Test
    void failedStatusWriteMustAbortCallbackBeforeUpdatingLink() {
        org.mockito.Mockito.doThrow(new IllegalStateException("write failed")).when(dynamicCrudService)
                .updateInternalFieldsByIdAllowDraft(any(), any(), any());
        FlowEventContext event = FlowEventContext.builder().event(FlowCallback.ON_CANCELED)
                .tenantId(1L).processInstanceId("flow-instance-1").businessKey("order:9001").build();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.handleFlowEngineEvent(event));
        verify(linkMapper, never()).updateById(any(AiBusinessFlowInstanceLink.class));
    }

    @Test
    void duplicateCancellationKeepsTerminalStateAndDoesNotFinishLinkAgain() {
        FlowEventContext event = FlowEventContext.builder().event(FlowCallback.ON_CANCELED)
                .tenantId(1L).processInstanceId("flow-instance-1").businessKey("order:9001").build();
        service.handleFlowEngineEvent(event);
        java.time.LocalDateTime endTime = link.getEndTime();
        service.handleFlowEngineEvent(event);
        assertEquals("CANCELED", link.getFlowStatus());
        assertEquals(endTime, link.getEndTime());
        verify(linkMapper).updateById(link);
        verify(dynamicCrudService, org.mockito.Mockito.times(2)).updateInternalFieldsByIdAllowDraft(
                "order_runtime", 9001L, Map.of("flowStatus", "CANCELED"));
    }
}
