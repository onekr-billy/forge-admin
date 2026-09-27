package com.mdframe.forge.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 人工重放流程通知死信请求。 */
@Data
public class FlowNotifyOutboxReplayDTO {

    @NotBlank(message = "重放原因不能为空")
    @Size(max = 500, message = "重放原因不能超过500个字符")
    private String reason;
}
