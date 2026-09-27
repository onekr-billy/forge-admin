package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.constant.BusinessPublishCheckLevel;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckItemVO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单优先发布校验器。
 *
 * <p>按表单组件、表单治理、字段查询事件、视图投影组成校验流水线，保持发布门面只负责编排。</p>
 */
final class BusinessObjectFormPublishValidator {

    private static final String FORM_DESIGNER_SCHEMA_OPTION_KEY = "formDesignerSchema";
    private static final String VIEW_SCHEMA_OPTION_KEY = "viewSchema";
    private static final Set<String> FIELD_EVENT_TRIGGERS = Set.of(
            "FORM_LOAD", "CHANGE", "BLUR", "MANUAL", "SCAN_COMPLETE");
    private static final Set<String> FIELD_EVENT_SOURCE_TYPES = Set.of(
            "EXTERNAL_API", "DATASET", "BUSINESS_OBJECT");
    private static final Set<String> FIELD_EVENT_PARAM_SOURCES = Set.of(
            "FORM_FIELD", "CONTEXT_PATH", "ROUTE_QUERY");
    private static final Set<String> FIELD_EVENT_RESULT_MODES = Set.of("ROOT", "FIRST_ROW");
    private static final Set<String> FIELD_EVENT_MISSING_MODES = Set.of("CLEAR", "KEEP");
    private static final Set<String> FIELD_EVENT_ERROR_MODES = Set.of("MESSAGE", "SILENT");
    private static final Set<String> FIELD_EVENT_DANGEROUS_KEYS = Set.of(
            "url", "uri", "header", "headers", "authorization", "authentication",
            "credential", "credentials", "secret", "token", "sql", "script", "handler");
    private static final Set<String> GENERIC_FORM_COMPONENT_ID_SUFFIXES = Set.of(
            "input", "textarea", "number", "inputnumber", "integer", "money", "date", "datetime", "time",
            "switch", "select", "radio", "checkbox", "dictselect", "cascader", "regiontreeselect",
            "orgtreeselect", "orgselect", "departmentselect", "departmenttreeselect", "deptselect",
            "depttreeselect", "eltreeselect", "orgname", "deptname", "userselect", "userpicker",
            "username", "fileupload", "imageupload", "upload", "objectreference", "fcrow", "row",
            "col", "elcard", "card", "eltabs", "tabs", "eltabpane", "tabpane", "elcollapse",
            "collapse", "elcollapseitem", "collapseitem", "fctable", "table", "fctablegrid",
            "tablegrid", "eldivider", "divider", "fctitle", "title", "text", "html", "space",
            "elalert", "alert", "elbutton", "button", "eltag", "tag", "elimage", "image");
    /** 与前端 formDesignerSchema 的虚拟组件协议对齐。 */
    private static final Set<String> FORM_VIRTUAL_COMPONENT_KEYS = Set.of(
            "card", "elCard", "collapse", "elCollapse", "collapseItem", "elCollapseItem",
            "fcRow", "row", "col", "tabs", "elTabs", "tabPane", "elTabPane",
            "fcTable", "table", "fcTableGrid", "tableGrid", "subTable",
            "elDivider", "divider", "fcTitle", "title", "text", "html", "space",
            "elAlert", "alert", "elButton", "button", "elTag", "tag", "elImage", "image");

    void validate(Map<String, Object> designerOptions, Set<String> modelFields, boolean hasBusinessFields,
                  List<BusinessPublishCheckItemVO> items) {
        Map<String, Object> formSchema = mapValue(designerOptions.get(FORM_DESIGNER_SCHEMA_OPTION_KEY));
        Map<String, Object> viewSchema = sanitizeViewSchemaFieldRefs(
                mapValue(designerOptions.get(VIEW_SCHEMA_OPTION_KEY)), modelFields);
        boolean checked = false;
        if (!formSchema.isEmpty()) {
            checked = true;
            checkFormDesignerSchema(formSchema, modelFields, items);
        } else if (hasBusinessFields) {
            add(items, "FORM_SCHEMA_DEFAULT", "FORM", BusinessPublishCheckLevel.PASS,
                    "表单检查通过", "未保存独立表单 Schema，将按字段注册表生成默认表单", null, null,
                    null, null, "form", 130);
        }
        if (!viewSchema.isEmpty()) {
            checked = true;
            checkViewSchema(viewSchema, modelFields, items);
        }
        if (checked && items.stream().noneMatch(item -> Set.of("FORM", "VIEW").contains(item.getCategory())
                && BusinessPublishCheckLevel.BLOCK.equals(item.getLevel()))) {
            add(items, "FORM_FIRST_PASS", "FORM", BusinessPublishCheckLevel.PASS,
                    "表单优先检查通过", "表单组件、视图投影和字段注册表引用一致", null, null,
                    null, null, "form", 180);
        }
    }

