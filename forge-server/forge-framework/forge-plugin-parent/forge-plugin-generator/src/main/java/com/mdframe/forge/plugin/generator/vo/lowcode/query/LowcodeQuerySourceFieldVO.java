package com.mdframe.forge.plugin.generator.vo.lowcode.query;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LowcodeQuerySourceFieldVO {

    private String field;

    private String label;

    /** 兼容旧前端：与 dataType 同义，优先使用 dataType */
    private String type;

    /** 目标字段真实存储类型，如 bigint / int / varchar */
    private String dataType;

    private Integer length;

    private Integer precision;

    private String path;

    private Boolean sensitive;
}
