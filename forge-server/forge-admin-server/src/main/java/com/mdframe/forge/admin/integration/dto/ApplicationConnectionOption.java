package com.mdframe.forge.admin.integration.dto;

public record ApplicationConnectionOption(Long id, String connectionCode, String connectionName,
        String platformName, boolean loginAvailable, boolean messageAvailable) { }