    void checkFormDesignerSchema(Map<String, Object> formSchema, Set<String> modelFields,
                                 List<BusinessPublishCheckItemVO> items) {
        List<Map<String, Object>> components = listOfMap(formSchema.get("components"));
        if (components.isEmpty()) {
            add(items, "FORM_COMPONENT_EMPTY", "FORM", BusinessPublishCheckLevel.BLOCK,
                    "表单组件为空", "请先在表单设计中放入至少一个业务字段组件", null, null,
                    "CONFIG_FORM", "设计表单", "form", 131);
            return;
        }
        List<Map<String, Object>> flattened = flattenFormComponents(components);
        int fieldComponentCount = 0;
        Set<String> componentIds = new LinkedHashSet<>();
        for (int index = 0; index < flattened.size(); index++) {
            Map<String, Object> component = flattened.get(index);
            String componentId = text(component.get("id"));
            String componentKey = text(component.get("componentKey"));
            if (StringUtils.isNotBlank(componentId) && !isGenericFormComponentId(componentId, componentKey)
                    && !componentIds.add(componentId)) {
                add(items, "FORM_COMPONENT_DUPLICATE", "FORM", BusinessPublishCheckLevel.BLOCK,
                        "表单组件重复", "组件 ID 重复: " + componentId, null, null,
                        "FIX_FORM", "修复表单", "form", 132);
            }
            if (FORM_VIRTUAL_COMPONENT_KEYS.contains(componentKey)) {
                continue;
            }
            Map<String, Object> binding = mapValue(component.get("fieldBinding"));
            if (!"field".equals(StringUtils.defaultIfBlank(text(binding.get("mode")), "field"))) {
                continue;
            }
            String fieldCode = text(binding.get("fieldCode"));
            if (StringUtils.isBlank(fieldCode)) {
                add(items, "FORM_FIELD_BINDING_EMPTY", "FORM", BusinessPublishCheckLevel.BLOCK,
                        "表单字段未绑定", "组件 " + StringUtils.defaultIfBlank(componentId, String.valueOf(index + 1))
                                + " 未绑定业务字段",
                        null, null, "BIND_FIELD", "绑定字段", "form", 133);
                continue;
            }
            fieldComponentCount++;
            if (!modelFields.contains(fieldCode)) {
                add(items, "FORM_FIELD_MISSING", "FORM", BusinessPublishCheckLevel.BLOCK,
                        "表单引用字段不存在", "表单组件引用了不存在字段: " + fieldCode, fieldCode, null,
                        "FIX_FORM", "修复表单", "form", 134);
            }
        }
        if (fieldComponentCount == 0) {
            add(items, "FORM_FIELD_COMPONENT_EMPTY", "FORM", BusinessPublishCheckLevel.BLOCK,
                    "表单缺少业务字段", "表单中没有绑定业务字段的组件", null, null,
                    "CONFIG_FORM", "设计表单", "form", 135);
        }
        checkFormGovernance(formSchema, modelFields, items);
        checkNestedFormFieldEvents(formSchema, modelFields, items);
    }

    private void checkNestedFormFieldEvents(Map<String, Object> formSchema, Set<String> modelFields,
                                            List<BusinessPublishCheckItemVO> items) {
        String activeFormKey = text(formSchema.get("formKey"));
        for (Map<String, Object> form : listOfMap(formSchema.get("forms"))) {
            String formKey = text(form.get("formKey"));
            if (StringUtils.isNotBlank(activeFormKey) && activeFormKey.equals(formKey)) {
                continue;
            }
            Map<String, Object> nestedSchema = mapValue(form.get("schema"));
            if (nestedSchema.isEmpty()) {
                nestedSchema = form;
            }
            Map<String, Object> governance = mapValue(mapValue(nestedSchema.get("settings")).get("governance"));
            if (!governance.isEmpty()) {
                checkFieldEvents(governance.get("fieldEvents"), modelFields, items);
            }
        }
    }

