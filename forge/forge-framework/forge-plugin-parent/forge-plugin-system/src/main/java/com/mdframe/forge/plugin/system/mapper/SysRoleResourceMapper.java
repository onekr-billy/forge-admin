package com.mdframe.forge.plugin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.system.entity.SysRoleResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色-资源关联Mapper接口
 */
@Mapper
public interface SysRoleResourceMapper extends BaseMapper<SysRoleResource> {

    /**
     * 批量插入角色资源关联。
     */
    int insertBatch(@Param("list") List<SysRoleResource> list);
}
