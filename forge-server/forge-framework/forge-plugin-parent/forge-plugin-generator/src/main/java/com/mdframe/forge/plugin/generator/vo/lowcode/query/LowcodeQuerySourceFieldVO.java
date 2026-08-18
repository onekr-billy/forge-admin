package com.mdframe.forge.plugin.generator.vo.lowcode.query;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LowcodeQuerySourceFieldVO {

    private String field;

    private String label;

    private String type;

    private String path;

    private Boolean sensitive;
}
