package com.mdframe.forge.plugin.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("人员和组织选择值校验")
class SelectionIdentifierValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("人员和组织 bigint 主字段只接受数字 ID")
    void bigintSelectionFieldsOnlyAcceptIdentifiers() {
        AiCrudConfig config = config();

        assertDoesNotThrow(() -> SelectionIdentifierValidator.validate(config, Map.of(
                "applicantId", "1900000000000000001",
                "departmentId", 1900000000000000002L,
                "applicantIdName", "张三",
                "departmentIdName", "研发部"
        ), objectMapper));

        BusinessException userError = assertThrows(BusinessException.class,
                () -> SelectionIdentifierValidator.validate(config, Map.of("applicantId", "张三"), objectMapper));
        assertEquals("申请人必须保存人员ID，不能保存姓名", userError.getMessage());

        BusinessException orgError = assertThrows(BusinessException.class,
                () -> SelectionIdentifierValidator.validate(config, Map.of("departmentId", "研发部"), objectMapper));
        assertEquals("所属部门必须保存组织ID，不能保存名称", orgError.getMessage());
    }

    @Test
    @DisplayName("人员多选允许逗号分隔的 ID")
    void multipleUserSelectionAcceptsCommaSeparatedIdentifiers() {
        AiCrudConfig config = config();
        config.setModelSchema("""
                {"fields":[
                  {"field":"applicantId","columnName":"applicant_id","label":"申请人","dataType":"varchar","componentType":"userSelect","basicProps":{"multiple":true}},
                  {"field":"applicantIdName","columnName":"applicant_id_name","label":"申请人姓名","dataType":"varchar","componentType":"input"}
                ]}
                """);
        config.setEditSchema("""
                [{"field":"applicantId","label":"申请人","type":"userSelect","multiple":true}]
                """);

        assertDoesNotThrow(() -> SelectionIdentifierValidator.validate(config, Map.of(
                "applicantId", "1900000000000000001,1900000000000000002",
                "applicantIdName", "张三,李四"
        ), objectMapper));

        BusinessException userError = assertThrows(BusinessException.class,
                () -> SelectionIdentifierValidator.validate(config, Map.of("applicantId", "张三,李四"), objectMapper));
        assertEquals("申请人必须保存人员ID，不能保存姓名", userError.getMessage());
    }

    private AiCrudConfig config() {
        AiCrudConfig config = new AiCrudConfig();
        config.setConfigKey("hr_apply");
        config.setEditSchema("""
                [
                  {"field":"applicantId","label":"申请人","type":"userSelect"},
                  {"field":"departmentId","label":"所属部门","type":"orgTreeSelect"}
                ]
                """);
        config.setModelSchema("""
                {"fields":[
                  {"field":"applicantId","columnName":"applicant_id","label":"申请人","dataType":"bigint","componentType":"userSelect"},
                  {"field":"departmentId","columnName":"department_id","label":"所属部门","dataType":"bigint","componentType":"orgTreeSelect"},
                  {"field":"applicantIdName","columnName":"applicant_id_name","label":"申请人姓名","dataType":"varchar","componentType":"input"},
                  {"field":"departmentIdName","columnName":"department_id_name","label":"部门名称","dataType":"varchar","componentType":"input"}
                ]}
                """);
        return config;
    }
}
