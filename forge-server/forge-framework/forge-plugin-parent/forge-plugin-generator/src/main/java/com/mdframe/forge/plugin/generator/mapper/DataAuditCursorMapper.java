package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiDataAuditCursor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataAuditCursorMapper extends BaseMapper<AiDataAuditCursor> {

    AiDataAuditCursor selectByRecord(@Param("tenantId") Long tenantId,
                                     @Param("objectId") Long objectId,
                                     @Param("recordId") String recordId);

    AiDataAuditCursor selectByRecordForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("objectId") Long objectId,
                                              @Param("recordId") String recordId);

    int updateRevision(@Param("id") Long id,
                       @Param("revision") Long revision,
                       @Param("lastEventId") Long lastEventId);
}
