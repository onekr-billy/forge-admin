package com.mdframe.forge.plugin.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.data.dto.DataDatasetSaveDTO;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.plugin.data.enums.DataDatasetAccessLevelEnum;
import com.mdframe.forge.plugin.data.service.DataConnectionService;
import com.mdframe.forge.plugin.data.service.DataDatasetAccessService;
import com.mdframe.forge.plugin.data.service.DataDatasetCategoryService;
import com.mdframe.forge.plugin.data.service.DataDatasetFieldService;
import com.mdframe.forge.plugin.data.service.DataDatasetRowScopeService;
import com.mdframe.forge.plugin.data.service.DataDatasetService;
import com.mdframe.forge.plugin.data.support.DataDatasetFieldViewAssembler;
import com.mdframe.forge.plugin.data.support.DatasetParamSchemaParser;
import com.mdframe.forge.plugin.data.support.DbDialectFactory;
import com.mdframe.forge.plugin.data.support.JdbcDataSourceProvider;
import com.mdframe.forge.plugin.data.support.SqlParameterBinder;
import com.mdframe.forge.plugin.data.support.SqlSafetyValidator;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataDatasetControllerSecurityTest {

    @Test
    void sqlPreviewMustRequireDedicatedPermission() throws Exception {
        SaCheckPermission annotation = DataDatasetController.class
                .getMethod("previewSql", DataDatasetSaveDTO.class)
                .getAnnotation(SaCheckPermission.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("data:dataset:preview-sql");
    }

    @Test
    void savedDatasetSqlPreviewMustRequireManageAclBeforeOpeningConnection() {
        DataDatasetService datasetService = mock(DataDatasetService.class);
        DataDatasetAccessService accessService = mock(DataDatasetAccessService.class);
        DataConnectionService connectionService = mock(DataConnectionService.class);
        JdbcDataSourceProvider dataSourceProvider = mock(JdbcDataSourceProvider.class);
        DataDatasetController controller = new DataDatasetController(
                datasetService,
                accessService,
                mock(DataDatasetRowScopeService.class),
                mock(DataDatasetFieldService.class),
                connectionService,
                mock(DataDatasetCategoryService.class),
                dataSourceProvider,
                mock(DbDialectFactory.class),
                mock(SqlSafetyValidator.class),
                mock(SqlParameterBinder.class),
                mock(DatasetParamSchemaParser.class),
                mock(DataDatasetFieldViewAssembler.class));

        DataDataset dataset = new DataDataset();
        dataset.setId(7L);
        when(datasetService.getById(7L)).thenReturn(dataset);
        doThrow(new BusinessException("无权访问该数据集"))
                .when(accessService).requireAccess(dataset, DataDatasetAccessLevelEnum.MANAGE);

        DataDatasetSaveDTO dto = new DataDatasetSaveDTO();
        dto.setId(7L);
        dto.setConnectionId(9L);
        dto.setSqlText("SELECT 1");

        assertThatThrownBy(() -> controller.previewSql(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
        verify(accessService).requireAccess(dataset, DataDatasetAccessLevelEnum.MANAGE);
        verifyNoInteractions(connectionService, dataSourceProvider);
    }
}
