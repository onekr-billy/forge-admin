package com.mdframe.forge.plugin.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.entity.DataConnection;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.plugin.data.entity.DataDatasetField;
import com.mdframe.forge.plugin.data.mapper.DataDimensionItemMapper;
import com.mdframe.forge.plugin.data.support.DataDatasetFieldViewAssembler;
import com.mdframe.forge.plugin.data.support.DataDatasetRowScopeCondition;
import com.mdframe.forge.plugin.data.support.DataQueryRuntimeCache;
import com.mdframe.forge.plugin.data.support.DatasetParamSchemaParser;
import com.mdframe.forge.plugin.data.support.DbDialect;
import com.mdframe.forge.plugin.data.support.DbDialectFactory;
import com.mdframe.forge.plugin.data.support.JdbcDataSourceProvider;
import com.mdframe.forge.plugin.data.support.SqlParameterBinder;
import com.mdframe.forge.plugin.data.support.SqlSafetyValidator;
import com.mdframe.forge.plugin.data.vo.DataDatasetQueryResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataQueryExecutorTest {

    private JdbcDataSourceProvider dataSourceProvider;
    private DbDialect dialect;
    private DataQueryRuntimeCache runtimeCache;
    private DataQueryExecutor executor;

    @BeforeEach
    void setUp() {
        dataSourceProvider = mock(JdbcDataSourceProvider.class);
        DbDialectFactory dialectFactory = mock(DbDialectFactory.class);
        dialect = mock(DbDialect.class);
        runtimeCache = mock(DataQueryRuntimeCache.class);
        when(dialectFactory.getDialect("mysql")).thenReturn(dialect);
        when(dialect.quoteIdentifier(anyString())).thenAnswer(invocation -> "`" + invocation.getArgument(0) + "`");
        when(dialect.buildLimitSql(anyString(), anyInt())).thenAnswer(
                invocation -> invocation.getArgument(0) + " LIMIT " + invocation.getArgument(1));
        when(runtimeCache.get(any(), any(), any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        DataDatasetRowScopeService rowScopeService = mock(DataDatasetRowScopeService.class);
        when(rowScopeService.buildCondition(any(), any())).thenReturn(DataDatasetRowScopeCondition.disabled());

        executor = new DataQueryExecutor(
                dataSourceProvider,
                dialectFactory,
                mock(SqlSafetyValidator.class),
                new SqlParameterBinder(),
                new DatasetParamSchemaParser(new ObjectMapper()),
                mock(DataDimensionItemMapper.class),
                mock(DataDatasetFieldViewAssembler.class),
                rowScopeService,
                runtimeCache);
    }

    @Test
    void shouldUseReadOnlyConnectionAndClampRowsAndTimeout() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(dataSourceProvider.getConnection(any())).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("memberName");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn("张三");

        DataDatasetQueryResultVO result = executor.execute(dataset(20000, 999), connection(),
                List.of(field()), query(20000, 20000));

        assertEquals(1, result.getSource().size());
        assertEquals(10000, result.getPageSize());
        verify(connection).setReadOnly(true);
        verify(statement).setQueryTimeout(120);
        verify(dialect).buildLimitSql(anyString(), eq(10000));
        verify(connection).close();
        verify(statement).close();
        verify(resultSet).close();
    }

    @Test
    void shouldPropagateDatabaseFailureInsteadOfReturningEmptyRows() throws Exception {
        when(dataSourceProvider.getConnection(any())).thenThrow(new SQLException("contains-sensitive-value"));

        assertThrows(BusinessException.class, () -> executor.execute(
                dataset(100, 30), connection(), List.of(field()), query(20, 20)));
    }

    private DataDataset dataset(Integer maxRows, Integer timeout) {
        DataDataset dataset = new DataDataset();
        dataset.setId(30L);
        dataset.setDatasetType("TABLE");
        dataset.setTableName("members");
        dataset.setMaxRows(maxRows);
        dataset.setTimeoutSeconds(timeout);
        dataset.setCacheEnabled(0);
        return dataset;
    }

    private DataConnection connection() {
        DataConnection connection = new DataConnection();
        connection.setId(40L);
        connection.setDbType("mysql");
        return connection;
    }

    private DataDatasetField field() {
        DataDatasetField field = new DataDatasetField();
        field.setFieldName("memberName");
        field.setDisplayEnabled(1);
        field.setQueryEnabled(1);
        field.setSensitiveLevel("PUBLIC");
        return field;
    }

    private DataDatasetQueryDTO query(Integer maxRows, Integer pageSize) {
        DataDatasetQueryDTO query = new DataDatasetQueryDTO();
        query.setParams(Map.of());
        query.setMaxRows(maxRows);
        query.setPageSize(pageSize);
        return query;
    }
}
