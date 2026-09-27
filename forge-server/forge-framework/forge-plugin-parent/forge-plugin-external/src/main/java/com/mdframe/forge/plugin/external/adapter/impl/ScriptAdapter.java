package com.mdframe.forge.plugin.external.adapter.impl;

import com.mdframe.forge.plugin.external.adapter.DataAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class ScriptAdapter implements DataAdapter {

    public static final String MIGRATION_MESSAGE =
            "脚本响应转换已禁用，请迁移为 JsonPath 白名单字段映射";

    @Override
    public String getAdapterType() {
        return "Script";
    }

    @Override
    public Object transform(Object originalData, String adapterConfig) {
        throw new BusinessException(MIGRATION_MESSAGE);
    }

    @Override
    public boolean validateConfig(String adapterConfig) {
        return false;
    }
}
