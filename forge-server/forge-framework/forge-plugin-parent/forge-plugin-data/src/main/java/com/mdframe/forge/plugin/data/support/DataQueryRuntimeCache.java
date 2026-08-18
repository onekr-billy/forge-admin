package com.mdframe.forge.plugin.data.support;

import com.alibaba.fastjson2.JSON;
import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.plugin.data.vo.DataDatasetQueryResultVO;
import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
public class DataQueryRuntimeCache {

    private static final int DEFAULT_TTL_SECONDS = 60;
    private static final int MAX_TTL_SECONDS = 86400;

    private final ICacheService cacheService;

    public Optional<DataDatasetQueryResultVO> get(DataDataset dataset, DataDatasetQueryDTO query,
                                                  List<String> dimensions, int pageNum, int pageSize) {
        if (!cacheable(dataset)) {
            return Optional.empty();
        }
        try {
            String payload = cacheService.get(buildKey(dataset, query, dimensions, pageNum, pageSize), String.class);
            return payload == null ? Optional.empty()
                    : Optional.ofNullable(JSON.parseObject(payload, DataDatasetQueryResultVO.class));
        } catch (Exception exception) {
            log.warn("读取数据集缓存失败，datasetId={}", dataset.getId());
            return Optional.empty();
        }
    }

    public void put(DataDataset dataset, DataDatasetQueryDTO query, List<String> dimensions,
                    int pageNum, int pageSize, DataDatasetQueryResultVO result) {
        if (!cacheable(dataset) || result == null) {
            return;
        }
        try {
            cacheService.set(buildKey(dataset, query, dimensions, pageNum, pageSize),
                    JSON.toJSONString(result), resolveTtl(dataset.getCacheTtlSeconds()), TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("写入数据集缓存失败，datasetId={}", dataset.getId());
        }
    }

    public boolean cacheable(DataDataset dataset) {
        return dataset != null && Integer.valueOf(1).equals(dataset.getCacheEnabled())
                && SessionHelper.getTenantId() != null && SessionHelper.getUserId() != null;
    }

    String buildKey(DataDataset dataset, DataDatasetQueryDTO query, List<String> dimensions,
                    int pageNum, int pageSize) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("datasetId", dataset.getId());
        fingerprint.put("updateTime", dataset.getUpdateTime());
        fingerprint.put("dimensions", dimensions);
        fingerprint.put("fields", query == null ? null : query.getFields());
        fingerprint.put("params", normalize(query == null ? null : query.getParams()));
        fingerprint.put("pageNum", pageNum);
        fingerprint.put("pageSize", pageSize);
        return "dataset:runtime:" + SessionHelper.getTenantId() + ":" + SessionHelper.getUserId()
                + ":" + sha256(JSON.toJSONString(fingerprint));
    }

    int resolveTtl(Integer ttl) {
        if (ttl == null || ttl <= 0) {
            return DEFAULT_TTL_SECONDS;
        }
        return Math.min(ttl, MAX_TTL_SECONDS);
    }

    private Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, child) -> sorted.put(String.valueOf(key), normalize(child)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalize).toList();
        }
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成数据集缓存摘要", exception);
        }
    }
}
