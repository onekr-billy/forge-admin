package com.mdframe.forge.report.project.directory.dto;

import lombok.Data;

/**
 * 目录移动请求
 */
@Data
public class ReportDirectoryMoveDTO {

    /**
     * 当前目录ID
     */
    private Long id;

    /**
     * 目标父目录ID，0 表示顶级目录
     */
    private Long targetParentId;
}
