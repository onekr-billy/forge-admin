package com.mdframe.forge.plugin.data.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataConnectionDetailVO {

    private Long id;

    private String connectionCode;

    private String connectionName;

    private String dbType;

    private String driverClassName;

    private String jdbcUrl;

    private String username;

    private Boolean hasPassword;

    private String schemaName;

    private String testSql;

    private String poolConfigJson;

    private Integer status;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}