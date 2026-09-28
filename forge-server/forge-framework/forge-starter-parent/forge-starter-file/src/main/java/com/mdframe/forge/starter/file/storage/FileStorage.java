package com.mdframe.forge.starter.file.storage;

import com.mdframe.forge.starter.file.model.FileMetadata;
import com.mdframe.forge.starter.file.model.StorageConfig;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储策略接口
 * 所有存储实现需实现此接口
 */
public interface FileStorage {
    
    /**
     * 获取存储类型
     */
    String getStorageType();
    
    /**
     * 初始化存储配置
     */
    void init(StorageConfig config);
    
    /**
     * 上传文件
     *
     * @param file        上传的文件
     * @param businessType 业务类型
     * @param businessId   业务ID
     * @return 文件元数据
     */
    FileMetadata upload(MultipartFile file, String businessType, String businessId);
    
    /**
     * 上传文件流
     *
     * @param inputStream  输入流
     * @param fileName     文件名
     * @param contentType  内容类型
     * @param businessType 业务类型
     * @param businessId   业务ID
     * @return 文件元数据
     */
    FileMetadata upload(InputStream inputStream, String fileName, String contentType, 
                       String businessType, String businessId);

    /**
     * 上传文件流，调用方已知文件大小时应使用该方法，避免对象存储实现为了计算长度而一次性读入内存。
     *
     * @param inputStream  输入流
     * @param fileName     文件名
     * @param contentType  内容类型
     * @param businessType 业务类型
     * @param businessId   业务ID
     * @param fileSize     文件大小（字节）
     * @return 文件元数据
     */
    default FileMetadata upload(InputStream inputStream, String fileName, String contentType,
                                String businessType, String businessId, Long fileSize) {
        return upload(inputStream, fileName, contentType, businessType, businessId);
    }
    
    /**
     * 分片上传初始化
     *
     * @param fileName     文件名
     * @param businessType 业务类型
     * @param businessId   业务ID
     * @return 上传ID
     */
    String initMultipartUpload(String fileName, String businessType, String businessId);
    
    /**
     * 上传分片
     *
     * @param uploadId    上传ID
     * @param partNumber  分片编号
     * @param inputStream 输入流
     * @return 分片ETag
     */
    String uploadPart(String uploadId, int partNumber, InputStream inputStream);
    
    /**
     * 完成分片上传
     *
     * @param uploadId    上传ID
     * @param partETags   分片ETag列表
     * @return 文件元数据
     */
    FileMetadata completeMultipartUpload(String uploadId, java.util.List<String> partETags);
    
    /**
     * 下载文件
     *
     * @param fileId 文件ID
     * @return 文件流
     */
    InputStream download(String fileId);
    
    /**
     * 获取文件访问URL
     *
     * @param fileId 文件ID
     * @param expires 过期时间（秒）
     * @return 访问URL
     */
    String getAccessUrl(String fileId, Integer expires);
    
    /**
     * 删除文件
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    boolean delete(String fileId);
    
    /**
     * 检查文件是否存在
     *
     * @param fileId 文件ID
     * @return 是否存在
     */
    boolean exists(String fileId);

    /**
     * 测试当前存储连接
     */
    default boolean testConnection() {
        return true;
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 存储桶名称
     * @return 是否创建成功
     */
    default boolean createBucket(String bucketName) {
        return false;
    }

    /**
     * 删除存储桶
     *
     * @param bucketName 存储桶名称
     * @return 是否删除成功
     */
    default boolean deleteBucket(String bucketName) {
        return false;
    }

    /**
     * 检查存储桶是否存在
     *
     * @param bucketName 存储桶名称
     * @return 是否存在
     */
    default boolean bucketExists(String bucketName) {
        return false;
    }
}
