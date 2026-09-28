package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessFlowRuntimeVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StartFlowActionStepExecutor implements BusinessActionStepExecutor {

    private final BusinessFlowService flowService;

    @Override
    public String supportType() {
        return "START_FLOW";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        if (context.getRequest() == null || StringUtils.isBlank(context.getRequest().getRecordId())) {
            throw new BusinessException("发起流程步骤缺少记录ID");
        }
        String objectCode = context.getBusinessObject() == null ? context.getRequest().getObjectCode() : context.getBusinessObject().getObjectCode();
        String businessKey = objectCode + ":" + context.getRequest().getRecordId();
        Map<String, Object> config = step.getStepConfig();
        String flowModelKey = Boolean.FALSE.equals(config.get("useMainFlow"))
                ? BusinessActionStepConfigHelper.firstText(config, "flowModelKey", "modelKey")
                : BusinessActionStepConfigHelper.firstText(config, "flowModelKey", "modelKey");
        String title = StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "title", "titleTemplate"),
                context.getAction() == null ? "业务动作发起流程" : context.getAction().getActionName());
        JSONObject variables = new JSONObject();
        variables.putAll(BusinessActionStepConfigHelper.buildData(config, context));
        variables.put("actionCode", context.getAction() == null ? null : context.getAction().getActionCode());
        variables.put("correlationId", context.getCorrelationId());

        BusinessFlowRuntimeVO runtime = flowService.startFlowFromTrigger(flowModelKey, businessKey, title,
                resolveUserId(), resolveUsername(), context.getTenantId(), variables);

        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus(com.mdframe.forge.plugin.generator.enums.BusinessActionStepStatus.SUCCESS.getCode());
        result.setMessage("流程已发起");
        result.getResult().put("businessKey", businessKey);
        result.getResult().put("flowModelKey", runtime == null ? flowModelKey : runtime.getFlowModelKey());
        result.getResult().put("processInstanceId", runtime == null ? null : runtime.getProcessInstanceId());
        return result;
    }

    private Long resolveUserId() {
        try {
            return SessionHelper.getUserId();
        } catch (Exception e) {
            return 1L;
        }
    }

    private String resolveUsername() {
        try {
            return SessionHelper.getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