    private void checkFormGovernance(Map<String, Object> formSchema, Set<String> modelFields,
                                     List<BusinessPublishCheckItemVO> items) {
        Map<String, Object> governance = mapValue(mapValue(formSchema.get("settings")).get("governance"));
        if (governance.isEmpty()) {
            return;
        }
        int index = 0;
        for (Map<String, Object> rule : listOfMap(governance.get("fieldRules"))) {
            index++;
            String field = text(rule.get("field"));
            if (StringUtils.isBlank(field)) {
                add(items, "FORM_RULE_FIELD_EMPTY", "FORM", BusinessPublishCheckLevel.WARN,
                        "表单字段规则未选择字段", "第 " + index + " 条字段覆盖规则没有选择字段", null, null,
                        "CONFIG_FORM_RULE", "配置字段规则", "form", 136 + index);
            } else if (!modelFields.contains(field)) {
                add(items, "FORM_RULE_FIELD_MISSING", "FORM", BusinessPublishCheckLevel.BLOCK,
                        "表单字段规则引用不存在字段", "字段覆盖规则引用了不存在字段: " + field, field, null,
                        "CONFIG_FORM_RULE", "修复字段规则", "form", 146 + index);
            }
        }
        index = 0;
        for (Map<String, Object> event : listOfMap(governance.get("events"))) {
            index++;
            String action = StringUtils.defaultIfBlank(text(event.get("action")), "customScript");
            String handler = text(event.get("handler"));
            if (!"none".equalsIgnoreCase(action) && StringUtils.isBlank(handler)) {
                add(items, "FORM_EVENT_HANDLER_EMPTY", "FORM", BusinessPublishCheckLevel.WARN,
                        "表单事件缺少处理器", "第 " + index + " 个表单事件没有配置脚本名、接口地址或动作编码",
                        null, null, "CONFIG_FORM_EVENT", "配置表单事件", "form", 156 + index);
            } else if ("customScript".equalsIgnoreCase(action) && StringUtils.isNotBlank(handler)
                    && !Set.of("noop", "fillCurrentDate", "fillCurrentTime").contains(handler)) {
                add(items, "FORM_EVENT_SCRIPT_NOT_ALLOWED", "FORM", BusinessPublishCheckLevel.BLOCK,
                        "表单事件脚本未登记", "脚本「" + handler + "」不在运行态白名单内，可用: noop、fillCurrentDate、fillCurrentTime",
                        handler, null, "CONFIG_FORM_EVENT", "修复表单事件", "form", 166 + index);
            }
        }
        checkFieldEvents(governance.get("fieldEvents"), modelFields, items);
    }

