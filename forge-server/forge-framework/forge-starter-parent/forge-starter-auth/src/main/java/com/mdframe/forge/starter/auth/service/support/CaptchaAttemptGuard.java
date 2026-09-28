package com.mdframe.forge.starter.auth.service.support;

import cn.hutool.crypto.digest.DigestUtil;
import com.mdframe.forge.starter.auth.config.CaptchaProperties;
import com.mdframe.forge.starter.cache.service.ICacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 短信、邮箱验证码的跨挑战失败窗口与短时锁定策略。
 *
 * <p>缓存键只包含目标摘要，日志也不输出手机号或邮箱原文。缓存不可用时统一按
 * 已锁定处理，确保验证和发码均 fail-closed。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaAttemptGuard {

    private static final String FAILURE_KEY_PREFIX = "captcha:verify:failure:";
    private static final String LOCK_KEY_PREFIX = "captcha:verify:lock:";
    private static final Duration DEFAULT_FAILURE_WINDOW = Duration.ofMinutes(10);
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(10);

    private final ICacheService cacheService;
    private final CaptchaProperties properties;

    public enum Channel {
        SMS,
        EMAIL
    }

    /**
     * 检查目标是否已锁定。Redis 异常时返回 true，调用方不得继续校验或发送。
     */
    public boolean isBlocked(Channel channel, String subject) {
        String lockKey = lockKey(channel, subject);
        try {
            return cacheService.hasKey(lockKey);
        } catch (RuntimeException exception) {
            logCacheFailure("check-lock", channel, lockKey, exception);
            return true;
        }
    }

    /**
     * 记录一次有效挑战的错误答案；达到阈值时原子创建短时锁。
     */
    public boolean recordFailure(Channel channel, String subject) {
        String failureKey = failureKey(channel, subject);
        try {
            long failures = cacheService.incrementWithExpiry(
                    failureKey,
                    1L,
                    positiveSeconds(properties.getVerificationFailureWindow(), DEFAULT_FAILURE_WINDOW),
                    TimeUnit.SECONDS);
            if (failures < Math.max(1, properties.getVerificationMaxFailures())) {
                return false;
            }
            cacheService.setIfAbsent(
                    lockKey(channel, subject),
                    Boolean.TRUE,
                    positiveSeconds(properties.getVerificationLockDuration(), DEFAULT_LOCK_DURATION),
                    TimeUnit.SECONDS);
            return true;
        } catch (RuntimeException exception) {
            logCacheFailure("record-failure", channel, failureKey, exception);
            return true;
        }
    }

    /**
     * 正确验证后清理历史失败状态。清理失败时返回 false，调用方应拒绝本次验证。
     */
    public boolean clearFailures(Channel channel, String subject) {
        String failureKey = failureKey(channel, subject);
        try {
            cacheService.delete(List.of(failureKey, lockKey(channel, subject)));
            return true;
        } catch (RuntimeException exception) {
            logCacheFailure("clear-failures", channel, failureKey, exception);
            return false;
        }
    }

    private String failureKey(Channel channel, String subject) {
        return FAILURE_KEY_PREFIX + keySuffix(channel, subject);
    }

    private String lockKey(Channel channel, String subject) {
        return LOCK_KEY_PREFIX + keySuffix(channel, subject);
    }

    private String keySuffix(Channel channel, String subject) {
        String normalized = subject == null ? "" : subject.trim();
        return channel.name().toLowerCase(Locale.ROOT) + ":"
                + DigestUtil.sha256Hex(normalized).substring(0, 16);
    }

    private long positiveSeconds(Duration configured, Duration fallback) {
        Duration duration = configured == null || configured.isZero() || configured.isNegative()
                ? fallback : configured;
        return Math.max(1L, duration.toSeconds());
    }

    private void logCacheFailure(String phase, Channel channel, String key, RuntimeException exception) {
        String keyDigest = DigestUtil.sha256Hex(key).substring(0, 12);
        log.error("验证码风控缓存异常: phase={}, channel={}, keyDigest={}, errorType={}",
                phase, channel, keyDigest, exception.getClass().getSimpleName());
    }
}
