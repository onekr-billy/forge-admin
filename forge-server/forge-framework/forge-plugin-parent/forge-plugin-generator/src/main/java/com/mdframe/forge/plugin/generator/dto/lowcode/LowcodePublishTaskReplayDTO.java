package com.mdframe.forge.plugin.generator.dto.lowcode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人工重放低代码发布死信请求。 */
@Data
public class LowcodePublishTaskReplayDTO {

    @NotBlank(message = "重放原因不能为空")
    @Size(max = 500, message = "重放原因不能超过500个字符")
    private String reason;
}