    void checkFieldEvents(Object rawEvents, Set<String> modelFields, List<BusinessPublishCheckItemVO> items) {
        if (rawEvents == null) {
            return;
        }
        if (!(rawEvents instanceof List<?>)) {
            addFieldEventBlock(items, 0, "fieldEvents", "字段查询事件必须是数组", null);
            return;
        }
        List<Map<String, Object>> events = listOfMap(rawEvents);
        if (((List<?>) rawEvents).size() != events.size()) {
            addFieldEventBlock(items, 0, "fieldEvents", "字段查询事件中包含非法配置项", null);
        }
        Set<String> eventIds = new LinkedHashSet<>();
        for (int index = 0; index < events.size(); index++) {
            Map<String, Object> event = events.get(index);
            int rowNumber = index + 1;
            String basePath = "fieldEvents[" + index + "]";
            if (containsDangerousFieldEventKey(event)) {
                addFieldEventBlock(items, rowNumber, basePath,
                        "第 " + rowNumber + " 条字段查询事件包含 URL、认证、SQL 或脚本等禁用配置", null);
                continue;
            }
            String eventId = text(event.get("id"));
            if (StringUtils.isBlank(eventId) || !eventId.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
                addFieldEventBlock(items, rowNumber, basePath + ".id",
                        "第 " + rowNumber + " 条字段查询事件编码为空或格式非法", null);
            } else if (!eventIds.add(eventId)) {
                addFieldEventBlock(items, rowNumber, basePath + ".id", "字段查询事件编码重复: " + eventId, null);
            }
            String trigger = text(event.get("trigger"));
            if (!FIELD_EVENT_TRIGGERS.contains(trigger)) {
                addFieldEventBlock(items, rowNumber, basePath + ".trigger",
                        "第 " + rowNumber + " 条字段查询事件触发时机不受支持", null);
            }
            String sourceField = text(event.get("sourceField"));
            if (!"FORM_LOAD".equals(trigger)) {
                checkFieldEventField(items, rowNumber, basePath + ".sourceField", "触发字段", sourceField, modelFields);
            } else if (StringUtils.isNotBlank(sourceField) && !modelFields.contains(sourceField)) {
                addFieldEventBlock(items, rowNumber, basePath + ".sourceField",
                        "字段查询事件引用了不存在的触发字段: " + sourceField, sourceField);
            }
            String sourceType = text(event.get("sourceType"));
            if (!FIELD_EVENT_SOURCE_TYPES.contains(sourceType)) {
                addFieldEventBlock(items, rowNumber, basePath + ".sourceType",
                        "查询源类型仅支持 EXTERNAL_API、DATASET、BUSINESS_OBJECT", null);
            }
            String sourceKey = text(event.get("sourceKey"));
            if (StringUtils.isBlank(sourceKey) || sourceKey.length() > 129
                    || !sourceKey.matches("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,128}")) {
                addFieldEventBlock(items, rowNumber, basePath + ".sourceKey", "查询源编码为空、过长或格式非法", null);
            }
            Integer debounceMs = integerValue(event.get("debounceMs"));
            if (debounceMs == null || debounceMs < 0 || debounceMs > 5000) {
                addFieldEventBlock(items, rowNumber, basePath + ".debounceMs",
                        "字段查询事件防抖时间必须是 0～5000 的整数", null);
            }
            String resultMode = StringUtils.defaultIfBlank(text(event.get("resultMode")), "ROOT");
            if (!FIELD_EVENT_RESULT_MODES.contains(resultMode)) {
                addFieldEventBlock(items, rowNumber, basePath + ".resultMode", "结果取值方式仅支持 ROOT、FIRST_ROW", null);
            }
            String errorMode = StringUtils.defaultIfBlank(text(event.get("errorMode")), "MESSAGE");
            if (!FIELD_EVENT_ERROR_MODES.contains(errorMode)) {
                addFieldEventBlock(items, rowNumber, basePath + ".errorMode", "错误反馈方式不受支持", null);
            }
            checkFieldEventMessage(items, rowNumber, basePath + ".name", event.get("name"), 80);
            checkFieldEventMessage(items, rowNumber, basePath + ".notFoundMessage", event.get("notFoundMessage"), 200);
            checkFieldEventMessage(items, rowNumber, basePath + ".errorMessage", event.get("errorMessage"), 200);
            checkFieldEventParams(event.get("paramMappings"), modelFields, items, rowNumber, basePath);
            checkFieldEventResults(event.get("resultMappings"), modelFields, items, rowNumber, basePath);
        }
    }

    private void checkFieldEventParams(Object rawMappings, Set<String> modelFields,
                                       List<BusinessPublishCheckItemVO> items, int rowNumber, String basePath) {
        if (!(rawMappings instanceof List<?>)) {
            addFieldEventBlock(items, rowNumber, basePath + ".paramMappings", "查询参数映射必须是数组", null);
            return;
        }
        List<Map<String, Object>> mappings = listOfMap(rawMappings);
        if (((List<?>) rawMappings).size() != mappings.size()) {
            addFieldEventBlock(items, rowNumber, basePath + ".paramMappings", "查询参数映射包含非法配置项", null);
        }
        Set<String> params = new LinkedHashSet<>();
        for (int index = 0; index < mappings.size(); index++) {
            Map<String, Object> mapping = mappings.get(index);
            String path = basePath + ".paramMappings[" + index + "]";
            String param = text(mapping.get("param"));
            if (StringUtils.isBlank(param) || !param.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || FIELD_EVENT_DANGEROUS_KEYS.contains(param.toLowerCase(java.util.Locale.ROOT))) {
                addFieldEventBlock(items, rowNumber, path + ".param", "查询参数名为空或格式非法", null);
            } else if (!params.add(param)) {
                addFieldEventBlock(items, rowNumber, path + ".param", "查询参数名重复: " + param, null);
            }
            String source = text(mapping.get("source"));
            if (!FIELD_EVENT_PARAM_SOURCES.contains(source)) {
                addFieldEventBlock(items, rowNumber, path + ".source", "查询参数来源不受支持", null);
                continue;
            }
            if ("FORM_FIELD".equals(source)) {
                checkFieldEventField(items, rowNumber, path + ".field", "参数字段",
                        text(mapping.get("field")), modelFields);
            } else if (!isSafeFieldEventPath(text(mapping.get("path")))) {
                addFieldEventBlock(items, rowNumber, path + ".path", "运行上下文或路由参数路径为空或不安全", null);
            }
        }
    }

