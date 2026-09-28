package com.mdframe.forge.plugin.system.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordCredentialPolicyContractTest {

    @Test
    void recoveryQueriesAndUpdatesShouldRejectDisabledDeletedAndCrossTenantUsers() throws IOException {
        String mapper = Files.readString(Path.of("src/main/resources/mapper/SysUserMapper.xml"));

        String phoneLookup = statement(mapper, "selectByPhoneForLogin", "select");
        String emailLookup = statement(mapper, "selectByEmailForLogin", "select");
        String update = statement(mapper, "updateActiveUserPassword", "update");
        assertThat(phoneLookup).contains(
                "u.user_status = 1", "u.del_flag = 0", "sut.tenant_id = #{tenantId}", "sut.status = 1");
        assertThat(emailLookup).contains(
                "u.user_status = 1", "u.del_flag = 0", "sut.tenant_id = #{tenantId}", "sut.status = 1");
        assertThat(update).contains(
                "u.user_status = 1", "u.del_flag = 0", "sut.tenant_id = #{tenantId}", "sut.status = 1",
                "password_changed_time = #{updateTime}");
    }

    @Test
    void passwordHistoryShouldBeTenantScopedAndRetentionBounded() throws IOException {
        String historyMapper = Files.readString(Path.of(
                "src/main/resources/mapper/SysUserPasswordHistoryMapper.xml"));
        assertThat(historyMapper).contains(
                "tenant_id = #{tenantId}",
                "user_id = #{userId}",
                "LIMIT #{limit}",
                "LIMIT #{retainCount}");

        String migration = Files.readString(Path.of("../../../db/migration/"
                + "V1.0.190__add_user_password_history.sql"));
        assertThat(migration).contains(
                "password_changed_time",
                "CREATE TABLE IF NOT EXISTS `sys_user_password_history`",
                "`password_hash` varchar(100) NOT NULL",
                "`tenant_id`, `user_id`, `changed_time`, `id`");
    }

    @Test
    void onlyPasswordStrategiesShouldApplyCredentialExpiration() throws IOException {
        String password = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/plugin/system/strategy/UsernamePasswordAuthStrategy.java"));
        String captcha = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/plugin/system/strategy/UsernamePasswordCaptchaAuthStrategy.java"));
        String social = Files.readString(Path.of(
                "src/main/java/com/mdframe/forge/plugin/system/strategy/SocialAuthStrategyImpl.java"));

        assertThat(password).contains("return applyPasswordExpiration(loginUser);");
        assertThat(captcha).contains("return applyPasswordExpiration(loginUser);");
        assertThat(social).doesNotContain("applyPasswordExpiration(loginUser)");
    }

    private String statement(String xml, String id, String element) {
        int start = xml.indexOf("id=\"" + id + "\"");
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(start).as(id).isGreaterThanOrEqualTo(0);
        assertThat(end).as(id).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
