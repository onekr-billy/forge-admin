package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.flow.entity.FlowProjectionOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FlowProjectionOutboxMapper extends BaseMapper<FlowProjectionOutbox> {

    FlowProjectionOutbox selectByEventId(@Param("tenantId") Long tenantId,
                                         @Param("eventId") String eventId);

    FlowProjectionOutbox selectByOutboxId(@Param("tenantId") Long tenantId,
                                          @Param("id") Long id);

    List<FlowProjectionOutbox> selectDispatchCandidates(@Param("now") LocalDateTime now,
                                                        @Param("staleBefore") LocalDateTime staleBefore,
                                                        @Param("maxRetryCount") int maxRetryCount,
                                                        @Param("batchSize") int batchSize);

    int claim(@Param("tenantId") Long tenantId,
              @Param("id") Long id,
              @Param("lockOwner") String lockOwner,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore,
              @Param("maxRetryCount") int maxRetryCount);

    int markAppliedImmediately(@Param("tenantId") Long tenantId,
                               @Param("id") Long id,
                               @Param("now") LocalDateTime now);

    int markApplied(@Param("tenantId") Long tenantId,
                    @Param("id") Long id,
                    @Param("lockOwner") String lockOwner,
                    @Param("now") LocalDateTime now);

    int markFailed(@Param("tenantId") Long tenantId,
                   @Param("id") Long id,
                   @Param("lockOwner") String lockOwner,
                   @Param("projectionStatus") int projectionStatus,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("lastError") String lastError,
                   @Param("now") LocalDateTime now);
}
