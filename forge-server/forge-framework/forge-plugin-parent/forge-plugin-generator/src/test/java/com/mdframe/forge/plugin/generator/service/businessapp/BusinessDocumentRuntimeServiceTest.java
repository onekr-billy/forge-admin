package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessFlowInstanceLinkMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessDocumentRuntimeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BusinessDocumentRuntimeService")
class BusinessDocumentRuntimeServiceTest {

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
}
