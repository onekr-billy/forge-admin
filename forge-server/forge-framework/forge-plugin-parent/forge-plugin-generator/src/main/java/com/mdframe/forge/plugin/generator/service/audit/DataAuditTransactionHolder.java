package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.enums.DataAuditErrorCode;
import com.mdframe.forge.plugin.generator.enums.DataAuditSourceType;
import org.apache.commons.lang3.StringUtils;

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
        prepareWrite(tableName, primaryKeyColumn, recordId, kind, null);
    }

    public static void prepareWrite(String tableName,
                                    String primaryKeyColumn,
                                    Object recordId,
                                    WriteKind kind,
                                    Map<String, Object> beforeSnapshot) {
        DataAuditPolicyIndex.TableBinding binding = resolveBinding(tableName);
        if (binding == null) {
            return;
        }
        Long tenantId = DataAuditTenantSupport.currentTenantIdOrNull();
        if (!INDEX.isObjectEnabled(tenantId, binding.objectId())) {
            return;
        }
        DataAuditCaptureSession session = SESSION.get();
        if (session == null) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception(missingCaptureContextMessage(tableName, kind));
        }
        session.prepareWrite(binding, tableName, primaryKeyColumn, recordId, kind, beforeSnapshot);
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
        Long tenantId = DataAuditTenantSupport.currentTenantIdOrNull();
        if (!INDEX.isObjectEnabled(tenantId, binding.objectId())) {
            return;
        }
        DataAuditCaptureSession session = SESSION.get();
        if (session == null) {
            throw DataAuditErrorCode.AUDIT_WRITE_FAILED.exception(missingCaptureContextMessage(tableName, kind));
        }
        session.afterWrite(binding, tableName, recordId, rowHint, fieldSource, kind);
    }

    private static String missingCaptureContextMessage(String tableName, WriteKind kind) {
        String action = kind == WriteKind.INSERT ? "新增" : kind == WriteKind.DELETE ? "删除" : "更新";
        return "表「" + StringUtils.defaultString(tableName) + "」已启用数据变更审计，但本次" + action
                + "未进入审计采集会话。"
                + "常见原因：1) 业务对象与运行配置 configKey 不一致；"
                + "2) 审计策略刚开启/切换后页面未刷新；"
                + "3) 写入口绕过了 DynamicCrud 审计开场。"
                + "处理：到「数据审计」确认该对象策略已正确开启并刷新页面重试；"
                + "联调也可先关闭该对象审计后再写入。";
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
