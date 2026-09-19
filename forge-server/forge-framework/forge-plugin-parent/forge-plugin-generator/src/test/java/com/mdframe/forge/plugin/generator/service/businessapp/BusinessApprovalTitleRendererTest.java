package com.mdframe.forge.plugin.generator.service.businessapp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessApprovalTitleRendererTest {

    @Test
    void replacesMainRecordFieldsAndBuiltinNames() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("main", Map.of("fieldInput", "采购单A", "id", 9));
        record.put("children", Map.of("detail_ujpc", List.of()));

        Map<String, String> extras = Map.of(
                "starterName", "张三",
                "objectName", "测试页面");

        assertEquals("测试页面-采购单A",
                BusinessApprovalTitleRenderer.render("测试页面-${fieldInput}", record, extras, "审批申请"));
        assertEquals("张三发起的测试页面审批单",
                BusinessApprovalTitleRenderer.render("${starterName}发起的${objectName}审批单", record, extras, "审批申请"));
    }
}
