package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditField;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditFieldQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataAuditFieldMapper extends BaseMapper<AiDataAuditField> {

    Page<AiDataAuditField> selectFieldPage(Page<AiDataAuditField> page,
                                           @Param("tenantId") Long tenantId,
                                           @Param("eventId") Long eventId,
                                           @Param("query") DataAuditFieldQueryDTO query,
                                           @Param("visibleFieldCodes") List<String> visibleFieldCodes);

    AiDataAuditField selectFieldById(@Param("tenantId") Long tenantId,
                                     @Param("eventId") Long eventId,
                                     @Param("id") Long id);

    int countVisibleFields(@Param("tenantId") Long tenantId,
                           @Param("eventId") Long eventId,
                           @Param("visibleFieldCodes") List<String> visibleFieldCodes);
}
