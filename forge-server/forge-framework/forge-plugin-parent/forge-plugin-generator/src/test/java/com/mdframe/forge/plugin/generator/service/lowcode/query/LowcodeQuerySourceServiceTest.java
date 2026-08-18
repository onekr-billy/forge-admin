package com.mdframe.forge.plugin.generator.service.lowcode.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.plugin.data.service.DataDatasetRuntimeService;
import com.mdframe.forge.plugin.data.vo.DataDatasetFieldVO;
import com.mdframe.forge.plugin.data.vo.DataDatasetMetadataVO;
import com.mdframe.forge.plugin.data.vo.DataDatasetQueryResultVO;
import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.service.ExternalQuerySourceService;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceRefDTO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LowcodeQuerySourceServiceTest {

    private ExternalQuerySourceService externalService;
    private DataDatasetRuntimeService datasetService;
    private LowcodeQuerySourceService service;

    @BeforeEach
    void setUp() {
        externalService = mock(ExternalQuerySourceService.class);
        datasetService = mock(DataDatasetRuntimeService.class);
        service = new LowcodeQuerySourceService(externalService, datasetService, new ObjectMapper());
    }

    @Test
    void shouldBuildMinimalCatalogForBothSourceTypesAndFilterKeyword() {
        ExternalApi api = externalApi();
        DataDataset dataset = new DataDataset();
        dataset.setId(30L);
        dataset.setDatasetCode("product_lookup");
        dataset.setDatasetName("商品查询");
        when(externalService.listAvailable()).thenReturn(List.of(api));
        when(externalService.sourceKey(api)).thenReturn("crm/member_lookup");
        when(datasetService.listAvailable()).thenReturn(List.of(dataset));

        List<?> all = service.catalog(null);
        List<?> filtered = service.catalog("product");

        assertEquals(2, all.size());
        assertEquals(1, filtered.size());
    }

    @Test
    void shouldRouteExternalSourceAndNormalizeOutputFields() {
        ExternalApi api = externalApi();
        when(externalService.requireMetadata("crm/member_lookup")).thenReturn(api);
        when(externalService.execute("crm/member_lookup", Map.of("mobile", "13800138000")))
                .thenReturn(Map.of("member", Map.of("name", "张三")));
        LowcodeQuerySourceExecuteDTO dto = executeDto("EXTERNAL_API", "crm/member_lookup");
        dto.setParams(Map.of("mobile", "13800138000"));

        LowcodeQuerySourceResultVO result = service.execute(dto);

        assertEquals("EXTERNAL_API", result.getSourceType());
        assertEquals(20L, result.getSourceId());
        assertEquals("displayName", result.getFields().get(0).getField());
        verify(externalService).execute("crm/member_lookup", Map.of("mobile", "13800138000"));
    }

    @Test
    void shouldRouteDatasetAndPreservePagingAndFieldMetadata() {
        DataDatasetMetadataVO metadata = new DataDatasetMetadataVO();
        metadata.setDatasetId(30L);
        metadata.setDatasetCode("product_lookup");
        metadata.setDatasetName("商品查询");
        DataDatasetFieldVO field = new DataDatasetFieldVO();
        field.setFieldName("productName");
        field.setFieldLabel("商品名称");
        field.setDataType("string");
        field.setSensitiveLevel("PUBLIC");
        metadata.setFields(List.of(field));
        DataDatasetQueryResultVO queryResult = new DataDatasetQueryResultVO();
        queryResult.setSource(List.of(Map.of("productName", "测试商品")));
        queryResult.setTotal(1L);
        queryResult.setPageNum(1);
        queryResult.setPageSize(20);
        queryResult.setFields(List.of(field));
        when(datasetService.metadataByCode("product_lookup")).thenReturn(metadata);
        when(datasetService.queryByCode(org.mockito.ArgumentMatchers.eq("product_lookup"), any()))
                .thenReturn(queryResult);

        LowcodeQuerySourceResultVO result = service.execute(executeDto("dataset", "product_lookup"));

        assertEquals("DATASET", result.getSourceType());
        assertEquals(1L, result.getTotal());
        assertEquals("productName", result.getFields().get(0).getPath());
    }

    @Test
    void shouldFailClosedForUnsupportedTypeOrMissingKey() {
        LowcodeQuerySourceRefDTO unsupported = new LowcodeQuerySourceRefDTO();
        unsupported.setSourceType("URL");
        unsupported.setSourceKey("https://example.com");
        assertThrows(BusinessException.class, () -> service.metadata(unsupported));

        LowcodeQuerySourceRefDTO missing = new LowcodeQuerySourceRefDTO();
        missing.setSourceType("DATASET");
        assertThrows(BusinessException.class, () -> service.metadata(missing));
    }

    private ExternalApi externalApi() {
        ExternalApi api = new ExternalApi();
        api.setId(20L);
        api.setApiName("会员查询");
        api.setSystemName("CRM");
        api.setInputSchemaJson("[{\"name\":\"mobile\",\"type\":\"string\"}]");
        api.setOutputSchemaJson(
                "[{\"name\":\"displayName\",\"path\":\"member.name\",\"label\":\"姓名\",\"type\":\"string\"}]");
        return api;
    }

    private LowcodeQuerySourceExecuteDTO executeDto(String type, String key) {
        LowcodeQuerySourceExecuteDTO dto = new LowcodeQuerySourceExecuteDTO();
        dto.setSourceType(type);
        dto.setSourceKey(key);
        return dto;
    }
}
