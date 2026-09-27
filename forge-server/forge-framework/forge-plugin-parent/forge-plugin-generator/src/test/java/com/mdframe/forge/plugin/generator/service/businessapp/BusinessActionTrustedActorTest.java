package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BusinessActionTrustedActorTest {

    @Test
    void startFlowUsesServerProjectedActorInsteadOfDefaultPlatformUser() {
        BusinessFlowService flowService = mock(BusinessFlowService.class);
        BusinessFlowRuntimeVO runtime = new BusinessFlowRuntimeVO();
        runtime.setFlowModelKey("order-approval");
        when(flowService.startFlowFromTrigger(eq("order-approval"), eq("order:100"), eq("提交审批"),
                eq(42L), eq("alice"), eq(9L), any(JSONObject.class))).thenReturn(runtime);
        StartFlowActionStepExecutor executor = new StartFlowActionStepExecutor(flowService);

        executor.execute(context(Map.of("userId", 42L, "username", "alice")), startFlowStep());

        verify(flowService).startFlowFromTrigger(eq("order-approval"), eq("order:100"), eq("提交审批"),
                eq(42L), eq("alice"), eq(9L), any(JSONObject.class));
    }

    @Test
    void missingTrustedActorFailsClosedInsteadOfUsingUserOne() {
        BusinessFlowService flowService = mock(BusinessFlowService.class);
        StartFlowActionStepExecutor executor = new StartFlowActionStepExecutor(flowService);
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getUserId).thenReturn(null);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> executor.execute(context(Map.of()), startFlowStep()));

            assertEquals("业务动作缺少可信执行用户", error.getMessage());
            verifyNoInteractions(flowService);
        }
    }

    @Test
    void capabilityServiceUserIsAcceptedAsTrustedActor() {
        BusinessActionExecutionContext context = context(Map.of());
        context.setCapabilityServiceUserId(77L);

        assertEquals(77L, BusinessActionStepConfigHelper.requireActorUserId(context));
        assertEquals("user-77", BusinessActionStepConfigHelper.resolveActorUsername(context, 77L));
    }

    @Test
    void sendMessageWithoutTrustedActorDoesNotFallBackToUserOne() {
        BusinessMessageChannelService channelService = mock(BusinessMessageChannelService.class);
        BusinessMessageChannelStatus channel = new BusinessMessageChannelStatus();
        channel.setChannelCode("internal");
        channel.setSendChannel("WEB");
        channel.setTodo(false);
        when(channelService.resolveChannel(null)).thenReturn(channel);
        SendMessageActionStepExecutor executor = new SendMessageActionStepExecutor(channelService);
        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepConfig(Map.of("templateCode", "order_submitted"));
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(SessionHelper::getUserId).thenReturn(null);

            BusinessException error = assertThrows(BusinessException.class,
                    () -> executor.execute(context(Map.of()), step));

            assertEquals("业务动作缺少可信执行用户", error.getMessage());
            verify(channelService).resolveChannel(null);
            verify(channelService, never()).sendInternalMessage(any());
        }
    }

    @Test
    void invalidContextTenantFailsClosed() {
        BusinessActionExecutionContext context = context(Map.of("userId", 42L));
        context.setTenantId(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessActionStepConfigHelper.requireTenantId(context));

        assertEquals("业务动作缺少可信租户上下文", error.getMessage());
    }

    private BusinessActionExecutionContext context(Map<String, Object> systemContext) {
        BusinessActionExecuteDTO request = new BusinessActionExecuteDTO();
        request.setObjectCode("order");
        request.setRecordId("100");
        BusinessActionExecutionContext context = new BusinessActionExecutionContext();
        context.setTenantId(9L);
        context.setCorrelationId("correlation-1");
        context.setRequest(request);
        context.setSystemContext(new LinkedHashMap<>(systemContext));
        return context;
    }

    private BusinessActionStepDTO startFlowStep() {
        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepConfig(Map.of(
                "flowModelKey", "order-approval",
                "title", "提交审批"));
        return step;
    }
}
