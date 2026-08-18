package com.mdframe.forge.plugin.generator.vo.lowcode.query;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LowcodeQuerySourceResultVO {

    private String sourceType;

    private String sourceKey;

    private Long sourceId;

    private Object data;

    private Long total;

    private Integer pageNum;

    private Integer pageSize;

    private List<LowcodeQuerySourceFieldVO> fields;
}
