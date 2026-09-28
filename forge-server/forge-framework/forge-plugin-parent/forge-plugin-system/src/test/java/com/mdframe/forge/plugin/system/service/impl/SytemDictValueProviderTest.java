package com.mdframe.forge.plugin.system.service.impl;

import com.mdframe.forge.plugin.system.entity.SysDictData;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.plugin.system.mapper.SysRegionMapper;
import com.mdframe.forge.plugin.system.mapper.SysUserMapper;
import com.mdframe.forge.plugin.system.service.ISysDictDataService;
import com.mdframe.forge.starter.file.core.FileManager;
import com.mdframe.forge.starter.file.model.FileMetadata;
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
                mock(FileManager.class));

        assertThat(provider.getLabel("sys_normal_disable", "1")).isEqualTo("启用");
        assertThat(provider.getLabel("sys_normal_disable", "1")).isEqualTo("启用");
        assertThat(provider.listLabels("sys_normal_disable")).containsExactly("启用", "停用");

        verify(dictDataService, times(3)).selectDictDataByType("sys_normal_disable");
    }

    @Test
    void fileTranslationShouldUseAuthorizedFileManagerEntryPoints() {
        FileManager fileManager = mock(FileManager.class);
        when(fileManager.getAccessUrl("file-1", 3600)).thenReturn("/authorized/file-1");
        when(fileManager.getMetadata("file-1"))
                .thenReturn(FileMetadata.builder().fileId("file-1").originalName("report.pdf").build());
        SytemDictValueProvider provider = new SytemDictValueProvider(
                mock(ISysDictDataService.class),
                mock(SysOrgMapper.class),
                mock(SysUserMapper.class),
                mock(SysRegionMapper.class),
                fileManager);

        assertThat(provider.getFileUrl("file-1")).isEqualTo("/authorized/file-1");
        assertThat(provider.getFileName("file-1")).isEqualTo("report.pdf");

        verify(fileManager).getAccessUrl("file-1", 3600);
        verify(fileManager).getMetadata("file-1");
    }

    private SysDictData dictData(String value, String label) {
        SysDictData dictData = new SysDictData();
        dictData.setDictValue(value);
        dictData.setDictLabel(label);
        return dictData;
    }
}
