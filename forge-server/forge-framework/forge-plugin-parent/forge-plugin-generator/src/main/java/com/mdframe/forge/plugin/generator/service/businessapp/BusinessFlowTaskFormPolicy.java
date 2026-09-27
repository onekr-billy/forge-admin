package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormSaveDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNullableBooleanValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.camelToSnake;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.contains;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.read;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowRecordValues.snakeToCamel;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.normalizeTaskFormFieldType;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowTaskFormControlTypes.resolveTaskFormControlType;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowNodeFormNormalizer.normalizeNodeEditMode;

/**
 * 审批任务表单字段策略。
 * <p>
 * 集中执行 BPMN 节点字段权限、只读投影、必填校验和可见数据裁剪，
 * 避免查询、保存和代码表单三条路径各自解释同一套权限协议。
 */
final class BusinessFlowTaskFormPolicy {

    void makeReadonly(BusinessTaskFormContextVO context) {
        if (context == null) {
            return;
        }
        if (context.getFields() != null) {
            for (Map<String, Object> field : context.getFields()) {
                if (field == null) {
                    continue;
                }
                field.put("writable", false);
                field.put("readonly", true);
                field.put("disabled", true);
            }
        }
        if (context.getFieldPermissions() != null) {
            for (Map<String, Object> permission : context.getFieldPermissions()) {
                if (permission == null) {
                    continue;
                }
                permission.put("writable", false);
                permission.put("readonly", true);
                permission.put("disabled", true);
            }
        }
        if (context.getChildrenConfig() != null) {
            for (Map<String, Object> child : context.getChildrenConfig()) {
                if (child == null) {
                    continue;
                }
                child.put("allowCreate", false);
                child.put("allowUpdate", false);
                child.put("allowDelete", false);
                child.put("readable", true);
                for (Map<String, Object> field : readMapList(readNestedArray(child.get("fields")))) {
                    if (field == null) {
                        continue;
                    }
                    field.put("writable", false);
                    field.put("readonly", true);
                    field.put("disabled", true);
                }
            }
        }
    }

