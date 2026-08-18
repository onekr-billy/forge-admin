package com.mdframe.forge.starter.datascope.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.datascope.entity.SysRoleModuleDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 角色业务模块数据范围 Mapper。
 */
@Mapper
public interface SysRoleModuleDataScopeMapper extends BaseMapper<SysRoleModuleDataScope> {

    /**
     * 查询角色已有的业务模块数据范围覆盖。
     */
    List<SysRoleModuleDataScope> selectByRole(@Param("tenantId") Long tenantId,
                                              @Param("roleId") Long roleId);

    /**
     * 查询全部角色业务模块数据范围覆盖，用于构建平台元数据快照。
     */
    List<SysRoleModuleDataScope> selectAllRoleModuleDataScopes();

    int deleteByRoleAndModules(@Param("tenantId") Long tenantId,
                               @Param("roleId") Long roleId,
                               @Param("moduleCodes") Set<String> moduleCodes);

    int insertBatch(@Param("list") List<SysRoleModuleDataScope> list);
}
