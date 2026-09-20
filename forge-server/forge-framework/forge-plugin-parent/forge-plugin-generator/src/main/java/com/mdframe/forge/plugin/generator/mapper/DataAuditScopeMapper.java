package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataAuditScopeMapper extends BaseMapper<AiDataAuditScope> {

    List<AiDataAuditScope> selectByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    List<AiDataAuditScope> selectByRoleIds(@Param("tenantId") Long tenantId, @Param("roleIds") List<Long> roleIds);

    int logicDeleteByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
}