    void applyApprovalPolicy(BusinessTaskFormContextVO vo, JSONObject source) {
        if (vo == null || source == null) {
            return;
        }
        vo.setAllowApprove(readNullableBooleanValue(source.get("allowApprove")));
        vo.setAllowDelegate(readNullableBooleanValue(source.get("allowDelegate")));
        vo.setAllowReject(readNullableBooleanValue(source.get("allowReject")));
        vo.setAllowRejectToStart(readNullableBooleanValue(source.get("allowRejectToStart")));
        vo.setAllowReturn(readNullableBooleanValue(source.get("allowReturn")));
        vo.setAllowMultiReturn(readNullableBooleanValue(source.get("allowMultiReturn")));
        vo.setAllowDirectSend(readNullableBooleanValue(source.get("allowDirectSend")));
        vo.setReturnSourceActivityId(StringUtils.trimToNull(textValue(source.get("returnSourceActivityId"))));
        vo.setReturnSourceActivityName(StringUtils.trimToNull(textValue(source.get("returnSourceActivityName"))));
        Object targets = source.get("returnTargets");
        if (targets instanceof List<?> list) {
            List<Map<String, Object>> returnTargets = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> target = new LinkedHashMap<>();
                    map.forEach((key, value) -> target.put(String.valueOf(key), value));
                    returnTargets.add(target);
                }
            }
            vo.setReturnTargets(returnTargets);
        }
        vo.setAllowTerminate(readNullableBooleanValue(source.get("allowTerminate")));
        vo.setRequireSignature(readNullableBooleanValue(source.get("requireSignature")));
        vo.setRequireComment(readNullableBooleanValue(source.get("requireComment")));
    }

    BusinessTaskFormContextVO applyFieldPermissions(BusinessTaskFormContextVO context,
                                                     List<Map<String, Object>> permissions) {
        if (context == null) {
            return null;
        }
        Map<String, Map<String, Object>> permissionMap = new LinkedHashMap<>();
        List<Map<String, Object>> safePermissions = permissions == null ? List.of() : permissions;
        for (Map<String, Object> permission : safePermissions) {
            String field = StringUtils.trimToNull(textValue(permission.get("field")));
            if (field != null) {
                putPermissionAliases(permissionMap, field, permission);
            }
        }
        List<Map<String, Object>> filteredFields = new ArrayList<>();
        List<Map<String, Object>> sourceFields = context.getFields() == null ? List.of() : context.getFields();
        for (Map<String, Object> source : sourceFields) {
            if (source == null) {
                continue;
            }
            String field = StringUtils.trimToNull(textValue(source.get("field")));
            if (field == null
                    || readBooleanValue(source.get("internal"), false)
                    || readBooleanValue(source.get("systemField"), false)) {
                continue;
            }
            Map<String, Object> permission = permissionMap.get(field);
            boolean readable = permission == null || readBooleanValue(permission.get("readable"), true);
            if (!readable) {
                continue;
            }
            boolean writable = permission != null && readBooleanValue(permission.get("writable"), false);
            boolean required = writable && permission != null && readBooleanValue(permission.get("required"), false);
            Map<String, Object> item = new LinkedHashMap<>(source);
            item.put("readable", true);
            item.put("writable", writable);
            item.put("required", required);
            item.put("readonly", !writable);
            item.put("disabled", !writable);
            Map<String, Object> props = new LinkedHashMap<>(readNestedObject(item.get("props")));
            props.put("disabled", !writable);
            item.put("props", props);
            filteredFields.add(item);
        }
        context.setFields(filteredFields);
        context.setRecordData(filterVisibleRecordData(context.getRecordData(), filteredFields));
        return context;
    }

    BusinessTaskFormSaveDTO filterSaveData(BusinessTaskFormSaveDTO dto,
                                            List<Map<String, Object>> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new BusinessException("当前节点没有可编辑业务字段");
        }
        Set<String> writableFields = collectPermissionFields(permissions, "writable", true);
        if (writableFields.isEmpty()) {
            throw new BusinessException("当前节点没有可编辑业务字段");
        }
        Map<String, Object> input = dto.getData() == null ? Map.of() : dto.getData();
        Map<String, Object> filteredData = new LinkedHashMap<>();
        for (String field : writableFields) {
            if (input.containsKey(field)) {
                filteredData.put(field, input.get(field));
            }
        }
        BusinessTaskFormSaveDTO filtered = new BusinessTaskFormSaveDTO();
        filtered.setTaskId(dto.getTaskId());
        filtered.setBusinessKey(dto.getBusinessKey());
        filtered.setProcessInstanceId(dto.getProcessInstanceId());
        filtered.setProcessDefKey(dto.getProcessDefKey());
        filtered.setTaskDefKey(dto.getTaskDefKey());
        filtered.setObjectCode(dto.getObjectCode());
        filtered.setObjectId(dto.getObjectId());
        filtered.setConfigKey(dto.getConfigKey());
        filtered.setSuiteCode(dto.getSuiteCode());
        filtered.setRecordId(dto.getRecordId());
        filtered.setFormKey(dto.getFormKey());
        filtered.setData(filteredData);
        return filtered;
    }

    List<Map<String, Object>> buildFields(List<Map<String, Object>> fieldCatalog,
                                          List<Map<String, Object>> permissions) {
        Map<String, Map<String, Object>> permissionMap = new LinkedHashMap<>();
        for (Map<String, Object> permission : permissions) {
            if ("child".equalsIgnoreCase(textValue(permission.get("scope")))) {
                continue;
            }
            String field = StringUtils.trimToNull(textValue(permission.get("field")));
            if (field != null) {
                putPermissionAliases(permissionMap, field, permission);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> field : fieldCatalog) {
            if (isChildField(field)) {
                continue;
            }
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("field"))),
                    StringUtils.trimToNull(textValue(field.get("fieldCode"))));
            if (fieldCode == null) {
                continue;
            }
            Map<String, Object> permission = permissionMap.get(fieldCode);
            boolean readable = permission == null || readBooleanValue(permission.get("readable"), true);
            if (!readable) {
                continue;
            }
            boolean writable = permission != null && readBooleanValue(permission.get("writable"), false);
            boolean required = writable && ((permission != null && readBooleanValue(permission.get("required"), false))
                    || readBooleanValue(field.get("required"), false));
            Map<String, Object> item = new LinkedHashMap<>(field);
            item.put("field", fieldCode);
            item.put("fieldCode", fieldCode);
            item.put("label", StringUtils.defaultIfBlank(textValue(field.get("label")), fieldCode));
            String rawType = resolveTaskFormControlType(item);
            String normalizedType = normalizeTaskFormFieldType(rawType);
            item.put("type", normalizedType);
            item.put("componentType", StringUtils.defaultIfBlank(rawType, normalizedType));
            if (StringUtils.isNotBlank(rawType) && !"input".equals(rawType)) {
                item.put("componentKey", rawType);
            }
            item.putIfAbsent("dataType", StringUtils.trimToEmpty(textValue(field.get("dataType"))));
            Map<String, Object> props = new LinkedHashMap<>(readNestedObject(item.get("props")));
            String dictType = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(item.get("dictType"))),
                    StringUtils.trimToNull(textValue(props.get("dictType"))));
            if (dictType != null) {
                item.put("dictType", dictType);
            }
            item.put("readable", true);
            item.put("writable", writable);
            item.put("required", required);
            item.put("readonly", !writable);
            item.put("disabled", !writable);
            props.put("disabled", !writable);
            props.put("readonly", !writable);
            if (item.get("dictType") != null) {
                props.put("dictType", item.get("dictType"));
            }
            item.put("props", props);
            result.add(item);
        }
        return result;
    }

    String resolveEditMode(JSONObject nodeForm, List<Map<String, Object>> permissions) {
        if (permissions != null && permissions.stream()
                .anyMatch(item -> readBooleanValue(item.get("writable"), false))) {
            return "EDITABLE";
        }
        return normalizeNodeEditMode(nodeForm == null ? null : nodeForm.getString("editMode"));
    }

    List<Map<String, Object>> normalizePermissions(List<Map<String, Object>> fieldCatalog,
                                                   List<Map<String, Object>> permissions) {
        if (permissions != null && !permissions.isEmpty()) {
            return permissions;
        }
        if (fieldCatalog == null || fieldCatalog.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> field : fieldCatalog) {
            if (field == null) {
                continue;
            }
            String fieldCode = StringUtils.firstNonBlank(
                    StringUtils.trimToNull(textValue(field.get("field"))),
                    StringUtils.trimToNull(textValue(field.get("fieldCode"))));
            if (fieldCode == null || !seen.add(fieldCode)
                    || readBooleanValue(field.get("internal"), false)
                    || readBooleanValue(field.get("systemField"), false)) {
                continue;
            }
            JSONObject props = readNestedObject(field.get("props"));
            boolean writable = !readBooleanValue(field.get("readonly"), false)
                    && !readBooleanValue(field.get("disabled"), false)
                    && !readBooleanValue(props.get("readonly"), false)
                    && !readBooleanValue(props.get("disabled"), false);
            Map<String, Object> permission = new LinkedHashMap<>();
            permission.put("field", fieldCode);
            permission.put("fieldCode", fieldCode);
            permission.put("label", StringUtils.defaultIfBlank(textValue(field.get("label")), fieldCode));
            permission.put("visible", true);
            permission.put("editable", writable);
            permission.put("readable", true);
            permission.put("writable", writable);
            permission.put("required", writable && readBooleanValue(field.get("required"), false));
            result.add(permission);
        }
        return result;
    }

    Map<String, Object> filterVisibleRecordData(Map<String, Object> recordData,
                                                List<Map<String, Object>> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (recordData == null || fields == null) {
            return result;
        }
        for (Map<String, Object> field : fields) {
            String fieldCode = StringUtils.trimToNull(textValue(field.get("field")));
            if (fieldCode != null) {
                result.put(fieldCode, read(recordData, fieldCode));
            }
            for (String displayField : collectReferenceDisplayFields(field)) {
                Object displayValue = read(recordData, displayField);
                if (displayValue != null || contains(recordData, displayField)) {
                    result.put(displayField, displayValue);
                }
            }
        }
        Object children = recordData.get("children");
        if (children instanceof Map<?, ?> || children instanceof List<?>) {
            result.put("children", children);
        }
        return result;
    }

    void validateRequiredFields(List<Map<String, Object>> permissions,
                                Map<String, Object> updateData,
                                Map<String, Object> input) {
        if (permissions == null) {
            return;
        }
        for (Map<String, Object> permission : permissions) {
            if ("child".equalsIgnoreCase(textValue(permission.get("scope")))) {
                continue;
            }
            boolean required = readBooleanValue(permission.get("required"), false);
            boolean writable = readBooleanValue(permission.get("writable"), false);
            if (!required || !writable) {
                continue;
            }
            String field = StringUtils.trimToNull(textValue(permission.get("field")));
            if (field == null || !input.containsKey(field)) {
                continue;
            }
            Object value = updateData.containsKey(field) ? updateData.get(field) : input.get(field);
            if (isEmptyRequiredValue(value)) {
                throw new BusinessException("请填写必填字段: " + field);
            }
        }
    }

    void putPermissionAliases(Map<String, Map<String, Object>> permissionMap,
                              String field,
                              Map<String, Object> permission) {
        if (permissionMap == null || StringUtils.isBlank(field) || permission == null) {
            return;
        }
        permissionMap.putIfAbsent(field, permission);
        String camelField = snakeToCamel(field);
        if (StringUtils.isNotBlank(camelField)) {
            permissionMap.putIfAbsent(camelField, permission);
        }
        String snakeField = camelToSnake(field);
        if (StringUtils.isNotBlank(snakeField)) {
            permissionMap.putIfAbsent(snakeField, permission);
        }
    }

    boolean isChildField(Map<String, Object> field) {
        if (field == null) {
            return false;
        }
        if ("child".equalsIgnoreCase(textValue(field.get("scope")))
                || StringUtils.isNotBlank(textValue(field.get("childKey")))) {
            return true;
        }
        String fieldCode = StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(field.get("field"))),
                StringUtils.trimToNull(textValue(field.get("fieldCode"))));
        return fieldCode != null && fieldCode.contains("__");
    }

    private Set<String> collectReferenceDisplayFields(Map<String, Object> field) {
        Set<String> result = new LinkedHashSet<>();
        if (field == null) {
            return result;
        }
        Map<String, Object> props = new LinkedHashMap<>(readNestedObject(field.get("props")));
        boolean selectionField = isSelectionLikeField(field, props);
        addTextFieldName(result, field.get("referenceDisplayField"));
        addTextFieldName(result, field.get("displayField"));
        addTextFieldName(result, field.get("labelField"));
        addTextFieldName(result, field.get("targetLabelField"));
        addTextFieldName(result, field.get("labelValueField"));
        addTextFieldName(result, field.get("targetField"));
        addTextFieldName(result, props.get("referenceDisplayField"));
        addTextFieldName(result, props.get("displayField"));
        addTextFieldName(result, props.get("labelField"));
        addTextFieldName(result, props.get("targetLabelField"));
        addTextFieldName(result, props.get("labelValueField"));
        addTextFieldName(result, props.get("targetField"));
        String fieldCode = StringUtils.trimToNull(textValue(field.get("field")));
        if (selectionField && fieldCode != null) {
            result.add(fieldCode + "Name");
            if (fieldCode.endsWith("Id")) {
                result.add(fieldCode.substring(0, fieldCode.length() - 2) + "Name");
            }
        }
        result.remove(fieldCode);
        return result;
    }

    private boolean isSelectionLikeField(Map<String, Object> field, Map<String, Object> props) {
        String type = normalizeTaskFormFieldType(StringUtils.firstNonBlank(
                StringUtils.trimToNull(textValue(field.get("type"))),
                StringUtils.trimToNull(textValue(field.get("componentType"))),
                StringUtils.trimToNull(textValue(field.get("componentKey")))));
        return Set.of("objectReference", "recordSelector", "userSelect", "orgTreeSelect",
                        "treeSelect", "cascader", "select", "dictSelect").contains(type)
                || StringUtils.isNotBlank(textValue(field.get("referenceObjectCode")))
                || StringUtils.isNotBlank(textValue(props.get("referenceObjectCode")))
                || StringUtils.isNotBlank(textValue(field.get("referenceDisplayField")))
                || StringUtils.isNotBlank(textValue(props.get("referenceDisplayField")));
    }

    private void addTextFieldName(Set<String> target, Object value) {
        String text = StringUtils.trimToNull(textValue(value));
        if (text != null) {
            target.add(text);
        }
    }

    Set<String> collectPermissionFields(List<Map<String, Object>> permissions,
                                        String permissionKey,
                                        boolean expected) {
        Set<String> result = new LinkedHashSet<>();
        if (permissions == null) {
            return result;
        }
        for (Map<String, Object> permission : permissions) {
            if ("child".equalsIgnoreCase(textValue(permission.get("scope")))) {
                continue;
            }
            if (readBooleanValue(permission.get(permissionKey), false) == expected) {
                String field = StringUtils.trimToNull(textValue(permission.get("field")));
                if (field != null) {
                    result.add(field);
                }
            }
        }
        return result;
    }

    private boolean isEmptyRequiredValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean || value instanceof Number) {
            return false;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return String.valueOf(value).trim().isEmpty();
    }
}
