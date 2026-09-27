package com.mdframe.forge.starter.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.starter.flow.entity.FlowNotifyOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 流程通知 Outbox Mapper。 */
@Mapper
public interface FlowNotifyOutboxMapper extends BaseMapper<FlowNotifyOutbox> {

    FlowNotifyOutbox selectByEventId(@Param("tenantId") Long tenantId,
                                     @Param("eventId") String eventId);

    FlowNotifyOutbox selectByOutboxId(@Param("tenantId") Long tenantId,
                                      @Param("id") Long id);

    List<FlowNotifyOutbox> selectDispatchCandidates(@Param("now") LocalDateTime now,
                                                    @Param("staleBefore") LocalDateTime staleBefore,
                                                    @Param("maxRetryCount") int maxRetryCount,
                                                    @Param("batchSize") int batchSize);

    int claim(@Param("tenantId") Long tenantId,
              @Param("id") Long id,
              @Param("lockOwner") String lockOwner,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore,
              @Param("maxRetryCount") int maxRetryCount);

    int markDelivered(@Param("tenantId") Long tenantId,
                      @Param("id") Long id,
                      @Param("lockOwner") String lockOwner,
                      @Param("now") LocalDateTime now);

    int markFailed(@Param("tenantId") Long tenantId,
                   @Param("id") Long id,
                   @Param("lockOwner") String lockOwner,
                   @Param("deliveryStatus") int deliveryStatus,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("lastError") String lastError,
                   @Param("now") LocalDateTime now);
}
