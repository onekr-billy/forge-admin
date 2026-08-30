package com.mdframe.forge.plugin.generator.dto.businessprocess;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手动开始节点启动请求。服务端只接受流程编码和记录 ID。
 */
@Data
public class BusinessProcessManualStartDTO {

    @NotBlank(message = "业务记录ID不能为空")
    private String recordId;

    /** 仅用于服务端校验，不能覆盖已发布主对象。 */
    private String objectCode;

    /** 启动时由申请人选择的动态流程变量，固定字段由服务端校验，业务字段可扩展。 */
    private Map<String, Object> variables = new LinkedHashMap<>();
}
