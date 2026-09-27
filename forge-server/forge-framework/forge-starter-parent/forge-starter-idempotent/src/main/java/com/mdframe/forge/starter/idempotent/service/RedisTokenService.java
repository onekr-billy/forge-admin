package com.mdframe.forge.starter.idempotent.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.mdframe.forge.starter.idempotent.properties.TokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class RedisTokenService implements TokenService {
    
    private final StringRedisTemplate redisTemplate;
    private final TokenProperties properties;
    
    private static final String TOKEN_STATUS_UNUSED = "UNUSED";
    private static final String TOKEN_STATUS_CONSUMED = "CONSUMED";
    private static final long CONSUMED_TOKEN_TTL_SECONDS = 60L;
    private static final DefaultRedisScript<Long> CONSUME_TOKEN_SCRIPT = new DefaultRedisScript<>("""
            local status = redis.call('HGET', KEYS[1], 'status')
            if not status or status ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1], 'status', ARGV[2])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            return 1
            """, Long.class);
    
    @Override
    public String generateToken(String prefix) {
        String token = IdUtil.fastSimpleUUID();
        String key = buildTokenKey(prefix, token);
        
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put("createTime", String.valueOf(System.currentTimeMillis()));
        tokenData.put("status", TOKEN_STATUS_UNUSED);
        
        redisTemplate.opsForHash().putAll(key, tokenData);
        redisTemplate.expire(key, properties.getExpire(), TimeUnit.SECONDS);
        
        log.debug("生成Token成功: tokenDigest={}, prefix={}, expire={}s",
                tokenDigest(token), prefix, properties.getExpire());
        return token;
    }
    
    @Override
    public boolean validateToken(String token, String prefix) {
        if (token == null || token.isEmpty()) {
            log.warn("Token为空");
            return false;
        }
        
        String key = buildTokenKey(prefix, token);
        
        Boolean exists = redisTemplate.hasKey(key);
        if (!Boolean.TRUE.equals(exists)) {
            log.warn("Token不存在: tokenDigest={}", tokenDigest(token));
            return false;
        }
        
        String status = (String) redisTemplate.opsForHash().get(key, "status");
        if (TOKEN_STATUS_CONSUMED.equals(status)) {
            log.warn("Token已被消费: tokenDigest={}", tokenDigest(token));
            return false;
        }
        
        log.debug("Token验证成功: tokenDigest={}", tokenDigest(token));
        return true;
    }
    
    @Override
    public boolean consumeToken(String token, String prefix) {
        if (token == null || token.isBlank()) {
            log.warn("Token为空，拒绝消费");
            return false;
        }
        String key = buildTokenKey(prefix, token);
        Long consumed = redisTemplate.execute(
                CONSUME_TOKEN_SCRIPT,
                Collections.singletonList(key),
                TOKEN_STATUS_UNUSED,
                TOKEN_STATUS_CONSUMED,
                String.valueOf(CONSUMED_TOKEN_TTL_SECONDS));
        boolean success = Long.valueOf(1L).equals(consumed);
        if (success) {
            log.debug("Token已原子消费: tokenDigest={}", tokenDigest(token));
        } else {
            log.warn("Token不存在、已过期或已消费: tokenDigest={}", tokenDigest(token));
        }
        return success;
    }
    
    @Override
    public boolean isTokenConsumed(String token, String prefix) {
        String key = buildTokenKey(prefix, token);
        String status = (String) redisTemplate.opsForHash().get(key, "status");
        return TOKEN_STATUS_CONSUMED.equals(status);
    }
    
    private String buildTokenKey(String prefix, String token) {
        String actualPrefix = (prefix != null && !prefix.isEmpty()) ? prefix : "global";
        return "idempotent:token:" + actualPrefix + ":" + token;
    }

    private String tokenDigest(String token) {
        if (token == null || token.isBlank()) {
            return "empty";
        }
        return DigestUtil.sha256Hex(token).substring(0, 12);
    }
}
