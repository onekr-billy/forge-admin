package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceExecuteDTO;
import com.mdframe.forge.plugin.generator.service.lowcode.query.LowcodeQuerySourceService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 受管外部接口调用步骤。
 *
 * <p>步骤不接受 URL、Header 或凭据，sourceKey 只能由查询源服务解析到已启用的
 * EXTERNAL_API。返回值仅按显式 resultMappings 写入动作上下文，不把完整外围响应写入动作日志。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallApiActionStepExecutor implements BusinessActionStepExecutor {

    private static final String SOURCE_TYPE = "EXTERNAL_API";
    private static final String FAILURE_THROW = "THROW";
    private static final String FAILURE_CONTINUE = "LOG_AND_CONTINUE";

    private final LowcodeQuerySourceService querySourceService;

    @Override
    public String supportType() {
        return "CALL_API";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context,
                                              BusinessActionStepDTO step) {
        Map<String, Object> config = step == null || step.getStepConfig() == null
                ? Map.of() : step.getStepConfig();
        BusinessActionCommandPolicy.validateCallApiStep(step, config);
        String failureStrategy = StringUtils.upperCase(StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "failureStrategy"), FAILURE_THROW));
        try {
            LowcodeQuerySourceResultVO sourceResult = querySourceService.execute(buildRequest(config, context));
            if (sourceResult == null) {
                throw new BusinessException("外部接口没有返回结果");
            }
            Map<String, Object> mapped = mapResults(config, sourceResult.getData(), context);
            String scopeKey = StringUtils.defaultIfBlank(step.getStepCode(), "callApi");
            context.getScopedVariables().put(scopeKey, mapped);

            BusinessActionStepResultVO result = new BusinessActionStepResultVO();
            result.setStatus("SUCCESS");
            result.setMessage("外部接口调用完成");
            result.getResult().put("sourceType", SOURCE_TYPE);
            result.getResult().put("sourceKey", sourceResult.getSourceKey());
            result.getResult().put("sourceId", sourceResult.getSourceId());
            result.getResult().put("mappingCount", mapped.size());
            result.getResult().put("resultMode", StringUtils.upperCase(StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(config, "resultMode"), "ROOT")));
            return result;
        } catch (Exception exception) {
            if (FAILURE_CONTINUE.equals(failureStrategy)) {
                log.warn("CALL_API 外部接口调用失败，继续执行后续步骤: sourceKey={}, correlationId={}, error={}",
                        BusinessActionStepConfigHelper.firstText(config, "sourceKey", "querySourceKey"),
                        context == null ? null : context.getCorrelationId(), exception.getMessage());
                BusinessActionStepResultVO result = new BusinessActionStepResultVO();
                result.setStatus("FAILED");
                result.setMessage("外部接口调用失败，已继续后续步骤");
                result.setErrorMessage(exception.getMessage());
                result.getResult().put("sourceType", SOURCE_TYPE);
                result.getResult().put("sourceKey",
                        BusinessActionStepConfigHelper.firstText(config, "sourceKey", "querySourceKey"));
                return result;
            }
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(StringUtils.defaultIfBlank(exception.getMessage(), "外部接口调用失败"));
        }
    }

    private LowcodeQuerySourceExecuteDTO buildRequest(Map<String, Object> config,
                                                       BusinessActionExecutionContext context) {
        LowcodeQuerySourceExecuteDTO request = new LowcodeQuerySourceExecuteDTO();
        request.setSourceType(SOURCE_TYPE);
        request.setSourceKey(BusinessActionStepConfigHelper.firstText(config, "sourceKey", "querySourceKey"));
        Map<String, Object> params = new LinkedHashMap<>();
        Object rawMappings = BusinessActionStepConfigHelper.firstValue(config,
                "paramMappings", "parameterMappings");
        if (rawMappings instanceof Collection<?> mappings) {
            for (Object raw : mappings) {
                Map<String, Object> mapping = BusinessActionStepConfigHelper.asMap(raw);
                String param = BusinessActionStepConfigHelper.firstText(mapping, "param", "name", "target");
                if (StringUtils.isBlank(param)) {
                    continue;
                }
                params.put(param, resolveParamValue(mapping, context));
            }
        }
        request.setParams(params);
        return request;
    }

    private Object resolveParamValue(Map<String, Object> mapping,
                                     BusinessActionExecutionContext context) {
        String source = StringUtils.upperCase(BusinessActionStepConfigHelper.firstText(mapping, "source"));
        if (source == null) {
            return BusinessActionStepConfigHelper.resolveMappingValue(mapping, context);
        }
        return switch (source) {
            case "FORM_FIELD" -> BusinessActionStepConfigHelper.readPath(
                    context.getFormData(), BusinessActionStepConfigHelper.firstText(mapping, "field"));
            case "RECORD_FIELD" -> BusinessActionStepConfigHelper.readPath(
                    context.getRecordData(), BusinessActionStepConfigHelper.firstText(mapping, "field"));
            case "CONTEXT_PATH" -> BusinessActionStepConfigHelper.readPath(
                    context.getExtraContext(), BusinessActionStepConfigHelper.firstText(mapping, "path"));
            case "ROUTE_QUERY" -> BusinessActionStepConfigHelper.readPath(
                    context.getExtraContext(), "routeQuery." + BusinessActionStepConfigHelper.firstText(mapping, "path"));
            case "SYSTEM_CONTEXT", "SYSTEM" -> BusinessActionStepConfigHelper.readPath(
                    context.getSystemContext(), BusinessActionStepConfigHelper.firstText(mapping, "path", "field"));
            case "STATIC", "STATIC_VALUE" -> mapping.containsKey("value")
                    ? mapping.get("value") : mapping.get("staticValue");
            default -> throw new BusinessException("CALL_API 参数来源不受支持: " + source);
        };
    }

    private Map<String, Object> mapResults(Map<String, Object> config,
                                            Object rawData,
                                            BusinessActionExecutionContext context) {
        Object selected = selectResult(config, rawData);
        Map<String, Object> mapped = new LinkedHashMap<>();
        Object rawMappings = BusinessActionStepConfigHelper.firstValue(config,
                "resultMappings", "responseMappings");
        if (!(rawMappings instanceof Collection<?> mappings)) {
            return mapped;
        }
        for (Object raw : mappings) {
            Map<String, Object> mapping = BusinessActionStepConfigHelper.asMap(raw);
            String from = BusinessActionStepConfigHelper.firstText(mapping, "from", "source", "path");
            Object value = StringUtils.isBlank(from) ? selected : BusinessActionStepConfigHelper.readPath(selected, from);
            String to = BusinessActionStepConfigHelper.firstText(mapping, "to", "targetField", "field");
            String whenMissing = StringUtils.upperCase(StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(mapping, "whenMissing"), "KEEP"));
            if (value == null && "KEEP".equals(whenMissing)) {
                continue;
            }
            mapped.put(to, value);
            String target = StringUtils.upperCase(StringUtils.defaultIfBlank(
                    BusinessActionStepConfigHelper.firstText(mapping, "target", "targetType"), "STEP_CONTEXT"));
            if ("FORM_DATA".equals(target)) {
                context.getFormData().put(to, value);
            }
        }
        return mapped;
    }

    private Object selectResult(Map<String, Object> config, Object data) {
        String resultMode = StringUtils.upperCase(StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "resultMode"), "ROOT"));
        if (!"FIRST_ROW".equals(resultMode)) {
            return data;
        }
        if (data instanceof Collection<?> collection) {
            return collection.stream().findFirst().orElse(null);
        }
        return data;
    }
}
