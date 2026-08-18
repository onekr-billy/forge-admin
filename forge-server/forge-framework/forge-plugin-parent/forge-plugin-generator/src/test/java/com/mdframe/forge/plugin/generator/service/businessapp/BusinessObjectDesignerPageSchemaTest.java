package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.FormDesignerSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.LinkageSchemaDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeModelSchemaNormalizer;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeSchemaValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BusinessObjectDesigner page schema")
class BusinessObjectDesignerPageSchemaTest {

    @Test
    @DisplayName("bridges legacy linkage rules through form governance")
    @SuppressWarnings("unchecked")
    void bridgesLegacyLinkageRulesThroughFormGovernance() throws Exception {
        BusinessObjectDesignerService service = designerService();
        FormDesignerSchemaDTO formSchema = new FormDesignerSchemaDTO();
        LinkageSchemaDTO legacy = new LinkageSchemaDTO();
        legacy.setSettings(Map.of("strict", true));
        legacy.setRules(List.of(Map.of(
                "ruleId", "legacy_rule",
                "type", "linkedDict",
                "sourceField", "province",
                "targetField", "city"
        )));

        Method hydrate = BusinessObjectDesignerService.class.getDeclaredMethod(
                "hydrateFormFieldLinkages", FormDesignerSchemaDTO.class, LinkageSchemaDTO.class);
        hydrate.setAccessible(true);
        FormDesignerSchemaDTO hydrated = (FormDesignerSchemaDTO) hydrate.invoke(service, formSchema, legacy);
        Map<String, Object> governance = (Map<String, Object>) hydrated.getSettings().get("governance");
        assertEquals(legacy.getRules(), governance.get("fieldLinkages"));

        Map<String, Object> configuredRule = new LinkedHashMap<>();
        configuredRule.put("ruleId", "application_rule");
        configuredRule.put("type", "clear");
        configuredRule.put("sourceField", "province");
        configuredRule.put("targetField", "city");
        governance.put("fieldLinkages", List.of(configuredRule));

        Method unify = BusinessObjectDesignerService.class.getDeclaredMethod(
                "resolveUnifiedLinkageSchema", FormDesignerSchemaDTO.class, LinkageSchemaDTO.class);
        unify.setAccessible(true);
        LinkageSchemaDTO unified = (LinkageSchemaDTO) unify.invoke(service, hydrated, legacy);

        assertEquals("application_rule", unified.getRules().get(0).get("ruleId"));
        assertEquals(Map.of("strict", true), unified.getSettings());
        assertEquals("legacy_rule", legacy.getRules().get(0).get("ruleId"));
    }

