package com.mdframe.forge.plugin.generator.service.lowcode.runtime;

import com.mdframe.forge.plugin.generator.domain.entity.GenDatasource;
import com.mdframe.forge.plugin.generator.util.DynamicDataSourceUtil;
import com.mdframe.forge.plugin.generator.util.GenDatasourcePasswordCodec;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 低代码运行时 JDBC 模板提供器。
 */
@Component
@RequiredArgsConstructor
public class RuntimeJdbcTemplateProvider {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final LowcodeRuntimeDataSourceResolver runtimeDataSourceResolver;

    public JdbcTemplate jdbcTemplate(LowcodeRuntimeDataSourceContext context) {
        if (context == null || context.isMaster()) {
            return jdbcTemplate;
        }
        return new JdbcTemplate(resolveDataSource(context));
    }

    public NamedParameterJdbcTemplate namedJdbcTemplate(LowcodeRuntimeDataSourceContext context) {
        if (context == null || context.isMaster()) {
            return namedParameterJdbcTemplate;
        }
        return new NamedParameterJdbcTemplate(resolveDataSource(context));
    }

    public DataSource resolveDataSource(LowcodeRuntimeDataSourceContext context) {
        if (context == null || context.isMaster()) {
            return jdbcTemplate.getDataSource();
        }
        if (context.getDatasourceId() == null) {
            throw new BusinessException("运行数据源ID不能为空");
        }
        GenDatasource datasource = runtimeDataSourceResolver.getDatasourceById(context.getDatasourceId());
        if (datasource == null) {
            throw new BusinessException("运行数据源不存在: " + context.getDatasourceId());
        }
        if (EnableStatus.DISABLED.matches(datasource.getIsEnabled())) {
            throw new BusinessException("运行数据源已禁用: " + datasource.getDatasourceName());
        }
        datasource.setPassword(GenDatasourcePasswordCodec.decrypt(datasource.getPassword()));
        return DynamicDataSourceUtil.getDataSource(datasource);
    }
}
