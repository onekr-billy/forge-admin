package com.mdframe.forge.starter.file.core;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.file.model.FileMetadata;
import com.mdframe.forge.starter.file.model.StorageConfig;
import com.mdframe.forge.starter.file.multipart.InMemoryMultipartUploadSessionStore;
import com.mdframe.forge.starter.file.multipart.MultipartUploadPart;
import com.mdframe.forge.starter.file.multipart.MultipartUploadSession;
import com.mdframe.forge.starter.file.multipart.MultipartUploadSessionStore;
import com.mdframe.forge.starter.file.spi.FileMetadataPersistence;
import com.mdframe.forge.starter.file.spi.StorageConfigProvider;
import com.mdframe.forge.starter.file.storage.FileStorage;
import com.mdframe.forge.starter.file.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件管理器
 * 统一文件上传、下载、删除等操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileManager {

    private static final long DEFAULT_MAX_FILE_SIZE_MB = 100L;
    private static final long MAX_MULTIPART_PART_SIZE = 20L * 1024 * 1024;
    private static final int MAX_MULTIPART_PARTS = 10_000;
    private static final long MULTIPART_CONTEXT_TTL_MILLIS = 30L * 60 * 1000;

    public static final String DEFAULT_ALLOWED_TYPES =
            "jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,txt,csv,zip,rar,mp4,mp3";

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "jsp", "jspx", "php", "asp", "aspx", "html", "htm",
            "js", "mjs", "ts", "vue", "sh", "bash", "bat", "cmd",
            "ps1", "exe", "dll", "so", "dylib", "jar", "war", "ear",
            "sql", "md", "svg"
    );

    private static final Set<String> DANGEROUS_MIME_TYPES = Set.of(
            "text/html", "application/javascript", "text/javascript",
            "application/x-javascript", "image/svg+xml",
            "application/x-sh", "application/x-msdownload",
            "application/x-msdos-program", "application/x-php"
    );

    private static final Map<String, Set<String>> EXTENSION_MIME_TYPES = Map.ofEntries(
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("webp", Set.of("image/webp")),
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword", "application/octet-stream")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/octet-stream")),
            Map.entry("xls", Set.of("application/vnd.ms-excel", "application/octet-stream")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip", "application/octet-stream")),
            Map.entry("txt", Set.of("text/plain", "application/octet-stream")),
            Map.entry("csv", Set.of("text/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed", "application/octet-stream")),
            Map.entry("rar", Set.of("application/vnd.rar", "application/x-rar-compressed", "application/octet-stream")),
            Map.entry("mp4", Set.of("video/mp4", "application/octet-stream")),
            Map.entry("mp3", Set.of("audio/mpeg", "audio/mp3", "application/octet-stream"))
    );
    
    private final Map<String, FileStorage> storageMap = new ConcurrentHashMap<>();
    private final MultipartUploadSessionStore fallbackMultipartSessionStore =
            new InMemoryMultipartUploadSessionStore();
    
    @Autowired(required = false)
    private StorageConfigProvider configProvider;
    
    @Autowired(required = false)
    private FileMetadataPersistence metadataPersistence;

    @Autowired(required = false)
    private MultipartUploadSessionStore multipartSessionStore;
    
    /**
     * 注册存储策略
     */
    public void registerStorage(FileStorage storage) {
        storageMap.put(storage.getStorageType(), storage);
        log.info("注册文件存储策略: {}", storage.getStorageType());
    }
    
    /**
     * 获取存储策略
     */
    public FileStorage getStorage(String storageType) {
        return storageMap.get(storageType);
    }

    /**
     * 获取文件元数据
     */
    public FileMetadata getMetadata(String fileId) {
        if (metadataPersistence == null) {
            return null;
        }
        FileMetadata metadata = metadataPersistence.getById(fileId);
        if (metadata != null) {
            assertReadPermission(fileId, metadata);
        }
        return metadata;
    }
    
    /**
     * 上传文件（使用默认存储策略）
     */
    public FileMetadata upload(MultipartFile file, String businessType, String businessId) {
        return upload(file, businessType, businessId, null, null);
    }

    /**
     * 上传文件（指定存储策略 + 可见范围）
     */
    public FileMetadata upload(MultipartFile file, String businessType, String businessId,
                               String storageType, Boolean isPrivate) {
        if (storageType == null) {
            if (configProvider == null) {
                throw new RuntimeException("未配置StorageConfigProvider");
            }
            StorageConfig config = configProvider.getDefaultConfig();
            if (config == null) {
                throw new RuntimeException("未找到默认存储配置");
            }
            storageType = config.getStorageType();
        }
        return doUpload(file, businessType, businessId, storageType, isPrivate);
    }

    /**
     * 上传文件（指定存储策略）
     */
    public FileMetadata upload(MultipartFile file, String businessType, String businessId, String storageType) {
        return doUpload(file, businessType, businessId, storageType, null);
    }

    /**
     * 上传文件流（使用默认存储策略）。
     */
    public FileMetadata upload(InputStream inputStream, String fileName, String contentType,
                               String businessType, String businessId) {
        return upload(inputStream, fileName, contentType, businessType, businessId, null, null);
    }

    /**
     * 上传文件流（指定存储策略 + 可见范围）。
     */
    public FileMetadata upload(InputStream inputStream, String fileName, String contentType,
                               String businessType, String businessId,
                               String storageType, Boolean isPrivate) {
        return upload(inputStream, fileName, contentType, businessType, businessId, storageType, isPrivate, null);
    }

    /**
     * 上传文件流（指定存储策略 + 可见范围 + 已知文件大小）。
     */
    public FileMetadata upload(InputStream inputStream, String fileName, String contentType,
                               String businessType, String businessId,
                               String storageType, Boolean isPrivate, Long fileSize) {
        if (inputStream == null) {
            throw new RuntimeException("文件流不能为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("文件名不能为空");
        }
        if (storageType == null) {
            if (configProvider == null) {
                throw new RuntimeException("未配置StorageConfigProvider");
            }
            StorageConfig config = configProvider.getDefaultConfig();
            if (config == null) {
                throw new RuntimeException("未找到默认存储配置");
            }
            storageType = config.getStorageType();
        }
        validateFileName(fileName, storageType, fileSize);

        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }

        FileMetadata metadata = storage.upload(inputStream, fileName, contentType, businessType, businessId, fileSize);
        if (metadata.getFileSize() == null && fileSize != null) {
            metadata.setFileSize(fileSize);
        }
        metadata.setIsPrivate(isPrivate == null || isPrivate);
        applyUploader(metadata);
        if (metadataPersistence != null) {
            metadataPersistence.save(metadata);
        }
        return metadata;
    }

    private FileMetadata doUpload(MultipartFile file, String businessType, String businessId,
                                  String storageType, Boolean isPrivate) {
        // 验证文件
        validateFile(file, storageType);
        
        // 秒传检查：仅当 businessType 和 businessId 也一致时才复用
        String md5 = FileUtil.calculateMd5(file);
        if (metadataPersistence != null) {
            FileMetadata existing = metadataPersistence.getByMd5(md5);
            if (existing != null
                    && java.util.Objects.equals(existing.getBusinessType(), businessType)
                    && java.util.Objects.equals(existing.getBusinessId(), businessId)) {
                assertReadPermission(existing.getFileId(), existing);
                log.info("文件秒传: md5={}, businessType={}", md5, businessType);
                return existing;
            }
        }
        
        // 获取存储策略并上传
        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }
        
        FileMetadata metadata = storage.upload(file, businessType, businessId);
        metadata.setMd5(md5);
        metadata.setIsPrivate(isPrivate == null || isPrivate);
        applyUploader(metadata);

        // 持久化元数据
        if (metadataPersistence != null) {
            metadataPersistence.save(metadata);
        }
        
        return metadata;
    }
    
    /**
     * 下载文件
     */
    public void download(String fileId, HttpServletResponse response) {
        if (metadataPersistence == null) {
            throw new RuntimeException("未配置FileMetadataPersistence");
        }
        
        FileMetadata metadata = metadataPersistence.getById(fileId);
        if (metadata == null) {
            throw new RuntimeException("文件不存在: " + fileId);
        }
        assertReadPermission(fileId, metadata);
        
        FileStorage storage = getStorage(metadata.getStorageType());
        if (storage == null) {
            throw new RuntimeException("存储策略不存在: " + metadata.getStorageType());
        }
        
        try (InputStream inputStream = storage.download(fileId);
             OutputStream outputStream = response.getOutputStream()) {
            
            response.setContentType(metadata.getMimeType());
            response.setHeader("Content-Disposition",
                "attachment;filename=" + java.net.URLEncoder.encode(metadata.getOriginalName(), StandardCharsets.UTF_8));
            if (Boolean.TRUE.equals(metadata.getIsPrivate())) {
                response.setHeader("Cache-Control", "private, no-store");
                response.setHeader("Pragma", "no-cache");
            }
            
            inputStream.transferTo(outputStream);
            
            // 更新下载次数
            metadataPersistence.incrementDownloadCount(fileId);
            
        } catch (Exception e) {
            log.error("文件下载失败: {}", fileId, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }
    
    /**
     * 获取文件访问URL
     */
    public String getAccessUrl(String fileId, Integer expires) {
        if (metadataPersistence == null) {
            throw new RuntimeException("未配置FileMetadataPersistence");
        }
        
        FileMetadata metadata = metadataPersistence.getById(fileId);
        if (metadata == null) {
            throw new RuntimeException("文件不存在: " + fileId);
        }
        assertReadPermission(fileId, metadata);
        
        FileStorage storage = getStorage(metadata.getStorageType());
        if (storage == null) {
            throw new RuntimeException("存储策略不存在: " + metadata.getStorageType());
        }
        
        return storage.getAccessUrl(fileId, expires);
    }
    
    /**
     * 获取文件内容的Base64编码
     */
    public String getFileContentBase64(String fileId) {
        if (metadataPersistence == null) {
            throw new RuntimeException("未配置FileMetadataPersistence");
        }
        
        FileMetadata metadata = metadataPersistence.getById(fileId);
        if (metadata == null) {
            return null;
        }
        assertReadPermission(fileId, metadata);
        
        FileStorage storage = getStorage(metadata.getStorageType());
        if (storage == null) {
            return null;
        }
        
        try (InputStream inputStream = storage.download(fileId)) {
            byte[] bytes = inputStream.readAllBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("获取文件Base64失败: {}", fileId, e);
            return null;
        }
    }

    /**
     * 获取文件内容的字节数组（用于服务端内部消费，如 Excel 图片导出）。
     */
    public byte[] getFileBytes(String fileId) {
        if (metadataPersistence == null) {
            throw new RuntimeException("未配置FileMetadataPersistence");
        }
        FileMetadata metadata = metadataPersistence.getById(fileId);
        if (metadata == null) {
            return null;
        }
        assertReadPermission(fileId, metadata);
        FileStorage storage = getStorage(metadata.getStorageType());
        if (storage == null) {
            return null;
        }
        try (InputStream inputStream = storage.download(fileId)) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("获取文件字节失败: {}", fileId, e);
            return null;
        }
    }
    
    /**
     * 获取文件元数据
     */
    public FileMetadata getFileMetadata(String fileId) {
        if (metadataPersistence == null) {
            return null;
        }
        FileMetadata metadata = metadataPersistence.getById(fileId);
        if (metadata != null) {
            assertReadPermission(fileId, metadata);
        }
        return metadata;
    }
    
    /**
     * 删除文件
     */
    public boolean delete(String fileId) {
        if (metadataPersistence == null) {
            throw new RuntimeException("未配置FileMetadataPersistence");
        }
        
        FileMetadata metadata = metadataPersistence.getById(fileId);
        if (metadata == null) {
            return false;
        }
        if (!metadataPersistence.canModify(fileId, null)) {
            throw new BusinessException(403, "无权删除该文件");
        }

        FileStorage storage = getStorage(metadata.getStorageType());
        if (storage != null) {
            storage.delete(fileId);
        }
        
        metadataPersistence.delete(fileId);
        return true;
    }
    
    /**
     * 分片上传初始化
     */
    public String initMultipartUpload(String fileName, String businessType, String businessId, String storageType) {
        return initMultipartUpload(fileName, businessType, businessId, storageType, null, null, true);
    }

    public String initMultipartUpload(String fileName, String businessType, String businessId, String storageType,
                                      Long totalSize, Integer totalParts, Boolean isPrivate) {
        return initMultipartUpload(fileName, businessType, businessId, storageType,
                totalSize, totalParts, isPrivate, "application/octet-stream");
    }

    public String initMultipartUpload(String fileName, String businessType, String businessId, String storageType,
                                      Long totalSize, Integer totalParts, Boolean isPrivate, String contentType) {
        requireAuthenticatedUploader();
        if (totalSize == null || totalSize <= 0) {
            throw new BusinessException("分片上传必须声明有效的文件总大小");
        }
        if (totalParts == null || totalParts <= 0 || totalParts > MAX_MULTIPART_PARTS) {
            throw new BusinessException("分片上传必须声明有效的分片数量");
        }
        contentType = normalizeContentType(contentType);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        validateFilePolicy(fileName, storageType, totalSize, contentType);
        if (totalSize > (long) totalParts * MAX_MULTIPART_PART_SIZE) {
            throw new BusinessException("文件总大小超过声明分片可承载的上限");
        }
        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }
        String providerUploadId = storage.initMultipartUpload(fileName, businessType, businessId);
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        MultipartUploadSession session = new MultipartUploadSession(
                sessionId, providerUploadId, SessionHelper.getUserId(), SessionHelper.getTenantId(),
                businessType, businessId, fileName, contentType, storageType,
                totalSize, totalParts, isPrivate == null || isPrivate,
                System.currentTimeMillis() + MULTIPART_CONTEXT_TTL_MILLIS);
        try {
            multipartSessionStore().saveSession(session);
            return sessionId;
        } catch (RuntimeException e) {
            abortQuietly(storage, providerUploadId);
            throw e;
        }
    }
    
    /**
     * 上传分片
     */
    public String uploadPart(String uploadId, int partNumber, InputStream inputStream, String storageType) {
        return uploadPart(uploadId, partNumber, inputStream, storageType, null);
    }

    public String uploadPart(String uploadId, int partNumber, InputStream inputStream, String storageType, Long partSize) {
        return uploadPart(uploadId, partNumber, inputStream, storageType, partSize, null);
    }

    public String uploadPart(String uploadId, int partNumber, InputStream inputStream, String storageType,
                             Long partSize, String contentType) {
        validateSessionId(uploadId);
        if (inputStream == null || partSize == null || partSize <= 0 || partSize > MAX_MULTIPART_PART_SIZE) {
            throw new BusinessException("单个分片大小不合法");
        }
        return multipartSessionStore().withLock(uploadId, () -> {
            MultipartUploadSession session = requireMultipartSession(uploadId, storageType);
            if (partNumber <= 0 || partNumber > session.getTotalParts()) {
                throw new BusinessException("分片序号不合法");
            }
            validateMultipartContentType(session, contentType);
            ensureMultipartQuota(session, partNumber, partSize);

            FileStorage storage = requireStorage(storageType);
            String eTag = storage.uploadPart(session.getProviderUploadId(), partNumber, inputStream);
            if (eTag == null || eTag.isBlank()) {
                throw new BusinessException("存储服务未返回有效的分片 ETag");
            }
            multipartSessionStore().savePart(session,
                    new MultipartUploadPart(partNumber, partSize, eTag));
            return eTag;
        });
    }
    
    /**
     * 完成分片上传
     */
    public FileMetadata completeMultipartUpload(String uploadId, List<String> partETags, String storageType) {
        validateSessionId(uploadId);
        return multipartSessionStore().withLock(uploadId, () -> {
            MultipartUploadSession session = requireMultipartSession(uploadId, storageType);
            List<MultipartUploadPart> uploadedParts = requireCompletePartList(session, partETags);
            long uploadedSize = uploadedParts.stream().mapToLong(MultipartUploadPart::getSize).sum();
            if (uploadedSize != session.getTotalSize()) {
                throw new BusinessException("已上传分片总大小与初始化声明不一致");
            }

            FileStorage storage = requireStorage(storageType);
            try {
                FileMetadata metadata = storage.completeMultipartUpload(
                        session.getProviderUploadId(), uploadedParts.stream()
                                .map(MultipartUploadPart::getETag).toList());
                validateCompletedMultipartMetadata(session, metadata);
                if (metadataPersistence != null) {
                    metadataPersistence.save(metadata);
                }
                multipartSessionStore().delete(session);
                return metadata;
            } catch (RuntimeException e) {
                abortQuietly(storage, session.getProviderUploadId());
                multipartSessionStore().delete(session);
                throw e;
            }
        });
    }

    private void requireAuthenticatedUploader() {
        if (SessionHelper.getUserId() == null || SessionHelper.getTenantId() == null) {
            throw new BusinessException(401, "分片上传需要登录用户和租户上下文");
        }
    }

    private MultipartUploadSession requireMultipartSession(String uploadId, String storageType) {
        requireAuthenticatedUploader();
        MultipartUploadSession session = multipartSessionStore().getSession(uploadId);
        if (session == null) {
            throw new BusinessException("分片上传会话不存在或已过期");
        }
        if (session.getExpiresAtMillis() < System.currentTimeMillis()) {
            cleanupMultipartSession(session);
            throw new BusinessException("分片上传会话不存在或已过期");
        }
        if (!Objects.equals(session.getUserId(), SessionHelper.getUserId())
                || !Objects.equals(session.getTenantId(), SessionHelper.getTenantId())
                || !Objects.equals(session.getStorageType(), storageType)) {
            throw new BusinessException(403, "无权使用该分片上传会话");
        }
        return session;
    }

    @Scheduled(fixedDelayString = "${forge.file.multipart.cleanup-interval-millis:60000}")
    public void cleanupExpiredMultipartSessions() {
        long now = System.currentTimeMillis();
        for (MultipartUploadSession session : multipartSessionStore().listSessions()) {
            if (session.getExpiresAtMillis() < now) {
                multipartSessionStore().withLock(session.getSessionId(), () -> {
                    MultipartUploadSession current = multipartSessionStore().getSession(session.getSessionId());
                    if (current != null && current.getExpiresAtMillis() < System.currentTimeMillis()) {
                        cleanupMultipartSession(current);
                    }
                    return null;
                });
            }
        }
    }

    private void cleanupMultipartSession(MultipartUploadSession session) {
        FileStorage storage = getStorage(session.getStorageType());
        if (storage != null) {
            abortQuietly(storage, session.getProviderUploadId());
        }
        multipartSessionStore().delete(session);
    }

    private void abortQuietly(FileStorage storage, String providerUploadId) {
        try {
            storage.abortMultipartUpload(providerUploadId);
        } catch (RuntimeException cleanupError) {
            log.warn("清理分片上传临时数据失败: storageType={}", storage.getStorageType(), cleanupError);
        }
    }

    private MultipartUploadSessionStore multipartSessionStore() {
        return multipartSessionStore == null ? fallbackMultipartSessionStore : multipartSessionStore;
    }

    private FileStorage requireStorage(String storageType) {
        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }
        return storage;
    }

    private List<MultipartUploadPart> requireCompletePartList(
            MultipartUploadSession session, List<String> partETags) {
        if (partETags == null || partETags.size() != session.getTotalParts()) {
            throw new BusinessException("分片列表不完整");
        }
        List<MultipartUploadPart> parts = new ArrayList<>(session.getTotalParts());
        for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
            MultipartUploadPart part = multipartSessionStore().getPart(session.getSessionId(), partNumber);
            if (part == null || part.getPartNumber() == null || part.getPartNumber() != partNumber) {
                throw new BusinessException("分片列表不连续或尚未全部上传");
            }
            if (!Objects.equals(part.getETag(), partETags.get(partNumber - 1))) {
                throw new BusinessException("分片 ETag 与服务端记录不一致");
            }
            parts.add(part);
        }
        return parts;
    }

    private void ensureMultipartQuota(MultipartUploadSession session, int currentPartNumber, long currentPartSize) {
        long uploadedSize = currentPartSize;
        for (int partNumber = 1; partNumber <= session.getTotalParts(); partNumber++) {
            if (partNumber == currentPartNumber) {
                continue;
            }
            MultipartUploadPart uploadedPart = multipartSessionStore().getPart(session.getSessionId(), partNumber);
            if (uploadedPart != null && uploadedPart.getSize() != null) {
                uploadedSize = Math.addExact(uploadedSize, uploadedPart.getSize());
            }
        }
        if (uploadedSize > session.getTotalSize()) {
            throw new BusinessException("已上传分片大小超过初始化声明的文件总大小");
        }
    }

    private void validateMultipartContentType(MultipartUploadSession session, String contentType) {
        String normalized = normalizeContentType(contentType);
        if (normalized == null || "application/octet-stream".equals(normalized)) {
            return;
        }
        if (DANGEROUS_MIME_TYPES.contains(normalized)) {
            throw new BusinessException("不支持上传高风险文件内容类型: " + normalized);
        }
        if (session.getContentType() != null && !Objects.equals(session.getContentType(), normalized)) {
            throw new BusinessException("分片内容类型与初始化声明不一致");
        }
    }

    private void validateCompletedMultipartMetadata(MultipartUploadSession session, FileMetadata metadata) {
        if (metadata == null || !Objects.equals(metadata.getFileSize(), session.getTotalSize())) {
            throw new BusinessException("存储端文件大小与初始化声明不一致");
        }
        String expectedExtension = normalizeExtension(FileUtil.getExtension(session.getFileName()));
        String actualExtension = normalizeExtension(metadata.getExtension());
        if (!actualExtension.isBlank() && !Objects.equals(expectedExtension, actualExtension)) {
            throw new BusinessException("存储端文件扩展名与初始化声明不一致");
        }
        String finalContentType = normalizeContentType(metadata.getMimeType());
        if (finalContentType == null) {
            finalContentType = session.getContentType();
        }
        validateFilePolicy(session.getFileName(), session.getStorageType(), metadata.getFileSize(), finalContentType);
        metadata.setOriginalName(session.getFileName());
        metadata.setExtension(expectedExtension);
        metadata.setMimeType(finalContentType);
        metadata.setBusinessType(session.getBusinessType());
        metadata.setBusinessId(session.getBusinessId());
        metadata.setUploaderId(session.getUserId());
        metadata.setIsPrivate(Boolean.TRUE.equals(session.getPrivateFile()));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void validateSessionId(String uploadId) {
        if (uploadId == null || !uploadId.matches("[a-f0-9]{32}")) {
            throw new BusinessException("无效的分片上传会话");
        }
    }

    private void applyUploader(FileMetadata metadata) {
        if (metadata != null && metadata.getUploaderId() == null) {
            metadata.setUploaderId(SessionHelper.getUserId());
        }
    }

    private void assertReadPermission(String fileId, FileMetadata metadata) {
        if (metadata.getExpireTime() != null && metadata.getExpireTime().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException(410, "文件已过期");
        }
        if (metadataPersistence == null || !Boolean.TRUE.equals(metadata.getIsPrivate())) {
            return;
        }
        if (!metadataPersistence.checkPermission(fileId, SessionHelper.getUserId())) {
            throw new BusinessException(403, "无权读取该文件");
        }
    }

    /**
     * 测试存储连接
     */
    public boolean testConnection(String storageType) {
        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }
        return storage.testConnection();
    }

    /**
     * 创建存储桶
     */
    public boolean createBucket(String storageType, String bucketName) {
        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }
        return storage.createBucket(bucketName);
    }

    /**
     * 删除存储桶
     */
    public boolean deleteBucket(String storageType, String bucketName) {
        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }
        return storage.deleteBucket(bucketName);
    }

    /**
     * 检查存储桶是否存在
     */
    public boolean bucketExists(String storageType, String bucketName) {
        FileStorage storage = getStorage(storageType);
        if (storage == null) {
            throw new RuntimeException("不支持的存储类型: " + storageType);
        }
        return storage.bucketExists(bucketName);
    }

    /**
     * 重新加载已启用的存储配置
     */
    public void refreshConfiguredStorages() {
        if (configProvider == null) {
            log.warn("未配置StorageConfigProvider，跳过存储策略刷新");
            return;
        }

        configProvider.getAllEnabledConfigs().forEach(config -> {
            FileStorage storage = getStorage(config.getStorageType());
            if (storage != null) {
                try {
                    storage.init(config);
                    log.info("刷新文件存储策略: {} - {}", config.getStorageType(), config.getConfigName());
                } catch (Exception e) {
                    log.warn("刷新文件存储策略失败: {} - {}", config.getStorageType(), config.getConfigName(), e);
                }
            }
        });
    }
    
    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file, String storageType) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        validateFilePolicy(file.getOriginalFilename(), storageType, file.getSize(), file.getContentType());
    }

    private void validateFileName(String fileName, String storageType) {
        validateFileName(fileName, storageType, null);
    }

    private void validateFileName(String fileName, String storageType, Long fileSize) {
        validateFilePolicy(fileName, storageType, fileSize, null);
    }

    private void validateFilePolicy(String fileName, String storageType, Long fileSize, String contentType) {
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("文件名不能为空");
        }

        StorageConfig config = resolveValidationConfig(storageType);
        long maxFileSizeMb = config != null && config.getMaxFileSize() != null && config.getMaxFileSize() > 0
                ? config.getMaxFileSize()
                : DEFAULT_MAX_FILE_SIZE_MB;
        if (fileSize != null && fileSize >= 0) {
            long maxSize = maxFileSizeMb * 1024L * 1024L;
            if (fileSize > maxSize) {
                throw new RuntimeException("文件大小超过限制: " + maxFileSizeMb + "MB");
            }
        }

        String extension = normalizeExtension(FileUtil.getExtension(fileName));
        if (extension.isBlank()) {
            throw new RuntimeException("文件必须包含扩展名");
        }
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("不支持上传高风险文件类型: " + extension);
        }

        Set<String> allowedTypes = resolveAllowedTypes(config);
        if (!allowedTypes.contains(extension)) {
            throw new RuntimeException("不支持的文件类型: " + extension);
        }

        validateMimeType(extension, contentType);
    }

    private StorageConfig resolveValidationConfig(String storageType) {
        if (configProvider == null) {
            return null;
        }
        if (storageType != null && !storageType.isBlank()) {
            return configProvider.getConfigByType(storageType);
        }
        return configProvider.getDefaultConfig();
    }

    private Set<String> resolveAllowedTypes(StorageConfig config) {
        if (config == null || config.getAllowedTypes() == null || config.getAllowedTypes().isBlank()) {
            throw new RuntimeException("文件存储配置未设置允许的文件类型");
        }
        Set<String> configuredTypes = config.getAllowedTypeList().stream()
                .map(this::normalizeExtension)
                .filter(type -> !type.isBlank())
                .filter(type -> !DANGEROUS_EXTENSIONS.contains(type))
                .collect(java.util.stream.Collectors.toSet());
        if (configuredTypes.isEmpty()) {
            throw new RuntimeException("文件存储配置未设置有效的允许文件类型");
        }
        return configuredTypes;
    }

    private String normalizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private void validateMimeType(String extension, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String normalizedContentType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (DANGEROUS_MIME_TYPES.contains(normalizedContentType)) {
            throw new RuntimeException("不支持上传高风险文件内容类型: " + normalizedContentType);
        }
        Set<String> expectedTypes = EXTENSION_MIME_TYPES.get(extension);
        if (expectedTypes == null || expectedTypes.isEmpty() || "application/octet-stream".equals(normalizedContentType)) {
            return;
        }
        if (!expectedTypes.contains(normalizedContentType)) {
            throw new RuntimeException("文件扩展名与内容类型不匹配: " + extension + " / " + normalizedContentType);
        }
    }

}