    private void checkFieldEventResults(Object rawMappings, Set<String> modelFields,
                                        List<BusinessPublishCheckItemVO> items, int rowNumber, String basePath) {
        if (!(rawMappings instanceof List<?>)) {
            addFieldEventBlock(items, rowNumber, basePath + ".resultMappings", "结果回填映射必须是数组", null);
            return;
        }
        List<Map<String, Object>> mappings = listOfMap(rawMappings);
        if (mappings.isEmpty()) {
            addFieldEventBlock(items, rowNumber, basePath + ".resultMappings", "至少配置一个结果回填字段", null);
            return;
        }
        Set<String> targets = new LinkedHashSet<>();
        for (int index = 0; index < mappings.size(); index++) {
            Map<String, Object> mapping = mappings.get(index);
            String path = basePath + ".resultMappings[" + index + "]";
            String from = text(mapping.get("from"));
            if (StringUtils.isNotBlank(from) && !isSafeFieldEventPath(from)) {
                addFieldEventBlock(items, rowNumber, path + ".from", "返回字段路径格式不安全", null);
            }
            String target = text(mapping.get("to"));
            checkFieldEventField(items, rowNumber, path + ".to", "回填目标字段", target, modelFields);
            if (StringUtils.isNotBlank(target) && !targets.add(target)) {
                addFieldEventBlock(items, rowNumber, path + ".to", "回填目标字段重复: " + target, target);
            }
            String missingMode = StringUtils.defaultIfBlank(text(mapping.get("whenMissing")), "CLEAR");
            if (!FIELD_EVENT_MISSING_MODES.contains(missingMode)) {
                addFieldEventBlock(items, rowNumber, path + ".whenMissing", "结果缺失处理方式不受支持", target);
            }
        }
    }

    private void checkFieldEventField(List<BusinessPublishCheckItemVO> items, int rowNumber, String path,
                                      String label, String field, Set<String> modelFields) {
        if (StringUtils.isBlank(field)) {
            addFieldEventBlock(items, rowNumber, path, label + "不能为空", null);
        } else if (!modelFields.contains(field)) {
            addFieldEventBlock(items, rowNumber, path, label + "不存在: " + field, field);
        }
    }

    private void checkFieldEventMessage(List<BusinessPublishCheckItemVO> items, int rowNumber, String path,
                                        Object value, int maxLength) {
        String message = text(value);
        if (message != null && message.length() > maxLength) {
            addFieldEventBlock(items, rowNumber, path, "字段查询事件文案长度不能超过 " + maxLength, null);
        }
    }

