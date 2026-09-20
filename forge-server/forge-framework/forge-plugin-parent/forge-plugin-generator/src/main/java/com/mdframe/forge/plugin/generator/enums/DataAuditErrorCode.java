package com.mdframe.forge.plugin.generator.enums;

import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据审计错误语义。沿用 BusinessException / RespInfo，不另建响应壳。
 */
@Getter
public enum DataAuditErrorCode {

    AUDIT_REASON_REQUIRED(400, "AUDIT_REASON_REQUIRED", "请填写修改原因"),
    AUDIT_REVISION_REQUIRED(400, "AUDIT_REVISION_REQUIRED", "请刷新后重试，缺少预期修订号"),
    AUDIT_REVISION_CONFLICT(409, "AUDIT_REVISION_CONFLICT", "记录已被他人更新，请刷新后重试"),
    AUDIT_UNSUPPORTED(400, "AUDIT_UNSUPPORTED", "当前对象不支持数据变更审计"),
    AUDIT_VALUE_TOO_LARGE(400, "AUDIT_VALUE_TOO_LARGE", "字段变更内容超过容量上限，已拒绝本次写入"),
    AUDIT_WRITE_FAILED(500, "AUDIT_WRITE_FAILED", "数据审计写入失败，业务已回滚"),
    AUDIT_FORBIDDEN(403, "AUDIT_FORBIDDEN", "无权访问数据变更记录");

    private final int status;
    private final String code;
    private final String message;

    DataAuditErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public BusinessException exception() {
        return exception(message);
    }

    public BusinessException exception(String detail) {
        String text = detail == null || detail.isBlank() ? message : detail;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("errorCode", code);
        return new BusinessException(status, text).setData(data);
    }
}
