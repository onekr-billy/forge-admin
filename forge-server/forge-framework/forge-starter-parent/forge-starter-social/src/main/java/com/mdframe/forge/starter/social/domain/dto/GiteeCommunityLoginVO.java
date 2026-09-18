package com.mdframe.forge.starter.social.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录页展示用的 Gitee 社区体验登录开关。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiteeCommunityLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean enabled;

    private Boolean requireStar;

    private String repoUrl;

    private String owner;

    private String repo;

    private Long tenantId;
}
