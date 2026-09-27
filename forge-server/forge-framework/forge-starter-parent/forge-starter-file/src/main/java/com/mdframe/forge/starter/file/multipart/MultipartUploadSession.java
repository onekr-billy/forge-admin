package com.mdframe.forge.starter.file.multipart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 服务端签发的分片上传会话。
 *
 * <p>对外只暴露随机 sessionId，对象存储返回的 providerUploadId 仅保存在服务端，
 * 防止调用方篡改 bucket/key 或将上传凭证脱离用户、租户和业务主体使用。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String providerUploadId;
    private Long userId;
    private Long tenantId;
    private String businessType;
    private String businessId;
    private String fileName;
    private String contentType;
    private String storageType;
    private Long totalSize;
    private Integer totalParts;
    private Boolean privateFile;
    private Long expiresAtMillis;
}
