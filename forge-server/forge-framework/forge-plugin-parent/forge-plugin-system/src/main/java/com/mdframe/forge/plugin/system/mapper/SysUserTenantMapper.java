package com.mdframe.forge.plugin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.system.entity.SysUserTenant;
import com.mdframe.forge.plugin.system.vo.SysUserTenantVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户-租户成员关系 Mapper
 */
@Mapper
public interface SysUserTenantMapper extends BaseMapper<SysUserTenant> {

    /** 校验用户是否为指定租户的有效成员。 */
    int countEnabledMembership(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * 查询用户可访问租户
     *
     * @param userId 用户ID
     * @param onlyEnabled 是否只查询启用状态
     * @return 租户列表
     */
    List<SysUserTenantVO> selectUserTenants(@Param("userId") Long userId, @Param("onlyEnabled") Boolean onlyEnabled);
}
