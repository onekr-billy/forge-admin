package com.mdframe.forge.flow.client.helper;

import com.mdframe.forge.flow.client.FlowClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.UUID;

/**
 * FlowHelper SDK 客户端自动配置
 * <p>
 * 注册 {@link FlowStartAspect} 和 {@link FlowEventSubscriber}。
 * 如果类路径中存在 Redis，则默认注册具备手工 ACK 和 pending 接管能力的 Redis Stream 消费器。
 *
 * @author forge
 */
@Configuration
public class FlowClientHelperAutoConfiguration {

    // ==================== @FlowStart 切面（必须有 AspectJ） ====================

    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnBean(FlowClient.class)
    public FlowStartAspect flowStartAspect(FlowClient flowClient,
                                           ApplicationContext applicationContext) {
        return new FlowStartAspect(flowClient, applicationContext);
    }

    // ==================== 事件分发器 ====================

    @Bean
    public FlowEventSubscriber flowEventSubscriber(ApplicationContext applicationContext) {
        return new FlowEventSubscriber(applicationContext);
    }

    // ==================== Redis Stream 可靠事件订阅 ====================

    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "forge.flow.client", name = "redis-stream-subscribe",
            havingValue = "true", matchIfMissing = true)
    public FlowRedisStreamSubscriber flowRedisStreamSubscriber(
            StringRedisTemplate redisTemplate,
            FlowEventSubscriber subscriber,
            @Value("${forge.flow.client.redis-stream-key:${forge.flow.event.redis-stream-key:flow:event:stream}}") String streamKey,
            @Value("${forge.flow.client.redis-stream-group:${spring.application.name:forge-flow-client}}") String groupName,
            @Value("${spring.application.name:forge-flow-client}") String applicationName,
            @Value("${forge.flow.client.redis-stream-batch-size:100}") int batchSize,
            @Value("${forge.flow.client.redis-stream-poll-interval-ms:1000}") long pollIntervalMs,
            @Value("${forge.flow.client.redis-stream-pending-idle-ms:60000}") long pendingIdleMs,
            @Value("${forge.flow.client.redis-stream-recovery-interval-ms:30000}") long recoveryIntervalMs) {
        String consumerName = normalizeConsumerName(applicationName) + "-" + UUID.randomUUID();
        return new FlowRedisStreamSubscriber(
                redisTemplate,
                subscriber,
                streamKey,
                groupName,
                consumerName,
                batchSize,
                Duration.ofMillis(Math.max(100, pollIntervalMs)),
                Duration.ofMillis(Math.max(1000, pendingIdleMs)),
                Duration.ofMillis(Math.max(1000, recoveryIntervalMs)));
    }

    // ==================== Redis Pub/Sub 兼容订阅 ====================

    /**
     * 兼容旧部署的 Redis Pub/Sub 监听器。默认关闭，避免与 Stream 消费重复执行；
     * 仅在迁移期显式配置 {@code forge.flow.client.redis-subscribe=true} 时启用。
     * <p>
     * flow-server 会同时发布到 {@code flow:event:{processDefKey}} 和 {@code flow:event:all}。
     * 这里默认只订阅全量频道，避免同一事件被自动回调多次。
     * 仅在以下条件同时满足时生效：
     * <ol>
     *   <li>类路径中存在 {@code spring-data-redis}</li>
     *   <li>容器中存在 {@code RedisConnectionFactory} Bean</li>
     *   <li>配置项 {@code forge.flow.client.redis-subscribe=true}</li>
     * </ol>
     */
    @Bean("flowRedisListenerContainer")
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @ConditionalOnProperty(prefix = "forge.flow.client", name = "redis-subscribe",
            havingValue = "true", matchIfMissing = false)
    public RedisMessageListenerContainer flowRedisListenerContainer(
            RedisConnectionFactory factory,
            FlowEventSubscriber subscriber) {

        // 适配器：将 String 消息路由到 FlowEventSubscriber.onMessage
        // 必须设置 StringRedisSerializer，否则 Redis 原始字节会带序列化头导致 JSON 解析失败
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "onMessage");
        adapter.setSerializer(new StringRedisSerializer());
        adapter.afterPropertiesSet();

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        // 订阅全量频道
        container.addMessageListener(adapter, new ChannelTopic("flow:event:all"));
        return container;
    }

    private String normalizeConsumerName(String applicationName) {
        String value = applicationName == null || applicationName.isBlank()
                ? "forge-flow-client" : applicationName.trim();
        return value.replaceAll("[^A-Za-z0-9:_-]", "_");
    }
}
