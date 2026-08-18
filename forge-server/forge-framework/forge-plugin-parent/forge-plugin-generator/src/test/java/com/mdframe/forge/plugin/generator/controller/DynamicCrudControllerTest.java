package com.mdframe.forge.plugin.generator.controller;

import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import com.mdframe.forge.starter.core.domain.RespInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicCrudControllerTest {

    @Test
    void createShouldReturnCreatedRecordForFollowUpBusinessActions() {
        DynamicCrudService crudService = mock(DynamicCrudService.class);
        BusinessEventPublisher eventPublisher = mock(BusinessEventPublisher.class);
        DynamicCrudController controller = new DynamicCrudController(crudService, null, eventPublisher);
        Map<String, Object> request = new LinkedHashMap<>(Map.of("memberPhone", "13800000000"));
        Map<String, Object> createdMain = Map.of("id", 1001L, "status", "DRAFT");
        Map<String, Object> created = Map.of("main", createdMain, "children", Map.of());
        when(crudService.insert("presale", request)).thenReturn(created);

        RespInfo<Map<String, Object>> response = controller.create("presale", request);

        assertSame(created, response.getData());
        assertSame(createdMain, ((Map<?, ?>) response.getData().get("main")));
        verify(eventPublisher).publishRecordCreated("presale", created);
    }

    @Test
    void pageShouldSeparateFlatSearchValuesFromPageSearchTypeMetadata() throws Exception {
        DynamicCrudController controller = new DynamicCrudController(null, null, null);
        DynamicCrudQuery query = new DynamicCrudQuery();
        Method buildQuery = DynamicCrudController.class.getDeclaredMethod(
                "buildQuery", DynamicCrudQuery.class, Map.class);
        buildQuery.setAccessible(true);
        DynamicCrudQuery captured = (DynamicCrudQuery) buildQuery.invoke(controller, query, Map.of(
                "pageNum", "1",
                "pageSize", "10",
                "designPreview", "1",
                "customerName", "星海",
                "_searchTypes", "{\"customerName\":\"like\"}"
        ));

        assertEquals(Map.of("customerName", "星海"), captured.getSearchParams());
        assertEquals(Map.of("customerName", "like"), captured.getSearchTypeMap());
        assertFalse(captured.getSearchParams().containsKey("_searchTypes"));
    }
}
