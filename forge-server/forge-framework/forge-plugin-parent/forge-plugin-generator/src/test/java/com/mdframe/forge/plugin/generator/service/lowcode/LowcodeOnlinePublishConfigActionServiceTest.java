package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LowcodeOnlinePublishConfigActionServiceTest {

    private AiCrudConfigMapper configMapper;
    private AiCrudConfigVersionMapper versionMapper;
    private LowcodeOnlinePublishConfigActionService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(AiCrudConfigMapper.class);
        versionMapper = mock(AiCrudConfigVersionMapper.class);
        service = new LowcodeOnlinePublishConfigActionService(
                configMapper,
                versionMapper,
                mock(LowcodeDomainService.class),
                mock(MenuRegisterAdapter.class),
                new ObjectMapper());
    }

    @Test
    void freshCommandAtomicallyPersistsConfigAndReservedVersion() {
        AiCrudConfig config = config(4, 2);
        LowcodeOnlinePublishCommand command = command();
        when(configMapper.selectByConfigIdForUpdate(7L, 10L)).thenReturn(config);
        when(versionMapper.insert(any(AiCrudConfigVersion.class))).thenReturn(1);
        when(configMapper.updateById(config)).thenReturn(1);

        LowcodeOnlinePublishConfigActionService.Result result = service.execute(command);

        assertEquals(LowcodeOnlinePublishConfigActionService.Result.COMPLETED, result);
        assertEquals(3, config.getPublishedVersion());
        assertEquals("PUBLISHED", config.getPublishStatus());
        assertEquals("biz_order", config.getRuntimeTableName());
        verify(versionMapper).insert(any(AiCrudConfigVersion.class));
        verify(configMapper).updateById(config);
    }

    @Test
    void changedDraftSupersedesCommandBeforeAnyWrite() {
        AiCrudConfig config = config(5, 2);
        when(configMapper.selectByConfigIdForUpdate(7L, 10L)).thenReturn(config);

        LowcodeOnlinePublishConfigActionService.Result result = service.execute(command());

        assertEquals(LowcodeOnlinePublishConfigActionService.Result.SUPERSEDED, result);
        verify(versionMapper, never()).insert(any(AiCrudConfigVersion.class));
        verify(configMapper, never()).updateById(any(AiCrudConfig.class));
    }

    @Test
    void committedConfigReplaysOnlyWhenReservedVersionMatches() {
        AiCrudConfig config = config(4, 3);
        AiCrudConfigVersion version = new AiCrudConfigVersion();
        version.setId(20L);
        version.setConfigKey("orders");
        version.setVersionNo(3);
        when(configMapper.selectByConfigIdForUpdate(7L, 10L)).thenReturn(config);
        when(versionMapper.selectVersionById(7L, 10L, 20L)).thenReturn(version);

        LowcodeOnlinePublishConfigActionService.Result result = service.execute(command());

        assertEquals(LowcodeOnlinePublishConfigActionService.Result.COMPLETED, result);
        verify(versionMapper, never()).insert(any(AiCrudConfigVersion.class));
        verify(configMapper, never()).updateById(any(AiCrudConfig.class));
    }

    @Test
    void concurrentWinnerWithSameReservedVersionSupersedesLosingCommand() {
        AiCrudConfig config = config(4, 3);
        AiCrudConfigVersion winningVersion = new AiCrudConfigVersion();
        winningVersion.setId(21L);
        winningVersion.setConfigKey("orders");
        winningVersion.setVersionNo(3);
        when(configMapper.selectByConfigIdForUpdate(7L, 10L)).thenReturn(config);
        when(versionMapper.selectVersionByNo(7L, 10L, 3)).thenReturn(winningVersion);

        LowcodeOnlinePublishConfigActionService.Result result = service.execute(command());

        assertEquals(LowcodeOnlinePublishConfigActionService.Result.SUPERSEDED, result);
        verify(versionMapper, never()).insert(any(AiCrudConfigVersion.class));
        verify(configMapper, never()).updateById(any(AiCrudConfig.class));
    }

    @Test
    void configCommitUsesIndependentTransaction() throws NoSuchMethodException {
        Transactional transactional = LowcodeOnlinePublishConfigActionService.class
                .getMethod("execute", LowcodeOnlinePublishCommand.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private AiCrudConfig config(int draftVersion, int publishedVersion) {
        AiCrudConfig config = new AiCrudConfig();
        config.setId(10L);
        config.setTenantId(7L);
        config.setConfigKey("orders");
        config.setDraftVersion(draftVersion);
        config.setPublishedVersion(publishedVersion);
        return config;
    }

    private LowcodeOnlinePublishCommand command() {
        LowcodeOnlinePublishConfigSnapshot snapshot = new LowcodeOnlinePublishConfigSnapshot();
        snapshot.setTableName("biz_order");
        snapshot.setTableComment("订单");
        snapshot.setAppName("订单应用");
        snapshot.setMenuName("订单应用");
        snapshot.setMenuSort(7);
        snapshot.setMountTarget("MOBILE");
        snapshot.setModelSchema("{\"tableName\":\"biz_order\",\"fields\":[]}");
        snapshot.setPageSchema("{}");
        snapshot.setRuntimeTableName("biz_order");
        snapshot.setPrimaryKeyField("id");
        snapshot.setPrimaryKeyColumn("id");
        snapshot.setPrimaryKeyType("bigint");
        return new LowcodeOnlinePublishCommand(
                1, 7L, 10L, "orders", 4, 2, 20L, 3, 42L,
                "digest", snapshot, false, null,
                null, null, null, "在线发布");
    }
}