    @Test
    @DisplayName("normalizes blank and alias page zones before validation")
    void normalizesBlankAndAliasPageZonesBeforeValidation() throws Exception {
        LowcodeModelSchema modelSchema = modelSchema();
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setZones(new ArrayList<>());
        pageSchema.getZones().add(new LowcodePageZone());
        pageSchema.getZones().add(zone("data-table", List.of("name")));
        pageSchema.getZones().add(zone("list", List.of("name")));

        LowcodePageSchema normalized = ensurePageSchema(pageSchema, modelSchema);

        assertEquals(5, normalized.getZones().size());
        LowcodePageZone tableZone = normalized.getZones().stream()
                .filter(zone -> "table".equals(zone.getZoneKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(tableZone);
        assertEquals(List.of("name"), tableZone.getFieldRefs());
        assertDoesNotThrow(() -> new LowcodeSchemaValidator().validatePage(normalized, modelSchema));
    }

    @Test
    @DisplayName("ignores toolbar field refs during page validation")
    void ignoresToolbarFieldRefsDuringValidation() {
        LowcodeModelSchema modelSchema = modelSchema();
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setZones(List.of(
                zone("table", List.of("name")),
                zone("toolbar", List.of("pw_supplier_material__specModel", "missingActionRef"))
        ));

        assertDoesNotThrow(() -> new LowcodeSchemaValidator().validatePage(pageSchema, modelSchema));
    }

    @Test
    @DisplayName("accepts input number component alias")
    void acceptsInputNumberComponentAlias() {
        LowcodeModelSchema modelSchema = modelSchema();
        modelSchema.setFields(List.of(field("name"), field("amountCent", "amount_cent", "input-number", "bigint")));
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        pageSchema.setZones(List.of(zone("edit", List.of("name", "amountCent"))));

        assertDoesNotThrow(() -> new LowcodeSchemaValidator().validatePage(pageSchema, modelSchema));
    }

    @Test
    @DisplayName("applies input number component alias defaults")
    void appliesInputNumberComponentAliasDefaults() throws Exception {
        BusinessFieldDTO field = new BusinessFieldDTO();
        Method method = BusinessObjectDesignerService.class.getDeclaredMethod(
                "applyComponentDefaults", BusinessFieldDTO.class, String.class);
        method.setAccessible(true);

        method.invoke(designerService(), field, "input-number");

        assertEquals("NUMBER", field.getFieldType());
        assertEquals("int", field.getDataType());
        assertEquals("eq", field.getQueryType());
    }

    @Test
    @DisplayName("preserves imported decimal type when bound to number component")
    void preservesImportedDecimalTypeForNumberComponent() throws Exception {
        BusinessFieldDTO field = new BusinessFieldDTO();
        field.setFieldType("MONEY");
        field.setDataType("decimal");
        field.setLength(18);
        field.setPrecision(2);
        Method method = BusinessObjectDesignerService.class.getDeclaredMethod(
                "applyComponentDefaults", BusinessFieldDTO.class, String.class);
        method.setAccessible(true);

        method.invoke(designerService(), field, "number");

        assertEquals("MONEY", field.getFieldType());
        assertEquals("decimal", field.getDataType());
        assertEquals(18, field.getLength());
        assertEquals(2, field.getPrecision());
        assertEquals("eq", field.getQueryType());
    }

    @Test
    @DisplayName("reads legacy page zone key alias")
    void readsLegacyPageZoneKeyAlias() throws Exception {
        LowcodePageSchema schema = new ObjectMapper().readValue("""
                {"zones":[{"key":"table","type":"data-table","fieldRefs":["name"]}]}
                """, LowcodePageSchema.class);

        LowcodePageZone zone = schema.getZones().get(0);
        assertEquals("table", zone.getZoneKey());
        assertEquals("data-table", zone.getComponentKey());
    }

    @Test
    @DisplayName("preserves designer-authored master-detail options")
    void preservesDesignerAuthoredMasterDetailOptions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LowcodePageSchema schema = mapper.readValue("""
                {
                  "options": {
                    "masterDetailConfig": {
                      "children": [{"relationKey":"order_items","saveMode":"CASCADE"}]
                    }
                  }
                }
                """, LowcodePageSchema.class);

        Map<?, ?> masterDetailConfig = (Map<?, ?>) schema.getOptions().get("masterDetailConfig");
        List<?> children = (List<?>) masterDetailConfig.get("children");
        assertEquals("order_items", ((Map<?, ?>) children.get(0)).get("relationKey"));
        assertTrue(mapper.writeValueAsString(schema).contains("masterDetailConfig"));
    }

    @Test
    @DisplayName("preserves H5 page sections and bottom bar in form designer payload")
    void preservesH5PageSectionsAndBottomBar() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FormDesignerSchemaDTO schema = mapper.readValue("""
                {
                  "components": [],
                  "pageSections": [{"sectionId":"member","sectionType":"card","fields":["memberPhone"]}],
                  "bottomBar": {"actions":[{"type":"save","label":"保存"}]}
                }
                """, FormDesignerSchemaDTO.class);

        assertEquals("member", schema.getPageSections().get(0).get("sectionId"));
        assertEquals("save", ((Map<?, ?>) ((List<?>) schema.getBottomBar().get("actions")).get(0)).get("type"));
        assertTrue(mapper.writeValueAsString(schema).contains("pageSections"));
    }

