package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 在单条 UPDATE 中原子调整一个记录的多个数值字段。
 */
@Component
@RequiredArgsConstructor
public class AdjustNumberActionStepExecutor implements BusinessActionStepExecutor {

    private final DynamicCrudService dynamicCrudService;

    @Override
    public String supportType() {
        return "ADJUST_NUMBER";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        Map<String, Object> config = step.getStepConfig();
        String targetConfigKey = StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "targetConfigKey"),
                context.getBusinessObject() == null ? null : context.getBusinessObject().getConfigKey());
        if (StringUtils.isBlank(targetConfigKey)) {
            throw new BusinessException("数值调整步骤缺少 targetConfigKey");
        }
        Object targetRecordId = BusinessActionStepConfigHelper.resolveTargetRecordId(config, context);
        if (targetRecordId == null || StringUtils.isBlank(String.valueOf(targetRecordId))) {
            throw new BusinessException("数值调整步骤缺少目标记录 ID");
        }

        Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        Map<String, BigDecimal> minimums = new LinkedHashMap<>();
        Map<String, BigDecimal> maximums = new LinkedHashMap<>();
        Set<String> fields = new LinkedHashSet<>();
        List<?> adjustments = BusinessActionStepConfigHelper.firstList(config, "adjustments", "fields");
        if (adjustments.isEmpty()) {
            throw new BusinessException("数值调整步骤没有配置调整字段");
        }
        for (Object item : adjustments) {
            Map<String, Object> mapping = BusinessActionStepConfigHelper.asMap(item);
            String targetField = BusinessActionStepConfigHelper.firstText(
                    mapping, "targetField", "target", "field", "name");
            if (StringUtils.isBlank(targetField) || !fields.add(targetField)) {
                throw new BusinessException(StringUtils.isBlank(targetField)
                        ? "数值调整字段不能为空" : "数值调整字段重复: " + targetField);
            }
            BigDecimal value = decimalValue(BusinessActionStepConfigHelper.resolveMappingValue(mapping, context), targetField);
            String operator = StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(mapping, "operator", "operation"), "ADD")
                    .trim().toUpperCase(Locale.ROOT);
            if ("SUBTRACT".equals(operator) || "SUB".equals(operator) || "DECREASE".equals(operator)) {
                value = value.negate();
            } else if (!"ADD".equals(operator) && !"INCREASE".equals(operator)) {
                throw new BusinessException("不支持的数值调整操作: " + operator);
            }
            deltas.put(targetField, value);
            putDecimalBound(minimums, targetField, mapping.get("min"), "最小值");
            putDecimalBound(maximums, targetField, mapping.get("max"), "最大值");
        }

        Map<String, Object> expected = BusinessActionStepConfigHelper.buildExpectedData(config, context);
        dynamicCrudService.adjustCommandNumbers(
                targetConfigKey, targetRecordId, deltas, minimums, maximums, expected);

        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus("SUCCESS");
        result.setMessage("数值字段已原子调整");
        result.getResult().put("targetConfigKey", targetConfigKey);
        result.getResult().put("targetRecordId", targetRecordId);
        result.getResult().put("fieldCount", deltas.size());
        return result;
    }

    private BigDecimal decimalValue(Object value, String field) {
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            throw new BusinessException("数值调整缺少调整量: " + field);
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException("数值调整量格式不正确: " + field);
        }
    }

    private void putDecimalBound(Map<String, BigDecimal> target,
                                 String field,
                                 Object rawValue,
                                 String label) {
        if (rawValue == null || StringUtils.isBlank(String.valueOf(rawValue))) {
            return;
        }
        try {
            target.put(field, new BigDecimal(String.valueOf(rawValue)));
        } catch (NumberFormatException e) {
            throw new BusinessException("数值调整" + label + "格式不正确: " + field);
        }
    }
}

