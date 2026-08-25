package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 遍历集合并执行子步骤的通用动作步骤。
 */
@Component
@RequiredArgsConstructor
public class ForeachActionStepExecutor implements BusinessActionStepExecutor {

    private static final String DEPTH_KEY = "__foreachDepth";
    private static final int MAX_DEPTH = 2;

    private final ObjectProvider<BusinessActionExecutionService> actionExecutionServiceProvider;

    @Override
    public String supportType() {
        return "FOREACH";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        Map<String, Object> config = step.getStepConfig() == null ? new LinkedHashMap<>() : step.getStepConfig();
        int depth = resolveDepth(context);
        if (depth >= MAX_DEPTH) {
            throw new BusinessException("FOREACH 动作步骤最多嵌套 " + MAX_DEPTH + " 层");
        }
        List<?> items = resolveItems(config, context);
        BusinessActionExecutionService actionExecutionService = actionExecutionServiceProvider.getObject();
        List<BusinessActionStepDTO> childSteps = actionExecutionService.normalizeNestedSteps(
                BusinessActionStepConfigHelper.firstValue(config, "steps", "childSteps"));
        String itemAlias = StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "itemAlias", "itemName"), "item");
        String indexAlias = StringUtils.defaultIfBlank(
                BusinessActionStepConfigHelper.firstText(config, "indexAlias", "indexName"), "index");
        List<Map<String, Object>> rowResults = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            int itemIndex = i;
            Map<String, Object> previousScope = new LinkedHashMap<>(context.getScopedVariables());
            Map<String, Object> nextScope = new LinkedHashMap<>(previousScope);
            nextScope.put(itemAlias, items.get(itemIndex));
            nextScope.put(indexAlias, itemIndex);
            nextScope.put(DEPTH_KEY, depth + 1);
            context.setScopedVariables(nextScope);
            try {
                List<BusinessActionStepResultVO> childResults = actionExecutionService.executeNestedSteps(context, childSteps);
                childResults.forEach(result -> result.getResult().putIfAbsent("itemIndex", itemIndex));
                rowResults.add(rowResult(itemIndex, "SUCCESS", null, childResults));
            } catch (RuntimeException e) {
                rowResults.add(rowResult(itemIndex, "FAILED", e.getMessage(), List.of()));
                throw e;
            } finally {
                context.setScopedVariables(previousScope);
            }
        }
        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus(com.mdframe.forge.plugin.generator.enums.BusinessActionStepStatus.SUCCESS.getCode());
        result.setMessage("循环步骤执行完成，共 " + items.size() + " 行");
        result.getResult().put("itemCount", items.size());
        result.getResult().put("rowResults", rowResults);
        return result;
    }

    private int resolveDepth(BusinessActionExecutionContext context) {
        Object value = context.getScopedVariables().get(DEPTH_KEY);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<?> resolveItems(Map<String, Object> config, BusinessActionExecutionContext context) {
        Object explicitItems = BusinessActionStepConfigHelper.firstValue(config, "items", "collection");
        Object value = explicitItems;
        String collectionPath = BusinessActionStepConfigHelper.firstText(config, "collectionPath", "sourcePath", "itemsPath");
        if (value == null && StringUtils.isNotBlank(collectionPath)) {
            value = resolvePathExpression(collectionPath, context);
        }
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().toList();
        }
        throw new BusinessException("FOREACH 集合路径不是数组: " + collectionPath);
    }

    private Object resolvePathExpression(String expression, BusinessActionExecutionContext context) {
        String text = StringUtils.trimToEmpty(expression);
        if (text.startsWith("${") && text.endsWith("}") && text.length() > 3) {
            text = text.substring(2, text.length() - 1);
        }
        return BusinessActionStepConfigHelper.resolvePath(text, context);
    }

    private Map<String, Object> rowResult(int itemIndex,
                                          String status,
                                          String errorMessage,
                                          List<BusinessActionStepResultVO> childResults) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemIndex", itemIndex);
        result.put("status", status);
        if (StringUtils.isNotBlank(errorMessage)) {
            result.put("errorMessage", errorMessage);
        }
        result.put("stepResults", childResults);
        return result;
    }
}
