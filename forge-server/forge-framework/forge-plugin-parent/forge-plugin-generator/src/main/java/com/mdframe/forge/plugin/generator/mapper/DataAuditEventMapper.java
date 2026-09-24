package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditEvent;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditEventQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataAuditEventMapper extends BaseMapper<AiDataAuditEvent> {

    Page<AiDataAuditEvent> selectEventPage(Page<AiDataAuditEvent> page,
                                           @Param("tenantId") Long tenantId,
                                           @Param("query") DataAuditEventQueryDTO query,
                                           @Param("objectIds") List<Long> objectIds);

    AiDataAuditEvent selectEventById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int insertBatch(@Param("items") List<AiDataAuditEvent> items);
}
