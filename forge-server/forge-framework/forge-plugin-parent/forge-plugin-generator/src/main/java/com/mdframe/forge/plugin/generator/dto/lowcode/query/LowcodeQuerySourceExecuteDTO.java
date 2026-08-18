package com.mdframe.forge.plugin.generator.dto.lowcode.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class LowcodeQuerySourceExecuteDTO extends LowcodeQuerySourceRefDTO {

    private Map<String, Object> params;

    private List<String> fields;

    private Integer pageNum;

    private Integer pageSize;

    private Integer maxRows;
}