    @Test
    @DisplayName("loads seed form schema from edit zone and merges it into runtime options")
    void loadsAndMergesSeedFormSchema() throws Exception {
        BusinessObjectDesignerService service = designerService();
        AiBusinessObject object = new AiBusinessObject();
        object.setObjectCode("PS_PRESALE_ORDER");
        object.setObjectName("预售单");
        LowcodePageSchema pageSchema = new LowcodePageSchema();
        LowcodePageZone editZone = zone("edit", List.of("memberPhone"));
        editZone.setProps(new java.util.LinkedHashMap<>(Map.of(
                "formDesignerSchema", Map.of(
                        "components", List.of(),
                        "pageSections", List.of(Map.of("sectionId", "member", "sectionType", "card")),
                        "bottomBar", Map.of("actions", List.of(Map.of("type", "save")))
                )
        )));
        pageSchema.setZones(List.of(editZone));

        Method migrate = BusinessObjectDesignerService.class.getDeclaredMethod(
                "migrateFormDesignerSchemaFromPageSchema",
                AiBusinessObject.class, LowcodeModelSchema.class, LowcodePageSchema.class);
        migrate.setAccessible(true);
        FormDesignerSchemaDTO migrated = (FormDesignerSchemaDTO) migrate.invoke(
                service, object, modelSchema(), pageSchema);

        Method merge = BusinessObjectDesignerService.class.getDeclaredMethod(
                "mergeFormDesignerSchemaIntoRuntimeOptions", String.class, LowcodePageSchema.class);
        merge.setAccessible(true);
        String options = (String) merge.invoke(service,
                "{\"actions\":[{\"actionCode\":\"submit\"}],\"masterDetailConfig\":{\"children\":[]}}",
                pageSchema);
        Map<?, ?> merged = new ObjectMapper().readValue(options, Map.class);

        assertEquals("member", migrated.getPageSections().get(0).get("sectionId"));
        assertTrue(merged.containsKey("actions"));
        assertTrue(merged.containsKey("masterDetailConfig"));
        assertTrue(merged.containsKey("formDesignerSchema"));
    }

    @Test
    @DisplayName("preserves field runtime metadata when rebuilding fields")
    void preservesFieldRuntimeMetadataWhenRebuildingFields() throws Exception {
        LowcodeModelSchema modelSchema = modelSchema();
        LowcodeFieldSchema status = field("status", "status", "select", "varchar");
        status.setBusinessFieldType("DICT");
        status.setDictType("pw_common_status");
        status.getBasicProps().put("dictType", "pw_common_status");
        LowcodeFieldSchema purchaseNo = field("purchaseNo", "purchase_no", "input", "varchar");
        purchaseNo.setReadonly(true);
        purchaseNo.getBasicProps().put("generation", Map.of("enabled", true, "ruleCode", "purchase_no"));
        LowcodeFieldSchema warehouseId = field("warehouseId", "warehouse_id", "recordSelector", "bigint");
        warehouseId.setBusinessFieldType("RECORD_SELECTOR");
        warehouseId.getBasicProps().put("recordSelector", Map.of("businessObjectCode", "PW_WAREHOUSE"));
        modelSchema.setFields(List.of(status, purchaseNo, warehouseId));

        LowcodeModelSchema rebuilt = rebuildModelFields(modelSchema, List.of(
                dto("状态", "status", "TEXT", "input"),
                dto("采购单号", "purchaseNo", "TEXT", "input"),
                dto("目标仓库", "warehouseId", "NUMBER", "number")
        ));

        LowcodeFieldSchema rebuiltStatus = findField(rebuilt, "status");
        assertEquals("select", rebuiltStatus.getComponentType());
        assertEquals("pw_common_status", rebuiltStatus.getDictType());
        LowcodeFieldSchema rebuiltPurchaseNo = findField(rebuilt, "purchaseNo");
        assertEquals("purchase_no", ((Map<?, ?>) rebuiltPurchaseNo.getBasicProps().get("generation")).get("ruleCode"));
        LowcodeFieldSchema rebuiltWarehouseId = findField(rebuilt, "warehouseId");
        assertEquals("recordSelector", rebuiltWarehouseId.getComponentType());
        assertEquals("PW_WAREHOUSE", ((Map<?, ?>) rebuiltWarehouseId.getBasicProps().get("recordSelector")).get("businessObjectCode"));
    }

