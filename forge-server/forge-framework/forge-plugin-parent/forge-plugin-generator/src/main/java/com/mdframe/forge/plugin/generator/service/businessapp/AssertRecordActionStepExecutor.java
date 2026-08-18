package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在本地事务中锁定并校验一条发布态记录，作为后续写入/数量调整的状态门禁。
 */
@Component
@RequiredArgsConstructor
public class AssertRecordActionStepExecutor implements BusinessActionStepExecutor {

    private final DynamicCrudService dynamicCrudService;

    @Override
    public String supportType() {
        return "ASSERT_RECORD";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        Map<String, Object> config = step.getStepConfig() == null ? Map.of() : step.getStepConfig();
        String targetConfigKey = StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "targetConfigKey"),
                context.getBusinessObject() == null ? null : context.getBusinessObject().getConfigKey());
        if (StringUtils.isBlank(targetConfigKey)) {
            throw new BusinessException("记录门禁缺少 targetConfigKey");
        }
        Object targetRecordId = BusinessActionStepConfigHelper.resolveTargetRecordId(config, context);
        if (targetRecordId == null || StringUtils.isBlank(String.valueOf(targetRecordId))) {
            throw new BusinessException("记录门禁缺少目标记录 ID");
        }
        Map<String, Object> expected = BusinessActionStepConfigHelper.buildExpectedData(config, context);
        List<Map<String, Object>> numericConstraints =
                BusinessActionStepConfigHelper.buildNumericConstraints(config, context);
        if (numericConstraints.isEmpty()) {
            dynamicCrudService.assertCommandRecord(targetConfigKey, targetRecordId, expected);
        } else {
            dynamicCrudService.assertCommandRecord(targetConfigKey, targetRecordId, expected, numericConstraints);
        }

        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus("SUCCESS");
        result.setMessage("记录状态门禁通过");
        result.setResult(new LinkedHashMap<>(Map.of(
                "targetConfigKey", targetConfigKey,
                "targetRecordId", targetRecordId)));
        return result;
    }
}
