package com.mdframe.forge.plugin.system.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SysUserMapperSqlContractTest {

    @Test
    void userListDisplayNamesMustBeCorrelatedToCurrentUser() throws IOException {
        String xml = Files.readString(Path.of("src/main/resources/mapper/SysUserMapper.xml"));
        String page = statement(xml, "selectUserPage");
        String export = statement(xml, "selectExportList");

        assertFalse(page.contains("GROUP BY sut.user_id"), "selectUserPage must not aggregate the whole user-tenant table");
        assertFalse(page.contains("GROUP BY uo.user_id"), "selectUserPage must not aggregate the whole user-org table");
        assertFalse(page.contains("GROUP BY up.user_id"), "selectUserPage must not aggregate the whole user-post table");
        assertFalse(export.contains("GROUP BY sut.user_id"));
        assertFalse(export.contains("GROUP BY uo.user_id"));
        assertFalse(export.contains("GROUP BY up.user_id"));

        assertTrue(xml.contains("WHERE sut.user_id = u.id"));
        assertTrue(xml.contains("WHERE uo.user_id = u.id"));
        assertTrue(xml.contains("WHERE up.user_id = u.id"));
    }

    private String statement(String xml, String id) {
        String startToken = "<select id=\"" + id + "\"";
        int start = xml.indexOf(startToken);
        assertTrue(start >= 0, () -> "Missing mapper statement: " + id);
        int end = xml.indexOf("</select>", start);
        assertTrue(end > start, () -> "Unclosed mapper statement: " + id);
        return xml.substring(start, end);
    }
}