    private boolean containsDangerousFieldEventKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                if (FIELD_EVENT_DANGEROUS_KEYS.contains(key) || containsDangerousFieldEventKey(entry.getValue())) {
                    return true;
                }
            }
        } else if (value instanceof List<?> list) {
            return list.stream().anyMatch(this::containsDangerousFieldEventKey);
        }
        return false;
    }

    private boolean isSafeFieldEventPath(String path) {
        if (StringUtils.isBlank(path)
                || !path.matches("[A-Za-z_$][A-Za-z0-9_$-]*(\\.[A-Za-z_$][A-Za-z0-9_$-]*)*")) {
            return false;
        }
        return List.of(path.split("\\.")).stream()
                .noneMatch(segment -> Set.of("__proto__", "prototype", "constructor").contains(segment)
                        || FIELD_EVENT_DANGEROUS_KEYS.contains(segment.toLowerCase(java.util.Locale.ROOT)));
    }

    private Integer integerValue(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double doubleValue = number.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue != Math.rint(doubleValue)) {
            return null;
        }
        return number.intValue();
    }

    private void addFieldEventBlock(List<BusinessPublishCheckItemVO> items, int rowNumber, String path,
                                    String message, String fieldCode) {
        add(items, "FORM_FIELD_EVENT_INVALID", "FORM", BusinessPublishCheckLevel.BLOCK,
                "字段查询事件配置无效", message, fieldCode, path,
                "CONFIG_FORM_EVENT", "修复字段查询", "form", 176 + rowNumber);
    }

    private boolean isGenericFormComponentId(String componentId, String componentKey) {
        String id = StringUtils.trimToEmpty(componentId);
        if (!StringUtils.startsWithIgnoreCase(id, "cmp_")) {
            return false;
        }
        String suffix = id.substring(4).trim().toLowerCase();
        if (StringUtils.isBlank(suffix)) {
            return true;
        }
        String key = StringUtils.trimToEmpty(componentKey).toLowerCase();
        return suffix.equals(key) || GENERIC_FORM_COMPONENT_ID_SUFFIXES.contains(suffix);
    }

    private void checkViewSchema(Map<String, Object> viewSchema, Set<String> modelFields,
                                 List<BusinessPublishCheckItemVO> items) {
        checkViewFieldRefs(listOfMap(mapValue(viewSchema.get("search")).get("fields")),
                modelFields, "查询条件", "search", items, 141);
        checkViewFieldRefs(listOfMap(mapValue(viewSchema.get("list")).get("columns")),
                modelFields, "数据列表", "list", items, 142);
        for (Map<String, Object> section : listOfMap(mapValue(viewSchema.get("detail")).get("sections"))) {
            String sectionKey = StringUtils.defaultIfBlank(text(section.get("sectionKey")), text(section.get("key")));
            checkViewFieldRefs(listOfMap(section.get("fields")), modelFields,
                    "详情视图", StringUtils.defaultIfBlank(sectionKey, "detail"), items, 143);
        }
    }

    private void checkViewFieldRefs(List<Map<String, Object>> refs, Set<String> modelFields, String viewName,
                                    String fixTarget, List<BusinessPublishCheckItemVO> items, int sortOrder) {
        for (Map<String, Object> ref : refs) {
            String fieldCode = StringUtils.defaultIfBlank(text(ref.get("fieldCode")), text(ref.get("field")));
            if (StringUtils.isNotBlank(fieldCode) && !modelFields.contains(fieldCode)) {
                add(items, "VIEW_FIELD_MISSING", "VIEW", BusinessPublishCheckLevel.BLOCK,
                        viewName + "引用字段不存在", viewName + "引用了不存在字段: " + fieldCode,
                        fieldCode, null, "FIX_VIEW", "修复视图", fixTarget, sortOrder);
            }
        }
    }

    private Map<String, Object> sanitizeViewSchemaFieldRefs(Map<String, Object> viewSchema, Set<String> modelFields) {
        if (viewSchema.isEmpty() || modelFields == null || modelFields.isEmpty()) {
            return viewSchema;
        }
        Map<String, Object> next = new LinkedHashMap<>(viewSchema);
        Map<String, Object> search = new LinkedHashMap<>(mapValue(next.get("search")));
        search.put("fields", filterViewFieldRefs(listOfMap(search.get("fields")), modelFields));
        next.put("search", search);
        Map<String, Object> list = new LinkedHashMap<>(mapValue(next.get("list")));
        list.put("columns", filterViewFieldRefs(listOfMap(list.get("columns")), modelFields));
        next.put("list", list);
        Map<String, Object> detail = new LinkedHashMap<>(mapValue(next.get("detail")));
        detail.put("sections", listOfMap(detail.get("sections")).stream()
                .map(section -> {
                    Map<String, Object> cleanSection = new LinkedHashMap<>(section);
                    cleanSection.put("fields", filterViewFieldRefs(listOfMap(section.get("fields")), modelFields));
                    return cleanSection;
                })
                .toList());
        next.put("detail", detail);
        return next;
    }

    private List<Map<String, Object>> filterViewFieldRefs(List<Map<String, Object>> refs, Set<String> modelFields) {
        return refs.stream().filter(item -> modelFields.contains(viewFieldCode(item))).toList();
    }

    private String viewFieldCode(Map<String, Object> item) {
        return StringUtils.defaultIfBlank(text(item.get("fieldCode")), text(item.get("field")));
    }

    private List<Map<String, Object>> flattenFormComponents(List<Map<String, Object>> components) {
        List<Map<String, Object>> result = new ArrayList<>();
        collectFormComponents(components, result);
        return result;
    }

    private void collectFormComponents(List<Map<String, Object>> components, List<Map<String, Object>> result) {
        if (components == null) {
            return;
        }
        for (Map<String, Object> component : components) {
            if (component == null) {
                continue;
            }
            result.add(component);
            collectFormComponents(listOfMap(component.get("children")), result);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(this::mapValue).toList();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private void add(List<BusinessPublishCheckItemVO> items, String code, String category, String level,
                     String title, String message, String fieldCode, String zoneKey, String fixAction,
                     String fixActionLabel, String fixTarget, Integer sortOrder) {
        BusinessPublishCheckCollector.add(items, code, category, level, title, message, fieldCode, zoneKey,
                fixAction, fixActionLabel, fixTarget, sortOrder);
    }
}
