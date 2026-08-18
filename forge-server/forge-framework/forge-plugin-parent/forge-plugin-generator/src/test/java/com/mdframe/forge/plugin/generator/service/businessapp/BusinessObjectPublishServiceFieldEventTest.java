package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessPublishCheckLevel;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务对象发布检查 - 字段查询事件")
@Tag("dev")
class BusinessObjectPublishServiceFieldEventTest {

    private BusinessObjectPublishService service;
    private Method checkFieldEvents;

    @BeforeEach
    void setUp() throws Exception {
        service = new BusinessObjectPublishService(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, new ObjectMapper());
        checkFieldEvents = BusinessObjectPublishService.class.getDeclaredMethod(
                "checkFieldEvents", Object.class, Set.class, List.class);
        checkFieldEvents.setAccessible(true);
    }

    @Test
    @DisplayName("合法受管查询规则通过发布检查")
    void acceptsValidManagedQueryRule() throws Exception {
        List<BusinessPublishCheckItemVO> items = validate(List.of(validEvent()));

        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("危险配置和未知协议值失败关闭")
    void rejectsDangerousAndUnknownProtocolValues() throws Exception {
        Map<String, Object> dangerous = validEvent();
        dangerous.put("headers", Map.of("Authorization", "secret"));
        Map<String, Object> unknown = validEvent();
        unknown.put("id", "unknown_source");
        unknown.put("sourceType", "REMOTE_URL");

        List<BusinessPublishCheckItemVO> items = validate(List.of(dangerous, unknown));

        assertTrue(items.stream().allMatch(item -> BusinessPublishCheckLevel.BLOCK.equals(item.getLevel())));
        assertTrue(items.stream().anyMatch(item -> item.getMessage().contains("禁用配置")));
        assertTrue(items.stream().anyMatch(item -> item.getZoneKey().endsWith("sourceType")));
    }

    @Test
    @DisplayName("重复编码、参数、目标字段及不存在字段均阻止发布")
    void rejectsDuplicateAndMissingFieldMappings() throws Exception {
        Map<String, Object> first = validEvent();
        first.put("sourceField", "missingSource");
        first.put("paramMappings", List.of(
                Map.of("param", "mobile", "source", "FORM_FIELD", "field", "mobile"),
                Map.of("param", "mobile", "source", "CONTEXT_PATH", "path", "currentUser.userId")));
        first.put("resultMappings", List.of(
                Map.of("from", "name", "to", "contactName", "whenMissing", "CLEAR"),
                Map.of("from", "level", "to", "contactName", "whenMissing", "KEEP"),
                Map.of("from", "id", "to", "missingTarget", "whenMissing", "CLEAR")));
        Map<String, Object> duplicateId = validEvent();

        List<BusinessPublishCheckItemVO> items = validate(List.of(first, duplicateId));

        assertTrue(items.stream().allMatch(item -> BusinessPublishCheckLevel.BLOCK.equals(item.getLevel())));
        assertTrue(items.stream().anyMatch(item -> item.getMessage().contains("编码重复")));
        assertTrue(items.stream().anyMatch(item -> item.getMessage().contains("参数名重复")));
        assertTrue(items.stream().anyMatch(item -> item.getMessage().contains("目标字段重复")));
        assertTrue(items.stream().anyMatch(item -> item.getMessage().contains("不存在")));
    }

    @Test
    @DisplayName("非数组字段事件协议被拒绝")
    void rejectsNonArrayProtocol() throws Exception {
        List<BusinessPublishCheckItemVO> items = validate(Map.of("id", "query_contact"));

        assertEquals(1, items.size());
        assertEquals("FORM_FIELD_EVENT_INVALID", items.get(0).getItemCode());
        assertEquals(BusinessPublishCheckLevel.BLOCK, items.get(0).getLevel());
    }

    @SuppressWarnings("unchecked")
    private List<BusinessPublishCheckItemVO> validate(Object fieldEvents) throws Exception {
        List<BusinessPublishCheckItemVO> items = new ArrayList<>();
        checkFieldEvents.invoke(service, fieldEvents, Set.of("mobile", "contactName"), items);
        return items;
    }

    private Map<String, Object> validEvent() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "query_contact");
        event.put("name", "查询联系人");
        event.put("enabled", true);
        event.put("trigger", "BLUR");
        event.put("sourceField", "mobile");
        event.put("sourceType", "EXTERNAL_API");
        event.put("sourceKey", "crm/contact_lookup");
        event.put("debounceMs", 0);
        event.put("paramMappings", List.of(
                Map.of("param", "mobile", "source", "FORM_FIELD", "field", "mobile")));
        event.put("resultMode", "ROOT");
        event.put("resultMappings", List.of(
                Map.of("from", "contact.name", "to", "contactName", "whenMissing", "CLEAR")));
        event.put("notFoundMessage", "未匹配到数据");
        event.put("errorMessage", "查询失败，请重试");
        event.put("errorMode", "MESSAGE");
        return event;
    }
}
