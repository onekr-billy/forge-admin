package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.flow.client.FlowClient;
import com.mdframe.forge.flow.client.FlowResult;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFlowStartDTO;
import com.mdframe.forge.plugin.generator.enums.BusinessDocumentFlowStatus;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowStartCoordinatorTest {

    private FlowClient flowClient;
    private BusinessFlowInstanceLinkMapper linkMapper;
    private DynamicCrudService dynamicCrudService;
    private BusinessFlowRuntimeContextResolver runtimeContextResolver;
    private BusinessFlowStatusRepairService statusRepairService;
    private BusinessFlowStatusTransitionService statusTransitionService;
    private BusinessFlowFormAssetAssembler formAssetAssembler;
    private BusinessFlowStartCoordinator coordinator;
    private AiCrudConfig runtimeConfig;
    private BusinessFlowStartDTO request;

    @BeforeEach
    void setUp() {
        flowClient = mock(FlowClient.class);
        linkMapper = mock(BusinessFlowInstanceLinkMapper.class);
        dynamicCrudService = mock(DynamicCrudService.class);
        runtimeContextResolver = mock(BusinessFlowRuntimeContextResolver.class);
        statusRepairService = mock(BusinessFlowStatusRepairService.class);
        statusTransitionService = mock(BusinessFlowStatusTransitionService.class);
        formAssetAssembler = mock(BusinessFlowFormAssetAssembler.class);
        BusinessBindingMapper bindingMapper = mock(BusinessBindingMapper.class);
        BusinessFlowBindingResolver bindingResolver = new BusinessFlowBindingResolver(bindingMapper);

        runtimeConfig = new AiCrudConfig();
        runtimeConfig.setConfigKey("purchase_runtime");
        runtimeConfig.setObjectCode("purchase");
        when(runtimeContextResolver.resolve(1L, "purchase", false)).thenReturn(
                new BusinessRuntimeContext(
                        "purchase", "purchase", "purchase_runtime", null, runtimeConfig, null));
        when(dynamicCrudService.selectById("purchase_runtime", 42L))
                .thenReturn(Map.of("orderNo", "PO-42"));
        AiBusinessBinding binding = new AiBusinessBinding();
        binding.setBindingType("FLOW");
        binding.setBindingKey("purchase_approval");
        binding.setBindingConfig("{\"flowModelKey\":\"purchase_approval\"}");
        when(bindingMapper.selectBindingByTypeAndCode(1L, "OBJECT", "purchase", "FLOW"))
                .thenReturn(binding);
        when(formAssetAssembler.resolveRuntimeCrudObjectName(null, runtimeConfig)).thenReturn("采购单");

        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        coordinator = new BusinessFlowStartCoordinator(
                () -> flowClient,
                linkMapper,
                mock(BusinessDocumentConfigService.class),
                mock(BusinessDocumentRuntimeService.class),
                dynamicCrudService,
                runtimeContextResolver,
                mock(BusinessRuntimeConfigResolver.class),
                bindingResolver,
                formAssetAssembler,
                statusRepairService,
                statusTransitionService,
                beans.getBeanProvider(RedissonClient.class),
                () -> 7L,
                () -> 9L,
                () -> "张三");

        request = new BusinessFlowStartDTO();
        request.setObjectCode("purchase");
        request.setRecordId(42L);
    }

    @Test
    void startsNormalFlowAndPersistsLinkBeforeStatusTransition() {
        when(flowClient.startProcess(
                eq("purchase_approval"), eq("purchase:42"), any(), anyMap(),
                eq("7"), eq("张三"), isNull(), isNull()))
                .thenReturn(FlowResult.success("process-42"));

        BusinessFlowRuntimeVO result = coordinator.start(
                request, true, null, null, 1L, false, false);

        assertEquals("process-42", result.getProcessInstanceId());
        assertEquals(BusinessDocumentFlowStatus.RUNNING.getCode(), result.getFlowStatus());
        InOrder order = inOrder(linkMapper, statusTransitionService);
        order.verify(linkMapper).insert(any(AiBusinessFlowInstanceLink.class));
        order.verify(statusTransitionService).updateBusinessFlowStatus(
                isNull(), eq(runtimeConfig), any(), eq(42L),
                eq(BusinessDocumentFlowStatus.IN_PROCESS.getCode()));
        verify(statusRepairService).syncConfiguredStatusField(
                eq(runtimeConfig), eq(42L), eq(request.getVariables()),
                eq(BusinessDocumentFlowStatus.IN_PROCESS.getCode()));
    }

    @Test
    void capabilityStartUsesStableBusinessKeyAndDelegatedEndpoint() {
        when(flowClient.startProcessForDelegatedUser(
                eq("purchase_approval"), eq("purchase:42"), eq("purchase"), any(), anyMap()))
                .thenReturn(FlowResult.success("delegated-process-42"));

        BusinessFlowRuntimeVO result = coordinator.start(
                request, true, 7L, "张三", 1L, true, false);

        assertEquals("delegated-process-42", result.getProcessInstanceId());
        verify(flowClient, never()).startProcess(
                any(), any(), any(), anyMap(), any(), any(), any(), any());
    }

    @Test
    void runningLinkPreventsDuplicateRemoteStart() {
        AiBusinessFlowInstanceLink running = new AiBusinessFlowInstanceLink();
        running.setProcessInstanceId("existing-process");
        running.setFlowStatus(BusinessDocumentFlowStatus.RUNNING.getCode());
        when(linkMapper.selectLatestByBusinessKey(1L, "purchase:42")).thenReturn(running);

        BusinessFlowRuntimeVO result = coordinator.start(
                request, true, null, null, 1L, false, false);

        assertEquals("existing-process", result.getProcessInstanceId());
        verify(flowClient, never()).startProcess(
                any(), any(), any(), anyMap(), any(), any(), any(), any());
        verify(linkMapper, never()).insert(any(AiBusinessFlowInstanceLink.class));
    }
}
