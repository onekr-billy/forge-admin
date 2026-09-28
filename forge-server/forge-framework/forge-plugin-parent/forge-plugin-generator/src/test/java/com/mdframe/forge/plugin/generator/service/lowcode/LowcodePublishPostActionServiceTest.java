package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.AiLowcodeModelMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LowcodePublishPostActionServiceTest {

    private AiCrudConfigMapper configMapper;
    private AiCrudConfigVersionMapper versionMapper;
    private AiCrudConfigService configService;
    private MenuRegisterAdapter menuAdapter;
    private BusinessObjectMapper objectMapper;
    private BusinessAppMapper appMapper;
    private AiLowcodeModelMapper modelMapper;
    private LowcodePublishPostActionService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(AiCrudConfigMapper.class);
        versionMapper = mock(AiCrudConfigVersionMapper.class);
        configService = mock(AiCrudConfigService.class);
        menuAdapter = mock(MenuRegisterAdapter.class);
        objectMapper = mock(BusinessObjectMapper.class);
        appMapper = mock(BusinessAppMapper.class);
        modelMapper = mock(AiLowcodeModelMapper.class);
        service = new LowcodePublishPostActionService(
                configMapper, versionMapper, configService,
                menuAdapter, objectMapper, appMapper, modelMapper);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void executeRestoresTenantAndAtomicallyDisablesExistingMenus() {
        AiCrudConfig config = config(3);
        config.setMenuResourceId(12L);
        config.setOptions("{\"mobileMenuResourceId\":13}");
        when(configMapper.selectByConfigId(7L, 10L)).thenAnswer(invocation -> {
            assertEquals(7L, TenantContextHolder.getTenantId());
            return config;
        });
        when(versionMapper.selectVersionById(7L, 10L, 20L)).thenReturn(version(3));
        when(configService.updateById(config)).thenReturn(true);

        LowcodePublishPostActionService.Result result = service.execute(command(3, false));

        assertEquals(LowcodePublishPostActionService.Result.COMPLETED, result);
        verify(menuAdapter).disableMenu(12L);
        verify(menuAdapter).disableMenu(13L);
        verify(configService).updateById(config);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void staleVersionIsSupersededBeforeAnySideEffect() {
        when(configMapper.selectByConfigId(7L, 10L)).thenReturn(config(4));

        LowcodePublishPostActionService.Result result = service.execute(command(3, true));

        assertEquals(LowcodePublishPostActionService.Result.SUPERSEDED, result);
        verifyNoInteractions(
                versionMapper, configService, menuAdapter, objectMapper, appMapper, modelMapper);
    }

    @Test
    void delayedExecutionUsesImmutablePublishedSnapshotInsteadOfMutableDraftFields() {
        AiCrudConfig config = config(3);
        config.setMountTarget("MOBILE");
        config.setMenuName("草稿菜单");
        config.setMenuSort(99);
        when(configMapper.selectByConfigId(7L, 10L)).thenReturn(config);
        when(versionMapper.selectVersionById(7L, 10L, 20L)).thenReturn(version(3));
        when(menuAdapter.registerMenu("已发布菜单", 99L, "orders", 7)).thenReturn(101L);
        when(configService.updateById(config)).thenReturn(true);

        LowcodePublishPostActionService.Result result = service.execute(command(3, true));

        assertEquals(LowcodePublishPostActionService.Result.COMPLETED, result);
        verify(menuAdapter).registerMenu("已发布菜单", 99L, "orders", 7);
        assertEquals("已发布菜单", config.getMenuName());
        assertEquals(7, config.getMenuSort());
    }

    @Test
    void failedBusinessEntryWriteKeepsPostSyncRetryableAndRetryConverges() {
        AiCrudConfig config = config(3);
        AiBusinessObject businessObject = new AiBusinessObject();
        businessObject.setId(31L);
        businessObject.setTenantId(7L);
        businessObject.setSuiteCode("sales");
        businessObject.setObjectCode("order");
        businessObject.setObjectName("订单");
        when(configMapper.selectByConfigId(7L, 10L)).thenReturn(config);
        when(versionMapper.selectVersionById(7L, 10L, 20L)).thenReturn(version(3));
        when(menuAdapter.registerMenu("已发布菜单", 99L, "orders", 7)).thenReturn(101L);
        when(objectMapper.selectByObjectCode(7L, "sales", "order"))
                .thenReturn(businessObject);
        when(objectMapper.updateById(businessObject)).thenReturn(1);
        when(appMapper.countByAppCode(7L, "SALES_ORDER_RUNTIME", null)).thenReturn(0L);
        when(appMapper.insert(any(AiBusinessApp.class))).thenReturn(0, 1);
        when(configService.updateById(config)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> service.execute(businessCommand(3, true)));
        assertEquals(LowcodePublishPostActionService.Result.COMPLETED,
                service.execute(businessCommand(3, true)));

        verify(menuAdapter).registerMenu("已发布菜单", 99L, "orders", 7);
        verify(menuAdapter).updateMenu(101L, "已发布菜单", 99L, 7);
        verify(appMapper, org.mockito.Mockito.times(2)).insert(any(AiBusinessApp.class));
        verify(configService).updateById(config);
    }

    @Test
    void missingRegisteredMenuIdentityFailsBeforeCompletingPostSync() {
        AiCrudConfig config = config(3);
        when(configMapper.selectByConfigId(7L, 10L)).thenReturn(config);
        when(versionMapper.selectVersionById(7L, 10L, 20L)).thenReturn(version(3));
        when(menuAdapter.registerMenu("已发布菜单", 99L, "orders", 7)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.execute(command(3, true)));

        verify(configService, never()).updateById(config);
        verifyNoInteractions(objectMapper, appMapper, modelMapper);
    }

    @Test
    void executionUsesIndependentLocalTransaction() throws NoSuchMethodException {
        Transactional transactional = LowcodePublishPostActionService.class
                .getMethod("execute", LowcodePublishPostCommand.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    private AiCrudConfig config(int versionNo) {
        AiCrudConfig config = new AiCrudConfig();
        config.setId(10L);
        config.setTenantId(7L);
        config.setConfigKey("orders");
        config.setPublishedVersion(versionNo);
        config.setPublishStatus("PUBLISHED");
        config.setMountTarget("ADMIN");
        return config;
    }

    private AiCrudConfigVersion version(int versionNo) {
        AiCrudConfigVersion version = new AiCrudConfigVersion();
        version.setId(20L);
        version.setTenantId(7L);
        version.setConfigId(10L);
        version.setConfigKey("orders");
        version.setVersionNo(versionNo);
        version.setPublishSnapshot("{\"mountTarget\":\"ADMIN\",\"menuName\":\"已发布菜单\","
                + "\"appName\":\"已发布应用\",\"tableComment\":\"订单\",\"menuSort\":7}");
        return version;
    }

    private LowcodePublishPostCommand command(int versionNo, boolean syncMenu) {
        return new LowcodePublishPostCommand(
                1, 7L, 10L, "orders", 20L, versionNo, "PUBLISH",
                syncMenu, 99L, null, null, null, 42L);
    }

    private LowcodePublishPostCommand businessCommand(int versionNo, boolean syncMenu) {
        return new LowcodePublishPostCommand(
                1, 7L, 10L, "orders", 20L, versionNo, "PUBLISH",
                syncMenu, 99L, "sales", "order", "订单", 42L);
    }
}
