package com.mdframe.forge.starter.file.multipart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 单节点兜底实现，主要用于未启用 Redis 的独立部署与单元测试。 */
public class InMemoryMultipartUploadSessionStore implements MultipartUploadSessionStore {

    private final Map<String, MultipartUploadSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, MultipartUploadPart> parts = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public void saveSession(MultipartUploadSession session) {
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public MultipartUploadSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void savePart(MultipartUploadSession session, MultipartUploadPart part) {
        parts.put(partKey(session.getSessionId(), part.getPartNumber()), part);
    }

    @Override
    public MultipartUploadPart getPart(String sessionId, int partNumber) {
        return parts.get(partKey(sessionId, partNumber));
    }

    @Override
    public List<MultipartUploadSession> listSessions() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public void delete(MultipartUploadSession session) {
        sessions.remove(session.getSessionId());
        for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
            parts.remove(partKey(session.getSessionId(), partNumber));
        }
    }

    @Override
    public <T> T withLock(String sessionId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(sessionId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                locks.remove(sessionId, lock);
            }
        }
    }

    private String partKey(String sessionId, int partNumber) {
        return sessionId + ':' + partNumber;
    }
}
