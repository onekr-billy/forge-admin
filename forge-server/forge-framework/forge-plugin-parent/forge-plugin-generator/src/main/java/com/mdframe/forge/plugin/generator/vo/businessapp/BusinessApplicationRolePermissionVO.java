package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 指定角色在应用范围内的授权状态。
 */
@Data
public class BusinessApplicationRolePermissionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long roleId;

    private String roleName;

    private String roleKey;

    private Integer defaultDataScope;

    private Set<Long> resourceIds = new LinkedHashSet<>();

    private Map<String, Integer> moduleScopes = new LinkedHashMap<>();
}
