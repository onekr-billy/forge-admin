package com.mdframe.forge.plugin.external.support;

import com.alibaba.fastjson2.JSON;
import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalResponseCache {

    static final int DEFAULT_TTL_SECONDS = 60;
    static final int MAX_TTL_SECONDS = 86400;

    private final ICacheService cacheService;

    public Optional<Object> get(ExternalApi api, Map<String, Object> params) {
        if (!cacheable(api)) {
            return Optional.empty();
        }
        String key = buildKey(api, params);
        try {
            String payload = cacheService.get(key, String.class);
            return payload == null ? Optional.empty() : Optional.ofNullable(JSON.parse(payload));
        } catch (Exception exception) {
            log.warn("读取外部接口缓存失败，apiId={}", api.getId());
            return Optional.empty();
        }
    }

    public void put(ExternalApi api, Map<String, Object> params, Object value) {
        if (!cacheable(api) || value == null) {
            return;
        }
        try {
            cacheService.set(buildKey(api, params), JSON.toJSONString(value), resolveTtl(api.getCacheTtl()), TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("写入外部接口缓存失败，apiId={}", api.getId());
        }
    }

    public boolean cacheable(ExternalApi api) {
        return api != null && Boolean.TRUE.equals(api.getCacheEnabled())
                && "GET".equalsIgnoreCase(api.getApiMethod())
                && SessionHelper.getTenantId() != null
                && SessionHelper.getUserId() != null;
    }

    String buildKey(ExternalApi api, Map<String, Object> params) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("template", resolveTemplate(api.getCacheKeyTemplate(), params));
        fingerprint.put("params", normalize(params));
        String digest = sha256(JSON.toJSONString(fingerprint));
        return "external:response:" + SessionHelper.getTenantId() + ":"
                + SessionHelper.getUserId() + ":" + value(api.getId()) + ":" + digest;
    }

    int resolveTtl(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_TTL_SECONDS;
        }
        return Math.min(value, MAX_TTL_SECONDS);
    }

    private String resolveTemplate(String template, Map<String, Object> params) {
        if (template == null || template.isBlank()) {
            return "default";
        }
        String resolved = template;
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                resolved = resolved.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return resolved;
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> sorted.put(String.valueOf(key), normalize(child)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            collection.forEach(child -> normalized.add(normalize(child)));
            return normalized;
        }
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成缓存摘要", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "anonymous" : String.valueOf(value);
    }
}
