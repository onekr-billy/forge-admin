package com.mdframe.forge.plugin.generator.dto;

import lombok.Data;

import java.util.Map;

/**
 * 业务触发器手动发起流程请求。
 */
@Data
public class BusinessTriggerFlowStartDTO {

    private String objectCode;

    private Object recordId;

    private Map<String, Object> recordData;

    public String recordIdText() {
        return recordId == null ? null : String.valueOf(recordId);
    }
}