    @Test
    @DisplayName("bridges legacy runtime schemas into page field settings")
    void bridgesLegacyRuntimeSchemasIntoPageFieldSettings() throws Exception {
        AiCrudConfig config = new AiCrudConfig();
        config.setEditSchema("""
                [
                  {"field":"warehouseId","label":"目标仓库","type":"recordSelector","props":{"recordSelector":{"businessObjectCode":"PW_WAREHOUSE"}}},
                  {"field":"orderStatus","label":"状态","type":"select","dictType":"pw_order_status","props":{"dictType":"pw_order_status"}}
                ]
                """);
        config.setSearchSchema("""
                [{"field":"orderStatus","label":"状态","type":"select","dictType":"pw_order_status"}]
                """);
        config.setColumnsSchema("""
                [{"prop":"orderStatus","label":"状态","render":{"type":"dictTag","dictType":"pw_order_status"}}]
                """);
        LowcodeModelSchema modelSchema = modelSchema();
        modelSchema.setFields(List.of(
                field("warehouseId", "warehouse_id", "number", "bigint"),
                field("orderStatus", "order_status", "input", "varchar")
        ));

        LowcodePageSchema pageSchema = resolvePageSchema(config, modelSchema);

        Map<String, Object> editSettings = fieldSettings(pageSchema, "edit");
        assertEquals("recordSelector", ((Map<?, ?>) editSettings.get("warehouseId")).get("componentType"));
        assertEquals("pw_order_status", ((Map<?, ?>) editSettings.get("orderStatus")).get("dictType"));
        Map<String, Object> searchSettings = fieldSettings(pageSchema, "search");
        assertEquals("pw_order_status", ((Map<?, ?>) searchSettings.get("orderStatus")).get("dictType"));
        Map<String, Object> tableSettings = fieldSettings(pageSchema, "table");
        assertEquals("dictTag", ((Map<?, ?>) tableSettings.get("orderStatus")).get("renderType"));
    }

    @Test
    @DisplayName("keeps existing chinese child tab titles when merging refs")
    void keepsExistingChineseChildTabTitlesWhenMergingRefs() throws Exception {
        BusinessObjectDesignerService service = designerService();
        Method method = BusinessObjectDesignerService.class.getDeclaredMethod(
                "mergeExistingPageModelRef", LowcodePageModelRef.class, LowcodePageModelRef.class);
        method.setAccessible(true);

        LowcodePageModelRef target = new LowcodePageModelRef();
        target.setModelCode("ps_presale_order_item");
        target.setModelName("预售商品明细");
        target.setProps(new java.util.LinkedHashMap<>(Map.of(
                "relationKey", "presale_items",
                "tabTitle", "presale_items",
                "relationName", "presale_items"
        )));

        LowcodePageModelRef existing = new LowcodePageModelRef();
        existing.setModelCode("ps_presale_order_item");
        existing.setModelName("预售商品");
        existing.setProps(new java.util.LinkedHashMap<>(Map.of(
                "relationKey", "presale_items",
                "tabTitle", "预售商品",
                "relationName", "预售商品"
        )));

        method.invoke(service, target, existing);

        assertEquals("预售商品", target.getModelName());
        assertEquals("预售商品", target.getProps().get("tabTitle"));
        assertEquals("预售商品", target.getProps().get("relationName"));
        assertEquals("presale_items", target.getProps().get("relationKey"));
    }

    private LowcodePageSchema ensurePageSchema(LowcodePageSchema pageSchema,
                                               LowcodeModelSchema modelSchema) throws Exception {
        BusinessFieldSchemaService fieldSchemaService = new BusinessFieldSchemaService(
                new LowcodeModelSchemaNormalizer(),
                new BusinessNamingService()
        );
        BusinessObjectDesignerService service = designerService(fieldSchemaService);
        Method method = BusinessObjectDesignerService.class.getDeclaredMethod(
                "ensurePageSchema", LowcodePageSchema.class, LowcodeModelSchema.class);
        method.setAccessible(true);
        return (LowcodePageSchema) method.invoke(service, pageSchema, modelSchema);
    }

