package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用范围内的角色授权请求。
 */
@Data
public class BusinessApplicationRolePermissionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Set<Long> resourceIds = new LinkedHashSet<>();

    private List<ModuleScope> moduleScopes = new ArrayList<>();

    @Data
    public static class ModuleScope implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String moduleCode;

        /** 为空表示跟随角色默认数据范围。 */
        private Integer dataScope;
    }
}
