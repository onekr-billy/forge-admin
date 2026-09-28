package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalPermissionGuardTest {

    private final ExternalPermissionGuard guard = new ExternalPermissionGuard();

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    void shouldFailClosedForMissingPermissionCodeOrPermission() {
        ExternalApi api = new ExternalApi();
        api.setPermissionCheckEnabled(true);

        assertThrows(BusinessException.class, () -> guard.check(api));

        api.setRequiredPermission("external:member:query");
        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity(Set.of()))) {
            assertThrows(BusinessException.class, () -> guard.check(api));
        }
        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(
                identity(Set.of("external:member:query")))) {
            assertDoesNotThrow(() -> guard.check(api));
        }
    }

    private ExecutionIdentity identity(Set<String> permissions) {
        LoginUser user = new LoginUser();
        user.setUserId(8L);
        user.setTenantId(1L);
        user.setUserType(2);
        user.setPermissions(permissions);
        return new ExecutionIdentity(user, "USER", 8L, null, 1L, "test", "token", Set.of());
    }
}
