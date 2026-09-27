package com.mdframe.forge.plugin.data.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.plugin.data.dto.DataConnectionTestDTO;
import com.mdframe.forge.plugin.data.service.DataConnectionService;
import com.mdframe.forge.plugin.data.support.DbDialectFactory;
import com.mdframe.forge.plugin.data.support.JdbcConnectionSecurityPolicy;
import com.mdframe.forge.plugin.data.support.JdbcDataSourceProvider;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DataConnectionControllerSecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void temporaryConnectionTestMustRequirePlatformAdministratorAndPermission() throws Exception {
        SaCheckPermission annotation = DataConnectionController.class
                .getMethod("testTemp", DataConnectionTestDTO.class)
                .getAnnotation(SaCheckPermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("data:connection:test-temp");

        DataConnectionController controller = new DataConnectionController(
                mock(DataConnectionService.class), mock(JdbcDataSourceProvider.class), mock(DbDialectFactory.class),
                mock(JdbcConnectionSecurityPolicy.class));
        LoginUser tenantAdministrator = new LoginUser();
        tenantAdministrator.setUserId(9L);
        tenantAdministrator.setTenantId(1L);
        tenantAdministrator.setUserType(1);
        ExecutionIdentity identity = new ExecutionIdentity(
                tenantAdministrator, "USER", 9L, null, 1L, "test", "token", Set.of());

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity)) {
            assertThatThrownBy(() -> controller.testTemp(new DataConnectionTestDTO()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("平台管理员");
        }
    }
}
