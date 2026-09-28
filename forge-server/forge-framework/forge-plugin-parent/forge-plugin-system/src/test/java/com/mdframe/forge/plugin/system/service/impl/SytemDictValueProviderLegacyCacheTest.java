package com.mdframe.forge.plugin.system.service.impl;

import com.mdframe.forge.plugin.system.mapper.SysFileMetadataMapper;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysRegionMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.service.ISysDictDataService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SytemDictValueProviderLegacyCacheTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dictionaryTranslationShouldAcceptMapElementsFromLegacyCache() {
        ISysDictDataService dictDataService = (ISysDictDataService) Proxy.newProxyInstance(
                ISysDictDataService.class.getClassLoader(),
                new Class<?>[]{ISysDictDataService.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("selectDictDataByType")) {
                        return (List) List.of(Map.of("dictValue", "1", "dictLabel", "启用"));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        SytemDictValueProvider provider = new SytemDictValueProvider(
                dictDataService,
                mapper(SysOrgMapper.class),
                mapper(SysUserMapper.class),
                mapper(SysRegionMapper.class),
                mapper(SysFileMetadataMapper.class));

        assertThat(provider.getLabel("sys_normal_disable", "1")).isEqualTo("启用");
        assertThat(provider.getValue("sys_normal_disable", "启用")).isEqualTo("1");
    }

    private <T> T mapper(Class<T> mapperType) {
        return mapperType.cast(Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                }));
    }
}
