package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.dto.AiCrudConfigRenderVO;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessAppVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessApplicationRuntimeConfigOverlayServiceTest {

    @Test
    void overlaysFlowInteractionFromThePublishedApplicationSnapshot() throws Exception {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(mapper, applicationObjectService, runtimeService, objectMapper, null);

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
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(mapper, applicationObjectService, runtimeService, new ObjectMapper(), null);

        AiBusinessApp entry = new AiBusinessApp();
        entry.setConfigKey("other_config");
        entry.setApplicationId(88L);
        when(mapper.selectEntityById(eq(1L), eq(7L))).thenReturn(entry);

        AiCrudConfigRenderVO render = new AiCrudConfigRenderVO();
        service.overlay("ps_presale_order", 7L, render);

        assertNull(render.getOptions());
    }

    @Test
    void overlaysPublishedManualStartActionsByObjectCodeWithoutAppContext() {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService projection =
                mock(com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService.class);
        when(projection.compileForRender(eq("ps_order"), eq("order"), isNull(), isNull(), eq(false))).thenReturn(List.of(Map.of(
                "key", "startProcess:submit_approval",
                "label", "提交审批",
                "actionType", "START_PROCESS",
                "position", "row")));
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(mapper, applicationObjectService, runtimeService, new ObjectMapper(), projection);

        AiCrudConfigRenderVO render = new AiCrudConfigRenderVO();
        render.setObjectCode("order");
        render.setColumnsSchema(List.of(Map.of(
                "key", "actions",
                "title", "操作",
                "actions", List.of(Map.of("key", "edit", "label", "编辑")))));
        service.overlay("ps_order", null, render);

        Map<?, ?> options = (Map<?, ?>) render.getOptions();
        List<?> rowActions = (List<?>) options.get("rowActions");
        assertEquals("startProcess:submit_approval", ((Map<?, ?>) rowActions.get(0)).get("key"));
        List<?> columns = (List<?>) render.getColumnsSchema();
        List<?> columnActions = (List<?>) ((Map<?, ?>) columns.get(0)).get("actions");
        assertTrue(columnActions.stream().anyMatch(item ->
                "startProcess:submit_approval".equals(((Map<?, ?>) item).get("key"))));
    }

    @Test
    void keepsDetailStartActionsOutOfTheListActionColumn() {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService projection =
                mock(com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService.class);
        when(projection.compileForRender(eq("ps_order"), eq("order"), isNull(), isNull(), eq(false))).thenReturn(List.of(Map.of(
                "key", "startProcess:submit_approval:detail",
                "label", "提交审批",
                "actionType", "START_PROCESS",
                "position", "detail")));
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(mapper, applicationObjectService, runtimeService, new ObjectMapper(), projection);

        AiCrudConfigRenderVO render = new AiCrudConfigRenderVO();
        render.setObjectCode("order");
        render.setColumnsSchema(List.of(Map.of(
                "key", "actions",
                "title", "操作",
                "actions", List.of(Map.of("key", "edit", "label", "编辑")))));
        service.overlay("ps_order", null, render);

        Map<?, ?> options = (Map<?, ?>) render.getOptions();
        List<?> detailActions = (List<?>) options.get("detailActions");
        assertEquals("startProcess:submit_approval:detail", ((Map<?, ?>) detailActions.get(0)).get("key"));
        List<?> rowActions = (List<?>) options.get("rowActions");
        assertTrue(rowActions.isEmpty());
        List<?> columnActions = (List<?>) ((Map<?, ?>) ((List<?>) render.getColumnsSchema()).get(0)).get("actions");
        assertTrue(columnActions.stream().noneMatch(item ->
                "startProcess:submit_approval:detail".equals(((Map<?, ?>) item).get("key"))));
    }

    @Test
    void keepsFormStartActionsOutOfTheListAndDetailActions() {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService projection =
                mock(com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService.class);
        when(projection.compileForRender(eq("ps_order"), eq("order"), isNull(), isNull(), eq(false))).thenReturn(List.of(Map.of(
                "key", "startProcess:submit_approval:form",
                "label", "提交审批",
                "actionType", "START_PROCESS",
                "position", "form")));
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(mapper, applicationObjectService, runtimeService, new ObjectMapper(), projection);

        AiCrudConfigRenderVO render = new AiCrudConfigRenderVO();
        render.setObjectCode("order");
        render.setColumnsSchema(List.of(Map.of(
                "key", "actions",
                "title", "操作",
                "actions", List.of(Map.of("key", "edit", "label", "编辑")))));
        service.overlay("ps_order", null, render);

        Map<?, ?> options = (Map<?, ?>) render.getOptions();
        assertEquals("startProcess:submit_approval:form", ((Map<?, ?>) ((List<?>) options.get("formActions")).get(0)).get("key"));
        assertTrue(((List<?>) options.get("detailActions")).isEmpty());
        assertTrue(((List<?>) options.get("rowActions")).isEmpty());
    }

    @Test
    void overlaysProcessActionsForAnApplicationObjectWithoutLegacyEntry() {
        BusinessAppMapper mapper = mock(BusinessAppMapper.class);
        BusinessApplicationRuntimeService runtimeService = mock(BusinessApplicationRuntimeService.class);
        BusinessApplicationObjectService applicationObjectService = mock(BusinessApplicationObjectService.class);
        com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService projection =
                mock(com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessRuntimeActionProjectionService.class);
        BusinessApplicationObjectVO object = new BusinessApplicationObjectVO();
        object.setConfigKey("ps_order");
        when(applicationObjectService.list(88L)).thenReturn(List.of(object));
        when(projection.compileForRender(eq("ps_order"), eq("order"), isNull(), eq(88L), eq(false))).thenReturn(List.of(Map.of(
                "key", "startProcess:submit_approval:detail",
                "label", "提交审批",
                "actionType", "START_PROCESS",
                "position", "detail")));
        BusinessApplicationRuntimeConfigOverlayService service =
                new BusinessApplicationRuntimeConfigOverlayService(
                        mapper, applicationObjectService, runtimeService, new ObjectMapper(), projection);

        AiCrudConfigRenderVO render = new AiCrudConfigRenderVO();
        render.setObjectCode("order");
        service.overlay("ps_order", null, 88L, render, false);

        Map<?, ?> options = (Map<?, ?>) render.getOptions();
        assertEquals("startProcess:submit_approval:detail",
                ((Map<?, ?>) ((List<?>) options.get("detailActions")).get(0)).get("key"));
    }
}
