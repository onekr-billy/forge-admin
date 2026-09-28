package com.mdframe.forge.starter.flow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.message.service.MessageService;
import com.mdframe.forge.plugin.system.mapper.SysOrgMapper;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.flow.dto.FlowFillBatchQueryDTO;
import com.mdframe.forge.starter.flow.entity.FlowFillBatch;
import com.mdframe.forge.starter.flow.entity.FlowForm;
import com.mdframe.forge.starter.flow.mapper.FlowFillBatchItemMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFillBatchMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFormMapper;
import com.mdframe.forge.starter.flow.mapper.FlowFormVersionMapper;
import com.mdframe.forge.starter.flow.mapper.FlowRecordParticipantMapper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.flowable.engine.ProcessEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Flow metadata tenant boundary")
class FlowMetadataTenantBoundaryTest {

    @AfterEach
    void clearIdentity() {
        ExecutionIdentityContextHolder.clear();
        TenantContextHolder.clear();
        TenantContextHolder.clearIgnore();
    }

    @Test
    @DisplayName("form list rejects missing identity before mapper access")
    void formListRejectsMissingIdentityBeforeMapperAccess() {
        FlowFormMapper formMapper = mock(FlowFormMapper.class);
        FlowFormVersionMapper versionMapper = mock(FlowFormVersionMapper.class);
        FlowFormServiceImpl service = new FlowFormServiceImpl(formMapper, versionMapper, new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> service.getPage(null, null, 1, 10));

        verifyNoInteractions(formMapper, versionMapper);
    }

    @Test
    @DisplayName("form list passes the trusted tenant to explicit SQL")
    void formListUsesTrustedTenant() {
        FlowFormMapper formMapper = mock(FlowFormMapper.class);
        FlowFormServiceImpl service = new FlowFormServiceImpl(
                formMapper, mock(FlowFormVersionMapper.class), new ObjectMapper());

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            service.getPage("申请", 1, 1, 10);
        }

