package com.mdframe.forge.plugin.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.data.dto.DataDatasetPreviewDTO;
import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.dto.DataDatasetSaveDTO;
import com.mdframe.forge.plugin.data.entity.DataConnection;
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
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DataDatasetControllerSecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void sqlPreviewMustRequireDedicatedPermission() throws Exception {
        SaCheckPermission annotation = DataDatasetController.class
                .getMethod("previewSql", DataDatasetSaveDTO.class)
                .getAnnotation(SaCheckPermission.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("data:dataset:preview-sql");
    }

    @Test
    void queryAndPreviewOperationsMustOnlyPersistAuditMetadata() throws Exception {
        assertMetadataOnlyAudit(DataDatasetRuntimeController.class
                .getMethod("query", DataDatasetQueryDTO.class));
        assertMetadataOnlyAudit(DataDatasetController.class
                .getMethod("preview", Long.class, DataDatasetPreviewDTO.class));
        assertMetadataOnlyAudit(DataDatasetController.class
                .getMethod("previewSql", DataDatasetSaveDTO.class));
        assertMetadataOnlyAudit(DataDatasetController.class
                .getMethod("add", DataDatasetSaveDTO.class));
        assertMetadataOnlyAudit(DataDatasetController.class
                .getMethod("edit", DataDatasetSaveDTO.class));
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

    @Test
    void sqlPreviewMustNotExposeJdbcExceptionDetails() throws Exception {
        DataConnectionService connectionService = mock(DataConnectionService.class);
        JdbcDataSourceProvider dataSourceProvider = mock(JdbcDataSourceProvider.class);
        DataConnection connection = new DataConnection();
        connection.setId(9L);
        connection.setStatus(EnableStatus.ENABLED.getCode());
        connection.setDbType("mysql");
        when(connectionService.getById(9L)).thenReturn(connection);
        when(dataSourceProvider.getConnection(connection))
                .thenThrow(new SQLException("password=super-secret; SELECT * FROM private_table"));

        DataDatasetController controller = new DataDatasetController(
                mock(DataDatasetService.class),
                mock(DataDatasetAccessService.class),
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
        DataDatasetSaveDTO dto = new DataDatasetSaveDTO();
        dto.setConnectionId(9L);
        dto.setSqlText("SELECT 1");

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            assertThatThrownBy(() -> controller.previewSql(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("SQL预览失败，请检查连接、语句和超时配置");
        }
    }

    private void assertMetadataOnlyAudit(java.lang.reflect.Method method) {
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        assertThat(annotation).as(method.toGenericString()).isNotNull();
        assertThat(annotation.saveRequestParams()).isFalse();
        assertThat(annotation.saveResponseResult()).isFalse();
    }

    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser();
        user.setUserId(8L);
        user.setTenantId(1L);
        user.setPermissions(Set.of("data:dataset:preview-sql"));
        return new ExecutionIdentity(user, "USER", 8L, null, 1L, "test", "token", Set.of());
    }
}
