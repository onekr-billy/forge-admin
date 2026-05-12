package com.mdframe.forge.plugin.data.dto;

import lombok.Data;

@Data
public class DataConnectionSaveDTO {

    private Long id;

    private String connectionCode;

    private String connectionName;

    private String dbType;

    private String driverClassName;

    private String jdbcUrl;

    private String username;

    private String password;

    private String schemaName;

    private String testSql;

    private String poolConfigJson;

    private Integer status;

    private String description;
}