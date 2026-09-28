package com.mdframe.forge.starter.file.multipart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 服务端记录的已上传分片状态。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadPart implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer partNumber;
    private Long size;
    private String eTag;
}
