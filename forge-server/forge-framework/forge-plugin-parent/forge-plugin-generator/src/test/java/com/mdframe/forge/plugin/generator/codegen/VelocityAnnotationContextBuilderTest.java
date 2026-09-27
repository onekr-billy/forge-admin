package com.mdframe.forge.plugin.generator.codegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.GenTableColumn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityAnnotationContextBuilderTest {

    private final VelocityAnnotationContextBuilder builder =
        new VelocityAnnotationContextBuilder(new ObjectMapper());

    @Test
    void buildsDictionaryEncryptionAndDesensitizeFlagsInOneContext() {
        AiCrudConfig config = new AiCrudConfig();
        config.setColumnsSchema("[{\"field\":\"status\",\"dictType\":\"biz_status\"}]");
        config.setEditSchema("[]");
        config.setDictConfig("[{\"dictType\":\"biz_status\"}]");
        config.setEncryptConfig("{\"enableEncrypt\":true,\"enableDecrypt\":false}");
        config.setDesensitizeConfig("{\"phone\":{\"type\":\"phone\"}}");
        GenTableColumn status = column("status", null);
        GenTableColumn phone = column("phone", "NONE");

        Map<String, Object> flags = builder.build(config, List.of(status, phone));

        assertTrue((Boolean) flags.get("hasDictConfig"));
        assertTrue((Boolean) flags.get("hasEncrypt"));
        assertTrue((Boolean) flags.get("hasDesensitize"));
        assertEquals("biz_status", status.getDictType());
        assertEquals("PHONE", phone.getDesensitizeType());
    }

    @Test
    void normalizesNoopDesensitizeToAbsent() {
        assertNull(builder.normalizeDesensitizeType(null));
        assertNull(builder.normalizeDesensitizeType("none"));
        assertEquals("ID_CARD", builder.normalizeDesensitizeType("id_card"));
    }

    private GenTableColumn column(String javaField, String desensitizeType) {
        GenTableColumn column = new GenTableColumn();
        column.setJavaField(javaField);
        column.setDesensitizeType(desensitizeType);
        return column;
    }
}
