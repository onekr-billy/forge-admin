package com.mdframe.forge.report.project.template.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 模板复制请求
 */
@Data
public class TemplateCopyDTO {

    /**
     * 模板ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long templateId;

    /**
     * 新项目名称
     */
    private String projectName;
}
