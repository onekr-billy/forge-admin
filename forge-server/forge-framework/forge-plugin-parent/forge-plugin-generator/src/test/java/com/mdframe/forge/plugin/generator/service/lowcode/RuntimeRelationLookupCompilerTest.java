package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageModelRef;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeRelationSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuntimeRelationLookupCompilerTest {

    @Test
    void referenceUsesNormalizedSourceAndPreferredDisplayField() {
        Fixture fixture = fixture("REFERENCE", "missing");
        Map<String, Object> translations = new LinkedHashMap<>();

        RuntimeRelationLookupCompiler.appendDisplayTranslations(
                translations, fixture.model(), fixture.page());
        RuntimeRelationLookupCompiler.RelationLookupMeta lookup = RuntimeRelationLookupCompiler.resolve(
                fixture.model(), fixture.page(), "customerId");
        Map<String, Object> item = new LinkedHashMap<>();
        RuntimeRelationLookupCompiler.applyProps(item, lookup, "客户");

        assertEquals(Map.of("type", "relationName", "targetField", "customerIdName",
                "relationModelCode", "customer", "displayField", "name"), translations.get("customerId"));
        assertEquals("name", lookup.displayField());
        assertEquals("customerIdName", RuntimeRelationLookupCompiler.buildConfig(lookup).get("targetFieldAlias"));
        Map<?, ?> source = (Map<?, ?>) item.get("optionSource");
        assertEquals("get@/ai/crud/customer_runtime/page", source.get("api"));
        assertEquals("name", source.get("labelField"));
        assertEquals(Map.of("pageNum", 1, "pageSize", 50), source.get("params"));
        assertEquals("customerIdName", ((Map<?, ?>) item.get("props")).get("labelValueField"));
    }

    @Test
    void configuredDisplayFieldWinsAndChildCollectionIsNotReferenceTranslation() {
        Fixture reference = fixture("REFERENCE", "code");
        Fixture childCollection = fixture("ONE_TO_MANY", "code");
        Map<String, Object> translations = new LinkedHashMap<>();

        assertEquals("code", RuntimeRelationLookupCompiler.resolve(
                reference.model(), reference.page(), "customerId").displayField());
        RuntimeRelationLookupCompiler.appendDisplayTranslations(
                translations, childCollection.model(), childCollection.page());

        assertFalse(translations.containsKey("customerId"));
        assertNull(RuntimeRelationLookupCompiler.resolve(
                childCollection.model(), childCollection.page(), "customerId"));
    }

    private Fixture fixture(String relationType, String displayField) {
        LowcodeFieldSchema customerId = new LowcodeFieldSchema();
        customerId.setField("customerId");
        customerId.setColumnName("customer_id");
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setFields(List.of(customerId));

        LowcodeRelationSchema relation = new LowcodeRelationSchema();
        relation.setRelationType(relationType);
        relation.setTargetObjectCode("customer");
        relation.setSourceField("customer_id");
        relation.setTargetField("id");
        relation.setDisplayField(displayField);
        LowcodePageModelRef primary = new LowcodePageModelRef();
        primary.setPrimary(true);
        primary.setModelCode("order");
        primary.setRelations(List.of(relation));
        LowcodePageModelRef reference = new LowcodePageModelRef();
        reference.setModelCode("customer");
        reference.setModelName("客户");
        reference.setProps(Map.of("targetConfigKey", "customer_runtime"));
        reference.setFields(List.of(
                Map.of("sourceField", "id", "columnName", "id"),
                Map.of("sourceField", "code", "columnName", "code"),
                Map.of("sourceField", "name", "columnName", "name")));
        LowcodePageSchema page = new LowcodePageSchema();
        page.setPrimaryModelCode("order");
        page.setModelRefs(List.of(primary, reference));
        return new Fixture(model, page);
    }

    private record Fixture(LowcodeModelSchema model, LowcodePageSchema page) {
    }
}
