package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessActionExecutionLog;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionLogQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessActionExecutionLogMapper extends BaseMapper<AiBusinessActionExecutionLog> {

    Page<AiBusinessActionExecutionLog> selectLogPage(Page<AiBusinessActionExecutionLog> page,
                                                     @Param("tenantId") Long tenantId,
                                                     @Param("query") BusinessActionLogQueryDTO query);

    AiBusinessActionExecutionLog selectLatestByIdempotencyKey(@Param("tenantId") Long tenantId,
                                                              @Param("objectCode") String objectCode,
                                                              @Param("recordId") String recordId,
                                                              @Param("actionCode") String actionCode,
                                                              @Param("actionVersion") Integer actionVersion,
                                                              @Param("idempotencyKey") String idempotencyKey);
}
