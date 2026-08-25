package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.util.DynamicQueryGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务事件模型。
 * <p>
 * 当动态 CRUD 执行增删改操作时，由 BusinessEventPublisher 发布此事件，
 * 触发器引擎（BusinessTriggerExecutor）根据事件类型和条件匹配触发器并执行动作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEvent {

    /** 事件类型 */
    private String eventType;

    /** 业务套件编码 */
    private String suiteCode;

    /** 业务对象编码 */
    private String objectCode;

    /** 运行配置键 */
    private String configKey;

    /** 记录ID */
    private String recordId;

    /** 当前记录数据 */
    private Map<String, Object> recordData;

    /** 变更前数据（UPDATE/STATUS_CHANGED 时有值） */
    private Map<String, Object> previousData;

    /** 操作用户ID */
    private Long operatorId;

    /** 操作用户名称 */
    private String operatorName;

    /** 租户ID */
    private Long tenantId;

    /**
     * 读取当前业务记录字段。动态 CRUD 的单表返回是平铺 Map，主子表返回则是
     * {@code {main, children}}；事件消费者不应感知这两种返回协议的差异。
     */
    public Object readRecordValue(String field) {
        return readValue(recordData, field);
    }

    public Object readPreviousValue(String field) {
        return readValue(previousData, field);
    }

    public static Object readValue(Map<String, Object> data, String field) {
        if (data == null || StringUtils.isBlank(field)) {
            return null;
        }
        String normalizedField = field.trim();
        String mainField = normalizedField.startsWith("main.")
                ? normalizedField.substring("main.".length()) : normalizedField;
        if (!normalizedField.startsWith("main.") && containsField(data, normalizedField)) {
            return readFlatValue(data, normalizedField);
        }
        Object main = data.get("main");
        if (main instanceof Map<?, ?> mainMap) {
            Map<String, Object> mainRecord = new LinkedHashMap<>();
            mainMap.forEach((key, value) -> {
                if (key != null) {
                    mainRecord.put(String.valueOf(key), value);
                }
            });
            return readFlatValue(mainRecord, mainField);
        }
        return readFlatValue(data, mainField);
    }

    private static boolean containsField(Map<String, Object> data, String field) {
        return data.containsKey(field)
                || data.containsKey(DynamicQueryGenerator.snakeToCamel(field))
                || data.containsKey(DynamicQueryGenerator.camelToSnake(field));
    }

    private static Object readFlatValue(Map<String, Object> data, String field) {
        if (data.containsKey(field)) {
            return data.get(field);
        }
        String camelField = DynamicQueryGenerator.snakeToCamel(field);
        if (data.containsKey(camelField)) {
            return data.get(camelField);
        }
        String snakeField = DynamicQueryGenerator.camelToSnake(field);
        return data.get(snakeField);
    }

    // ========== 事件类型常量 ==========

    public static final String RECORD_CREATED = "RECORD_CREATED";
    public static final String RECORD_UPDATED = "RECORD_UPDATED";
    public static final String RECORD_DELETED = "RECORD_DELETED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String FIELD_CHANGED = "FIELD_CHANGED";
    public static final String FORM_SUBMITTED = "FORM_SUBMITTED";
    public static final String FLOW_APPROVED = "FLOW_APPROVED";
    public static final String FLOW_REJECTED = "FLOW_REJECTED";
    public static final String FLOW_CANCELED = "FLOW_CANCELED";
    public static final String SCHEDULED_DUE = "SCHEDULED_DUE";
}
