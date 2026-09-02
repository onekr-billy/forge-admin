package com.mdframe.forge.plugin.system.security;

import com.mdframe.forge.plugin.system.controller.SysConfigController;
import com.mdframe.forge.plugin.system.controller.SysDictDataController;
import com.mdframe.forge.plugin.system.controller.SysDictTypeController;
import com.mdframe.forge.plugin.system.controller.SysFileStorageConfigController;
import com.mdframe.forge.plugin.system.controller.SysManagedCachePolicyController;
import com.mdframe.forge.plugin.system.controller.SysNoticeController;
import com.mdframe.forge.plugin.system.controller.SysOrgController;
import com.mdframe.forge.plugin.system.controller.SysPostController;
import com.mdframe.forge.plugin.system.controller.SysRegionController;
import com.mdframe.forge.plugin.system.dto.SysNoticeQuery;
import com.mdframe.forge.plugin.system.dto.SysOrgQuery;
import com.mdframe.forge.plugin.system.dto.SysPostQuery;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.domain.PageQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PickerAndInboxPermissionContractTest {

    @Test
    void adminControllersShouldNotIgnorePermissionAtClassLevel() {
        assertThat(SysConfigController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysDictTypeController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysDictDataController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysManagedCachePolicyController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysFileStorageConfigController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysOrgController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysNoticeController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysPostController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysRegionController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }

    @Test
    void currentUserAndPickerApisShouldRemainPermissionIgnored() throws NoSuchMethodException {
        assertIgnored(SysOrgController.class, "tree", SysOrgQuery.class);
        assertIgnored(SysOrgController.class, "currentOptions");
        assertIgnored(SysOrgController.class, "switchOrg", Long.class);
        assertIgnored(SysDictDataController.class, "getByType", String.class);
        assertIgnored(SysNoticeController.class, "userPage", PageQuery.class, SysNoticeQuery.class);
        assertIgnored(SysNoticeController.class, "getUserUnreadCount");
        assertIgnored(SysNoticeController.class, "markAsRead", Long.class);
        assertIgnored(SysPostController.class, "list", SysPostQuery.class);
        assertIgnored(SysRegionController.class, "treeAll", String.class, Boolean.class);
        assertNotIgnored(SysOrgController.class, "add", com.mdframe.forge.plugin.system.dto.SysOrgDTO.class);
        assertNotIgnored(SysNoticeController.class, "add", com.mdframe.forge.plugin.system.dto.SysNoticeDTO.class);
        assertNotIgnored(SysDictDataController.class, "add", com.mdframe.forge.plugin.system.dto.SysDictDataDTO.class);
    }

    private void assertIgnored(Class<?> type, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(ApiPermissionIgnore.class)).isTrue();
    }

    private void assertNotIgnored(Class<?> type, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }
}
