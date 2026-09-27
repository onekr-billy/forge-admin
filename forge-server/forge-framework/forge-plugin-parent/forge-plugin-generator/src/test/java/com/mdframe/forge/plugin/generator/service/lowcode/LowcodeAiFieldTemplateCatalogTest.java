package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowcodeAiFieldTemplateCatalogTest {

    private final LowcodeAiFieldTemplateCatalog catalog = new LowcodeAiFieldTemplateCatalog();

    @Test
    void customerTemplatePreservesSensitiveAndQueryMetadata() {
        List<LowcodeFieldSchema> fields = catalog.fieldsForObject(
            new LowcodeAiObjectPlan("customer", "客户", "crm", false));

        LowcodeFieldSchema phone = fields.stream()
            .filter(field -> "phone".equals(field.getField()))
            .findFirst()
            .orElseThrow();
        assertEquals("PHONE", phone.getSensitiveType());
        assertEquals("like", phone.getQueryType());
        assertFalse(phone.getSystemField());
    }

    @Test
    void numericFieldFactoryAppliesPrecisionAndSortability() {
        LowcodeFieldSchema amount = catalog.field(
            "amount", "amount", "金额", "decimal", 18,
            true, false, true, true, "number");

        assertEquals(2, amount.getPrecision());
        assertTrue(amount.getSortable());
        assertEquals(160, amount.getWidth());
    }

    @Test
    void zoneCopiesFieldReferences() {
        List<String> refs = new ArrayList<>(List.of("name", "status"));
        LowcodePageZone zone = catalog.zone("search", "search-form", refs);
        refs.add("remark");

        assertEquals(List.of("name", "status"), zone.getFieldRefs());
        assertTrue(zone.getEnabled());
    }
}
