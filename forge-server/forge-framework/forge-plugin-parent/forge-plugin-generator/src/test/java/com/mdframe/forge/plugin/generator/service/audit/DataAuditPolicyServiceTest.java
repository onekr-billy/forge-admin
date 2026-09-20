package com.mdframe.forge.plugin.generator.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditPolicy;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditPolicyDTO;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditPolicyMapper;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditPolicyVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DataAuditPolicyServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OBJECT_ID = 10L;

    @AfterEach
    void clearIndex() {
        DataAuditTransactionHolder.index().replaceTenant(TENANT_ID, Map.of(), Map.of());
        ExecutionIdentityContextHolder.clear();
        TenantContextHolder.clear();
    }

    @Test
    void enabledPolicyCanBeStoppedWithoutDeletingItsConfiguration() {
        AiBusinessObject object = new AiBusinessObject();
        object.setId(OBJECT_ID);
        object.setObjectCode("purchase_request");
        object.setObjectName("采购申请");
        object.setConfigKey("purchase_request");
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("purchase_request");
        config.setTableName("biz_purchase_request");
        config.setOptions("{}");
        config.setModelSchema("{\"fields\":[]}");
        AiDataAuditPolicy policy = new AiDataAuditPolicy();
        policy.setId(100L);
        policy.setTenantId(TENANT_ID);
        policy.setObjectId(OBJECT_ID);
        policy.setEnabled(EnableStatus.ENABLED.getCode());
        policy.setReasonRequired(EnableStatus.ENABLED.getCode());
        policy.setShowInDetail(EnableStatus.ENABLED.getCode());
        policy.setPolicyVersion(2);
        policy.setEnabledAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        policy.setWriteBarrier(EnableStatus.DISABLED.getCode());

        DataAuditPolicyMapper policyMapper = stub(DataAuditPolicyMapper.class, (method, args) -> switch (method) {
            case "selectByObjectIdForUpdate", "selectByObjectId" -> policy;
            case "selectConfigured" -> List.of(policy);
            case "insert", "updateById" -> 1;
            default -> defaultValue(method);
        });
        BusinessObjectMapper objectMapper = stub(BusinessObjectMapper.class, (method, args) ->
                "selectByIdForTenant".equals(method) ? object : defaultValue(method));
        AiCrudConfigMapper configMapper = stub(AiCrudConfigMapper.class, (method, args) ->
                "selectByConfigKey".equals(method) ? config : defaultValue(method));
        DataAuditPolicyService service = new DataAuditPolicyService(
                policyMapper, objectMapper, configMapper, new ObjectMapper());

        DataAuditPolicyDTO request = new DataAuditPolicyDTO();
        request.setEnabled(false);
        request.setReasonRequired(false);
        request.setShowInDetail(false);

        TenantContextHolder.setTenantId(TENANT_ID);
        try (ExecutionIdentityContextHolder.Scope ignored = ExecutionIdentityContextHolder.open(identity())) {
            DataAuditPolicyVO result = service.updatePolicy(OBJECT_ID, request);

            assertFalse(result.getEnabled());
            assertFalse(result.getReasonRequired());
            assertFalse(result.getShowInDetail());
            assertEquals(3, result.getPolicyVersion());
            assertEquals(LocalDateTime.of(2026, 9, 20, 10, 0), result.getEnabledAt());
            assertEquals(EnableStatus.DISABLED.getCode(), policy.getWriteBarrier());
        }
    }

    private ExecutionIdentity identity() {
        LoginUser user = new LoginUser();
        user.setUserId(7L);
        user.setTenantId(TENANT_ID);
        user.setMainOrgId(3L);
        return new ExecutionIdentity(user, "USER", 7L, null,
                1L, "audit-test", "audit-test-token", Set.of());
    }

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.apply(method.getName(), args));
    }

    private Object defaultValue(String method) {
        if ("toString".equals(method)) {
            return "DataAuditPolicyServiceTestStub";
        }
        return null;
    }
}
