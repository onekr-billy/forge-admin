package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.entity.DataDatasetField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TableQueryBuilder {

    public String buildQuerySql(String tableName, List<DataDatasetField> fields, 
            Map<String, Object> params, DbDialect dialect) {
        StringBuilder sql = new StringBuilder();
        
        List<DataDatasetField> displayFields = fields.stream()
                .filter(f -> f.getDisplayEnabled() == 1)
                .collect(Collectors.toList());
        
        if (displayFields.isEmpty()) {
            log.warn("No display fields available for table query");
            return null;
        }
        
        sql.append("SELECT ");
        List<String> fieldNames = displayFields.stream()
                .map(f -> dialect.quoteIdentifier(f.getFieldName()))
                .collect(Collectors.toList());
        sql.append(String.join(", ", fieldNames));
        
        sql.append(" FROM ").append(dialect.quoteIdentifier(tableName));
        sql.append(" WHERE 1 = 1");
        
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                Object value = entry.getValue();
                if (value != null) {
                    DataDatasetField field = displayFields.stream()
                            .filter(f -> f.getFieldName().equals(paramName))
                            .findFirst()
                            .orElse(null);
                    if (field != null) {
                        sql.append(" AND ").append(dialect.quoteIdentifier(field.getFieldName()));
                        sql.append(" = :").append(paramName);
                    }
                }
            }
        }
        
        return sql.toString();
    }
    
    public String buildQuerySqlWithFields(String tableName, List<String> selectedFields, 
            Map<String, Object> params, DbDialect dialect) {
        StringBuilder sql = new StringBuilder();
        
        if (selectedFields == null || selectedFields.isEmpty()) {
            sql.append("SELECT *");
        } else {
            List<String> quotedFields = selectedFields.stream()
                    .map(dialect::quoteIdentifier)
                    .collect(Collectors.toList());
            sql.append("SELECT ").append(String.join(", ", quotedFields));
        }
        
        sql.append(" FROM ").append(dialect.quoteIdentifier(tableName));
        sql.append(" WHERE 1 = 1");
        
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() != null) {
                    sql.append(" AND ").append(dialect.quoteIdentifier(entry.getKey()));
                    sql.append(" = :").append(entry.getKey());
                }
            }
        }
        
        return sql.toString();
    }
}