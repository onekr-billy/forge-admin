package com.mdframe.forge.starter.file.core;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.file.model.FileMetadata;
import com.mdframe.forge.starter.file.model.StorageConfig;
import com.mdframe.forge.starter.file.multipart.InMemoryMultipartUploadSessionStore;
import com.mdframe.forge.starter.file.multipart.MultipartUploadSession;
import com.mdframe.forge.starter.file.multipart.MultipartUploadSessionStore;
import com.mdframe.forge.starter.file.spi.FileMetadataPersistence;
import com.mdframe.forge.starter.file.spi.StorageConfigProvider;
import com.mdframe.forge.starter.file.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FileManager upload policy")
@Tag("dev")
class FileManagerTest {

    private ExecutionIdentityContextHolder.Scope identityScope;

    @BeforeEach
    void openIdentity() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        user.setTenantId(1L);
        identityScope = ExecutionIdentityContextHolder.open(
                new ExecutionIdentity(user, "USER", 1L, null, 1L, "test", "test-token", java.util.Set.of()));
    }

    @AfterEach
    void closeIdentity() {
        if (identityScope != null) {
            identityScope.close();
        }
    }

    @Test
    @DisplayName("rejects upload when storage allowed types are blank")
    void rejectsUploadWhenAllowedTypesAreBlank() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes(" ");
        MultipartFile file = multipartFile("report.pdf", "application/pdf");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileManager.upload(file, "test", "1", "local"));

        assertTrue(exception.getMessage().contains("未设置允许的文件类型"));
    }

    @Test
    @DisplayName("uses configured storage whitelist instead of default fallback")
    void usesConfiguredStorageWhitelist() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");
        MultipartFile file = multipartFile("avatar.jpg", "image/jpeg");

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> fileManager.upload(file, "test", "1", "local"));

        assertTrue(exception.getMessage().contains("不支持的文件类型: jpg"));
    }

    @Test
    @DisplayName("rejects delete when caller cannot modify the file")
    void rejectsDeleteWhenCannotModify() throws Exception {
        FileManager fileManager = new FileManager();
        AtomicBoolean deleted = new AtomicBoolean(false);
        setPersistence(fileManager, persistence(false, deleted));

        BusinessException exception = assertThrows(BusinessException.class, () -> fileManager.delete("file-1"));

        assertEquals(403, exception.getCode());
        assertTrue(exception.getMessage().contains("无权删除该文件"));
        assertTrue(!deleted.get());
    }

    @Test
    @DisplayName("deletes metadata when caller can modify the file")
    void deletesWhenCanModify() throws Exception {
        FileManager fileManager = new FileManager();
        AtomicBoolean deleted = new AtomicBoolean(false);
        setPersistence(fileManager, persistence(true, deleted));

        assertTrue(fileManager.delete("file-1"));
        assertTrue(deleted.get());
    }

    @Test
    @DisplayName("rejects private file download when caller lacks permission")
    void rejectsPrivateDownloadWhenCallerLacksPermission() throws Exception {
        FileManager fileManager = new FileManager();
        setPersistence(fileManager, new FileMetadataPersistence() {
            @Override
            public void save(FileMetadata metadata) {
            }

            @Override
            public FileMetadata getById(String fileId) {
                return FileMetadata.builder().fileId(fileId).storageType("local").isPrivate(true).build();
            }

            @Override
            public FileMetadata getByMd5(String md5) {
                return null;
            }

            @Override
            public void incrementDownloadCount(String fileId) {
            }

            @Override
            public void delete(String fileId) {
            }

            @Override
            public boolean checkPermission(String fileId, Long userId) {
                return false;
            }

            @Override
            public boolean canModify(String fileId, Long userId) {
                return false;
            }
        });

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileManager.download("file-1", new org.springframework.mock.web.MockHttpServletResponse()));

        assertEquals(403, exception.getCode());
        assertTrue(exception.getMessage().contains("无权读取"));
    }

    @Test
    @DisplayName("sets no-store headers for private downloads")
    void setsNoStoreHeadersForPrivateDownloads() throws Exception {
        FileManager fileManager = new FileManager();
        FileMetadata metadata = FileMetadata.builder().fileId("file-1").storageType("local")
                .isPrivate(true).originalName("secret.txt").mimeType("text/plain").build();
        setPersistence(fileManager, new FileMetadataPersistence() {
            @Override public void save(FileMetadata value) { }
            @Override public FileMetadata getById(String fileId) { return metadata; }
            @Override public FileMetadata getByMd5(String md5) { return null; }
            @Override public void incrementDownloadCount(String fileId) { }
            @Override public void delete(String fileId) { }
            @Override public boolean checkPermission(String fileId, Long userId) { return true; }
            @Override public boolean canModify(String fileId, Long userId) { return true; }
        });
        FileStorage storage = mock(FileStorage.class);
        when(storage.download("file-1")).thenReturn(new ByteArrayInputStream("secret".getBytes()));
        Field storageField = FileManager.class.getDeclaredField("storageMap");
        storageField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, FileStorage> storageMap = (java.util.Map<String, FileStorage>) storageField.get(fileManager);
        storageMap.put("local", storage);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        fileManager.download("file-1", response);

        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
    }

    @Test
    @DisplayName("rejects internal byte reads when private file permission is missing")
    void rejectsPrivateFileBytesWhenCallerLacksPermission() throws Exception {
        FileManager fileManager = new FileManager();
        setPersistence(fileManager, new FileMetadataPersistence() {
            @Override public void save(FileMetadata metadata) { }
            @Override public FileMetadata getById(String fileId) {
                return FileMetadata.builder().fileId(fileId).storageType("local").isPrivate(true).build();
            }
            @Override public FileMetadata getByMd5(String md5) { return null; }
            @Override public void incrementDownloadCount(String fileId) { }
            @Override public void delete(String fileId) { }
            @Override public boolean checkPermission(String fileId, Long userId) { return false; }
            @Override public boolean canModify(String fileId, Long userId) { return false; }
        });

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileManager.getFileBytes("private-file"));

        assertEquals(403, exception.getCode());
    }

    @Test
    @DisplayName("binds multipart upload sessions to their user and tenant")
    void rejectsMultipartSessionFromDifferentUser() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");
        FileStorage storage = mock(FileStorage.class);
        when(storage.getStorageType()).thenReturn("local");
        when(storage.initMultipartUpload("report.pdf", "report", "7")).thenReturn("upload-1");
        fileManager.registerStorage(storage);
        String sessionId = fileManager.initMultipartUpload(
                "report.pdf", "report", "7", "local", 4L, 1, true);

        identityScope.close();
        LoginUser other = new LoginUser();
        other.setUserId(2L);
        other.setTenantId(1L);
        identityScope = ExecutionIdentityContextHolder.open(
                new ExecutionIdentity(other, "USER", 2L, null, 1L, "test", "other-token", java.util.Set.of()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileManager.uploadPart(sessionId, 1,
                        new ByteArrayInputStream("part".getBytes()), "local", 4L));

        assertEquals(403, exception.getCode());
    }

    @Test
    @DisplayName("multipart completion keeps private visibility and uploader")
    void multipartCompletionInheritsVisibilityAndUploader() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");
        FileStorage storage = mock(FileStorage.class);
        when(storage.getStorageType()).thenReturn("local");
        when(storage.initMultipartUpload("report.pdf", "report", "7")).thenReturn("upload-1");
        when(storage.uploadPart(eq("upload-1"), eq(1), any(InputStream.class))).thenReturn("etag-1");
        when(storage.completeMultipartUpload("upload-1", List.of("etag-1")))
                .thenReturn(FileMetadata.builder().fileId("file-1").fileSize(4L)
                        .extension("pdf").mimeType("application/pdf").storageType("local").build());
        fileManager.registerStorage(storage);
        String sessionId = fileManager.initMultipartUpload(
                "report.pdf", "report", "7", "local", 4L, 1, true, "application/pdf");
        fileManager.uploadPart(sessionId, 1, new ByteArrayInputStream("part".getBytes()),
                "local", 4L, "application/octet-stream");

        FileMetadata metadata = fileManager.completeMultipartUpload(sessionId, List.of("etag-1"), "local");

        assertTrue(Boolean.TRUE.equals(metadata.getIsPrivate()));
        assertEquals(1L, metadata.getUploaderId());
        verify(storage).completeMultipartUpload("upload-1", List.of("etag-1"));
    }

    @Test
    @DisplayName("requires total size and part count for multipart initialization")
    void rejectsMultipartInitializationWithoutSignedLimits() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileManager.initMultipartUpload(
                        "report.pdf", "report", "7", "local", null, null, true));

        assertTrue(exception.getMessage().contains("总大小"));
    }

    @Test
    @DisplayName("rejects completion when client ETag differs from server record")
    void rejectsMultipartCompletionWithForgedEtag() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");
        FileStorage storage = multipartStorage();
        fileManager.registerStorage(storage);
        String sessionId = fileManager.initMultipartUpload(
                "report.pdf", "report", "7", "local", 4L, 1, true, "application/pdf");
        fileManager.uploadPart(sessionId, 1, new ByteArrayInputStream("part".getBytes()),
                "local", 4L, "application/octet-stream");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileManager.completeMultipartUpload(sessionId, List.of("forged"), "local"));

        assertTrue(exception.getMessage().contains("ETag"));
        verify(storage, never()).completeMultipartUpload(any(), any());
    }

    @Test
    @DisplayName("rejects incomplete and over-quota multipart uploads")
    void rejectsIncompleteAndOverQuotaMultipartUploads() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");
        FileStorage storage = multipartStorage();
        fileManager.registerStorage(storage);
        String sessionId = fileManager.initMultipartUpload(
                "report.pdf", "report", "7", "local", 6L, 2, true, "application/pdf");
        fileManager.uploadPart(sessionId, 1, new ByteArrayInputStream("part".getBytes()),
                "local", 4L, "application/octet-stream");

        BusinessException incomplete = assertThrows(BusinessException.class,
                () -> fileManager.completeMultipartUpload(sessionId, List.of("etag-1", "etag-2"), "local"));
        BusinessException quota = assertThrows(BusinessException.class,
                () -> fileManager.uploadPart(sessionId, 2, new ByteArrayInputStream("more".getBytes()),
                        "local", 4L, "application/octet-stream"));

        assertTrue(incomplete.getMessage().contains("连续") || incomplete.getMessage().contains("全部"));
        assertTrue(quota.getMessage().contains("超过"));
    }

    @Test
    @DisplayName("shared session store allows another node to resume multipart upload")
    void resumesMultipartUploadAcrossNodes() throws Exception {
        MultipartUploadSessionStore sharedStore = new InMemoryMultipartUploadSessionStore();
        FileManager firstNode = fileManagerWithAllowedTypes("pdf");
        FileManager secondNode = fileManagerWithAllowedTypes("pdf");
        setMultipartSessionStore(firstNode, sharedStore);
        setMultipartSessionStore(secondNode, sharedStore);
        FileStorage storage = multipartStorage();
        when(storage.completeMultipartUpload("upload-1", List.of("etag-1")))
                .thenReturn(FileMetadata.builder().fileId("file-1").fileSize(4L)
                        .extension("pdf").mimeType("application/pdf").storageType("local").build());
        firstNode.registerStorage(storage);
        secondNode.registerStorage(storage);

        String sessionId = firstNode.initMultipartUpload(
                "report.pdf", "report", "7", "local", 4L, 1, true, "application/pdf");
        secondNode.uploadPart(sessionId, 1, new ByteArrayInputStream("part".getBytes()),
                "local", 4L, "application/octet-stream");
        FileMetadata metadata = secondNode.completeMultipartUpload(sessionId, List.of("etag-1"), "local");

        assertEquals("file-1", metadata.getFileId());
        verify(storage).uploadPart(eq("upload-1"), eq(1), any(InputStream.class));
    }

    @Test
    @DisplayName("expired multipart sessions abort provider uploads and delete state")
    void cleansExpiredMultipartSession() throws Exception {
        InMemoryMultipartUploadSessionStore store = new InMemoryMultipartUploadSessionStore();
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");
        setMultipartSessionStore(fileManager, store);
        FileStorage storage = multipartStorage();
        fileManager.registerStorage(storage);
        String sessionId = fileManager.initMultipartUpload(
                "report.pdf", "report", "7", "local", 4L, 1, true, "application/pdf");
        MultipartUploadSession session = store.getSession(sessionId);
        session.setExpiresAtMillis(System.currentTimeMillis() - 1);

        fileManager.cleanupExpiredMultipartSessions();

        verify(storage).abortMultipartUpload("upload-1");
        assertEquals(null, store.getSession(sessionId));
    }

    @Test
    @DisplayName("rejects multipart MIME type changes")
    void rejectsMultipartMimeTypeChanges() throws Exception {
        FileManager fileManager = fileManagerWithAllowedTypes("pdf");
        FileStorage storage = multipartStorage();
        fileManager.registerStorage(storage);
        String sessionId = fileManager.initMultipartUpload(
                "report.pdf", "report", "7", "local", 4L, 1, true, "application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileManager.uploadPart(sessionId, 1, new ByteArrayInputStream("part".getBytes()),
                        "local", 4L, "text/html"));

        assertTrue(exception.getMessage().contains("高风险"));
        verify(storage, never()).uploadPart(any(), any(Integer.class), any(InputStream.class));
    }

    private void setPersistence(FileManager fileManager, FileMetadataPersistence persistence) throws Exception {
        Field field = FileManager.class.getDeclaredField("metadataPersistence");
        field.setAccessible(true);
        field.set(fileManager, persistence);
    }

    private void setMultipartSessionStore(FileManager fileManager, MultipartUploadSessionStore store) throws Exception {
        Field field = FileManager.class.getDeclaredField("multipartSessionStore");
        field.setAccessible(true);
        field.set(fileManager, store);
    }

    private FileStorage multipartStorage() {
        FileStorage storage = mock(FileStorage.class);
        when(storage.getStorageType()).thenReturn("local");
        when(storage.initMultipartUpload("report.pdf", "report", "7")).thenReturn("upload-1");
        when(storage.uploadPart(eq("upload-1"), any(Integer.class), any(InputStream.class)))
                .thenAnswer(invocation -> "etag-" + invocation.getArgument(1));
        return storage;
    }

    private FileMetadataPersistence persistence(boolean allowedToModify, AtomicBoolean deleted) {
        return new FileMetadataPersistence() {
            @Override
            public void save(FileMetadata metadata) {
            }

            @Override
            public FileMetadata getById(String fileId) {
                return FileMetadata.builder().fileId(fileId).storageType("local").build();
            }

            @Override
            public FileMetadata getByMd5(String md5) {
                return null;
            }

            @Override
            public void incrementDownloadCount(String fileId) {
            }

            @Override
            public void delete(String fileId) {
                deleted.set(true);
            }

            @Override
            public boolean checkPermission(String fileId, Long userId) {
                return true;
            }

            @Override
            public boolean canModify(String fileId, Long userId) {
                return allowedToModify;
            }
        };
    }

    private FileManager fileManagerWithAllowedTypes(String allowedTypes) throws Exception {
        FileManager fileManager = new FileManager();
        StorageConfig config = new StorageConfig();
        config.setStorageType("local");
        config.setAllowedTypes(allowedTypes);

        Field field = FileManager.class.getDeclaredField("configProvider");
        field.setAccessible(true);
        field.set(fileManager, new StorageConfigProvider() {
            @Override
            public StorageConfig getDefaultConfig() {
                return config;
            }

            @Override
            public StorageConfig getConfigByType(String storageType) {
                return config;
            }

            @Override
            public List<StorageConfig> getAllEnabledConfigs() {
                return List.of(config);
            }

            @Override
            public void refreshConfig() {
                // Test provider has no cache.
            }
        });
        return fileManager;
    }

    private MultipartFile multipartFile(String originalFilename, String contentType) {
        byte[] content = "test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return content.length == 0;
            }

            @Override
            public long getSize() {
                return content.length;
            }

            @Override
            public byte[] getBytes() {
                return content;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                throw new UnsupportedOperationException("Not needed for policy tests");
            }
        };
    }
}
