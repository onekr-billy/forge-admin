package com.mdframe.forge.plugin.system.security;

import com.mdframe.forge.plugin.system.controller.SysExcelColumnConfigController;
import com.mdframe.forge.plugin.system.controller.SysExcelExportConfigController;
import com.mdframe.forge.plugin.system.controller.SysFileGroupController;
import com.mdframe.forge.plugin.system.controller.SysFileMetadataController;
import com.mdframe.forge.plugin.system.entity.SysFileGroup;
import com.mdframe.forge.plugin.system.entity.SysFileMetadata;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import com.mdframe.forge.starter.core.domain.PageQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FileAndExcelPermissionContractTest {

    @Test
    void fileAndExcelControllersShouldNotIgnorePermissionAtClassLevel() {
        assertThat(SysFileMetadataController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysFileGroupController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysExcelExportConfigController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysExcelColumnConfigController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }

    @Test
    void filePickerApisShouldRemainPermissionIgnored() throws NoSuchMethodException {
        assertIgnored(SysFileMetadataController.class, "getByFileId", String.class);
        assertIgnored(SysFileMetadataController.class, "rename", String.class, String.class);
        assertNotIgnored(SysFileMetadataController.class, "page", PageQuery.class, SysFileMetadata.class);
        assertNotIgnored(SysFileMetadataController.class, "remove", String[].class);
        assertNotIgnored(SysFileGroupController.class, "create", SysFileGroup.class);
        assertNotIgnored(SysFileGroupController.class, "delete", Long.class);
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
