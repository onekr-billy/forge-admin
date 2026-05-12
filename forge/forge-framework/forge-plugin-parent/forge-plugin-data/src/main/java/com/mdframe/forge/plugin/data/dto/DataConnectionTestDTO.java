package com.mdframe.forge.plugin.data.dto;

import lombok.Data;

@Data
public class DataConnectionTestDTO {

    private Long id;

    private String dbType;

    private String driverClassName;

    private String jdbcUrl;

    private String username;

    private String password;

    private String schemaName;

    private String testSql;
}