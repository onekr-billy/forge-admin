package com.mdframe.forge.plugin.generator.dto.businessapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人工重放流程状态对账死信请求。 */
@Data
public class BusinessFlowStatusReplayDTO {

    @NotBlank(message = "重放原因不能为空")
    @Size(max = 500, message = "重放原因不能超过500个字符")
    private String reason;
}
