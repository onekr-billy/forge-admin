package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.List;

/**
 * 业务应用查询参数。
 */
@Data
public class BusinessApplicationQueryDTO {

    private String keyword;

    private String applicationCode;

    private String suiteCode;

    private List<String> suiteCodes;

    private Integer status;

    private String designStatus;

    /** ALL、CREATED、RECENT。 */
    private String scope;

    /** RECENT 模式下由前端最近访问记录提供，服务端仍按可信租户和权限查询。 */
    private String applicationIds;

    /** 服务端归一化字段，不信任客户端直接传入。 */
    private List<Long> resolvedApplicationIds;

    /** 服务端从当前登录会话填充，不信任客户端直接传入。 */
    private Long creatorId;

    /** 服务端从当前登录会话填充，用于应用可见范围过滤。 */
    private Long currentUserId;

    /** 服务端从当前登录会话填充，用于应用角色可见范围过滤。 */
    private List<Long> currentRoleIds;

    /** 服务端从当前登录会话填充，用于应用部门/组织可见范围过滤。 */
    private List<Long> currentDepartmentIds;

    /** 服务端从当前登录会话填充，系统管理员可查看全部应用。 */
    private Boolean currentAdmin;

    /** 服务端根据门户权限计算，不信任客户端直接传入。 */
    private List<Long> visibleApplicationIds;
}
