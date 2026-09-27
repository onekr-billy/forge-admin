package com.mdframe.forge.plugin.system.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserPasswordVersionContractTest {

    @Test
    void everyPasswordWriteShouldAdvanceCredentialVersion() throws Exception {
        String mapper = Files.readString(Path.of("src/main/resources/mapper/SysUserMapper.xml"));

        assertThat(mapper)
                .contains("<update id=\"updateActiveUserPassword\">")
                .contains("<update id=\"resetUserPassword\">")
                .containsOnlyOnce("<select id=\"selectActivePasswordVersion\"")
                .contains("password_version = COALESCE(password_version, 0) + 1");
        assertThat(count(mapper, "password_version = COALESCE(password_version, 0) + 1"))
                .isEqualTo(2);
    }

    private long count(String source, String fragment) {
        return source.lines().filter(line -> line.contains(fragment)).count();
    }
}
