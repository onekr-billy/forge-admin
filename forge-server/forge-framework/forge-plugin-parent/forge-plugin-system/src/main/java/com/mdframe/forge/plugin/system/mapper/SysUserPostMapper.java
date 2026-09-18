package com.mdframe.forge.plugin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.system.entity.SysUserPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户-岗位关联Mapper接口
 */
@Mapper
public interface SysUserPostMapper extends BaseMapper<SysUserPost> {

    /**
     * 查询用户在当前租户下仍有效的岗位ID（过滤已删除岗位）。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return 有效岗位ID列表
     */
    List<Long> selectActivePostIdsByUser(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

}
