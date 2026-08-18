package com.mdframe.forge.plugin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.system.entity.SysResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 资源Mapper接口
 */
@Mapper
public interface SysResourceMapper extends BaseMapper<SysResource> {

    /**
     * 查询租户下用于业务模块名称解析的资源信息。
     */
    List<SysResource> selectModuleNamingResources(@Param("tenantId") Long tenantId);

    /**
     * 根据角色ID列表和客户端代码查询资源列表
     */
    List<SysResource> selectUserResourcesByRoleIds(@Param("roleIds") List<Long> roleIds, @Param("clientCode") String clientCode);

    /**
     * 查询用户指定客户端下的导航资源列表（目录和菜单）
     */
    List<SysResource> selectUserNavigationResources(@Param("userId") Long userId, @Param("clientCode") String clientCode);

    /**
     * 查询用户指定客户端下的API权限列表
     */
    List<String> selectUserApiPermissions(@Param("userId") Long userId, @Param("clientCode") String clientCode);

    /**
     * 查询指定HTTP方法下已配置的API权限资源地址。
     */
    List<String> selectConfiguredApiUrls(@Param("method") String method);

    /**
     * 按权限标识查询单个资源。
     */
    SysResource selectOneByPerms(@Param("tenantId") Long tenantId,
                                 @Param("resourceType") Integer resourceType,
                                 @Param("perms") String perms);

    /**
     * 按路由查询单个资源。
     */
    SysResource selectOneByPath(@Param("tenantId") Long tenantId,
                                @Param("resourceType") Integer resourceType,
                                @Param("path") String path);

    /**
     * 按资源ID查询API权限匹配表达式。
     */
    List<String> selectApiPermissionPatternsByResourceIds(@Param("resourceIds") List<Long> resourceIds);

    /** 按权限码批量查询有效资源。 */
    List<SysResource> selectByPermissions(@Param("tenantId") Long tenantId,
                                          @Param("permissions") Set<String> permissions);
}
