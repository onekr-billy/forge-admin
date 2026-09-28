package com.mdframe.forge.plugin.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "forge.data.jdbc-security")
public class JdbcSecurityProperties {

    /** 临时连接允许访问的精确主机名或 IP；空集合表示全部拒绝。 */
    private Set<String> allowedHosts = new LinkedHashSet<>();

    /** 临时连接允许访问的端口。 */
    private Set<Integer> allowedPorts = new LinkedHashSet<>(Set.of(3306, 5432, 1521, 1433));

    /** 即使主机在白名单内，也需显式开启才允许私网、环回或链路本地地址。 */
    private boolean allowPrivateAddresses = false;

    private Duration connectionTimeout = Duration.ofSeconds(3);

    private Duration queryTimeout = Duration.ofSeconds(3);
}
