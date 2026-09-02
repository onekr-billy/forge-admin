package com.mdframe.forge.plugin.system.security;

import com.mdframe.forge.plugin.system.controller.SysTenantController;
import com.mdframe.forge.starter.core.annotation.api.ApiPermissionIgnore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SysTenantPermissionContractTest {

    @Test
    void tenantAdminApisShouldNotIgnorePermissionAtClassLevel() {
        assertThat(SysTenantController.class.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }

    @Test
    void currentUserTenantSwitchShouldRemainPermissionIgnored() throws NoSuchMethodException {
        assertIgnored("selectUserTenantConfig", Long.class);
        assertIgnored("currentTenantOptions");
        assertIgnored("switchTenant", Long.class);
        assertNotIgnored("page", com.mdframe.forge.plugin.system.dto.SysTenantQuery.class);
        assertNotIgnored("add", com.mdframe.forge.plugin.system.dto.SysTenantDTO.class);
        assertNotIgnored("remove", Long.class);
    }

    private void assertIgnored(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = SysTenantController.class.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(ApiPermissionIgnore.class)).isTrue();
    }

    private void assertNotIgnored(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = SysTenantController.class.getDeclaredMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(ApiPermissionIgnore.class)).isFalse();
    }
}