        verify(formMapper).selectFormPage(any(), eq(9L), eq("申请"), eq(1));
    }

    @Test
    @DisplayName("form update rejects a cross-tenant id before update")
    void formUpdateRejectsCrossTenantIdBeforeUpdate() {
        FlowFormMapper formMapper = mock(FlowFormMapper.class);
        FlowFormServiceImpl service = new FlowFormServiceImpl(
                formMapper, mock(FlowFormVersionMapper.class), new ObjectMapper());
        FlowForm form = new FlowForm();
        form.setId(11L);
        form.setTenantId(8L);
        when(formMapper.selectByIdAndTenant(11L, 9L)).thenReturn(null);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            assertThrows(RuntimeException.class, () -> service.updateForm(form));
        }

        verify(formMapper).selectByIdAndTenant(11L, 9L);
        verify(formMapper, never()).updateById(any(FlowForm.class));
    }

    @Test
    @DisplayName("fill batch list rejects missing identity before mapper access")
    void fillBatchListRejectsMissingIdentityBeforeMapperAccess() {
        FillFixture fixture = fillFixture();

        assertThrows(IllegalStateException.class,
                () -> fixture.service().pageBatches(new FlowFillBatchQueryDTO(), 1, 10));

        verifyNoInteractions(fixture.batchMapper(), fixture.itemMapper());
    }

    @Test
    @DisplayName("fill batch update rejects a cross-tenant id before update")
    void fillBatchUpdateRejectsCrossTenantIdBeforeUpdate() {
        FillFixture fixture = fillFixture();
        FlowFillBatch batch = new FlowFillBatch();
        batch.setId(21L);
        batch.setTenantId(8L);
        when(fixture.batchMapper().selectByIdAndTenant(21L, 9L)).thenReturn(null);

        try (ExecutionIdentityContextHolder.Scope ignored = identity(9L, 7L)) {
            assertThrows(RuntimeException.class, () -> fixture.service().saveBatchConfig(batch));
        }

        assertEquals(9L, batch.getTenantId());
        verify(fixture.batchMapper()).selectByIdAndTenant(21L, 9L);
        verify(fixture.batchMapper(), never()).updateById(any(FlowFillBatch.class));
    }

    @Test
    @DisplayName("participant index rejects a missing explicit tenant")
    void participantIndexRejectsMissingExplicitTenant() {
        FlowRecordParticipantMapper mapper = mock(FlowRecordParticipantMapper.class);
        FlowRecordParticipantServiceImpl service = new FlowRecordParticipantServiceImpl(mapper);

        assertThrows(IllegalStateException.class,
                () -> service.record(null, "ORDER", "ORDER:31", "process-1", "7", "INITIATOR"));

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("participant index persists the supplied trusted tenant")
    void participantIndexUsesExplicitTenant() {
        FlowRecordParticipantMapper mapper = mock(FlowRecordParticipantMapper.class);
        FlowRecordParticipantServiceImpl service = new FlowRecordParticipantServiceImpl(mapper);

        service.record(9L, "ORDER", "ORDER:31", "process-1", "7", "INITIATOR");

        verify(mapper).insertIgnore(org.mockito.ArgumentMatchers.argThat(row ->
                Long.valueOf(9L).equals(row.getTenantId())
                        && "ORDER".equals(row.getBusinessType())
                        && "31".equals(row.getBusinessId())));
    }

    @Test
    @DisplayName("process start rejects missing tenant before Flowable access")
    void processStartRejectsMissingTenantBeforeFlowableAccess() {
        FlowInstanceServiceImpl service = new FlowInstanceServiceImpl();
        ProcessEngine processEngine = mock(ProcessEngine.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "processEngine", processEngine);

        assertThrows(RuntimeException.class, () -> service.startProcess(
                "approval", "ORDER:31", "ORDER", "审批", Map.of(), "7", "张三", null, null));

        verifyNoInteractions(processEngine);
    }

    @Test
    @DisplayName("process start rejects missing starter before Flowable access")
    void processStartRejectsMissingStarterBeforeFlowableAccess() {
        FlowInstanceServiceImpl service = new FlowInstanceServiceImpl();
        ProcessEngine processEngine = mock(ProcessEngine.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "processEngine", processEngine);
        TenantContextHolder.setTenantId(9L);

        assertThrows(RuntimeException.class, () -> service.startProcess(
                "approval", "ORDER:31", "ORDER", "审批", Map.of(), null, null, null, null));

        verifyNoInteractions(processEngine);
    }

    @Test
    @DisplayName("process start rejects a tenant from an ignore-tenant background scope")
    void processStartRejectsTenantFromIgnoreScope() {
        FlowInstanceServiceImpl service = new FlowInstanceServiceImpl();
        ProcessEngine processEngine = mock(ProcessEngine.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "processEngine", processEngine);
        TenantContextHolder.setTenantId(9L);
        TenantContextHolder.setIgnore(true);

        assertThrows(RuntimeException.class, () -> service.startProcess(
                "approval", "ORDER:31", "ORDER", "审批", Map.of(), "7", "张三", null, null));

        verifyNoInteractions(processEngine);
    }

    @Test
    @DisplayName("mapper XML keeps explicit tenant predicates under ignore-tenant controllers")
    void mapperXmlKeepsExplicitTenantPredicates() throws Exception {
        String formXml = Files.readString(Path.of("src/main/resources/mapper/FlowFormMapper.xml"));
        String versionXml = Files.readString(Path.of("src/main/resources/mapper/FlowFormVersionMapper.xml"));
        String batchXml = Files.readString(Path.of("src/main/resources/mapper/FlowFillBatchMapper.xml"));
        String itemXml = Files.readString(Path.of("src/main/resources/mapper/FlowFillBatchItemMapper.xml"));

        assertTrue(formXml.contains("f.tenant_id = #{tenantId}"));
        assertTrue(versionXml.contains("v.tenant_id = #{tenantId}"));
        assertTrue(batchXml.contains("b.tenant_id = #{tenantId}"));
        assertTrue(itemXml.contains("i.tenant_id = #{tenantId}"));
    }

    private static FillFixture fillFixture() {
        FlowFillBatchMapper batchMapper = mock(FlowFillBatchMapper.class);
        FlowFillBatchItemMapper itemMapper = mock(FlowFillBatchItemMapper.class);
        FlowFillBatchServiceImpl service = new FlowFillBatchServiceImpl(
                batchMapper,
                itemMapper,
                mock(SysOrgMapper.class),
                new ObjectMapper(),
                mock(MessageService.class));
        return new FillFixture(service, batchMapper, itemMapper);
    }

    private static ExecutionIdentityContextHolder.Scope identity(Long tenantId, Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setTenantId(tenantId);
        loginUser.setUserId(userId);
        loginUser.setUsername("tester");
        return ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                loginUser, "USER", userId, null, tenantId,
                "pc", "flow-metadata-tenant-test", Set.of()));
    }

    private record FillFixture(
            FlowFillBatchServiceImpl service,
            FlowFillBatchMapper batchMapper,
            FlowFillBatchItemMapper itemMapper) {
    }
}
