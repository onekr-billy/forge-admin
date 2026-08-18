package com.mdframe.forge.plugin.system.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 限定资源和业务模块范围的角色授权请求。
 */
@Data
public class ScopedRolePermissionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String clientCode;

    private Set<Long> scopeResourceIds = new LinkedHashSet<>();

    private Set<Long> selectedResourceIds = new LinkedHashSet<>();

    private Set<String> scopeModuleCodes = new LinkedHashSet<>();

    private List<RoleModuleDataScopeDTO> moduleScopes = new ArrayList<>();
}
