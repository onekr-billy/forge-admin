package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.enums.DbTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DbDialectFactory {

    private final Map<String, DbDialect> dialectMap = new HashMap<>();

    @Autowired
    public DbDialectFactory(MySqlDialect mysqlDialect) {
        dialectMap.put(DbTypeEnum.MYSQL.getCode(), mysqlDialect);
    }

    public DbDialect getDialect(String dbType) {
        return dialectMap.getOrDefault(dbType, dialectMap.get(DbTypeEnum.MYSQL.getCode()));
    }
}