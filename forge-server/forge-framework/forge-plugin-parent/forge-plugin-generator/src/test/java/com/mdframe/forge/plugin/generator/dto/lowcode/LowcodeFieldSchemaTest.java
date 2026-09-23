package com.mdframe.forge.plugin.generator.dto.lowcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodeFieldSchemaTest {

    @Test
    @DisplayName("personnel multi-select switches bigint storage to comma-separated varchar")
    void applyMultipleSelectionStorageConvertsNumericIdsToVarchar() {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField("ownerUserId");
        field.setColumnName("owner_user_id");
        field.setComponentType("userSelect");
        field.setDataType("bigint");
        field.setBasicProps(Map.of("multiple", true));

        field.applyMultipleSelectionStorage();

        assertTrue(field.isMultipleSelection());
        assertTrue(field.isSelectionLabelField());
        assertEquals("varchar", field.getDataType());
        assertEquals(LowcodeFieldSchema.MULTI_SELECT_VARCHAR_LENGTH, field.getLength());
    }

    @Test
    @DisplayName("dynamic optionSource select needs companion label column like reference fields")
    void dynamicOptionSourceSelectIsSelectionLabelField() {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField("customerId");
        field.setColumnName("customer_id");
        field.setComponentType("select");
        field.setBasicProps(Map.of(
                "optionSource", Map.of(
                        "type", "QUERY_SOURCE",
                        "sourceType", "BUSINESS_OBJECT",
                        "sourceKey", "crm_customer",
                        "valueField", "id",
                        "labelField", "customerName"
                )
        ));

        assertTrue(field.hasDynamicOptionSource());
        assertTrue(field.isSelectionLabelField());
        assertEquals("customer_id_name", field.referenceDisplayColumnName());
        assertEquals("customerIdName", field.referenceDisplayFieldName());
    }

    @Test
    @DisplayName("static option select does not create companion label column")
    void staticOptionSelectIsNotSelectionLabelField() {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField("status");
        field.setColumnName("status");
        field.setComponentType("select");
        field.setBasicProps(Map.of("optionSource", Map.of("type", "STATIC")));

        assertTrue(!field.hasDynamicOptionSource());
        assertTrue(!field.isSelectionLabelField());
    }
}
