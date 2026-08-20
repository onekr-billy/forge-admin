package com.mdframe.forge.plugin.generator.dto.businessprocess;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 手动开始节点启动请求。服务端只接受流程编码和记录 ID。
 */
@Data
public class BusinessProcessManualStartDTO {

    @NotBlank(message = "业务记录ID不能为空")
    private String recordId;

    /** 仅用于服务端校验，不能覆盖已发布主对象。 */
    private String objectCode;
}
