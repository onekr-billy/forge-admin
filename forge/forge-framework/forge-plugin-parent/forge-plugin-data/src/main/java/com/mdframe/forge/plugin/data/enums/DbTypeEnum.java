package com.mdframe.forge.plugin.data.enums;

public enum DbTypeEnum {

    MYSQL("MYSQL", "com.mysql.cj.jdbc.Driver"),

    POSTGRESQL("POSTGRESQL", "org.postgresql.Driver"),

    ORACLE("ORACLE", "oracle.jdbc.OracleDriver"),

    SQLSERVER("SQLSERVER", "com.microsoft.sqlserver.jdbc.SQLServerDriver");

    private final String code;

    private final String defaultDriver;

    DbTypeEnum(String code, String defaultDriver) {
        this.code = code;
        this.defaultDriver = defaultDriver;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultDriver() {
        return defaultDriver;
    }

    public static DbTypeEnum fromCode(String code) {
        for (DbTypeEnum type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return MYSQL;
    }
}