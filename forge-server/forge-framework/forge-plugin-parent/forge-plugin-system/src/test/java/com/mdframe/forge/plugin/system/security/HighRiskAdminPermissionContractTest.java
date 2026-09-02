package com.mdframe.forge.plugin.system.security;

import com.mdframe.forge.plugin.system.controller.SysClientController;
import com.mdframe.forge.plugin.system.controller.SysDataScopeConfigController;
import com.mdframe.forge.plugin.system.controller.SysMonitorController;
import com.mdframe.forge.plugin.system.controller.SysResourceController;
import com.mdframe.forge.plugin.system.controller.SysRoleController;
import com.mdframe.forge.plugin.system.controller.SysUserController;
import com.mdframe.forge.plugin.system.dto.SysUserDTO;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class HighRiskAdminPermissionContractTest {

    @Test
    void highRiskAdminControllersShouldNotIgnorePermissionAtClassLevel() {
        assertThat(SysRoleController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysResourceController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysClientController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysDataScopeConfigController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysMonitorController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
        assertThat(SysUserController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }

    @Test
    void currentUserProfileShouldRemainPermissionIgnored() throws NoSuchMethodException {
        Method profile = SysUserController.class.getDeclaredMethod("profile");
        Method updateProfile = SysUserController.class.getDeclaredMethod("updateProfile", SysUserDTO.class);
        Method page = SysUserController.class.getDeclaredMethod(
                "page", com.mdframe.forge.plugin.system.dto.SysUserQuery.class);
        assertThat(profile.isAnnotationPresent(ApiPermissionIgnore.class)).isTrue();
        assertThat(updateProfile.isAnnotationPresent(ApiPermissionIgnore.class)).isTrue();
        assertThat(page.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }
}
