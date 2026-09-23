package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditCursor;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditPolicy;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditCursorMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditPolicyMapper;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditRecordMetaVO;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAuditRecordMetaBuilderTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OBJECT_ID = 10L;

    @AfterEach
    void clearIndex() {
        DataAuditTransactionHolder.index().replaceTenant(TENANT_ID, Map.of(), Map.of());
        TenantContextHolder.clear();
    }

    @Test
    void stoppedPolicyKeepsHistoryVisibilityAndRevision() {
        DataAuditPolicyIndex.ObjectPolicy policy = new DataAuditPolicyIndex.ObjectPolicy(
                OBJECT_ID, false, false, true, 4, false);
        DataAuditTransactionHolder.index().replaceTenant(
                TENANT_ID, Map.of(), Map.of(OBJECT_ID, policy));

        AiBusinessObject object = new AiBusinessObject();
        object.setId(OBJECT_ID);
        AiDataAuditCursor cursor = new AiDataAuditCursor();
        cursor.setRevision(7L);
        DataAuditCursorMapper cursorMapper = (DataAuditCursorMapper) Proxy.newProxyInstance(
                DataAuditCursorMapper.class.getClassLoader(),
                new Class<?>[]{DataAuditCursorMapper.class},
                (proxy, method, args) -> "selectByRecord".equals(method.getName()) ? cursor : null);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", "1001");

        DataAuditRecordMetaVO meta = DataAuditRecordMetaBuilder.build(record, TENANT_ID, object, cursorMapper);

        assertTrue(meta.getConfigured());
        assertFalse(meta.getEnabled());
        assertTrue(meta.getShowInDetail());
        assertTrue(meta.getHistoryAvailable());
        assertEquals(7L, meta.getRevision());
    }

    @Test
    void detailReadLoadsPolicyBeforeAttachingAuditMetadata() {
        AiBusinessObject object = new AiBusinessObject();
        object.setId(OBJECT_ID);
        object.setConfigKey("purchase_request");
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("purchase_request");
        config.setTableName("biz_purchase_request");
        AiDataAuditPolicy policy = new AiDataAuditPolicy();
        policy.setObjectId(OBJECT_ID);
        policy.setEnabled(EnableStatus.ENABLED.getCode());
        policy.setReasonRequired(EnableStatus.ENABLED.getCode());
        policy.setShowInDetail(EnableStatus.ENABLED.getCode());
        policy.setPolicyVersion(1);
        policy.setWriteBarrier(EnableStatus.DISABLED.getCode());
        AiDataAuditCursor cursor = new AiDataAuditCursor();
        cursor.setRevision(3L);

        DataAuditPolicyMapper policyMapper = stub(DataAuditPolicyMapper.class, (method, args) ->
                "selectConfigured".equals(method) ? List.of(policy) : null);
        BusinessObjectMapper objectMapper = stub(BusinessObjectMapper.class, (method, args) ->
                switch (method) {
                    case "selectByConfigKey", "selectByIdForTenant" -> object;
                    default -> null;
                });
        AiCrudConfigMapper configMapper = stub(AiCrudConfigMapper.class, (method, args) ->
                "selectByConfigKey".equals(method) ? config : null);
        DataAuditCursorMapper cursorMapper = stub(DataAuditCursorMapper.class, (method, args) ->
                "selectByRecord".equals(method) ? cursor : null);
        DataAuditPolicyService policyService = new DataAuditPolicyService(
                policyMapper, objectMapper, configMapper, new ObjectMapper());
        DataAuditCaptureService captureService = new DataAuditCaptureService(
                cursorMapper, null, null, policyService, null, null, null,
                objectMapper, null, new ObjectMapper());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", "1001");

        TenantContextHolder.setTenantId(TENANT_ID);
        captureService.attachReadMeta(config, record);

        DataAuditRecordMetaVO meta = (DataAuditRecordMetaVO) record.get(DataAuditPayloadSupport.PAYLOAD_KEY);
        assertTrue(meta.getConfigured());
        assertTrue(meta.getEnabled());
        assertTrue(meta.getReasonRequired());
        assertTrue(meta.getShowInDetail());
        assertTrue(meta.getHistoryAvailable());
        assertEquals(3L, meta.getRevision());
    }

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.apply(method.getName(), args));
    }
}
