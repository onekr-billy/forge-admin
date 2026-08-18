package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class UpdateFieldActionStepExecutor implements BusinessActionStepExecutor {

    private final DynamicCrudService dynamicCrudService;

    @Override
    public String supportType() {
        return "UPDATE_FIELD";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        Map<String, Object> config = step.getStepConfig();
        String targetConfigKey = StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "targetConfigKey"),
                context.getBusinessObject() == null ? null : context.getBusinessObject().getConfigKey());
        if (StringUtils.isBlank(targetConfigKey)) {
            throw new BusinessException("更新字段步骤缺少 targetConfigKey");
        }
        Object targetRecordId = BusinessActionStepConfigHelper.resolveTargetRecordId(config, context);
        Map<String, Object> fields = BusinessActionStepConfigHelper.buildData(config, context);
        if (fields.isEmpty()) {
            throw new BusinessException("更新字段步骤没有可更新字段");
        }
        Map<String, Object> expected = BusinessActionStepConfigHelper.buildExpectedData(config, context);
        dynamicCrudService.updateCommandFields(targetConfigKey, targetRecordId, fields, expected);

        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus("SUCCESS");
        result.setMessage("字段已更新");
        result.getResult().put("targetConfigKey", targetConfigKey);
        result.getResult().put("targetRecordId", targetRecordId);
        result.getResult().put("fieldCount", fields.size());
        return result;
    }
}
