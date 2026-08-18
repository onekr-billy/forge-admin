package com.mdframe.forge.plugin.data.service;

import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.entity.DataConnection;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.plugin.data.entity.DataDatasetField;
import com.mdframe.forge.plugin.data.enums.DataDatasetAccessLevelEnum;
import com.mdframe.forge.plugin.data.support.DataDatasetFieldViewAssembler;
import com.mdframe.forge.plugin.data.vo.DataDatasetQueryResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataDatasetRuntimeServiceTest {

    private DataDatasetService datasetService;
    private DataDatasetAccessService accessService;
    private DataConnectionService connectionService;
    private DataDatasetFieldService fieldService;
    private DataQueryExecutor queryExecutor;
    private DataDatasetRuntimeService runtimeService;

    @BeforeEach
    void setUp() {
        datasetService = mock(DataDatasetService.class);
        accessService = mock(DataDatasetAccessService.class);
        connectionService = mock(DataConnectionService.class);
        fieldService = mock(DataDatasetFieldService.class);
        queryExecutor = mock(DataQueryExecutor.class);
        runtimeService = new DataDatasetRuntimeService(
                datasetService, accessService, connectionService, fieldService,
                queryExecutor, mock(DataDatasetFieldViewAssembler.class));
    }

    @Test
    void shouldRejectUnpublishedOrDisabledDatasetBeforeExecution() {
        DataDataset unpublished = dataset(0, 1);
        when(datasetService.getById(30L)).thenReturn(unpublished);
        DataDatasetQueryDTO query = query(30L);

        assertThrows(BusinessException.class, () -> runtimeService.query(query));

        DataDataset disabled = dataset(1, 0);
        when(datasetService.getById(30L)).thenReturn(disabled);
        assertThrows(BusinessException.class, () -> runtimeService.query(query));
    }

    @Test
    void shouldApplyQueryAccessConnectionAndFieldsForCodeBasedExecution() {
        DataDataset dataset = dataset(1, 1);
        DataConnection connection = new DataConnection();
        connection.setId(40L);
        connection.setStatus(1);
        DataDatasetField field = new DataDatasetField();
        field.setFieldName("displayName");
        DataDatasetQueryResultVO expected = new DataDatasetQueryResultVO();
        when(datasetService.getByCode("member_lookup")).thenReturn(dataset);
        when(connectionService.getById(40L)).thenReturn(connection);
        when(fieldService.listByDatasetId(30L)).thenReturn(List.of(field));
        when(queryExecutor.execute(any(), any(), any(), any())).thenReturn(expected);

        DataDatasetQueryResultVO result = runtimeService.queryByCode("member_lookup", new DataDatasetQueryDTO());

        assertSame(expected, result);
        verify(accessService).requireAccess(dataset, DataDatasetAccessLevelEnum.QUERY);
        verify(queryExecutor).execute(any(), any(), any(), any());
    }

    private DataDataset dataset(Integer publishStatus, Integer status) {
        DataDataset dataset = new DataDataset();
        dataset.setId(30L);
        dataset.setDatasetCode("member_lookup");
        dataset.setConnectionId(40L);
        dataset.setPublishStatus(publishStatus);
        dataset.setStatus(status);
        return dataset;
    }

    private DataDatasetQueryDTO query(Long datasetId) {
        DataDatasetQueryDTO query = new DataDatasetQueryDTO();
        query.setDatasetId(datasetId);
        return query;
    }
}
