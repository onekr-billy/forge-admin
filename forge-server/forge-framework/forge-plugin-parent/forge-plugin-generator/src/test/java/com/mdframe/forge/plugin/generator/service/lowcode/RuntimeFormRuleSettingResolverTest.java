package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeFormRuleSettingResolverTest {

    @Test
    void requiredSwitchAddsRuleAndMapsComponentAndGridSpan() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("field", "reviewer");
        rule.put("$required", "请选择审批人");
        rule.put("validate", List.of());
        rule.put("props", Map.of("dictType", "sys_user"));
        rule.put("_forge", Map.of("componentKey", "userSelect", "layout", Map.of("align", "center")));
        rule.put("col", Map.of("span", 12));

        Map<String, Object> setting = RuntimeFormRuleSettingResolver.resolveFormRuleSetting(
                pageWithRule(rule), "reviewer", () -> 2);

        assertEquals("userSelect", setting.get("componentType"));
        assertEquals("sys_user", setting.get("dictType"));
        assertEquals("center", setting.get("align"));
        assertEquals(1, setting.get("span"));
        assertEquals(true, setting.get("required"));
        assertEquals("请选择审批人", setting.get("requiredMessage"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rules = (List<Map<String, Object>>) setting.get("rules");
        assertEquals(List.of("blur", "change"), rules.get(0).get("trigger"));
        assertTrue(((List<?>) rule.get("validate")).isEmpty());
    }

    @Test
    void explicitRequiredOffAndDragTagFallbackArePreserved() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("field", "attachment");
        rule.put("$required", false);
        rule.put("_fc_drag_tag", "forgeFileUpload");
        rule.put("validate", List.of(Map.of("message", "format", "required", false)));

        Map<String, Object> setting = RuntimeFormRuleSettingResolver.resolveFormRuleSetting(
                pageWithRule(rule), "attachment", () -> 2);

        assertEquals("fileUpload", setting.get("componentType"));
        assertEquals(false, setting.get("required"));
        assertFalse(((List<?>) setting.get("rules")).isEmpty());
    }

    @Test
    void validationMessageAndMissingFieldFallbackArePreserved() {
        Map<String, Object> rule = Map.of(
                "field", "code",
                "validate", List.of(Map.of("required", true, "message", "请输入编码")));
        LowcodePageSchema page = pageWithRule(rule);

        Map<String, Object> setting = RuntimeFormRuleSettingResolver.resolveFormRuleSetting(
                page, "code", () -> 1);

        assertEquals(true, setting.get("required"));
        assertEquals("请输入编码", setting.get("requiredMessage"));
        assertTrue(RuntimeFormRuleSettingResolver.resolveFormRuleSetting(page, "other", () -> 1).isEmpty());
    }

    private static LowcodePageSchema pageWithRule(Map<String, Object> rule) {
        LowcodePageZone edit = new LowcodePageZone();
        edit.setZoneKey("edit");
        edit.setProps(Map.of("formCreateRule", List.of(rule)));
        LowcodePageSchema page = new LowcodePageSchema();
        page.setZones(List.of(edit));
        return page;
    }
}
