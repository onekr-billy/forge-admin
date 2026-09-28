package com.mdframe.forge.starter.file.multipart;

import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisMultipartUploadSessionStoreTest {

    @Test
    void storesSessionAndPartsWithServerSideTtl() {
        ICacheService cacheService = mock(ICacheService.class);
        RedisMultipartUploadSessionStore store = new RedisMultipartUploadSessionStore(
                cacheService, mock(RedissonClient.class));
        MultipartUploadSession session = session();
        MultipartUploadPart part = new MultipartUploadPart(1, 4L, "etag-1");

        store.saveSession(session);
        store.savePart(session, part);

        verify(cacheService).set(eq("forge:file:multipart:session:session-1"), eq(session),
                longThat(ttl -> ttl > 0), eq(TimeUnit.MILLISECONDS));
        verify(cacheService).set(eq("forge:file:multipart:part:session-1:1"), eq(part),
                longThat(ttl -> ttl > 0), eq(TimeUnit.MILLISECONDS));
        verify(cacheService).sAdd("forge:file:multipart:sessions", "session-1");
    }

    @Test
    void cacheFailuresAreFailClosed() {
        ICacheService cacheService = mock(ICacheService.class);
        when(cacheService.get("forge:file:multipart:session:session-1", MultipartUploadSession.class))
                .thenThrow(new IllegalStateException("redis unavailable"));
        RedisMultipartUploadSessionStore store = new RedisMultipartUploadSessionStore(
                cacheService, mock(RedissonClient.class));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> store.getSession("session-1"));

        assertEquals(503, exception.getCode());
    }

    @Test
    void criticalTransitionsUseDistributedLock() throws Exception {
        ICacheService cacheService = mock(ICacheService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("forge:file:multipart:lock:session-1")).thenReturn(lock);
        when(lock.tryLock(5, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        RedisMultipartUploadSessionStore store = new RedisMultipartUploadSessionStore(cacheService, redissonClient);

        String result = store.withLock("session-1", () -> "done");

        assertEquals("done", result);
        verify(lock).unlock();
    }

    private MultipartUploadSession session() {
        return new MultipartUploadSession(
                "session-1", "provider-upload-1", 1L, 1L,
                "report", "7", "report.pdf", "application/pdf", "rustfs",
                4L, 1, true, System.currentTimeMillis() + 60_000L);
    }
}
