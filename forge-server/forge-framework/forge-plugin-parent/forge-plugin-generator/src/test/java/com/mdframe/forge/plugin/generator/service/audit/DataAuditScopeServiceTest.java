package com.mdframe.forge.plugin.generator.service.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditEvent;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditPolicy;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditEventQueryDTO;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditEventMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditPolicyMapper;
import com.mdframe.forge.plugin.generator.mapper.DataAuditScopeMapper;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditScopeItemVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataAuditScopeServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long OBJECT_ID = 10L;

    @AfterEach
    void clearContext() {
        DataAuditTransactionHolder.index().replaceTenant(TENANT_ID, Map.of(), Map.of());
        ExecutionIdentityContextHolder.clear();
        TenantContextHolder.clear();
    }

    @Test
    void superAdminUsesAllConfiguredPoliciesWithoutExplicitRoleScope() {
        DataAuditScopeMapper scopeMapper = stub(DataAuditScopeMapper.class, (method, args) -> {
            if ("selectByRoleIds".equals(method)) {
                throw new AssertionError("超级管理员不应依赖角色审计范围");
            }
            return defaultValue(method);
        });
        AiDataAuditPolicy stoppedPolicy = policy(OBJECT_ID, EnableStatus.DISABLED.getCode());
        DataAuditPolicyMapper policyMapper = stub(DataAuditPolicyMapper.class, (method, args) ->
                "selectConfigured".equals(method) ? List.of(stoppedPolicy) : defaultValue(method));
        AiBusinessObject object = new AiBusinessObject();
        object.setId(OBJECT_ID);
        object.setObjectCode("purchase_request");
        object.setObjectName("采购申请");
        BusinessObjectMapper businessObjectMapper = stub(BusinessObjectMapper.class, (method, args) ->
                "selectByIdForTenant".equals(method) ? object : defaultValue(method));
        DataAuditScopeService service = new DataAuditScopeService(
                scopeMapper, businessObjectMapper, policyMapper);

        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity(0, List.of())) {
            assertEquals(Set.of(OBJECT_ID), service.currentUserObjectIds());
            List<DataAuditScopeItemVO> objects = service.listCurrentUserObjects();
            assertEquals(1, objects.size());
            assertEquals(String.valueOf(OBJECT_ID), objects.get(0).getObjectId());
            assertEquals("采购申请", objects.get(0).getObjectName());
        }
    }

    @Test
    void regularUserWithoutRoleScopeStillHasNoAuditObjects() {
        List<Long> roleIds = List.of(20L);
        DataAuditScopeMapper scopeMapper = stub(DataAuditScopeMapper.class, (method, args) ->
                "selectByRoleIds".equals(method) ? List.of() : defaultValue(method));
        BusinessObjectMapper businessObjectMapper = stub(BusinessObjectMapper.class,
                (method, args) -> defaultValue(method));
        DataAuditPolicyMapper policyMapper = stub(DataAuditPolicyMapper.class, (method, args) -> {
            if ("selectConfigured".equals(method)) {
                throw new AssertionError("普通用户仍应按角色审计范围授权");
            }
            return defaultValue(method);
        });
        DataAuditScopeService service = new DataAuditScopeService(
                scopeMapper, businessObjectMapper, policyMapper);

        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity(2, roleIds)) {
            assertTrue(service.currentUserObjectIds().isEmpty());
            assertTrue(service.listCurrentUserObjects().isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void superAdminAuditQueryReachesEventMapperWithConfiguredObjects() {
        AiDataAuditPolicy stoppedPolicy = policy(OBJECT_ID, EnableStatus.DISABLED.getCode());
        DataAuditPolicyMapper policyMapper = stub(DataAuditPolicyMapper.class, (method, args) ->
                "selectConfigured".equals(method) ? List.of(stoppedPolicy) : defaultValue(method));
        DataAuditScopeMapper scopeMapper = stub(DataAuditScopeMapper.class,
                (method, args) -> defaultValue(method));
        BusinessObjectMapper businessObjectMapper = stub(BusinessObjectMapper.class,
                (method, args) -> defaultValue(method));
        DataAuditScopeService scopeService = new DataAuditScopeService(
                scopeMapper, businessObjectMapper, policyMapper);
        AtomicReference<List<Long>> queriedObjectIds = new AtomicReference<>();
        DataAuditEventMapper eventMapper = stub(DataAuditEventMapper.class, (method, args) -> {
            if ("selectEventPage".equals(method)) {
                queriedObjectIds.set(new ArrayList<>((List<Long>) args[3]));
                return new Page<AiDataAuditEvent>(1, 20, 0);
            }
            return defaultValue(method);
        });
        AiCrudConfigMapper crudConfigMapper = stub(AiCrudConfigMapper.class,
                (method, args) -> defaultValue(method));
        DataAuditPolicyService policyService = new DataAuditPolicyService(
                policyMapper, businessObjectMapper, crudConfigMapper, new ObjectMapper());
        DataAuditQueryService queryService = new DataAuditQueryService(
                eventMapper,
                null,
                null,
                scopeService,
                null,
                policyService,
                crudConfigMapper,
                businessObjectMapper,
                null);

        try (ExecutionIdentityContextHolder.Scope ignored = openIdentity(0, List.of())) {
            queryService.pageAdminEvents(new DataAuditEventQueryDTO(), 1, 20);
        }

        assertEquals(List.of(OBJECT_ID), queriedObjectIds.get());
    }

    private AiDataAuditPolicy policy(Long objectId, Integer enabled) {
        AiDataAuditPolicy policy = new AiDataAuditPolicy();
        policy.setTenantId(TENANT_ID);
        policy.setObjectId(objectId);
        policy.setEnabled(enabled);
        return policy;
    }

    private ExecutionIdentityContextHolder.Scope openIdentity(Integer userType, List<Long> roleIds) {
        LoginUser user = new LoginUser();
        user.setUserId(7L);
        user.setTenantId(TENANT_ID);
        user.setUserType(userType);
        user.setRoleIds(roleIds);
        ExecutionIdentity identity = new ExecutionIdentity(
                user, "USER", 7L, null, 1L, "audit-test", "audit-test-token", Set.of());
        TenantContextHolder.setTenantId(TENANT_ID);
        return ExecutionIdentityContextHolder.open(identity);
    }

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, BiFunction<String, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.apply(method.getName(), args));
    }

    private Object defaultValue(String method) {
        if ("toString".equals(method)) {
            return "DataAuditScopeServiceTestStub";
        }
        return null;
    }
}
