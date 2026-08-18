package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.dto.AiCrudConfigRenderVO;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessAppVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessApplicationRuntimeConfigOverlayServiceTest {

    @Test
    void overlaysFlowInteractionFromThePublishedApplicationSnapshot() throws Exception {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(mapper, runtimeService, objectMapper);

        AiBusinessApp entry = new AiBusinessApp();
        entry.setConfigKey("ps_presale_order");
        entry.setApplicationId(88L);
        when(mapper.selectEntityById(eq(1L), eq(7L))).thenReturn(entry);

        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setOptions(objectMapper.writeValueAsString(Map.of(
                "inAppBuilder", Map.of("flowInteraction", Map.of(
                        "timeline", Map.of("enabled", true),
                        "approvalActions", List.of(Map.of("operation", "approve")))))));
        BusinessApplicationRuntimeVO runtime = new BusinessApplicationRuntimeVO();
        runtime.setApplication(application);
        BusinessAppVO publishedEntry = new BusinessAppVO();
        publishedEntry.setId(7L);
        runtime.setEntries(List.of(publishedEntry));
        when(runtimeService.runtimeById(88L)).thenReturn(runtime);

        AiCrudConfigRenderVO render = new AiCrudConfigRenderVO();
        render.setOptions(Map.of("formOpenMode", "drawer"));
        service.overlay("ps_presale_order", 7L, render);

        Map<?, ?> options = (Map<?, ?>) render.getOptions();
        assertEquals("drawer", options.get("formOpenMode"));
        assertTrue(((Map<?, ?>) options.get("flowInteraction")).containsKey("timeline"));
    }

    @Test
    void ignoresAnEntryThatDoesNotBelongToTheRequestedConfig() {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(mapper, runtimeService, new ObjectMapper());

        AiBusinessApp entry = new AiBusinessApp();
        entry.setConfigKey("other_config");
        entry.setApplicationId(88L);
        when(mapper.selectEntityById(eq(1L), eq(7L))).thenReturn(entry);

        AiCrudConfigRenderVO render = new AiCrudConfigRenderVO();
        service.overlay("ps_presale_order", 7L, render);

        assertNull(render.getOptions());
    }
}
