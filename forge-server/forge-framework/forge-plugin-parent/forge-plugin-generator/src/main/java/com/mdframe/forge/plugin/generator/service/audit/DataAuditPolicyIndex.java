package com.mdframe.forge.plugin.generator.service.audit;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审计对象策略与已启用表的内存索引。按租户隔离。
 */
public class DataAuditPolicyIndex {

    private final Map<Long, Map<String, TableBinding>> byTenant = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, ObjectPolicy>> byObject = new ConcurrentHashMap<>();

    public record TableBinding(Long objectId,
                               Long parentObjectId,
                               String tableName,
                               String relationKey,
                               String fkColumn,
                               boolean master) {
    }

    public record ObjectPolicy(Long objectId,
                               boolean enabled,
                               boolean reasonRequired,
                               boolean showInDetail,
                               int policyVersion,
                               boolean writeBarrier) {
    }

    public void replaceTenant(Long tenantId, Map<String, TableBinding> tables, Map<Long, ObjectPolicy> objects) {
        if (tenantId == null) {
            return;
        }
        byTenant.put(tenantId, tables == null ? Map.of() : Map.copyOf(tables));
        byObject.put(tenantId, objects == null ? Map.of() : Map.copyOf(objects));
    }

    public boolean hasTenant(Long tenantId) {
        return tenantId != null && byObject.containsKey(tenantId);
    }

    public TableBinding findTable(Long tenantId, String tableName) {
        if (tenantId == null || StringUtils.isBlank(tableName)) {
            return null;
        }
        Map<String, TableBinding> tables = byTenant.get(tenantId);
        if (tables == null) {
            return null;
        }
        TableBinding binding = tables.get(tableName);
        if (binding != null) {
            return binding;
        }
        return tables.get(tableName.toLowerCase(Locale.ROOT));
    }

    public ObjectPolicy findObject(Long tenantId, Long objectId) {
        if (tenantId == null || objectId == null) {
            return null;
        }
        Map<Long, ObjectPolicy> objects = byObject.get(tenantId);
        return objects == null ? null : objects.get(objectId);
    }

    public boolean isObjectEnabled(Long tenantId, Long objectId) {
        ObjectPolicy policy = findObject(tenantId, objectId);
        return policy != null && policy.enabled() && !policy.writeBarrier();
    }

    public Map<String, TableBinding> tablesOf(Long tenantId) {
        Map<String, TableBinding> tables = byTenant.get(tenantId);
        return tables == null ? Collections.emptyMap() : tables;
    }
}
