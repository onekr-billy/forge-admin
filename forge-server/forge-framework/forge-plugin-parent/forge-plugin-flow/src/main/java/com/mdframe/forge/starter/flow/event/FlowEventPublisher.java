package com.mdframe.forge.starter.flow.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mdframe.forge.starter.core.domain.FlowEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 流程事件 Redis Stream 可靠发布器
 *
 * <p>先同步写入 {@code flow:event:stream}，成功返回后上层通知 Outbox 才能标记完成。
 * Pub/Sub 只作为旧客户端滚动升级期间的兼容广播，不参与可靠投递结果。</p>
 * <p>兼容频道命名规则：</p>
 * <pre>
 *   flow:event:{processDefKey}     — 按流程 Key 订阅（精准订阅某类流程的事件）
 *   flow:event:*                   — 通配符订阅所有流程事件（Redis PSUBSCRIBE）
 * </pre>
 *
 * <p>只有当 Spring Data Redis 存在于类路径时才会生效（optional 依赖），
 * 若未引入 Redis 依赖，此 Bean 不会被注册。</p>
 *
 * <h3>业务侧消费示例（Spring Boot 业务服务）</h3>
 * <pre>
 * {@literal @}Configuration
 * public class FlowEventConfig {
 *     {@literal @}Bean
 *     public MessageListenerAdapter flowEventListener(MyFlowEventHandler handler) {
 *         return new MessageListenerAdapter(handler, "onMessage");
 *     }
 *
 *     {@literal @}Bean
 *     public RedisMessageListenerContainer listenerContainer(
 *             RedisConnectionFactory factory,
 *             MessageListenerAdapter flowEventListener) {
 *         RedisMessageListenerContainer container = new RedisMessageListenerContainer();
 *         container.setConnectionFactory(factory);
 *         // 精准订阅某个流程
 *         container.addMessageListener(flowEventListener,
 *             new ChannelTopic("flow:event:leave-apply"));
 *         // 或通配符订阅全部流程事件
 *         // container.addMessageListener(flowEventListener,
 *         //     new PatternTopic("flow:event:*"));
 *         return container;
 *     }
 * }
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnClass(StringRedisTemplate.class)
public class FlowEventPublisher {

    /** 可靠事件流；每个业务应用使用独立 consumer group。 */
    public static final String STREAM_KEY = "flow:event:stream";

    /** Redis 频道前缀 */
    public static final String CHANNEL_PREFIX = "flow:event:";

    /** 全量事件频道（所有流程类型都会发布到此频道，配合 PSUBSCRIBE 使用）*/
    public static final String CHANNEL_ALL = "flow:event:all";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${forge.flow.event.redis-stream-key:flow:event:stream}")
    private String streamKey = STREAM_KEY;

    @Value("${forge.flow.event.redis-pubsub-compatibility-enabled:true}")
    private boolean pubSubCompatibilityEnabled = true;

    @Autowired(required = false)
    public FlowEventPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        // 注册 Java 8 时间类型支持
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 同步写入 Redis Stream，并按需广播到兼容 Pub/Sub 频道。
     *
     * <p>同时发布到两个频道：</p>
     * <ol>
     *   <li>{@code flow:event:{processDefKey}} - 按流程 Key 细分的频道</li>
     *   <li>{@code flow:event:all}             - 全量事件汇聚频道</li>
     * </ol>
     *
     * @param message 流程事件消息
     */
    public void publish(FlowEventMessage message) {
        if (redisTemplate == null) {
            throw new IllegalStateException("FLOW_EVENT_REDIS_REQUIRED");
        }
        validateReliableIdentity(message);
        try {
            String json = objectMapper.writeValueAsString(message);
            RecordId recordId = redisTemplate.<String, String>opsForStream().add(
                    MapRecord.create(streamKey, Map.of(
                            "payload", json,
                            "eventId", message.getEventId())),
                    RedisStreamCommands.XAddOptions.none());
            if (recordId == null) {
                throw new IllegalStateException("FLOW_EVENT_STREAM_XADD_NOT_CONFIRMED");
            }
            publishCompatibilityChannels(message, json);
            log.info("[FlowEvent] Redis Stream 发布成功: recordId={}, eventId={}, eventType={}, processDefKey={}",
                    recordId, message.getEventId(), message.getEventType(), message.getProcessDefKey());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("FLOW_EVENT_SERIALIZE_FAILED", e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("FLOW_EVENT_STREAM_PUBLISH_FAILED", e);
        }
    }

    private void publishCompatibilityChannels(FlowEventMessage message, String json) {
        if (!pubSubCompatibilityEnabled) {
            return;
        }
        try {
            if (message.getProcessDefKey() != null) {
                redisTemplate.convertAndSend(CHANNEL_PREFIX + message.getProcessDefKey(), json);
            }
            redisTemplate.convertAndSend(CHANNEL_ALL, json);
        } catch (RuntimeException compatibilityFailure) {
            log.warn("[FlowEvent] Redis Pub/Sub 兼容广播失败但 Stream 已持久化: eventId={}, failureType={}",
                    message.getEventId(), compatibilityFailure.getClass().getSimpleName());
        }
    }

    private void validateReliableIdentity(FlowEventMessage message) {
        if (message == null
                || isBlank(message.getEventId())
                || message.getEventVersion() == null || message.getEventVersion() <= 0
                || message.getEventSequence() == null || message.getEventSequence() <= 0
                || isBlank(message.getEventType())
                || isBlank(message.getProcessDefKey())
                || (isBlank(message.getProcessInstanceId()) && isBlank(message.getBusinessKey()))
                || !hasPositiveTenant(message.getTenantId())) {
            throw new IllegalArgumentException("FLOW_EVENT_RELIABLE_IDENTITY_REQUIRED");
        }
    }

    private boolean hasPositiveTenant(String tenantId) {
        try {
            return tenantId != null && Long.parseLong(tenantId.trim()) > 0;
        } catch (NumberFormatException invalidTenant) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
