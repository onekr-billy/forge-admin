package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessRecordSelectorQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessRecordSelectorResultVO;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessRecordSelectorService")
class BusinessRecordSelectorServiceTest {

    private final BusinessObjectMapper businessObjectMapper = Mockito.mock(BusinessObjectMapper.class);
    private final DynamicCrudService dynamicCrudService = Mockito.mock(DynamicCrudService.class);
    private final BusinessPermissionService permissionService = Mockito.mock(BusinessPermissionService.class);
    private final BusinessRecordSelectorService service = new BusinessRecordSelectorService(
            businessObjectMapper,
            dynamicCrudService,
            permissionService);

    @Test
    @DisplayName("query projects raw record to allowed selector fields only")
    void queryProjectsRawRecordToAllowedFieldsOnly() {
        when(businessObjectMapper.selectFirstByObjectCode(1L, "supplier")).thenReturn(object("supplier"));
        when(permissionService.hasDocumentActionPermission("supplier", "VIEW")).thenReturn(true);
        Page<Map<String, Object>> page = new Page<>(1, 10, 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1001L);
        row.put("name", "供应商A");
        row.put("price", 1200L);
        row.put("secret_token", "hidden");
        row.put("tenantId", 1L);
        page.setRecords(List.of(row));
        when(dynamicCrudService.selectPage(eq("runtime_supplier"), any(PageQuery.class), any(DynamicCrudQuery.class)))
                .thenReturn(page);

        BusinessRecordSelectorQueryDTO dto = new BusinessRecordSelectorQueryDTO();
        dto.setObjectCode("supplier");
        dto.setDisplayFields(List.of("name:名称"));
        BusinessRecordSelectorQueryDTO.FieldMappingDTO mapping = new BusinessRecordSelectorQueryDTO.FieldMappingDTO();
        mapping.setSourceField("price");
        mapping.setTargetField("unitPrice");
        dto.setFieldMappings(List.of(mapping));

        BusinessRecordSelectorResultVO result = service.query(dto, new PageQuery());

        assertEquals("supplier", result.getObjectCode());
        assertEquals(1, result.getRecords().size());
        Map<String, Object> record = result.getRecords().get(0);
        assertEquals("1001", record.get("id"));
        assertEquals("供应商A", record.get("name"));
        Map<?, ?> raw = (Map<?, ?>) record.get("_raw");
        assertEquals("1001", raw.get("id"));
        assertEquals("供应商A", raw.get("name"));
        assertEquals("1200", raw.get("price"));
        assertFalse(raw.containsKey("secret_token"));
        assertFalse(raw.containsKey("tenantId"));
        assertEquals(Map.of("price", "unitPrice"), result.getFieldMappings());
        assertEquals(null, result.getConfigKey());
    }

    @Test
    @DisplayName("query rejects object without view permission")
    void queryRejectsObjectWithoutViewPermission() {
        when(businessObjectMapper.selectFirstByObjectCode(1L, "supplier")).thenReturn(object("supplier"));
        when(permissionService.hasDocumentActionPermission("supplier", "VIEW")).thenReturn(false);

        BusinessRecordSelectorQueryDTO dto = new BusinessRecordSelectorQueryDTO();
        dto.setObjectCode("supplier");

        BusinessException error = assertThrows(BusinessException.class, () -> service.query(dto, new PageQuery()));

        assertEquals("无权限查询选择器对象: 供应商", error.getMessage());
        verify(dynamicCrudService, never()).selectPage(any(), any(), any());
    }

    @Test
    @DisplayName("query ignores unrequested order by column")
    void queryIgnoresUnrequestedOrderByColumn() {
        when(businessObjectMapper.selectFirstByObjectCode(1L, "supplier")).thenReturn(object("supplier"));
        when(permissionService.hasDocumentActionPermission("supplier", "VIEW")).thenReturn(true);
        Page<Map<String, Object>> page = new Page<>(1, 10, 0);
        page.setRecords(List.of());
        when(dynamicCrudService.selectPage(eq("runtime_supplier"), any(PageQuery.class), any(DynamicCrudQuery.class)))
                .thenReturn(page);

        BusinessRecordSelectorQueryDTO dto = new BusinessRecordSelectorQueryDTO();
        dto.setObjectCode("supplier");
        dto.setDisplayFields(List.of("name:名称"));
        dto.setOrderByColumn("secret_token");

        service.query(dto, new PageQuery());

        ArgumentCaptor<PageQuery> pageQueryCaptor = ArgumentCaptor.forClass(PageQuery.class);
        verify(dynamicCrudService).selectPage(eq("runtime_supplier"), pageQueryCaptor.capture(), any(DynamicCrudQuery.class));
        assertEquals(null, pageQueryCaptor.getValue().getOrderByColumn());
    }

    @Test
    @DisplayName("query uses object field labels when display fields have no explicit labels")
    void queryUsesObjectFieldLabelsWhenDisplayFieldsHaveNoExplicitLabels() {
        when(businessObjectMapper.selectFirstByObjectCode(1L, "supplier")).thenReturn(object("supplier"));
        when(permissionService.hasDocumentActionPermission("supplier", "VIEW")).thenReturn(true);
        Page<Map<String, Object>> page = new Page<>(1, 10, 0);
        page.setRecords(List.of());
        when(dynamicCrudService.selectPage(eq("runtime_supplier"), any(PageQuery.class), any(DynamicCrudQuery.class)))
                .thenReturn(page);
        AiCrudConfig config = new AiCrudConfig();
        config.setEditSchema("[{\"field\":\"fieldInput\",\"label\":\"产品\"},{\"field\":\"fieldInput2\",\"label\":\"编码\"}]");
        when(dynamicCrudService.getRuntimeConfig("runtime_supplier")).thenReturn(config);

        BusinessRecordSelectorQueryDTO dto = new BusinessRecordSelectorQueryDTO();
        dto.setObjectCode("supplier");
        dto.setDisplayFields(List.of("fieldInput", "fieldInput2"));

        BusinessRecordSelectorResultVO result = service.query(dto, new PageQuery());

        assertEquals(2, result.getColumns().size());
        assertEquals("fieldInput", result.getColumns().get(0).getField());
        assertEquals("产品", result.getColumns().get(0).getLabel());
        assertEquals("编码", result.getColumns().get(1).getLabel());
    }

    private AiBusinessObject object(String objectCode) {
        AiBusinessObject object = new AiBusinessObject();
        object.setTenantId(1L);
        object.setSuiteCode("default");
        object.setObjectCode(objectCode);
        object.setObjectName("供应商");
        object.setConfigKey("runtime_supplier");
        object.setStatus(1);
        return object;
    }
}
