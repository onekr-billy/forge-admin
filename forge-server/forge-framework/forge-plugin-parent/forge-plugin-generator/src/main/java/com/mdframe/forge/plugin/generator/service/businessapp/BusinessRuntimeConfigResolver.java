package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Supplier;

/** Resolves published or draft runtime configuration within the effective tenant. */
final class BusinessRuntimeConfigResolver {

    private final AiCrudConfigMapper crudConfigMapper;
    private final Supplier<Long> tenantIdSupplier;

    BusinessRuntimeConfigResolver(AiCrudConfigMapper crudConfigMapper, Supplier<Long> tenantIdSupplier) {
        this.crudConfigMapper = crudConfigMapper;
        this.tenantIdSupplier = tenantIdSupplier;
    }

    AiCrudConfig published(Long tenantId, String objectCodeOrConfigKey) {
        if (StringUtils.isBlank(objectCodeOrConfigKey)) {
            return null;
        }
        return crudConfigMapper.selectPublishedByObjectCodeOrConfigKey(
                tenantId != null ? tenantId : tenantIdSupplier.get(), objectCodeOrConfigKey);
    }

    AiCrudConfig runtime(Long tenantId, String objectCodeOrConfigKey) {
        if (StringUtils.isBlank(objectCodeOrConfigKey)) {
            return null;
        }
        return crudConfigMapper.selectRuntimeByObjectCodeOrConfigKey(
                tenantId != null ? tenantId : tenantIdSupplier.get(), objectCodeOrConfigKey);
    }
}
