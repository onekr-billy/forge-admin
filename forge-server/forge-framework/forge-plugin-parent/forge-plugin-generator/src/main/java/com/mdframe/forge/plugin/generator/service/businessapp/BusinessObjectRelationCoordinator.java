package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessObjectDesignStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObjectRelation;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectRelationDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectRelationMapper;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * 业务对象关系协调器。
 *
 * <p>集中编排关系持久化、运行时关系投影、页面模型引用、主子表外键补齐与快照恢复。
 * {@link BusinessObjectDesignerService} 保留事务边界和对外门面，本类只承担关系域协作。</p>
 */
final class BusinessObjectRelationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(BusinessObjectRelationCoordinator.class);
    private static final String AUTO_SUBTABLE_RELATION_DESC = "表单子表组件自动创建";

    private final ObjectMapper objectMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessObjectRelationMapper relationMapper;
    private final LongSupplier tenantIdSupplier;
    private final Function<Long, BusinessObjectDesignerService.DesignerContext> contextLoader;
    private final BiConsumer<BusinessObjectDesignerService.DesignerContext, String> draftSaver;

    BusinessObjectRelationCoordinator(ObjectMapper objectMapper,
                                      BusinessObjectMapper businessObjectMapper,
                                      BusinessObjectRelationMapper relationMapper,
                                      LongSupplier tenantIdSupplier,
                                      Function<Long, BusinessObjectDesignerService.DesignerContext> contextLoader,
                                      BiConsumer<BusinessObjectDesignerService.DesignerContext, String> draftSaver) {
        this.objectMapper = objectMapper;
        this.businessObjectMapper = businessObjectMapper;
        this.relationMapper = relationMapper;
        this.tenantIdSupplier = tenantIdSupplier;
        this.contextLoader = contextLoader;
        this.draftSaver = draftSaver;
    }

    void applyRelationsToModel(BusinessObjectDesignerService.DesignerContext context) {
        relationProjector().applyRelationsToModel(context);
    }

    private BusinessObjectRelationProjector relationProjector() {
        return new BusinessObjectRelationProjector(
                objectMapper,
                businessObjectMapper,
                relationMapper,
                tenantIdSupplier,
                contextLoader
        );
    }
    void saveSourceRelations(AiBusinessObject object, List<BusinessObjectRelationDTO> relations) {
        List<Long> savedIds = new ArrayList<>();
        for (BusinessObjectRelationDTO dto : relations) {
            if (dto == null) {
                continue;
            }
            AiBusinessObjectRelation relation = dto.getId() == null
                    ? new AiBusinessObjectRelation()
                    : relationMapper.selectRelationById(tenantIdSupplier.getAsLong(), dto.getId());
            if (relation == null) {
                relation = new AiBusinessObjectRelation();
            }
            relation.setTenantId(tenantIdSupplier.getAsLong());
            relation.setSuiteCode(object.getSuiteCode());
            relation.setSourceObjectCode(object.getObjectCode());
            relation.setTargetObjectCode(StringUtils.trimToNull(dto.getTargetObjectCode()));
            relation.setRelationType(StringUtils.defaultIfBlank(dto.getRelationType(), "REFERENCE").toUpperCase(Locale.ROOT));
            relation.setRelationName(StringUtils.defaultIfBlank(dto.getRelationName(), relation.getTargetObjectCode()));
            relation.setSourceFieldCode(StringUtils.trimToNull(dto.getSourceFieldCode()));
            relation.setTargetFieldCode(StringUtils.trimToNull(dto.getTargetFieldCode()));
            relation.setRelationConfig(StringUtils.trimToNull(dto.getRelationConfig()));
            relation.setDescription(StringUtils.trimToNull(dto.getDescription()));
            relation.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            relation.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
            if (relation.getId() == null) {
                relationMapper.insert(relation);
            } else {
                relationMapper.updateById(relation);
            }
            savedIds.add(relation.getId());
        }
        relationMapper.deleteMissingRelations(tenantIdSupplier.getAsLong(), object.getSuiteCode(), object.getObjectCode(), savedIds);
    }

    /**
     * 运行时主子表渲染只依赖持久化的对象关系；表单里拖入子表组件后，这里在保存时
     * 自动补齐子对象外键字段和 CHILD_LIST 关系，兑现设计器中“系统自动关联”的承诺。
     * 面板配置是唯一事实来源：已存在的关系全量覆盖，表单里删掉的组件对应关系一并移除。
     */
    boolean ensureChildTableRelations(BusinessObjectDesignerService.DesignerContext context, FormDesignerSchemaDTO formSchema) {
        if (context == null || context.getObject() == null || formSchema == null) {
            return false;
        }
        AiBusinessObject object = context.getObject();
        List<Map<String, Object>> subTables = new ArrayList<>();
        collectSubTableComponents(formSchema.getComponents(), subTables);
        collectSubTableComponents(formSchema.getForms(), subTables);
        log.info("[子表自动关联] collected subTables.size={} componentsFrom.components={} componentsFrom.forms={}",
                subTables.size(),
                formSchema.getComponents() == null ? 0 : formSchema.getComponents().size(),
                formSchema.getForms() == null ? 0 : formSchema.getForms().size());
        List<AiBusinessObjectRelation> existing = relationMapper.selectRuntimeRelationsBySource(
                tenantIdSupplier.getAsLong(), object.getSuiteCode(), object.getObjectCode());
        boolean changed = false;
        Set<String> handledTargets = new LinkedHashSet<>();
        for (Map<String, Object> component : subTables) {
            Map<String, Object> props = mapValue(component.get("props"));
            String targetObjectCode = StringUtils.trimToNull(text(props.get("modelCode")));
            if (targetObjectCode == null || targetObjectCode.equals(object.getObjectCode())
                    || !handledTargets.add(targetObjectCode)) {
                continue;
            }
            AiBusinessObjectRelation existingRelation = findEmbeddedRelationTo(existing, targetObjectCode);
            AiBusinessObject child = businessObjectMapper.selectByObjectCode(
                    tenantIdSupplier.getAsLong(), object.getSuiteCode(), targetObjectCode);
            if (child == null) {
                continue;
            }
            // 关系已存在时仍要确保外键字段在子对象模型里，否则库表永远补不上外键列。
            String foreignKeyField = ensureChildForeignKeyField(object, child);
            if (existingRelation != null) {
                if (StringUtils.isNotBlank(foreignKeyField)
                        && !StringUtils.equals(foreignKeyField, existingRelation.getTargetFieldCode())) {
                    existingRelation.setTargetFieldCode(foreignKeyField);
                    relationMapper.updateById(existingRelation);
                    changed = true;
                }
                if (overwriteChildRelationConfig(existingRelation, props)) {
                    relationMapper.updateById(existingRelation);
                    log.info("[子表自动关联] overwritten existing relation id={} target={}",
                            existingRelation.getId(), targetObjectCode);
                    changed = true;
                }
                continue;
            }
            if (StringUtils.isBlank(foreignKeyField)) {
                continue;
            }
            insertChildTableRelation(object, child, props, foreignKeyField);
            changed = true;
        }
        for (AiBusinessObjectRelation relation : existing) {
            if (relation == null || handledTargets.contains(relation.getTargetObjectCode())
                    || !AUTO_SUBTABLE_RELATION_DESC.equals(StringUtils.trimToEmpty(relation.getDescription()))) {
                continue;
            }
            relationMapper.deleteById(relation.getId());
            log.info("[子表自动关联] removed stale auto relation id={} target={}",
                    relation.getId(), relation.getTargetObjectCode());
            changed = true;
        }
        return changed;
    }

    void collectSubTableComponents(List<Map<String, Object>> components, List<Map<String, Object>> result) {
        if (components == null) {
            return;
        }
        for (Map<String, Object> component : components) {
            if (component == null) {
                continue;
            }
            String componentKey = text(component.get("componentKey"));
            if ("subTable".equals(componentKey) || "forgeSubTable".equals(componentKey)) {
                result.add(component);
            }
            collectSubTableComponents(listOfMap(component.get("children")), result);
            // 多表单协议的节点形如 { formKey, schema: { components: [...] } }；
            // 同时兼容少量历史数据把 components 直接放在表单节点上的结构。
            collectSubTableComponents(listOfMap(component.get("components")), result);
            Map<String, Object> nestedSchema = mapValue(component.get("schema"));
            collectSubTableComponents(listOfMap(nestedSchema.get("components")), result);
        }
    }

    private AiBusinessObjectRelation findEmbeddedRelationTo(List<AiBusinessObjectRelation> relations,
                                                            String targetObjectCode) {
        if (relations == null) {
            return null;
        }
        return relations.stream()
                .filter(relation -> relation != null
                        && targetObjectCode.equals(relation.getTargetObjectCode())
                        && isEmbeddedRelation(relation))
                .findFirst()
                .orElse(null);
    }

    /**
     * 面板配置是唯一事实来源：标题、开关、展示模式、显示字段全量覆盖关系记录。
     *
     * @return 配置是否有变化
     */
    private boolean overwriteChildRelationConfig(AiBusinessObjectRelation relation, Map<String, Object> props) {
        String relationName = StringUtils.firstNonBlank(StringUtils.trimToNull(text(props.get("header"))),
                relation.getRelationName(), relation.getTargetObjectCode());
        Map<String, Object> config = buildSubTableRelationConfig(
                relation.getTargetObjectCode(), relationName, props);
        String configJson = writeJson(config, "relationConfig");
        boolean changed = !StringUtils.equals(relation.getRelationName(), relationName)
                || !jsonEquals(relation.getRelationConfig(), configJson)
                || !AUTO_SUBTABLE_RELATION_DESC.equals(StringUtils.trimToEmpty(relation.getDescription()));
        if (!changed) {
            return false;
        }
        relation.setRelationName(relationName);
        relation.setRelationConfig(configJson);
        relation.setDescription(AUTO_SUBTABLE_RELATION_DESC);
        return true;
    }

    /**
     * 关系配置按 JSON 语义比较，避免空白/键序差异导致每次 designPreview 都 updateById。
     */
    private boolean jsonEquals(String left, String right) {
        if (StringUtils.equals(left, right)) {
            return true;
        }
        if (StringUtils.isBlank(left) && StringUtils.isBlank(right)) {
            return true;
        }
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return false;
        }
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (Exception e) {
            log.debug("[子表自动关联] relationConfig JSON解析失败，按文本处理", e);
            return false;
        }
    }

    private Map<String, Object> buildSubTableRelationConfig(String targetObjectCode, String relationName,
                                                            Map<String, Object> props) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("showInDetail", true);
        config.put("inlineCreateEnabled", readBoolean(props.get("allowCreate"), true));
        config.put("inlineEditEnabled", true);
        config.put("saveMode", "replace");
        config.put("detailTabTitle", relationName);
        config.put("allowSelectExisting", readBoolean(props.get("allowSelectExisting"), false));
        String displayMode = StringUtils.trimToNull(text(props.get("displayMode")));
        if (displayMode != null) {
            config.put("displayMode", displayMode);
        }
        config.put("relationKey", StringUtils.defaultIfBlank(
                StringUtils.trimToNull(text(props.get("relationKey"))), defaultRelationKey(targetObjectCode)));
        List<String> childFieldCodes = extractChildFieldCodes(props);
        if (!childFieldCodes.isEmpty()) {
            config.put("childFieldCodes", childFieldCodes);
        }
        if (readBoolean(props.get("allowSelectExisting"), false)) {
            Map<String, Object> defaultSelector = new LinkedHashMap<>();
            defaultSelector.put("objectCode", targetObjectCode);
            defaultSelector.put("businessObjectCode", targetObjectCode);
            defaultSelector.put("buttonText", "选择记录");
            defaultSelector.put("multiple", readBoolean(props.get("selectorMultiple"), true));
            List<String> displayFields = readStringList(props.get("selectorDisplayFields"));
            if (!displayFields.isEmpty()) {
                defaultSelector.put("displayFields", displayFields);
                defaultSelector.put("keywordFields", displayFields);
            }
            List<Map<String, Object>> filterFields = readSelectorFilterFields(props.get("selectorFilterFields"));
            if (!filterFields.isEmpty()) {
                defaultSelector.put("filterFields", filterFields);
            }
            config.put("recordSelector", defaultSelector);
        }
        return config;
    }

    /**
     * 读取面板配置的“筛选字段”：弹窗顶部可供使用者输入的筛选条件，
     * 每条支持默认值——固定值或 ${form.主表字段} 联动占位。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readSelectorFilterFields(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) raw);
            String fieldCode = StringUtils.trimToNull(text(row.get("fieldCode")));
            if (fieldCode == null) {
                continue;
            }
            row.put("fieldCode", fieldCode);
            result.add(row);
        }
        return result;
    }

    private List<String> extractChildFieldCodes(Map<String, Object> props) {
        Object columns = props.get("columns");
        if (!(columns instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String code = null;
            if (item instanceof Map<?, ?> column) {
                code = StringUtils.trimToNull(text(column.get("fieldCode")));
                if (code == null) {
                    code = StringUtils.trimToNull(text(column.get("field")));
                }
            } else {
                code = StringUtils.trimToNull(text(item));
            }
            if (code != null && !result.contains(code)) {
                result.add(code);
            }
        }
        return result;
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> StringUtils.trimToNull(text(item)))
                .filter(item -> item != null)
                .toList();
    }

    /**
     * 确保子对象存在指向主对象的外键字段；缺失时自动追加隐藏的 bigint 字段并保存子对象草稿。
     *
     * @return 外键字段编码（camelCase），子对象模型不可用时返回 null
     */
    private String ensureChildForeignKeyField(AiBusinessObject master, AiBusinessObject child) {
        BusinessObjectDesignerService.DesignerContext childContext = contextLoader.apply(child.getId());
        LowcodeModelSchema childModel = childContext.getModelSchema();
        if (childModel == null) {
            return null;
        }
        String fieldCode = childForeignKeyFieldCode(master.getObjectCode());
        String columnName = camelToSnakeCase(fieldCode);
        List<LowcodeFieldSchema> fields = childModel.getFields() == null
                ? new ArrayList<>()
                : new ArrayList<>(childModel.getFields());
        for (LowcodeFieldSchema field : fields) {
            if (field == null) {
                continue;
            }
            if (fieldCode.equals(field.getField()) || columnName.equals(field.getColumnName())) {
                return StringUtils.defaultIfBlank(field.getField(), fieldCode);
            }
        }
        LowcodeFieldSchema foreignKey = new LowcodeFieldSchema();
        foreignKey.setField(fieldCode);
        foreignKey.setColumnName(columnName);
        foreignKey.setLabel("所属" + StringUtils.defaultIfBlank(master.getObjectName(), master.getObjectCode()));
        foreignKey.setDataType("bigint");
        foreignKey.setBusinessFieldType("NUMBER");
        foreignKey.setComponentType("number");
        foreignKey.setRequired(false);
        foreignKey.setListVisible(false);
        foreignKey.setFormVisible(false);
        foreignKey.setFieldStatus("ENABLED");
        foreignKey.setSortOrder(fields.size());
        foreignKey.setRemark("子表组件自动外键，关联 " + master.getObjectCode() + ".id");
        fields.add(foreignKey);
        childModel.setFields(fields);
        childContext.setModelSchema(childModel);
        draftSaver.accept(childContext, BusinessObjectDesignStatus.CHANGED.getCode());
        return fieldCode;
    }

    private void insertChildTableRelation(AiBusinessObject master, AiBusinessObject child,
                                          Map<String, Object> props, String foreignKeyField) {
        String relationName = StringUtils.firstNonBlank(StringUtils.trimToNull(text(props.get("header"))),
                child.getObjectName(), child.getObjectCode());
        Map<String, Object> config = buildSubTableRelationConfig(child.getObjectCode(), relationName, props);

        AiBusinessObjectRelation relation = new AiBusinessObjectRelation();
        relation.setTenantId(tenantIdSupplier.getAsLong());
        relation.setSuiteCode(master.getSuiteCode());
        relation.setSourceObjectCode(master.getObjectCode());
        relation.setTargetObjectCode(child.getObjectCode());
        relation.setRelationType("CHILD_LIST");
        relation.setRelationName(relationName);
        relation.setSourceFieldCode("id");
        relation.setTargetFieldCode(foreignKeyField);
        relation.setRelationConfig(writeJson(config, "relationConfig"));
        relation.setDescription(AUTO_SUBTABLE_RELATION_DESC);
        relation.setStatus(EnableStatus.ENABLED.getCode());
        relation.setSortOrder(0);
        relationMapper.insert(relation);
    }

    private String childForeignKeyFieldCode(String masterObjectCode) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : StringUtils.defaultString(masterObjectCode).toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result + "Id";
    }

    private String camelToSnakeCase(String value) {
        return StringUtils.defaultString(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    void restoreRelationsFromSnapshot(AiBusinessObject object, String relationSnapshot) {
        if (StringUtils.isBlank(relationSnapshot)) {
            return;
        }
        List<Map<String, Object>> relations;
        try {
            relations = objectMapper.readValue(relationSnapshot, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException("关系快照格式不正确");
        }
        List<BusinessObjectRelationDTO> dtoList = new ArrayList<>();
        for (Map<String, Object> item : relations) {
            if (item == null || !object.getObjectCode().equals(text(item.get("sourceObjectCode")))) {
                continue;
            }
            BusinessObjectRelationDTO dto = new BusinessObjectRelationDTO();
            dto.setId(numberAsLong(item.get("id")));
            dto.setTargetObjectCode(text(item.get("targetObjectCode")));
            dto.setRelationType(text(item.get("relationType")));
            dto.setRelationName(text(item.get("relationName")));
            dto.setSourceFieldCode(text(item.get("sourceFieldCode")));
            dto.setTargetFieldCode(text(item.get("targetFieldCode")));
            dto.setRelationConfig(text(item.get("relationConfig")));
            dto.setDescription(text(item.get("description")));
            dto.setStatus(numberAsInteger(item.get("status")));
            dto.setSortOrder(numberAsInteger(item.get("sortOrder")));
            dtoList.add(dto);
        }
        saveSourceRelations(object, dtoList);
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(this::mapValue).toList();
    }

    private Map<String, Object> readMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = StringUtils.trimToEmpty(String.valueOf(value));
        if (StringUtils.isBlank(text)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private String writeJson(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(fieldName + "序列化失败");
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long numberAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Long.valueOf(text);
        }
        return null;
    }

    private Integer numberAsInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Integer.valueOf(text);
        }
        return null;
    }
}
