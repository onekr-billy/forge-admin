package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeDdlService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFieldVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessFlowStatusFieldService")
class BusinessFlowStatusFieldServiceTest {

    @Test
    @DisplayName("one-click ensure creates an independent managed field and additive column")
    void createsManagedFlowStatusWithoutTouchingBusinessStatus() {
        BusinessObjectDesignerService designerService = mock(BusinessObjectDesignerService.class);
        BusinessFieldDesignService fieldDesignService = mock(BusinessFieldDesignService.class);
        LowcodeDdlService ddlService = mock(LowcodeDdlService.class);
        LowcodeModelSchema initialSchema = schema(field("status", "status", "varchar", 32));
        LowcodeModelSchema updatedSchema = schema(
                field("status", "status", "varchar", 32),
                field("flowStatus", "flow_status", "varchar", 32));
        when(designerService.loadContext(77L)).thenReturn(
                context(initialSchema), context(updatedSchema));
        BusinessFieldVO resultField = flowStatusVO();
        when(fieldDesignService.listFields(77L)).thenReturn(List.of(resultField));
        BusinessFlowStatusFieldService service = new BusinessFlowStatusFieldService(
                designerService, fieldDesignService, ddlService);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(() -> SessionHelper.hasPermission("ai:lowcode:deploy-ddl")).thenReturn(true);

            BusinessFieldVO result = service.ensure(77L);

            assertEquals("flowStatus", result.getFieldCode());
        }
        ArgumentCaptor<BusinessFieldDTO> fieldCaptor = ArgumentCaptor.forClass(BusinessFieldDTO.class);
        verify(fieldDesignService).addField(org.mockito.ArgumentMatchers.eq(77L), fieldCaptor.capture());
        BusinessFieldDTO created = fieldCaptor.getValue();
        assertEquals("flowStatus", created.getFieldCode());
        assertEquals("flow_status", created.getColumnName());
        assertEquals("DRAFT", created.getDefaultValue());
        assertEquals("business_flow_status", created.getDictType());
        assertTrue(Boolean.TRUE.equals(created.getReadonly()));
        assertEquals("BUSINESS_FLOW", created.getAdvancedProps().get("managedBy"));
        verify(ddlService).executeAdditiveColumn(updatedSchema, "flow_status");
    }

    @Test
    @DisplayName("compatible managed field is reused idempotently")
    void reusesCompatibleFieldIdempotently() {
        BusinessObjectDesignerService designerService = mock(BusinessObjectDesignerService.class);
        BusinessFieldDesignService fieldDesignService = mock(BusinessFieldDesignService.class);
        LowcodeDdlService ddlService = mock(LowcodeDdlService.class);
        LowcodeModelSchema schema = schema(field("flowStatus", "flow_status", "varchar", 32));
        when(designerService.loadContext(77L)).thenReturn(context(schema));
        when(fieldDesignService.listFields(77L)).thenReturn(List.of(flowStatusVO()));
        BusinessFlowStatusFieldService service = new BusinessFlowStatusFieldService(
                designerService, fieldDesignService, ddlService);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(() -> SessionHelper.hasPermission("ai:lowcode:deploy-ddl")).thenReturn(true);
            service.ensure(77L);
        }

        verify(fieldDesignService, never()).addField(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        verify(ddlService).executeAdditiveColumn(schema, "flow_status");
    }

    @Test
    @DisplayName("incompatible flow status field fails before DDL")
    void rejectsIncompatibleFieldBeforeDdl() {
        BusinessObjectDesignerService designerService = mock(BusinessObjectDesignerService.class);
        BusinessFieldDesignService fieldDesignService = mock(BusinessFieldDesignService.class);
        LowcodeDdlService ddlService = mock(LowcodeDdlService.class);
        when(designerService.loadContext(77L)).thenReturn(
                context(schema(field("flowStatus", "flow_status", "bigint", null))));
        BusinessFlowStatusFieldService service = new BusinessFlowStatusFieldService(
                designerService, fieldDesignService, ddlService);

        try (MockedStatic<SessionHelper> session = mockStatic(SessionHelper.class)) {
            session.when(() -> SessionHelper.hasPermission("ai:lowcode:deploy-ddl")).thenReturn(true);
            BusinessException error = assertThrows(BusinessException.class, () -> service.ensure(77L));
            assertTrue(error.getMessage().contains("类型不兼容"));
        }

        verify(ddlService, never()).executeAdditiveColumn(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private BusinessObjectDesignerService.DesignerContext context(LowcodeModelSchema schema) {
        BusinessObjectDesignerService.DesignerContext context = new BusinessObjectDesignerService.DesignerContext();
        context.setModelSchema(schema);
        return context;
    }

    private LowcodeModelSchema schema(LowcodeFieldSchema... fields) {
        LowcodeModelSchema schema = new LowcodeModelSchema();
        schema.setFields(new ArrayList<>(List.of(fields)));
        return schema;
    }

    private LowcodeFieldSchema field(String fieldCode, String columnName, String dataType, Integer length) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(fieldCode);
        field.setColumnName(columnName);
        field.setDataType(dataType);
        field.setLength(length);
        return field;
    }

    private BusinessFieldVO flowStatusVO() {
        BusinessFieldVO field = new BusinessFieldVO();
        field.setFieldCode("flowStatus");
        field.setColumnName("flow_status");
        return field;
    }
}
