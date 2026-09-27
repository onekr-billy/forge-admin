package com.mdframe.forge.plugin.generator.config;

import com.mdframe.forge.plugin.generator.domain.entity.GenDatasource;
import com.mdframe.forge.plugin.generator.service.IGenDatasourceService;
import com.mdframe.forge.plugin.generator.util.DynamicDataSourceUtil;
import com.mdframe.forge.plugin.generator.util.GenDatasourcePasswordCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时预热已启用的应用数据源连接池，避免审批/低代码首请求踩 Hikari 冷启动。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class GenDatasourcePoolWarmupRunner implements ApplicationRunner {

    private final IGenDatasourceService genDatasourceService;

    @Override
    public void run(ApplicationArguments args) {
        List<GenDatasource> datasources;
        try {
            datasources = genDatasourceService.selectEnabledDatasources(null);
        } catch (Exception error) {
            log.warn("预热应用数据源时读取配置失败: {}", error.getMessage());
            return;
        }
        if (datasources == null || datasources.isEmpty()) {
            return;
        }
        int warmed = 0;
        for (GenDatasource datasource : datasources) {
            if (datasource == null || datasource.getDatasourceId() == null) {
                continue;
            }
            try {
                GenDatasource ready = new GenDatasource();
                ready.setDatasourceId(datasource.getDatasourceId());
                ready.setDatasourceName(datasource.getDatasourceName());
                ready.setDatasourceCode(datasource.getDatasourceCode());
                ready.setUrl(datasource.getUrl());
                ready.setUsername(datasource.getUsername());
                ready.setPassword(GenDatasourcePasswordCodec.decrypt(datasource.getPassword()));
                ready.setDriverClassName(datasource.getDriverClassName());
                ready.setDbType(datasource.getDbType());
                if (StringUtils.isAnyBlank(ready.getUrl(), ready.getUsername(), ready.getDriverClassName())) {
                    continue;
                }
                DynamicDataSourceUtil.warmUp(ready);
                warmed++;
            } catch (Exception error) {
                log.warn("预热应用数据源失败: id={}, name={}, error={}",
                        datasource.getDatasourceId(), datasource.getDatasourceName(), error.getMessage());
            }
        }
        if (warmed > 0) {
            log.info("已预热 {} 个应用数据源连接池", warmed);
        }
    }
}
