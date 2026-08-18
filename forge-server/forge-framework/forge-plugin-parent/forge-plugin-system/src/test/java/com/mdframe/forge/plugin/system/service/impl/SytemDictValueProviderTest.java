package com.mdframe.forge.plugin.system.service.impl;

import com.mdframe.forge.plugin.system.entity.SysDictData;
import com.mdframe.forge.plugin.system.mapper.SysFileMetadataMapper;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysRegionMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.service.ISysDictDataService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SytemDictValueProviderTest {

    @Test
    void dictionaryTranslationShouldAlwaysDelegateCachingToManagedService() {
        ISysDictDataService dictDataService = mock(ISysDictDataService.class);
        when(dictDataService.selectDictDataByType("sys_normal_disable"))
                .thenReturn(List.of(dictData("1", "启用"), dictData("0", "停用")));
        SytemDictValueProvider provider = new SytemDictValueProvider(
                dictDataService,
                mock(SysOrgMapper.class),
                mock(SysUserMapper.class),
                mock(SysRegionMapper.class),
                mock(SysFileMetadataMapper.class));

        assertThat(provider.getLabel("sys_normal_disable", "1")).isEqualTo("启用");
        assertThat(provider.getLabel("sys_normal_disable", "1")).isEqualTo("启用");

        verify(dictDataService, times(2)).selectDictDataByType("sys_normal_disable");
    }

    private SysDictData dictData(String value, String label) {
        SysDictData dictData = new SysDictData();
        dictData.setDictValue(value);
        dictData.setDictLabel(label);
        return dictData;
    }
}
