package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeFieldMetadataCompilerTest {

    private final RuntimeFieldMetadataCompiler compiler = new RuntimeFieldMetadataCompiler();

    @Test
    void preservesDictionaryOrderAndFieldPolicies() {
        LowcodeFieldSchema phone = field("phone", "手机号");
        phone.setDictType("contact_type");
        phone.setSensitiveType("mobile");
        phone.setEncryptAlgorithm("SM4");

        LowcodeFieldSchema backupPhone = field("backupPhone", "备用手机号");
        backupPhone.setDictType("contact_type");

        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setFields(List.of(phone, backupPhone));

        assertEquals(List.of(Map.of(
                "dictType", "contact_type",
                "dictName", "手机号",
                "isNew", false,
                "items", List.of())), compiler.dictConfig(model));
        assertEquals(Map.of("phone", Map.of("type", "MOBILE", "label", "手机号")),
                compiler.desensitizeConfig(model));
        assertEquals(Map.of("phone", Map.of("algorithm", "SM4")), compiler.encryptConfig(model));
        assertEquals(Map.of(
                        "phone", Map.of("dictType", "contact_type", "targetField", "phoneName"),
                        "backupPhone", Map.of("dictType", "contact_type", "targetField", "backupPhoneName")),
                compiler.translationConfig(model));
    }

    @Test
    void compilesDynamicSelectionNameWithoutTreatingStaticOptionsAsRelations() {
        LowcodeFieldSchema dynamic = field("ownerId", "负责人");
        dynamic.setComponentType("select");
        dynamic.setBasicProps(Map.of("optionSource", Map.of("type", "API")));
        LowcodeFieldSchema staticField = field("category", "类别");
        staticField.setComponentType("select");
        staticField.setBasicProps(Map.of("optionSource", Map.of("type", "STATIC")));
        LowcodeModelSchema model = new LowcodeModelSchema();
        model.setFields(List.of(dynamic, staticField));

        assertEquals(Map.of("ownerId", Map.of("type", "relationName", "targetField", "ownerIdName")),
                compiler.translationConfig(model));
    }

    private LowcodeFieldSchema field(String code, String label) {
        LowcodeFieldSchema field = new LowcodeFieldSchema();
        field.setField(code);
        field.setLabel(label);
        return field;
    }
}
