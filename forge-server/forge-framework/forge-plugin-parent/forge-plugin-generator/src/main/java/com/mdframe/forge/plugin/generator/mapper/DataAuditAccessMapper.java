package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditAccess;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataAuditAccessMapper extends BaseMapper<AiDataAuditAccess> {
}
