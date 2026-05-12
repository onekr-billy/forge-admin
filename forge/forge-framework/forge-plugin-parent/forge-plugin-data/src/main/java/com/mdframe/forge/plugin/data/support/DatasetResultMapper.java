package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.entity.DataDatasetField;
import com.mdframe.forge.plugin.data.vo.DataDatasetFieldVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DatasetResultMapper {

    public Map<String, Object> resultSetToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            String label = metaData.getColumnLabel(i);
            Object value = rs.getObject(i);
            row.put(label, value);
        }
        return row;
    }

    public List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(resultSetToMap(rs));
        }
        return rows;
    }

    public Map<String, Object> toEchartsDataset(List<Map<String, Object>> rows, 
            List<DataDatasetField> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        List<DataDatasetField> displayFields = fields.stream()
                .filter(f -> f.getDisplayEnabled() == 1)
                .filter(f -> !"HIDDEN".equals(f.getSensitiveLevel()))
                .collect(Collectors.toList());
        
        List<String> dimensions = displayFields.stream()
                .map(DataDatasetField::getFieldName)
                .collect(Collectors.toList());
        
        List<Map<String, Object>> source = applyMasking(rows, displayFields);
        
        result.put("dimensions", dimensions);
        result.put("source", source);
        result.put("total", source.size());
        result.put("fields", convertToFieldVOList(displayFields));
        
        return result;
    }

    private List<Map<String, Object>> applyMasking(List<Map<String, Object>> rows, 
            List<DataDatasetField> fields) {
        Map<String, DataDatasetField> fieldMap = fields.stream()
                .collect(Collectors.toMap(DataDatasetField::getFieldName, f -> f));
        
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();
                DataDatasetField field = fieldMap.get(fieldName);
                if (field != null && "MASK".equals(field.getSensitiveLevel()) && value != null) {
                    String maskRule = field.getMaskRule();
                    entry.setValue(applyMaskRule(value.toString(), maskRule));
                }
            }
        }
        return rows;
    }

    private String applyMaskRule(String value, String maskRule) {
        if (value == null) return null;
        if (maskRule == null || maskRule.isEmpty()) {
            int len = value.length();
            if (len <= 4) return value;
            return value.substring(0, 2) + "****" + value.substring(len - 2);
        }
        return value.replaceAll(maskRule, "****");
    }

    private List<DataDatasetFieldVO> convertToFieldVOList(List<DataDatasetField> fields) {
        return fields.stream().map(f -> {
            DataDatasetFieldVO vo = new DataDatasetFieldVO();
            vo.setId(f.getId());
            vo.setFieldName(f.getFieldName());
            vo.setFieldLabel(f.getFieldLabel());
            vo.setDataType(f.getDataType());
            vo.setFieldRole(f.getFieldRole());
            return vo;
        }).collect(Collectors.toList());
    }
}