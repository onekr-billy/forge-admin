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
public class CreateRecordActionStepExecutor implements BusinessActionStepExecutor {

    private final DynamicCrudService dynamicCrudService;

    @Override
    public String supportType() {
        return "CREATE_RECORD";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        Map<String, Object> config = step.getStepConfig();
        String targetConfigKey = BusinessActionStepConfigHelper.firstText(config, "targetConfigKey");
        if (StringUtils.isBlank(targetConfigKey)) {
            throw new BusinessException("创建记录步骤缺少 targetConfigKey");
        }
        Map<String, Object> data = BusinessActionStepConfigHelper.buildData(config, context);
        if (data.isEmpty()) {
            throw new BusinessException("创建记录步骤没有可写入字段");
        }
        Map<String, Object> created = dynamicCrudService.insertCommandRecord(targetConfigKey, data);
        Object createdRecordId = created == null ? null : dynamicCrudService.resolveRecordId(targetConfigKey, created);

        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus("SUCCESS");
        result.setMessage("记录已创建");
        result.getResult().put("targetConfigKey", targetConfigKey);
        result.getResult().put("createdRecordId", createdRecordId);
        return result;
    }
}
