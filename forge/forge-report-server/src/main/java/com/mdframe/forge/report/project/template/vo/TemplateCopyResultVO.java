package com.mdframe.forge.report.project.template.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板复制结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCopyResultVO {

    /**
     * 新项目ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long projectId;
}
