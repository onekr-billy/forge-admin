package com.mdframe.forge.plugin.generator.dto.businessapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 已发布应用运行态扩展钩子参数。 */
@Data
public class BusinessExtensionRuntimeHookDTO {

    @NotNull(message = "应用ID不能为空")
    private Long applicationId;

    private Long objectId;

    private Long entryId;

    @NotNull(message = "扩展ID不能为空")
    private Long extensionId;

    @NotBlank(message = "运行钩子不能为空")
    private String hookCode;

    private Map<String, Object> input = new LinkedHashMap<>();
}
