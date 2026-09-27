package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.config.JdbcSecurityProperties;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcConnectionSecurityPolicyTest {

    @Test
    void shouldDenyAllTargetsWhenHostAllowlistIsEmpty() {
        JdbcConnectionSecurityPolicy policy = new JdbcConnectionSecurityPolicy(new JdbcSecurityProperties());

        assertThatThrownBy(() -> policy.validateTemporaryTarget(
                "com.mysql.cj.jdbc.Driver", "jdbc:mysql://8.8.8.8:3306/forge"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("白名单");
    }

    @Test
    void shouldRejectUnknownDriversProtocolsPortsAndPrivateAddresses() {
        JdbcSecurityProperties properties = new JdbcSecurityProperties();
        properties.setAllowedHosts(Set.of("127.0.0.1", "8.8.8.8"));
        JdbcConnectionSecurityPolicy policy = new JdbcConnectionSecurityPolicy(properties);

        assertThatThrownBy(() -> policy.validateTemporaryTarget(
                "evil.Driver", "jdbc:evil://8.8.8.8:3306/forge"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validateTemporaryTarget(
                "com.mysql.cj.jdbc.Driver", "jdbc:postgresql://8.8.8.8:3306/forge"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.validateTemporaryTarget(
                "com.mysql.cj.jdbc.Driver", "jdbc:mysql://8.8.8.8:22/forge"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("端口");
        assertThatThrownBy(() -> policy.validateTemporaryTarget(
                "com.mysql.cj.jdbc.Driver", "jdbc:mysql://127.0.0.1:3306/forge"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("私网");
    }

    @Test
    void shouldAllowExplicitPrivateTargetOnlyWhenBothControlsPermitIt() {
        JdbcSecurityProperties properties = new JdbcSecurityProperties();
        properties.setAllowedHosts(Set.of("127.0.0.1"));
        properties.setAllowPrivateAddresses(true);
        JdbcConnectionSecurityPolicy policy = new JdbcConnectionSecurityPolicy(properties);

        policy.validateTemporaryTarget(
                "com.mysql.cj.jdbc.Driver", "jdbc:mysql://127.0.0.1:3306/forge");
    }
}
