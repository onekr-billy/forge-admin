package com.mdframe.forge.plugin.ai.session.dto;

import com.mdframe.forge.starter.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiSessionPageQuery extends PageQuery {

    private String keyword;
    private String startTime;
    private String endTime;
    private String status;
    private String agentCode;

    /** 用户ID（用户侧分页按当前登录用户过滤；管理端不设则查全部） */
    private Long userId;
}