    private LowcodePageSchema resolvePageSchema(AiCrudConfig config, LowcodeModelSchema modelSchema) throws Exception {
        Method method = BusinessObjectDesignerService.class.getDeclaredMethod(
                "resolvePageSchema", AiCrudConfig.class, LowcodeModelSchema.class);
        method.setAccessible(true);
        return (LowcodePageSchema) method.invoke(designerService(), config, modelSchema);
    }

    private LowcodeModelSchema rebuildModelFields(LowcodeModelSchema modelSchema, List<BusinessFieldDTO> fields) throws Exception {
        Method method = BusinessObjectDesignerService.class.getDeclaredMethod(
                "rebuildModelFields", LowcodeModelSchema.class, List.class);
        method.setAccessible(true);
        return (LowcodeModelSchema) method.invoke(designerService(), modelSchema, fields);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fieldSettings(LowcodePageSchema pageSchema, String zoneKey) {
        LowcodePageZone zone = pageSchema.getZones().stream()
                .filter(item -> zoneKey.equals(item.getZoneKey()))
                .findFirst()
                .orElseThrow();
        return (Map<String, Object>) zone.getProps().get("fieldSettings");
    }

    private LowcodeFieldSchema findField(LowcodeModelSchema modelSchema, String fieldCode) {
        return modelSchema.getFields().stream()
                .filter(field -> fieldCode.equals(field.getField()))
                .findFirst()
                .orElseThrow();
    }

    private BusinessFieldDTO dto(String label, String fieldCode, String fieldType, String componentType) {
        BusinessFieldDTO dto = new BusinessFieldDTO();
        dto.setFieldName(label);
        dto.setFieldCode(fieldCode);
        dto.setColumnName(fieldCode);
        dto.setFieldType(fieldType);
        dto.setComponentType(componentType);
        dto.setDataType("varchar");
        dto.setListVisible(true);
        dto.setFormVisible(true);
        dto.setSearchable(true);
        return dto;
    }

    private BusinessObjectDesignerService designerService() {
        BusinessFieldSchemaService fieldSchemaService = new BusinessFieldSchemaService(
                new LowcodeModelSchemaNormalizer(),
                new BusinessNamingService()
        );
        return designerService(fieldSchemaService);
    }

    private BusinessObjectDesignerService designerService(BusinessFieldSchemaService fieldSchemaService) {
        return new BusinessObjectDesignerService(
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new LowcodeModelSchemaNormalizer(),
                new LowcodeSchemaValidator(),
                null,
                null,
                fieldSchemaService,
                null,
                null,
                null
        );
    }

    private LowcodePageZone zone(String zoneKey, List<String> fieldRefs) {
        LowcodePageZone zone = new LowcodePageZone();
        zone.setZoneKey(zoneKey);
        zone.setFieldRefs(new ArrayList<>(fieldRefs));
        return zone;
    }

    private LowcodeModelSchema modelSchema() {
        LowcodeModelSchema modelSchema = new LowcodeModelSchema();
        modelSchema.setTableMode("EXISTING");
        modelSchema.setAppType("SINGLE");
        modelSchema.setTableName("pw_purchase_order");
        modelSchema.setFields(List.of(field("name")));
        return modelSchema;
    }

    private LowcodeFieldSchema field(String fieldCode) {
        return field(fieldCode, fieldCode, "input", "varchar");
    }

    private LowcodeFieldSchema field(String fieldCode, String columnName, String componentType, String dataType) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(fieldCode);
        field.setColumnName(columnName);
        field.setLabel("名称");
        field.setDataType(dataType);
        field.setComponentType(componentType);
        field.setQueryType("like");
        field.setSensitiveType("NONE");
        field.setReadonly(false);
        field.setSystemField(false);
        return field;
    }
}
