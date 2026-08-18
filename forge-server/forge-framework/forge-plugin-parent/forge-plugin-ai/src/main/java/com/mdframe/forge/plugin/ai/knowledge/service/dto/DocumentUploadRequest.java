package com.mdframe.forge.plugin.ai.knowledge.service.dto;

import lombok.Data;

import java.util.List;

/**
 * 文档上传请求
 */
@Data
public class DocumentUploadRequest {

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 文件ID（FileManager 上传返回的 UUID 字符串）
     */
    private String fileId;

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 文档类型（可选，不传则自动推断）
     */
    private String docType;

    /**
     * 来源类型（upload/url/manual）
     */
    private String sourceType;

    /**
     * URL来源（source_type=url时必填）
     */
    private String sourceUrl;

    /**
     * 是否确认处理（两步上传模式时使用）
     */
    private Boolean confirm;
}
