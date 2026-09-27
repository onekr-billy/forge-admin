package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowStatusRepairServiceTest {

    private AiCrudConfigMapper configMapper;
    private DynamicCrudService crudService;
    private BusinessFlowStatusRepairService service;
    private AiBusinessFlowInstanceLink link;

    @BeforeEach
    void setUp() {
        configMapper = mock(AiCrudConfigMapper.class);
        crudService = mock(DynamicCrudService.class);
        service = new BusinessFlowStatusRepairService(
                new BusinessRuntimeConfigResolver(configMapper, () -> 1L),
                crudService, mock(BusinessDocumentConfigService.class));
        link = new AiBusinessFlowInstanceLink();
        link.setTenantId(1L);
        link.setObjectCode("order");
        link.setRecordId(9001L);
    }

    @Test
    void prefersPublishedSnapshotOverDraftRuntime() {
        AiCrudConfig published = config("published_order");
        when(configMapper.selectPublishedByObjectCodeOrConfigKey(1L, "order_runtime"))
                .thenReturn(published);

        assertSame(published, service.resolveStatusWriteConfig(
                link, Map.of("configKey", "order_runtime"), null));
    }

    @Test
    void fallsBackToDraftRuntimeForUnpublishedFlow() {
        AiCrudConfig draft = config("order_runtime");
        when(configMapper.selectRuntimeByObjectCodeOrConfigKey(1L, "order_runtime"))
                .thenReturn(draft);

        assertSame(draft, service.resolveStatusWriteConfig(
                link, Map.of("configKey", "order_runtime"), null));
    }

    @Test
    void permitsOnlyDedicatedFlowStatusField() {
        assertEquals("flow_status", service.configuredStatusField(Map.of("statusField", "flow_status")));
        assertThrows(BusinessException.class,
                () -> service.configuredStatusField(Map.of("flowStatusField", "status")));
    }

    @Test
    void writeFailurePropagatesBeforeCallbackCanUpdateLink() {
        AiCrudConfig runtime = config("order_runtime");
        Map<String, Object> update = Map.of("flowStatus", "CANCELED");
        doThrow(new IllegalStateException("write failed")).when(crudService)
                .updateInternalFieldsByIdAllowDraft("order_runtime", 9001L, update);

        assertThrows(IllegalStateException.class, () -> service.syncConfiguredStatusField(
                runtime, 9001L, Map.of("flowStatusField", "flowStatus"), "CANCELED"));
        verify(crudService).updateInternalFieldsByIdAllowDraft("order_runtime", 9001L, update);
    }

    @Test
    void readsExistingTerminalRecordStateBeforeDelayedTaskEvent() {
        when(crudService.selectByIdAllowDraft("order_runtime", 9001L))
                .thenReturn(Map.of("flowStatus", "APPROVED"));

        assertEquals("APPROVED", service.resolveCurrentDocumentStatusKey(
                link, null, config("order_runtime"), Map.of("flowStatusField", "flowStatus")));
    }

    private AiCrudConfig config(String configKey) {
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey(configKey);
        return config;
    }
}
