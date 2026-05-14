package com.mdframe.forge.plugin.data.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DatasetParamSchemaParser {

    private static final Set<String> SUPPORTED_OPERATORS = Set.of("=", "!=", ">", ">=", "<", "<=", "LIKE");

    private final ObjectMapper objectMapper;

    public DatasetParamSchemaParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DatasetParamSchemaItem> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<DatasetParamSchemaItem> items = objectMapper.readValue(json, new TypeReference<List<DatasetParamSchemaItem>>() {
            });
            return items != null ? items : Collections.emptyList();
        } catch (Exception e) {
            throw new IllegalArgumentException("查询参数定义JSON格式不正确");
        }
    }

    public void validate(String json, boolean tableMode) {
        List<DatasetParamSchemaItem> items = parse(json);
        Set<String> paramNames = new HashSet<>();
        for (DatasetParamSchemaItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("查询参数定义中存在空对象");
            }
            if (isBlank(item.getParamName())) {
                throw new IllegalArgumentException("查询参数定义必须填写paramName");
            }
            if (!paramNames.add(item.getParamName())) {
                throw new IllegalArgumentException("查询参数定义存在重复paramName: " + item.getParamName());
            }
            String operator = normalizeOperator(item.getOperator());
            if (!SUPPORTED_OPERATORS.contains(operator)) {
                throw new IllegalArgumentException("查询参数定义operator不支持: " + item.getOperator());
            }
            if (tableMode && isBlank(item.getFieldName())) {
                throw new IllegalArgumentException("TABLE数据集的查询参数定义必须填写fieldName");
            }
        }
    }

    public String normalizeOperator(String operator) {
        if (isBlank(operator)) {
            return "=";
        }
        return operator.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
