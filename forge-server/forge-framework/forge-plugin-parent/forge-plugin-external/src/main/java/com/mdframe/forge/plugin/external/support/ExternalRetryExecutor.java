package com.mdframe.forge.plugin.external.support;

import com.mdframe.forge.plugin.external.entity.ExternalSystem;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.outbound.model.OutboundResponse;
import com.mdframe.forge.starter.outbound.security.OutboundSecurityException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;

@Component
public class ExternalRetryExecutor {

    private static final Set<String> RETRYABLE_METHODS = Set.of("GET", "HEAD");
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(502, 503, 504);
    private static final int DEFAULT_ATTEMPTS = 3;
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEFAULT_BACKOFF_MS = 500;
    private static final int MAX_BACKOFF_MS = 5000;

    public OutboundResponse execute(ExternalSystem system, String method, Supplier<OutboundResponse> operation) {
        int attempts = resolveAttempts(system, method);
        int backoffMs = resolveBackoff(system.getRetryBackoffInterval());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                OutboundResponse response = operation.get();
                if (attempt == attempts || !RETRYABLE_STATUS.contains(response.getStatusCode())) {
                    return response;
                }
            } catch (OutboundSecurityException exception) {
                if (attempt == attempts || !isRetryableNetworkFailure(exception)) {
                    throw exception;
                }
            }
            waitBeforeRetry(backoffMs, attempt);
        }
        throw new BusinessException("外部接口重试执行异常");
    }

    int resolveAttempts(ExternalSystem system, String method) {
        if (!Boolean.TRUE.equals(system.getRetryEnabled()) || method == null
                || !RETRYABLE_METHODS.contains(method.toUpperCase())) {
            return 1;
        }
        Integer configured = system.getRetryMaxAttempts();
        if (configured == null || configured <= 0) {
            return DEFAULT_ATTEMPTS;
        }
        return Math.min(configured, MAX_ATTEMPTS);
    }

    int resolveBackoff(Integer configured) {
        if (configured == null || configured < 0) {
            return DEFAULT_BACKOFF_MS;
        }
        return Math.min(configured, MAX_BACKOFF_MS);
    }

    private boolean isRetryableNetworkFailure(OutboundSecurityException exception) {
        return "出站请求失败".equals(exception.getMessage())
                || "出站请求超时".equals(exception.getMessage())
                || "出站请求超过整体超时".equals(exception.getMessage());
    }

    private void waitBeforeRetry(int backoffMs, int attempt) {
        if (backoffMs <= 0) {
            return;
        }
        long delay = Math.min((long) backoffMs * attempt, MAX_BACKOFF_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("外部接口重试已中断");
        }
    }
}
