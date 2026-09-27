package com.mdframe.forge.starter.file.multipart;

import java.util.List;
import java.util.function.Supplier;

/**
 * 分片上传会话仓储。
 *
 * <p>分片按独立键保存，避免不同节点并行写入同一个 Map 时覆盖彼此状态；
 * 关键状态转换通过分布式锁串行化。</p>
 */
public interface MultipartUploadSessionStore {

    void saveSession(MultipartUploadSession session);

    MultipartUploadSession getSession(String sessionId);

    void savePart(MultipartUploadSession session, MultipartUploadPart part);

    MultipartUploadPart getPart(String sessionId, int partNumber);

    List<MultipartUploadSession> listSessions();

    void delete(MultipartUploadSession session);

    <T> T withLock(String sessionId, Supplier<T> action);
}
