package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodePolicyService;
import com.mdframe.forge.starter.datascope.context.DataScopeContext;
import com.mdframe.forge.starter.datascope.service.IDataScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DynamicDataScopeService")
class DynamicDataScopeServiceTest {

    private final IDataScopeService dataScopeService = mock(IDataScopeService.class);
    private final DynamicDataScopeService service = new DynamicDataScopeService(
            dataScopeService,
            mock(DynamicCrudRepository.class),
            new ObjectMapper(),
            new LowcodePolicyService());

    @Test
    @DisplayName("FOLLOW_SYSTEM resolves the role override with the business object module code")
    void resolvesObjectModuleOverride() {
        AiCrudConfig config = config("ORDER");
        when(dataScopeService.getCurrentUserDataScope("ai:business:ORDER")).thenReturn(allScope());

        assertNull(service.buildCondition(config, "biz_order", "o"));

        verify(dataScopeService).getCurrentUserDataScope("ai:business:ORDER");
        verify(dataScopeService, never()).getCurrentUserDataScope();
    }

    @Test
    @DisplayName("missing object code safely falls back to the role default data scope")
    void missingObjectCodeFallsBackToDefaultScope() {
        AiCrudConfig config = config(" ");
        when(dataScopeService.getCurrentUserDataScope()).thenReturn(allScope());

        assertNull(service.buildCondition(config, "biz_order", null));

        verify(dataScopeService).getCurrentUserDataScope();
        verify(dataScopeService, never()).getCurrentUserDataScope("ai:business:ORDER");
    }

    private AiCrudConfig config(String objectCode) {
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("order_list");
        config.setBuildMode("LOWCODE");
        config.setObjectCode(objectCode);
        config.setModelSchema("{\"policies\":{\"dataScope\":\"FOLLOW_SYSTEM\"}}");
        return config;
    }

    private DataScopeContext allScope() {
        return new DataScopeContext(9L, List.of(2L), 2L, List.of(7L), 1,
                Set.of(), 1L, null, null, null);
    }
}
