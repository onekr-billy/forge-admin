package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.entity.DataConnection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JdbcDataSourceProvider {

    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DataSource getDataSource(DataConnection connection) {
        return dataSourceCache.computeIfAbsent(connection.getId(), id -> createDataSource(connection));
    }

    public DataSource createTempDataSource(DataConnection connection, String password) {
        HikariConfig config = buildHikariConfig(connection, password);
        return new HikariDataSource(config);
    }

    private HikariDataSource createDataSource(DataConnection connection) {
        HikariConfig config = buildHikariConfig(connection, connection.getPasswordCipher());
        config.setPoolName("data-conn-" + connection.getId());
        return new HikariDataSource(config);
    }

    private HikariConfig buildHikariConfig(DataConnection connection, String password) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(connection.getDriverClassName());
        config.setJdbcUrl(connection.getJdbcUrl());
        config.setUsername(connection.getUsername());
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery(connection.getTestSql());
        return config;
    }

    public Connection getConnection(DataConnection connection) throws SQLException {
        DataSource dataSource = getDataSource(connection);
        return dataSource.getConnection();
    }

    public void closeDataSource(Long connectionId) {
        HikariDataSource dataSource = dataSourceCache.remove(connectionId);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Closed data source for connection: {}", connectionId);
        }
    }

    public void closeAll() {
        dataSourceCache.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
            }
        });
        dataSourceCache.clear();
    }
}