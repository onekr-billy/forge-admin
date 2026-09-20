package com.mdframe.forge.flow.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 流程客户端配置属性
 * <p>
 * 在 application.yml 中配置：
 * <pre>
 * forge:
 *   flow:
 *     client:
 *       url: http://flow-service:8080   # 流程服务地址
 *       token: your-service-token       # 服务间鉴权 Token（可选）
 *       connect-timeout: 3000           # 连接超时(ms)，默认 3000
 *       read-timeout: 10000             # 读取超时(ms)，默认 10000
 *       connection-request-timeout: 1000 # 从连接池申请连接超时(ms)
 *       max-connections: 100             # 连接池总连接数
 *       max-connections-per-route: 50    # 单路由最大连接数
 *       keep-alive-duration: 300000      # 默认连接保活时间(ms)
 * </pre>
 *
 * @author forge
 */
@Data
@ConfigurationProperties(prefix = "forge.flow.client")
public class FlowClientProperties {

    /**
     * 流程服务地址
     */
    private String url = "http://localhost:8080";

    /**
     * 服务间鉴权 Token（可选，透传到 Authorization: Bearer <token>）
     */
    private String token;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 3000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 10000;

    /**
     * 从连接池申请连接的超时（毫秒）
     */
    private int connectionRequestTimeout = 1000;

    /**
     * 连接池最大连接数
     */
    private int maxConnections = 100;

    /**
     * 单路由最大连接数。Flow 客户端通常只有一个服务地址。
     */
    private int maxConnectionsPerRoute = 50;

    /**
     * 服务端未返回 Keep-Alive 提示时的默认连接保活时间（毫秒）
     */
    private long keepAliveDuration = 300000L;

    /**
     * 空闲连接再次借用前的校验间隔（毫秒）
     */
    private long validateAfterInactivity = 5000L;

    /**
     * 是否自动订阅 Redis 流程事件频道（需引入 spring-data-redis 且配置了 Redis）
     * 默认 true，设为 false 可关闭自动订阅
     */
    private boolean redisSubscribe = true;
}
