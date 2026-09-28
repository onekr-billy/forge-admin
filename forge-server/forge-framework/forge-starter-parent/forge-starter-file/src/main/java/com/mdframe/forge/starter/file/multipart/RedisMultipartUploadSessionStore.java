package com.mdframe.forge.starter.file.multipart;

import com.mdframe.forge.starter.cache.service.ICacheService;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Redis/Redisson 实现，使任意应用节点都能恢复同一上传会话。 */
@Slf4j
@RequiredArgsConstructor
public class RedisMultipartUploadSessionStore implements MultipartUploadSessionStore {

    private static final String KEY_PREFIX = "forge:file:multipart:";
    private static final String SESSION_INDEX_KEY = KEY_PREFIX + "sessions";
    private static final long CLEANUP_GRACE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 60;

    private final ICacheService cacheService;
    private final RedissonClient redissonClient;

    @Override
    public void saveSession(MultipartUploadSession session) {
        executeFailClosed(session.getSessionId(), () -> {
            cacheService.set(sessionKey(session.getSessionId()), session, ttlMillis(session), TimeUnit.MILLISECONDS);
            cacheService.sAdd(SESSION_INDEX_KEY, session.getSessionId());
            return null;
        });
    }

    @Override
    public MultipartUploadSession getSession(String sessionId) {
        return executeFailClosed(sessionId,
                () -> cacheService.get(sessionKey(sessionId), MultipartUploadSession.class));
    }

    @Override
    public void savePart(MultipartUploadSession session, MultipartUploadPart part) {
        executeFailClosed(session.getSessionId(), () -> {
            cacheService.set(partKey(session.getSessionId(), part.getPartNumber()), part,
                    ttlMillis(session), TimeUnit.MILLISECONDS);
            return null;
        });
    }

    @Override
    public MultipartUploadPart getPart(String sessionId, int partNumber) {
        return executeFailClosed(sessionId,
                () -> cacheService.get(partKey(sessionId, partNumber), MultipartUploadPart.class));
    }

    @Override
    public List<MultipartUploadSession> listSessions() {
        return executeFailClosed("index", () -> {
            Set<String> sessionIds = cacheService.sMembers(SESSION_INDEX_KEY);
            List<MultipartUploadSession> sessions = new ArrayList<>();
            for (String sessionId : sessionIds) {
                MultipartUploadSession session = cacheService.get(
                        sessionKey(sessionId), MultipartUploadSession.class);
                if (session == null) {
                    cacheService.sRemove(SESSION_INDEX_KEY, sessionId);
                } else {
                    sessions.add(session);
                }
            }
            return sessions;
        });
    }

    @Override
    public void delete(MultipartUploadSession session) {
        executeFailClosed(session.getSessionId(), () -> {
            List<String> keys = new ArrayList<>(session.getTotalParts() + 1);
            keys.add(sessionKey(session.getSessionId()));
            for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
                keys.add(partKey(session.getSessionId(), partNumber));
            }
            cacheService.delete(keys);
            cacheService.sRemove(SESSION_INDEX_KEY, session.getSessionId());
            return null;
        });
    }

    @Override
    public <T> T withLock(String sessionId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey(sessionId));
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(429, "分片上传会话正忙，请稍后重试");
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "分片上传会话服务暂不可用");
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("分片上传会话操作失败: session={}", safeSessionId(sessionId), e);
            throw new BusinessException(503, "分片上传会话服务暂不可用");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private long ttlMillis(MultipartUploadSession session) {
        return Math.max(1_000L, session.getExpiresAtMillis() - System.currentTimeMillis() + CLEANUP_GRACE_MILLIS);
    }

    private <T> T executeFailClosed(String sessionId, Supplier<T> action) {
        try {
            return action.get();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("分片上传会话缓存异常: session={}", safeSessionId(sessionId), e);
            throw new BusinessException(503, "分片上传会话服务暂不可用");
        }
    }

    private String sessionKey(String sessionId) {
        return KEY_PREFIX + "session:" + sessionId;
    }

    private String partKey(String sessionId, int partNumber) {
        return KEY_PREFIX + "part:" + sessionId + ':' + partNumber;
    }

    private String lockKey(String sessionId) {
        return KEY_PREFIX + "lock:" + sessionId;
    }

    private String safeSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return sessionId;
        }
        return sessionId.substring(0, 8);
    }
}
