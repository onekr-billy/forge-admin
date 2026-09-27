package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BusinessFlowTrustedIdentityTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void missingTenantFailsBeforeReadingBindings() {
        BusinessBindingMapper bindingMapper = mock(BusinessBindingMapper.class);
        BusinessFlowService service = service(bindingMapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listBusinessBindingsByModelKey("order-approval"));

        assertEquals("业务流程缺少隔离的可信租户上下文", error.getMessage());
        verifyNoInteractions(bindingMapper);
    }

    @Test
    void explicitTenantContextIsUsedWithoutDefaultingToTenantOne() {
        TenantContextHolder.setTenantId(9L);
        BusinessBindingMapper bindingMapper = mock(BusinessBindingMapper.class);
        when(bindingMapper.selectFlowBindingsByModelKey(9L, "order-approval")).thenReturn(List.of());
        BusinessFlowService service = service(bindingMapper);

        assertEquals(List.of(), service.listBusinessBindingsByModelKey("order-approval"));

        verify(bindingMapper).selectFlowBindingsByModelKey(9L, "order-approval");
    }

    @Test
    void triggerStartRejectsInvalidTenantBeforeCallingFlowClient() {
        BusinessFlowService service = service(mock(BusinessBindingMapper.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.startFlowFromTrigger(
                        "order-approval", "order:100", "订单审批",
                        7L, "alice", 0L, new JSONObject()));

        assertEquals("业务流程租户上下文无效", error.getMessage());
    }

    @Test
    void triggerStartRejectsMissingActorInsteadOfSendingNullInitiator() {
        BusinessFlowService service = service(mock(BusinessBindingMapper.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.startFlowFromTrigger(
                        "order-approval", "order:100", "订单审批",
                        null, "system", 9L, new JSONObject()));

        assertEquals("业务流程缺少可信发起人", error.getMessage());
    }

    @Test
    void businessProcessApprovalRejectsMissingActor() {
        BusinessFlowService service = service(mock(BusinessBindingMapper.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.startFromBusinessProcess(
                        "order-approval", "order:100", "订单审批",
                        null, "system", 9L, new JSONObject()));

        assertEquals("业务流程缺少可信发起人", error.getMessage());
    }

    @Test
    void explicitTenantCannotBypassAnIgnoreTenantScope() {
        TenantContextHolder.setIgnore(true);
        BusinessFlowService service = service(mock(BusinessBindingMapper.class));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.startFlowFromTrigger(
                        "order-approval", "order:100", "订单审批",
                        7L, "alice", 9L, new JSONObject()));

        assertEquals("业务流程缺少隔离的可信租户上下文", error.getMessage());
    }

    private BusinessFlowService service(BusinessBindingMapper bindingMapper) {
        return new BusinessFlowService(
                bindingMapper,
                mock(BusinessFlowInstanceLinkMapper.class),
                mock(AiCrudConfigMapper.class),
                mock(BusinessObjectMapper.class),
                mock(BusinessDocumentConfigService.class),
                mock(BusinessDocumentRuntimeService.class),
                mock(DynamicCrudService.class),
                mock(BusinessFieldDesignService.class),
                mock(BusinessFlowVariableResolver.class),
                mock(BusinessCodeFormProviderRegistry.class),
                mock(ApplicationEventPublisher.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));
    }
}
