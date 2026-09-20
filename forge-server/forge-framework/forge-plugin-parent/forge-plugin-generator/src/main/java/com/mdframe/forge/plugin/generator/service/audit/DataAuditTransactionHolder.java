package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;

import java.util.Map;

/**
 * 事务级采集上下文。Repository 只依赖本 Holder，避免与 DynamicCrudService 循环注入。
 */
public final class DataAuditTransactionHolder {

    private static final ThreadLocal<DataAuditCaptureSession> SESSION = new ThreadLocal<>();
    private static final ThreadLocal<DataAuditSourceType> SOURCE_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<DataAuditSourceType> FIELD_SOURCE = new ThreadLocal<>();
    private static final DataAuditPolicyIndex INDEX = new DataAuditPolicyIndex();

    private DataAuditTransactionHolder() {
    }

    public static DataAuditPolicyIndex index() {
        return INDEX;
    }

    public static DataAuditCaptureSession current() {
        return SESSION.get();
    }

    public static void bind(DataAuditCaptureSession session) {
        SESSION.set(session);
    }

    public static void clear() {
        SESSION.remove();
    }

    public static boolean hasSession() {
        return SESSION.get() != null;
    }

    public static AutoCloseable overrideSource(DataAuditSourceType sourceType) {
        DataAuditSourceType previous = SOURCE_OVERRIDE.get();
        SOURCE_OVERRIDE.set(sourceType);
        return () -> restore(SOURCE_OVERRIDE, previous);
    }

    public static AutoCloseable overrideFieldSource(DataAuditSourceType sourceType) {
        DataAuditSourceType previous = FIELD_SOURCE.get();
        FIELD_SOURCE.set(sourceType);
        return () -> restore(FIELD_SOURCE, previous);
    }

    public static DataAuditSourceType currentSourceOr(DataAuditSourceType fallback) {
        DataAuditSourceType override = SOURCE_OVERRIDE.get();
        return override == null ? fallback : override;
    }

    public static DataAuditSourceType currentFieldSource(DataAuditSourceType fallback) {
        DataAuditSourceType field = FIELD_SOURCE.get();
        if (field != null) {
            return field;
        }
        return currentSourceOr(fallback);
    }

    private static void restore(ThreadLocal<DataAuditSourceType> holder, DataAuditSourceType previous) {
        if (previous == null) {
            holder.remove();
        } else {
            holder.set(previous);
        }
    }

    public static void prepareWrite(String tableName, String primaryKeyColumn, Object recordId, WriteKind kind) {
        DataAuditPolicyIndex.TableBinding binding = resolveBinding(tableName);
        if (binding == null) {
            return;
        }
        DataAuditCaptureSession session = SESSION.get();
        if (session == null) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("启用审计的对象缺少采集上下文");
        }
        session.prepareWrite(binding, tableName, primaryKeyColumn, recordId, kind);
    }

    public static void afterWrite(String tableName,
                                  Object recordId,
                                  Map<String, Object> rowHint,
                                  DataAuditSourceType fieldSource,
                                  WriteKind kind,
                                  int affected) {
        if (affected <= 0) {
            return;
        }
        DataAuditPolicyIndex.TableBinding binding = resolveBinding(tableName);
        if (binding == null) {
            return;
        }
        DataAuditCaptureSession session = SESSION.get();
        if (session == null) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception("启用审计的对象缺少采集上下文");
        }
        session.afterWrite(binding, tableName, recordId, rowHint, fieldSource, kind);
    }

    private static DataAuditPolicyIndex.TableBinding resolveBinding(String tableName) {
        Long tenantId = DataAuditTenantSupport.currentTenantIdOrNull();
        return INDEX.findTable(tenantId, tableName);
    }

    public enum WriteKind {
        INSERT,
        UPDATE,
        DELETE
    }
}
