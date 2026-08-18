package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 普通低代码单据的结构化状态迁移。
 *
 * <p>状态期望值和目标值会在同一条条件 UPDATE 中执行，避免先查后改的竞态。</p>
 */
@Component
@RequiredArgsConstructor
public class TransitionStatusActionStepExecutor implements BusinessActionStepExecutor {

    private final DynamicCrudService dynamicCrudService;

    @Override
    public String supportType() {
        return "TRANSITION_STATUS";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        Map<String, Object> config = step.getStepConfig() == null ? Map.of() : step.getStepConfig();
        String targetConfigKey = StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "targetConfigKey"),
                context.getBusinessObject() == null ? null : context.getBusinessObject().getConfigKey());
        if (StringUtils.isBlank(targetConfigKey)) {
            throw new BusinessException("状态迁移步骤缺少 targetConfigKey");
        }
        Object targetRecordId = BusinessActionStepConfigHelper.resolveTargetRecordId(config, context);
        if (targetRecordId == null || StringUtils.isBlank(String.valueOf(targetRecordId))) {
            throw new BusinessException("状态迁移步骤缺少目标记录 ID");
        }
        String statusField = BusinessActionStepConfigHelper.firstText(
                config, "statusField", "status", "targetField");
        Object fromValue = BusinessActionStepConfigHelper.firstValue(config, "fromValue", "from");
        Object toValue = BusinessActionStepConfigHelper.firstValue(config, "toValue", "to");
        if (StringUtils.isBlank(statusField) || fromValue == null || toValue == null) {
            throw new BusinessException("状态迁移步骤缺少状态字段或起止值");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(statusField, toValue);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put(statusField, fromValue);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("eventType", "STATUS_TRANSITION");
        audit.put("statusField", statusField);
        audit.put("from", String.valueOf(fromValue));
        audit.put("to", String.valueOf(toValue));
        audit.put("targetConfigKey", targetConfigKey);
        audit.put("targetRecordId", String.valueOf(targetRecordId));
        try {
            dynamicCrudService.updateCommandFields(targetConfigKey, targetRecordId, fields, expected);
            audit.put("outcome", "SUCCESS");
            context.getAuditTransitions().add(audit);
        } catch (RuntimeException e) {
            audit.put("outcome", "FAILED");
            context.getAuditTransitions().add(audit);
            throw e;
        }

        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus("SUCCESS");
        result.setMessage("单据状态已变更");
        result.getResult().putAll(audit);
        return result;
    }
}
