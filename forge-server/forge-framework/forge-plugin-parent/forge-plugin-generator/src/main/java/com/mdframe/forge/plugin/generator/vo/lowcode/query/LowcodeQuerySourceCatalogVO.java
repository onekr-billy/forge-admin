package com.mdframe.forge.plugin.generator.vo.lowcode.query;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LowcodeQuerySourceCatalogVO {

    private String sourceType;

    private String sourceKey;

    private Long sourceId;

    private String sourceName;

    private String sourceGroup;

    private String description;
}
