package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用业务动作执行请求。
 */
@Data
public class BusinessActionExecuteDTO {

    private String suiteCode;

    private String objectCode;

    private String businessObjectCode;

    private String targetObjectCode;

    private String targetEntityCode;

    private String candidateObjectCode;

    private String referenceObjectCode;

    private String refObjectCode;

    private String sourceObjectCode;

    private String targetCode;

    private String recordId;

    /** 子表行动作所属父记录 ID。普通动作无需传入。 */
    private String parentRecordId;

    /** 子表行动作当前已落库子记录 ID。 */
    private String childRecordId;

    /** 不可由客户端自由解释的发布态主子关系键。 */
    private String relationKey;

    private String actionCode;

    private Map<String, Object> formData = new LinkedHashMap<>();

    private Map<String, Object> context = new LinkedHashMap<>();

    private String idempotencyKey;
}
