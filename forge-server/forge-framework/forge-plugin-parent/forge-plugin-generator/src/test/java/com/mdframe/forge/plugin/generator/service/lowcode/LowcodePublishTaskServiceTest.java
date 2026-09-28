package com.mdframe.forge.plugin.generator.service.lowcode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfigVersion;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishDTO;
import com.mdframe.forge.plugin.generator.enums.LowcodePublishTaskStatus;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigVersionMapper;
import com.mdframe.forge.plugin.generator.mapper.LowcodePublishTaskMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LowcodePublishTaskServiceTest {

    private LowcodePublishTaskMapper mapper;
    private AiCrudConfigVersionMapper versionMapper;
    private LowcodePublishTaskService service;

    @BeforeEach
    void setUp() {
        mapper = mock(LowcodePublishTaskMapper.class);
        versionMapper = mock(AiCrudConfigVersionMapper.class);
        service = new LowcodePublishTaskService(
                mapper, versionMapper, new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxRetryCount", 3);
        ReflectionTestUtils.setField(service, "lockTimeoutSeconds", 120L);
        ReflectionTestUtils.setField(service, "retryBaseSeconds", 5L);
    }

    @Test
    void appendPersistsMinimalImmutableCommandAndOperationalIdentity() {
        when(mapper.insert(any(AiLowcodePublishTask.class))).thenReturn(1);
        AiCrudConfig config = config();
        AiCrudConfigVersion version = version();
        LowcodePublishDTO dto = new LowcodePublishDTO();
        dto.setBusinessSuiteCode("sales");
        dto.setBusinessObjectCode("order");

        AiLowcodePublishTask task = service.appendPostSync(
                config, dto, version, "PUBLISH", true, 99L);

        assertEquals("lowcode-publish:7:10:20:PUBLISH", task.getRequestId());
        assertEquals("POST_SYNC", task.getCurrentStage());
        assertEquals(LowcodePublishTaskStatus.PENDING.getCode(), task.getTaskStatus());
        assertEquals(64, task.getSchemaHash().length());
        assertEquals(64, task.getCommandDigest().length());
        assertEquals(12L, task.getRuntimeDatasourceId());
        assertEquals(42L, task.getOperatorId());

        task.setTaskStatus(LowcodePublishTaskStatus.PROCESSING.getCode());
        task.setLockOwner("worker-1");
        LowcodePublishPostCommand restored = service.restore(task);
        assertEquals("sales", restored.businessSuiteCode());
        assertEquals("order", restored.businessObjectCode());
        assertEquals(99L, restored.menuParentId());
    }

    @Test
    void restoreRejectsTamperedCommandPayload() {
        when(mapper.insert(any(AiLowcodePublishTask.class))).thenReturn(1);
        AiLowcodePublishTask task = service.appendPostSync(
                config(), null, version(), "PUBLISH", false, null);
        task.setTaskStatus(LowcodePublishTaskStatus.PROCESSING.getCode());
        task.setLockOwner("worker-1");
        task.setCommandPayload(task.getCommandPayload().replace("orders", "invoices"));

        assertThrows(BusinessException.class, () -> service.restore(task));
    }

    @Test
    void deadReplayValidationRejectsTamperedCommandBeforeRequeue() {
        when(mapper.insert(any(AiLowcodePublishTask.class))).thenReturn(1);
        AiLowcodePublishTask task = service.appendPostSync(
                config(), null, version(), "PUBLISH", false, null);
        task.setTaskStatus(LowcodePublishTaskStatus.DEAD.getCode());
        task.setCommandPayload(task.getCommandPayload().replace("orders", "invoices"));

        assertThrows(BusinessException.class, () -> service.validateReplayable(task));
    }

    @Test
    void claimUsesTenantScopedLeaseCas() {
        AiLowcodePublishTask candidate = task(LowcodePublishTaskStatus.PENDING, 0);
        AiLowcodePublishTask claimed = task(LowcodePublishTaskStatus.PROCESSING, 1);
        claimed.setLockOwner("worker-1");
        when(mapper.claim(eq(7L), eq(30L), eq("worker-1"),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(3))).thenReturn(1);
        when(mapper.selectByTaskId(7L, 30L)).thenReturn(claimed);

        AiLowcodePublishTask result = service.claim(candidate, "worker-1", LocalDateTime.now());

        assertNotNull(result);
        assertEquals("worker-1", result.getLockOwner());
    }

    @Test
    void exhaustedFailureMovesTaskToDeadWithoutPersistingExceptionMessage() {
        AiLowcodePublishTask claimed = task(LowcodePublishTaskStatus.PROCESSING, 3);
        claimed.setLockOwner("worker-1");
        when(mapper.markFailed(eq(claimed), eq("DEAD"), eq(null),
                eq("IllegalStateException"), any(LocalDateTime.class))).thenReturn(1);

        service.markFailed(claimed, new IllegalStateException("secret datasource payload"));

        verify(mapper).markFailed(eq(claimed), eq("DEAD"), eq(null),
                eq("IllegalStateException"), any(LocalDateTime.class));
    }

    @Test
    void onlinePublishIsPersistedAsClaimedDdlStageBeforeExecution() {
        when(versionMapper.selectMaxVersionNo(7L, 10L)).thenReturn(1);
        when(mapper.insert(any(AiLowcodePublishTask.class))).thenReturn(1);

        AiLowcodePublishTask task = service.stageOnlinePublish(onlinePlan(), "request-worker");

        assertEquals("ONLINE_PUBLISH", task.getOperationType());
        assertEquals("DDL_PENDING", task.getCurrentStage());
        assertEquals("PROCESSING", task.getTaskStatus());
        assertEquals(3, task.getVersionNo());
        assertEquals("request-worker", task.getLockOwner());
        assertEquals(1, task.getRetryCount());
        assertEquals(64, task.getSchemaHash().length());
        LowcodeOnlinePublishCommand restored = service.restoreOnlinePublish(task);
        assertEquals(4, restored.expectedDraftVersion());
        assertEquals(task.getVersionId(), restored.versionId());
    }

    @Test
    void onlineDeadReplayRejectsMismatchedOperationalSchemaIdentity() {
        when(versionMapper.selectMaxVersionNo(7L, 10L)).thenReturn(2);
        when(mapper.insert(any(AiLowcodePublishTask.class))).thenReturn(1);
        AiLowcodePublishTask task = service.stageOnlinePublish(onlinePlan(), "request-worker");
        task.setTaskStatus(LowcodePublishTaskStatus.DEAD.getCode());
        task.setSchemaHash("0".repeat(64));

        assertThrows(BusinessException.class, () -> service.validateReplayable(task));
    }

    private AiCrudConfig config() {
        AiCrudConfig config = new AiCrudConfig();
        config.setId(10L);
        config.setTenantId(7L);
        config.setConfigKey("orders");
        config.setModelSchema("{\"tableName\":\"biz_order\"}");
        config.setPageSchema("{}");
        config.setRuntimeDatasourceId(12L);
        config.setRuntimeDatasourceCode("master");
        config.setRuntimeTableName("biz_order");
        config.setPublishBy(42L);
        return config;
    }

    private AiCrudConfigVersion version() {
        AiCrudConfigVersion version = new AiCrudConfigVersion();
        version.setId(20L);
        version.setTenantId(7L);
        version.setConfigId(10L);
        version.setConfigKey("orders");
        version.setVersionNo(3);
        return version;
    }

    private AiLowcodePublishTask task(LowcodePublishTaskStatus status, int retryCount) {
        AiLowcodePublishTask task = new AiLowcodePublishTask();
        task.setId(30L);
        task.setTenantId(7L);
        task.setTaskStatus(status.getCode());
        task.setRetryCount(retryCount);
        task.setOperatorId(42L);
        return task;
    }

    private LowcodeOnlinePublishPlan onlinePlan() {
        LowcodeOnlinePublishConfigSnapshot snapshot = new LowcodeOnlinePublishConfigSnapshot();
        snapshot.setModelSchema("{\"tableName\":\"biz_order\",\"fields\":[]}");
        snapshot.setPageSchema("{\"layoutType\":\"simple-crud\"}");
        snapshot.setRuntimeDatasourceId(12L);
        snapshot.setRuntimeDatasourceCode("master");
        snapshot.setRuntimeTableName("biz_order");
        return new LowcodeOnlinePublishPlan(
                1, 7L, 10L, "orders", 4, 2, 42L, snapshot,
                true, 99L, "sales", "order", "订单", "在线发布");
    }
}
