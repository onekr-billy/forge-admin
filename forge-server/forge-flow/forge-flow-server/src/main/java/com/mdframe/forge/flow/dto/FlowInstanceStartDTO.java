package com.mdframe.forge.flow.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发起流程请求。
 */
@Data
public class FlowInstanceStartDTO {

    private String businessKey;

    private String businessType;

    private String title;

    private String userId;

    private String userName;

    private String deptId;

    private String deptName;

    /**
     * 额外流程变量，键值由流程模型动态决定。
     */
    private Map<String, Object> variables;

    @JsonIgnore
    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        extraFields.put(key, value);
    }

    /**
     * 兼容历史请求：顶层未知字段与嵌套 {@code variables} 一并作为流程变量。
     */
    public Map<String, Object> resolveVariables() {
        Map<String, Object> resolved = new LinkedHashMap<>(extraFields);
        if (variables != null) {
            resolved.putAll(variables);
        }
        return resolved;
    }
}
