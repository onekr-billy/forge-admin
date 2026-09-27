package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.config.JdbcSecurityProperties;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class JdbcConnectionSecurityPolicy {

    private static final Map<String, DriverRule> DRIVER_RULES = Map.of(
            "com.mysql.cj.jdbc.Driver", new DriverRule("mysql", 3306),
            "org.postgresql.Driver", new DriverRule("postgresql", 5432),
            "oracle.jdbc.OracleDriver", new DriverRule("oracle", 1521),
            "com.microsoft.sqlserver.jdbc.SQLServerDriver", new DriverRule("sqlserver", 1433));
    private static final Pattern SQLSERVER_URL = Pattern.compile(
            "^jdbc:sqlserver://(\\[[^]]+]|[^:;/]+)(?::(\\d+))?(?:;.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORACLE_URL = Pattern.compile(
            "^jdbc:oracle:thin:@(?://)?(\\[[^]]+]|[^:/]+):(\\d+)(?:[:/].*)?$", Pattern.CASE_INSENSITIVE);

    private final JdbcSecurityProperties properties;

    public void validateTemporaryTarget(String driverClassName, String jdbcUrl) {
        DriverRule rule = DRIVER_RULES.get(driverClassName);
        if (rule == null) {
            throw new BusinessException("临时数据连接使用了未允许的JDBC驱动");
        }
        JdbcTarget target = parseTarget(jdbcUrl, rule);
        Set<String> allowedHosts = properties.getAllowedHosts();
        boolean hostAllowed = allowedHosts != null && allowedHosts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeHost)
                .anyMatch(target.host()::equals);
        if (!hostAllowed) {
            throw new BusinessException("临时数据连接目标主机不在白名单中");
        }
        if (properties.getAllowedPorts() == null || !properties.getAllowedPorts().contains(target.port())) {
            throw new BusinessException("临时数据连接目标端口不在白名单中");
        }
        if (!properties.isAllowPrivateAddresses()) {
            rejectPrivateAddresses(target.host());
        }
    }

    public int connectionTimeoutMillis() {
        return boundedMillis(properties.getConnectionTimeout(), 1000, 10000, 3000);
    }

    public int queryTimeoutSeconds() {
        int millis = boundedMillis(properties.getQueryTimeout(), 1000, 30000, 3000);
        return Math.max(1, (int) Math.ceil(millis / 1000.0));
    }

    private JdbcTarget parseTarget(String jdbcUrl, DriverRule rule) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new BusinessException("临时数据连接地址不能为空");
        }
        String normalizedUrl = jdbcUrl.trim();
        try {
            if ("mysql".equals(rule.protocol()) || "postgresql".equals(rule.protocol())) {
                String prefix = "jdbc:" + rule.protocol() + ":";
                if (!normalizedUrl.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    throw new BusinessException("JDBC驱动与连接协议不匹配");
                }
                URI uri = URI.create(normalizedUrl.substring("jdbc:".length()));
                if (uri.getUserInfo() != null || uri.getHost() == null) {
                    throw new BusinessException("临时数据连接地址格式不安全");
                }
                return new JdbcTarget(normalizeHost(uri.getHost()),
                        uri.getPort() > 0 ? uri.getPort() : rule.defaultPort());
            }
            Matcher matcher = ("sqlserver".equals(rule.protocol()) ? SQLSERVER_URL : ORACLE_URL)
                    .matcher(normalizedUrl);
            if (!matcher.matches()) {
                throw new BusinessException("JDBC驱动与连接协议不匹配或地址格式不安全");
            }
            int port = matcher.group(2) == null ? rule.defaultPort() : Integer.parseInt(matcher.group(2));
            return new JdbcTarget(normalizeHost(matcher.group(1)), port);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("临时数据连接地址格式不安全");
        }
    }

    private void rejectPrivateAddresses(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress() || isCarrierGradeNat(address)) {
                    throw new BusinessException("临时数据连接禁止访问私网、环回或链路本地地址");
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("临时数据连接目标主机无法安全解析");
        }
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && Byte.toUnsignedInt(bytes[0]) == 100
                && (Byte.toUnsignedInt(bytes[1]) & 0xC0) == 0x40;
    }

    private String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private int boundedMillis(java.time.Duration duration, int minimum, int maximum, int fallback) {
        if (duration == null) {
            return fallback;
        }
        long millis = duration.toMillis();
        if (millis < minimum || millis > maximum) {
            throw new BusinessException("临时数据连接超时配置不合法");
        }
        return Math.toIntExact(millis);
    }

    private record DriverRule(String protocol, int defaultPort) {
    }

    private record JdbcTarget(String host, int port) {
    }
}
