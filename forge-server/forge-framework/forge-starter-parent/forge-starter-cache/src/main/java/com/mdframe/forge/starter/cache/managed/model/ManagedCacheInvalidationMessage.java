package com.mdframe.forge.starter.cache.managed.model;

import java.io.Serializable;

/**
 * MULTI 缓存实例间的 L1 失效通知。key 为空表示清空整个命名缓存的本地层。
 */
public record ManagedCacheInvalidationMessage(
        String sourceId,
        String key
) implements Serializable {
}
