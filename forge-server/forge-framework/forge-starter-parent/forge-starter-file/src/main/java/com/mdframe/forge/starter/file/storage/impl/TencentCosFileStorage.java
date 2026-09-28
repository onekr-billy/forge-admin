package com.mdframe.forge.starter.file.storage.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.file.model.FileMetadata;
import com.mdframe.forge.starter.file.model.StorageConfig;
import com.mdframe.forge.starter.file.spi.FileMetadataPersistence;
import com.mdframe.forge.starter.file.storage.FileStorage;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.CompleteMultipartUploadRequest;
import com.qcloud.cos.model.CreateBucketRequest;
import com.qcloud.cos.model.InitiateMultipartUploadRequest;
import com.qcloud.cos.model.InitiateMultipartUploadResult;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PartETag;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.UploadPartRequest;
import com.qcloud.cos.model.UploadPartResult;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 腾讯云 COS 文件存储实现。
 */
@Slf4j
@Component
public class TencentCosFileStorage implements FileStorage {

    private static final String STORAGE_TYPE = "tencent";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String IMAGE_CACHE_CONTROL = "public,max-age=2592000,immutable";

    private StorageConfig config;
    private COSClient cosClient;
    private String defaultBucket;

    @Autowired(required = false)
    private FileMetadataPersistence metadataPersistence;

    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    @Override
    public void init(StorageConfig config) {
        this.config = config;
        this.defaultBucket = config.getBucketName();

        if (StrUtil.isBlank(config.getRegion())) {
            throw new IllegalArgumentException("腾讯云 COS region 不能为空");
        }
        if (StrUtil.isBlank(config.getAccessKey())) {
            throw new IllegalArgumentException("腾讯云 COS accessKey 不能为空");
        }
        if (StrUtil.isBlank(config.getSecretKey())) {
            throw new IllegalArgumentException("腾讯云 COS secretKey 不能为空");
        }
        if (StrUtil.isBlank(config.getBucketName())) {
            throw new IllegalArgumentException("腾讯云 COS bucketName 不能为空");
        }

        COSCredentials credentials = new BasicCOSCredentials(config.getAccessKey(), config.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(config.getRegion()));
        clientConfig.setHttpProtocol(Boolean.FALSE.equals(config.getUseHttps())
                ? com.qcloud.cos.http.HttpProtocol.http
                : com.qcloud.cos.http.HttpProtocol.https);

        if (cosClient != null) {
            cosClient.shutdown();
        }
        cosClient = new COSClient(credentials, clientConfig);
        log.info("腾讯云 COS 存储初始化完成, region: {}, bucket: {}", config.getRegion(), defaultBucket);
    }

