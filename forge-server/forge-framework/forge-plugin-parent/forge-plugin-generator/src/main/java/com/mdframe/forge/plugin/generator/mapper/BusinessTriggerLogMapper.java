package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTriggerLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessTriggerLogMapper extends BaseMapper<AiBusinessTriggerLog> {

    Page<AiBusinessTriggerLog> selectTriggerLogPage(Page<AiBusinessTriggerLog> page,
                                                    @Param("tenantId") Long tenantId,
                                                    @Param("triggerId") Long triggerId);

    Long countSuccessOrTodoSince(@Param("tenantId") Long tenantId,
                                 @Param("triggerId") Long triggerId,
                                 @Param("recordId") String recordId,
                                 @Param("eventType") String eventType,
                                 @Param("sinceTime") LocalDateTime sinceTime);

    int updateExecutionResult(@Param("log") AiBusinessTriggerLog log);

    AiBusinessTriggerLog selectByExecutionKey(@Param("tenantId") Long tenantId,
                                              @Param("triggerId") Long triggerId,
                                              @Param("eventId") String eventId);

    AiBusinessTriggerLog selectByLogId(@Param("tenantId") Long tenantId,
                                       @Param("id") Long id);

    List<AiBusinessTriggerLog> selectRecoveryCandidates(@Param("now") LocalDateTime now,
                                                        @Param("staleBefore") LocalDateTime staleBefore,
                                                        @Param("maxRetryCount") int maxRetryCount,
                                                        @Param("batchSize") int batchSize);

    int claimExecution(@Param("tenantId") Long tenantId,
                       @Param("id") Long id,
                       @Param("lockOwner") String lockOwner,
                       @Param("now") LocalDateTime now,
                       @Param("staleBefore") LocalDateTime staleBefore,
                       @Param("maxRetryCount") int maxRetryCount);
}
