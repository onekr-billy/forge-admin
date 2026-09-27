package com.mdframe.forge.plugin.external.controller;

import com.mdframe.forge.plugin.external.adapter.DataAdapterFactory;
import com.mdframe.forge.plugin.external.adapter.impl.JsonPathAdapter;
import com.mdframe.forge.plugin.external.dto.ExternalApiDTO;
import com.mdframe.forge.plugin.external.service.ExternalApiService;
import com.mdframe.forge.plugin.external.support.ExternalQueryContractValidator;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalApiControllerTransformValidationTest {

    private final ExternalApiService apiService = mock(ExternalApiService.class);
    private final DataAdapterFactory adapterFactory = mock(DataAdapterFactory.class);
    private final ExternalApiController controller = new ExternalApiController(
            apiService, mock(ExternalQueryContractValidator.class), adapterFactory);

    @Test
    void shouldRejectLegacyScriptBeforePersistence() {
        when(adapterFactory.getRequiredAdapter("JsonPath")).thenReturn(new JsonPathAdapter());
        ExternalApiDTO dto = validDto();
        dto.setResponseTransformScript("function transform(response) { return response.data; }");

        assertThrows(BusinessException.class, () -> controller.add(dto));
        verify(apiService, never()).save(any());
    }

    @Test
    void shouldPersistVersionedFieldMapping() {
        when(adapterFactory.getRequiredAdapter("JsonPath")).thenReturn(new JsonPathAdapter());
        ExternalApiDTO dto = validDto();
        dto.setResponseTransformScript("""
                {"version":"FIELD_MAP_V1","sourcePath":"payload.items",
                 "fieldMapping":{"id":"id"},"targetPath":"records"}
                """);

        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            assertDoesNotThrow(() -> controller.add(dto));
        }
        verify(apiService).saveApi(any());
    }

    private ExternalApiDTO validDto() {
        ExternalApiDTO dto = new ExternalApiDTO();
        dto.setSystemId(1L);
        dto.setApiName("安全字段映射");
        dto.setApiCode("safe-field-map");
        dto.setExecutionMode("HTTP");
        dto.setApiMethod("GET");
        dto.setApiPath("/members");
        dto.setPermissionCheckEnabled(false);
        dto.setResponseTransformEnabled(true);
        return dto;
    }

    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser();
        user.setUserId(8L);
        user.setTenantId(1L);
        user.setPermissions(Set.of());
        return new ExecutionIdentity(user, "USER", 8L, null, 1L, "test", "token", Set.of());
    }
}