    @Override
    public FileMetadata upload(MultipartFile file, String businessType, String businessId) {
        try {
            return upload(file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
                    businessType, businessId, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("腾讯云 COS 文件上传失败", e);
        }
    }

    @Override
    public FileMetadata upload(InputStream inputStream, String fileName, String contentType,
                               String businessType, String businessId) {
        return upload(inputStream, fileName, contentType, businessType, businessId, null);
    }

    @Override
    public FileMetadata upload(InputStream inputStream, String fileName, String contentType,
                               String businessType, String businessId, Long fileSize) {
        try (InputStream stream = inputStream) {
            Long knownSize = fileSize == null || fileSize < 0 ? null : fileSize;
            byte[] fallbackBytes = null;
            if (knownSize == null) {
                fallbackBytes = stream.readAllBytes();
                knownSize = (long) fallbackBytes.length;
            }
            String key = generateObjectKey(fileName, businessType);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(knownSize);
            metadata.setContentType(StrUtil.blankToDefault(contentType, "application/octet-stream"));
            applyCacheControl(metadata, fileName, contentType);

            InputStream uploadStream = fallbackBytes == null ? stream : new ByteArrayInputStream(fallbackBytes);
            PutObjectRequest request = new PutObjectRequest(defaultBucket, key, uploadStream, metadata);
            PutObjectResult result = cosClient.putObject(request);

            log.info("腾讯云 COS 文件上传成功: bucket={}, key={}, etag={}", defaultBucket, key, result.getETag());
            return FileMetadata.builder()
                    .fileId(IdUtil.fastSimpleUUID())
                    .originalName(fileName)
                    .storageName(key)
                    .filePath(key)
                    .fileSize(knownSize)
                    .mimeType(metadata.getContentType())
                    .extension(getExtension(fileName))
                    .storageType(STORAGE_TYPE)
                    .bucket(defaultBucket)
                    .accessUrl(buildAccessUrl(key, null))
                    .businessType(businessType)
                    .businessId(businessId)
                    .uploadTime(LocalDateTime.now())
                    .isPrivate(false)
                    .downloadCount(0)
                    .build();
        } catch (IOException | CosClientException e) {
            log.error("腾讯云 COS 文件上传失败", e);
            throw new RuntimeException("腾讯云 COS 文件上传失败", e);
        }
    }

    @Override
    public String initMultipartUpload(String fileName, String businessType, String businessId) {
        String key = generateObjectKey(fileName, businessType);
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(defaultBucket, key);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(detectContentType(fileName));
        applyCacheControl(metadata, fileName, metadata.getContentType());
        request.setObjectMetadata(metadata);
        InitiateMultipartUploadResult result = cosClient.initiateMultipartUpload(request);
        return String.join("|", result.getUploadId(), defaultBucket, key, StrUtil.nullToEmpty(fileName),
                StrUtil.nullToEmpty(businessType), StrUtil.nullToEmpty(businessId));
    }

    @Override
    public String uploadPart(String uploadId, int partNumber, InputStream inputStream) {
        String[] parts = parseUploadId(uploadId);
        try (InputStream stream = inputStream) {
            byte[] bytes = stream.readAllBytes();
            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(parts[1]);
            request.setKey(parts[2]);
            request.setUploadId(parts[0]);
            request.setPartNumber(partNumber);
            request.setInputStream(new ByteArrayInputStream(bytes));
            request.setPartSize(bytes.length);
            UploadPartResult result = cosClient.uploadPart(request);
            return result.getPartETag().getETag();
        } catch (IOException | CosClientException e) {
            log.error("腾讯云 COS 分片上传失败", e);
            throw new RuntimeException("腾讯云 COS 分片上传失败", e);
        }
    }

    @Override
    public FileMetadata completeMultipartUpload(String uploadId, List<String> partETags) {
        String[] parts = parseUploadId(uploadId);
        List<PartETag> cosPartETags = java.util.stream.IntStream.range(0, partETags.size())
                .mapToObj(i -> new PartETag(i + 1, partETags.get(i)))
                .toList();

        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(parts[1], parts[2], parts[0], cosPartETags);
        cosClient.completeMultipartUpload(request);

        ObjectMetadata objectMetadata = cosClient.getObjectMetadata(parts[1], parts[2]);
        String originalName = parts.length > 3 && StrUtil.isNotBlank(parts[3])
                ? parts[3]
                : parts[2].substring(parts[2].lastIndexOf('/') + 1);
        return FileMetadata.builder()
                .fileId(IdUtil.fastSimpleUUID())
                .originalName(originalName)
                .storageName(parts[2])
                .filePath(parts[2])
                .fileSize(objectMetadata.getContentLength())
                .mimeType(objectMetadata.getContentType())
                .extension(getExtension(originalName))
                .storageType(STORAGE_TYPE)
                .bucket(parts[1])
                .accessUrl(buildAccessUrl(parts[2], null))
                .businessType(parts.length > 4 ? parts[4] : null)
                .businessId(parts.length > 5 ? parts[5] : null)
                .uploadTime(LocalDateTime.now())
                .isPrivate(false)
                .downloadCount(0)
                .build();
    }

    @Override
    public InputStream download(String fileId) {
        FileMetadata metadata = getFileMetadata(fileId);
        if (metadata == null) {
            throw new RuntimeException("文件不存在: " + fileId);
        }

        try {
            COSObject cosObject = cosClient.getObject(metadata.getBucket(), metadata.getStorageName());
            try (COSObjectInputStream inputStream = cosObject.getObjectContent()) {
                return new ByteArrayInputStream(inputStream.readAllBytes());
            }
        } catch (IOException | CosClientException e) {
            log.error("腾讯云 COS 文件下载失败: {}", fileId, e);
            throw new RuntimeException("腾讯云 COS 文件下载失败", e);
        }
    }

    @Override
    public String getAccessUrl(String fileId, Integer expires) {
        FileMetadata metadata = getFileMetadata(fileId);
        if (metadata == null) {
            return null;
        }
        return buildAccessUrl(metadata.getStorageName(), expires);
    }

    @Override
    public boolean delete(String fileId) {
        FileMetadata metadata = getFileMetadata(fileId);
        if (metadata == null) {
            return false;
        }

        cosClient.deleteObject(metadata.getBucket(), metadata.getStorageName());
        return true;
    }

    @Override
    public boolean exists(String fileId) {
        FileMetadata metadata = getFileMetadata(fileId);
        if (metadata == null) {
            return false;
        }
        return cosClient.doesObjectExist(metadata.getBucket(), metadata.getStorageName());
    }

    @Override
    public boolean testConnection() {
        return bucketExists(defaultBucket);
    }

    @Override
    public boolean createBucket(String bucketName) {
        String bucket = StrUtil.blankToDefault(bucketName, defaultBucket);
        if (cosClient.doesBucketExist(bucket)) {
            return true;
        }
        cosClient.createBucket(new CreateBucketRequest(bucket));
        return true;
    }

    @Override
    public boolean deleteBucket(String bucketName) {
        String bucket = StrUtil.blankToDefault(bucketName, defaultBucket);
        cosClient.deleteBucket(bucket);
        return true;
    }

    @Override
    public boolean bucketExists(String bucketName) {
        String bucket = StrUtil.blankToDefault(bucketName, defaultBucket);
        return cosClient.doesBucketExist(bucket);
    }

    private String buildAccessUrl(String key, Integer expires) {
        if (config != null && StrUtil.isNotBlank(config.getDomain())) {
            return StrUtil.removeSuffix(config.getDomain(), "/") + "/" + key;
        }

        Date expiration = new Date(System.currentTimeMillis() + (expires != null ? expires : 3600) * 1000L);
        return cosClient.generatePresignedUrl(defaultBucket, key, expiration, HttpMethodName.GET).toString();
    }

    private String generateObjectKey(String fileName, String businessType) {
        String datePath = LocalDateTime.now().format(DATE_FORMATTER);
        String extension = getExtension(fileName);
        String storageName = extension.isEmpty() ? IdUtil.fastSimpleUUID() : IdUtil.fastSimpleUUID() + "." + extension;
        String prefix = StrUtil.blankToDefault(businessType, "common");
        if (config != null && StrUtil.isNotBlank(config.getBasePath())) {
            String basePath = StrUtil.removePrefix(StrUtil.removeSuffix(config.getBasePath(), "/"), "/");
            if (StrUtil.isNotBlank(basePath)) {
                prefix = basePath + "/" + prefix;
            }
        }
        return prefix + "/" + datePath + "/" + storageName;
    }

    private String[] parseUploadId(String uploadId) {
        String[] parts = uploadId.split("\\|", -1);
        if (parts.length < 3) {
            throw new IllegalArgumentException("无效的 uploadId 格式");
        }
        return parts;
    }

    private void applyCacheControl(ObjectMetadata metadata, String fileName, String contentType) {
        if (metadata == null) {
            return;
        }
        if (isImageFile(fileName, contentType)) {
            metadata.setCacheControl(IMAGE_CACHE_CONTROL);
        }
    }

    private boolean isImageFile(String fileName, String contentType) {
        if (StrUtil.isNotBlank(contentType) && contentType.startsWith("image/")) {
            return true;
        }
        String extension = getExtension(fileName).toLowerCase();
        return List.of("jpg", "jpeg", "png", "gif", "webp", "svg").contains(extension);
    }

    private String detectContentType(String fileName) {
        String extension = getExtension(fileName).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private String getExtension(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 && lastDot < fileName.length() - 1 ? fileName.substring(lastDot + 1) : "";
    }

    private FileMetadata getFileMetadata(String fileId) {
        if (metadataPersistence == null) {
            throw new RuntimeException("未配置FileMetadataPersistence");
        }
        return metadataPersistence.getById(fileId);
    }
}
