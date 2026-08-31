package com.mdframe.forge.starter.tenant.interceptor;

import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantInterceptorWorkspaceTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void superAdminUsesCurrentTenantAsWorkspace() {
        TenantInterceptor.applyWorkspaceTenant(9L, true);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(9L);
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void ordinaryUserUsesCurrentTenant() {
        TenantInterceptor.applyWorkspaceTenant(3L, false);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(3L);
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }
}
