package com.mdframe.forge.starter.auth.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 邮箱验证码响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCaptchaResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String codeKey;
    private String email;
    private Long expiresIn;
    private String status;
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    private String captchaType;
    private Integer interval;
}
