package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeRuntimeConfigBuilder;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("AiCrudConfigService design preview permission")
class AiCrudConfigServiceDesignPreviewTest {

    @Test
    @DisplayName("business application editors can preview draft CRUD configs")
    void applicationEditorCanPreviewDraftCrudConfig() {
        AiCrudConfigService service = new AiCrudConfigService(null, null, null, null, null, null);
        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(() -> SessionHelper.hasPermission("ai:businessObject:design")).thenReturn(false);
            session.when(() -> SessionHelper.hasPermission("ai:businessApplication:edit")).thenReturn(true);

            assertTrue(service.hasDesignPreviewPermission());
            assertDoesNotThrow(service::assertDesignPreviewPermission);
        }
    }

    @Test
    @DisplayName("legacy flowStatus fields without managed metadata are included in published columns")
    void legacyFlowStatusFieldHealsPublishedRuntimeColumns() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiCrudConfigVersionMapper versions = mock(AiCrudConfigVersionMapper.class);
        LowcodeRuntimeConfigBuilder runtimeBuilder = mock(LowcodeRuntimeConfigBuilder.class);
        AiCrudConfigService service = new AiCrudConfigService(
                objectMapper, null, runtimeBuilder, null, versions, null);

        AiCrudConfig draft = new AiCrudConfig();
        draft.setId(77L);
        draft.setTenantId(1L);
        draft.setConfigKey("legacy_order");
        draft.setBuildMode("LOWCODE");
        draft.setPublishStatus("PUBLISHED");
        draft.setPublishedVersion(1);
        draft.setModelSchema("{\"fields\":[{\"field\":\"flowStatus\",\"columnName\":\"flow_status\","
                + "\"dictType\":\"business_flow_status\",\"fieldStatus\":\"ENABLED\",\"listVisible\":true}]}");
        draft.setPageSchema("{\"layoutType\":\"simple-crud\",\"zones\":[{\"zoneKey\":\"table\","
                + "\"fieldRefs\":[\"flowStatus\"]}]}");

        AiCrudConfigVersion version = new AiCrudConfigVersion();
        version.setVersionNo(1);
        version.setModelSchema("{\"fields\":[{\"field\":\"orderNo\",\"columnName\":\"order_no\"}]}");
        version.setPageSchema(draft.getPageSchema());
        version.setColumnsSchema("[{\"key\":\"orderNo\",\"dataIndex\":\"orderNo\"},"
                + "{\"key\":\"actions\",\"dataIndex\":\"actions\"}]");
        version.setSearchSchema("[]");
        version.setEditSchema("[]");
        version.setApiConfig("{}");
        when(versions.selectVersionByNo(1L, 77L, 1)).thenReturn(version);
        when(runtimeBuilder.buildManagedFlowStatusColumn(any(), any(), any()))
                .thenReturn(Map.of("key", "flowStatus", "dataIndex", "flowStatus"));

        AiCrudConfig published = service.resolvePublishedRuntimeConfig(draft);

        assertTrue(published.getModelSchema().contains("BUSINESS_FLOW"));
        assertTrue(published.getColumnsSchema().contains("flowStatus"));
    }
}
