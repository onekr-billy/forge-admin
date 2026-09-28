package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessEventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessEventOutboxMapper extends BaseMapper<AiBusinessEventOutbox> {

    AiBusinessEventOutbox selectByEventId(@Param("tenantId") Long tenantId,
                                           @Param("eventId") String eventId);

    int advanceAggregateSequence(@Param("tenantId") Long tenantId,
                                 @Param("aggregateKey") String aggregateKey,
                                 @Param("objectCode") String objectCode,
                                 @Param("recordId") String recordId,
                                 @Param("now") LocalDateTime now);

    Long selectAggregateSequence(@Param("tenantId") Long tenantId,
                                 @Param("aggregateKey") String aggregateKey);

    List<AiBusinessEventOutbox> selectDeliveryCandidates(@Param("now") LocalDateTime now,
                                                          @Param("staleBefore") LocalDateTime staleBefore,
                                                          @Param("maxRetryCount") int maxRetryCount,
                                                          @Param("batchSize") int batchSize);

    int claimDelivery(@Param("tenantId") Long tenantId,
                      @Param("id") Long id,
                      @Param("lockOwner") String lockOwner,
                      @Param("now") LocalDateTime now,
                      @Param("staleBefore") LocalDateTime staleBefore,
                      @Param("maxRetryCount") int maxRetryCount);

    int expireExhaustedLease(@Param("tenantId") Long tenantId,
                             @Param("id") Long id,
                             @Param("now") LocalDateTime now,
                             @Param("staleBefore") LocalDateTime staleBefore,
                             @Param("maxRetryCount") int maxRetryCount);

    AiBusinessEventOutbox selectByOutboxId(@Param("tenantId") Long tenantId,
                                           @Param("id") Long id);

    int markDelivered(@Param("outbox") AiBusinessEventOutbox outbox,
                      @Param("now") LocalDateTime now);

    int markFailed(@Param("outbox") AiBusinessEventOutbox outbox,
                   @Param("status") String status,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("errorType") String errorType,
                   @Param("now") LocalDateTime now);
}
