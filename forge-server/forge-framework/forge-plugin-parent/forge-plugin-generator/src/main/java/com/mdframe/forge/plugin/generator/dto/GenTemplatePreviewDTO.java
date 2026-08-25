package com.mdframe.forge.plugin.generator.dto;

import lombok.Data;

/**
 * 代码生成模板预览请求。
 */
@Data
public class GenTemplatePreviewDTO {

    private Long templateId;

    private Long tableId;
}
