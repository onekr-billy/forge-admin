package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataAuditPolicyMapper extends BaseMapper<AiDataAuditPolicy> {

    AiDataAuditPolicy selectByObjectId(@Param("tenantId") Long tenantId, @Param("objectId") Long objectId);

    AiDataAuditPolicy selectByObjectIdForUpdate(@Param("tenantId") Long tenantId, @Param("objectId") Long objectId);

    List<AiDataAuditPolicy> selectConfigured(@Param("tenantId") Long tenantId);

    List<AiDataAuditPolicy> selectAllEnabled();
}
