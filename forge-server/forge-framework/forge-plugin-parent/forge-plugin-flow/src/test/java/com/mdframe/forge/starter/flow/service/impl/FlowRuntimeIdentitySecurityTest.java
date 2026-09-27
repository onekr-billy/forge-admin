package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.flow.dto.FlowEntrySubmitDTO;
import com.mdframe.forge.starter.flow.entity.FlowEntry;
import com.mdframe.forge.starter.flow.entity.FlowFormVersion;
import com.mdframe.forge.starter.flow.enums.FlowEnableStatus;
import com.mdframe.forge.starter.flow.mapper.FlowEntryFieldMappingMapper;
import com.mdframe.forge.starter.flow.mapper.FlowEntryMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFillBatchItemMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFormInstanceMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFormVersionMapper;
import com.mdframe.forge.starter.flow.service.FlowEntryService;
import com.mdframe.forge.starter.flow.service.FlowInstanceService;
import com.mdframe.forge.starter.flow.service.FlowTaskService;
import com.mdframe.forge.starter.flow.vo.FlowEntryRuntimeVO;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Flow runtime identity security")
class FlowRuntimeIdentitySecurityTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
        TenantContextHolder.clear();
        TenantContextHolder.clearIgnore();
    }

    @Test
    @DisplayName("entry runtime lookup rejects missing tenant before mapper access")
    void entryRuntimeRejectsMissingTenantBeforeDataAccess() {
        EntryFixture fixture = entryFixture();

        assertThrows(IllegalStateException.class,
                () -> fixture.service().getRuntimeEntry("orders"));

        verifyNoInteractions(fixture.entryMapper(), fixture.mappingMapper(), fixture.versionMapper());
    }

    @Test
    @DisplayName("entry runtime uses explicit tenant conditions for all metadata")
    void entryRuntimeUsesExplicitTenantConditions() {
        EntryFixture fixture = entryFixture();
        FlowEntry entry = entry(9L);
        FlowFormVersion version = new FlowFormVersion();
        version.setTenantId(9L);
        when(fixture.entryMapper().selectByEntryCode(9L, "orders")).thenReturn(entry);
        when(fixture.mappingMapper().selectByEntryId(9L, 11L)).thenReturn(List.of());
        when(fixture.versionMapper().selectByIdForRuntimeAndTenant(21L, 9L)).thenReturn(version);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            fixture.service().getRuntimeEntry("orders");
        }

        verify(fixture.entryMapper()).selectByEntryCode(9L, "orders");
        verify(fixture.mappingMapper()).selectByEntryId(9L, 11L);
        verify(fixture.versionMapper()).selectByIdForRuntimeAndTenant(21L, 9L);
    }

    @Test
    @DisplayName("form submit rejects missing identity before entry access")
    void formSubmitRejectsMissingIdentityBeforeDataAccess() {
        RuntimeFixture fixture = runtimeFixture();

        assertThrows(IllegalStateException.class,
                () -> fixture.service().submitEntryForm("orders", new FlowEntrySubmitDTO()));

        verifyNoInteractions(fixture.entryService(), fixture.mappingMapper(), fixture.formInstanceMapper());
    }

    @Test
    @DisplayName("form submit rejects spoofed starter before entry access")
    void formSubmitRejectsSpoofedStarterBeforeDataAccess() {
        RuntimeFixture fixture = runtimeFixture();
        FlowEntrySubmitDTO dto = new FlowEntrySubmitDTO();
        dto.setStartUserId("8");

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            assertThrows(IllegalStateException.class,
                    () -> fixture.service().submitEntryForm("orders", dto));
        }

        verifyNoInteractions(fixture.entryService(), fixture.mappingMapper(), fixture.formInstanceMapper());
    }

    @Test
    @DisplayName("runtime facade rejects an entry from another tenant")
    void runtimeFacadeRejectsCrossTenantEntry() {
        RuntimeFixture fixture = runtimeFixture();
        FlowEntryRuntimeVO runtime = new FlowEntryRuntimeVO();
        runtime.setEntry(entry(8L));
        when(fixture.entryService().getRuntimeEntry("orders")).thenReturn(runtime);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            assertThrows(IllegalStateException.class,
                    () -> fixture.service().getRuntimeEntry("orders"));
        }
    }

    @Test
    @DisplayName("entry mapper XML keeps tenant predicates under ignore-tenant controllers")
    void entryMapperXmlHasExplicitTenantPredicates() throws Exception {
        String entryXml = Files.readString(Path.of("src/main/resources/mapper/FlowEntryMapper.xml"));
        String mappingXml = Files.readString(Path.of("src/main/resources/mapper/FlowEntryFieldMappingMapper.xml"));

        org.junit.jupiter.api.Assertions.assertTrue(entryXml.contains("e.tenant_id = #{tenantId}"));
        org.junit.jupiter.api.Assertions.assertTrue(mappingXml.contains("m.tenant_id = #{tenantId}"));
        org.junit.jupiter.api.Assertions.assertTrue(mappingXml.contains("WHERE tenant_id = #{tenantId}"));
    }

    private static EntryFixture entryFixture() {
        FlowEntryMapper entryMapper = mock(FlowEntryMapper.class);
        FlowEntryFieldMappingMapper mappingMapper = mock(FlowEntryFieldMappingMapper.class);
        FlowFormVersionMapper versionMapper = mock(FlowFormVersionMapper.class);
        return new EntryFixture(
                new FlowEntryServiceImpl(entryMapper, mappingMapper, versionMapper),
                entryMapper, mappingMapper, versionMapper);
    }

    private static RuntimeFixture runtimeFixture() {
        FlowEntryService entryService = mock(FlowEntryService.class);
        FlowEntryFieldMappingMapper mappingMapper = mock(FlowEntryFieldMappingMapper.class);
        FlowFormInstanceMapper formInstanceMapper = mock(FlowFormInstanceMapper.class);
        FlowRuntimeServiceImpl service = new FlowRuntimeServiceImpl(
                entryService,
                mock(FlowInstanceService.class),
                formInstanceMapper,
                mappingMapper,
                mock(FlowFillBatchItemMapper.class),
                mock(FlowTaskService.class),
                new ObjectMapper());
        return new RuntimeFixture(service, entryService, mappingMapper, formInstanceMapper);
    }

    private static FlowEntry entry(Long tenantId) {
        FlowEntry entry = new FlowEntry();
        entry.setId(11L);
        entry.setTenantId(tenantId);
        entry.setEntryCode("orders");
        entry.setFormVersionId(21L);
        entry.setStatus(FlowEnableStatus.ENABLED.getCode());
        return entry;
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId, Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setTenantId(tenantId);
        loginUser.setUserId(userId);
        loginUser.setUsername("tester");
        loginUser.setMainOrgId(3L);
        loginUser.setDeptName("QA");
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", userId, 3L, tenantId,
                "pc", "flow-runtime-security-test", Set.of()));
    }

    private record EntryFixture(
            FlowEntryServiceImpl service,
            FlowEntryMapper entryMapper,
            FlowEntryFieldMappingMapper mappingMapper,
            FlowFormVersionMapper versionMapper) {
    }

    private record RuntimeFixture(
            FlowRuntimeServiceImpl service,
            FlowEntryService entryService,
            FlowEntryFieldMappingMapper mappingMapper,
            FlowFormInstanceMapper formInstanceMapper) {
    }
}
