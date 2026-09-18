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
}
