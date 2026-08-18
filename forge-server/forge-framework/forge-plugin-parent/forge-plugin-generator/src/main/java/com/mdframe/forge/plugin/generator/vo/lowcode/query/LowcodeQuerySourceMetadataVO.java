package com.mdframe.forge.plugin.generator.vo.lowcode.query;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LowcodeQuerySourceMetadataVO {

    private String sourceType;

    private String sourceKey;

    private Long sourceId;

    private String sourceName;

    private String inputSchemaJson;

    private List<LowcodeQuerySourceFieldVO> fields;
}
