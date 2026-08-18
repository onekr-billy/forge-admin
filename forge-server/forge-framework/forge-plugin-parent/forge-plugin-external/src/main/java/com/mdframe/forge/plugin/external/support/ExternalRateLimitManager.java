package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ExternalRateLimitManager {

    static final int MIN_QPS = 1;
    static final int MAX_QPS = 1000;

    private final RedissonClient redissonClient;

    public void acquire(ExternalApi api) {
        if (!Boolean.TRUE.equals(api.getRateLimitEnabled())) {
            return;
        }
        int qps = normalizeQps(api.getRateLimitQps());
        Long tenantId = SessionHelper.getTenantId();
        if (tenantId == null || api.getId() == null) {
            throw new BusinessException("外部接口限流缺少可信租户或接口上下文");
        }
        String key = "external:rate:" + tenantId + ":" + api.getId() + ":" + qps;
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, qps, Duration.ofSeconds(1));
            if (!limiter.tryAcquire()) {
                throw new BusinessException("外部接口调用过于频繁，请稍后重试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("外部接口限流服务不可用，已拒绝本次调用");
        }
    }

    int normalizeQps(Integer value) {
        if (value == null || value < MIN_QPS || value > MAX_QPS) {
            throw new BusinessException("外部接口限流QPS必须在1到1000之间");
        }
        return value;
    }
}
