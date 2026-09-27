package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowCallbackInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessFlowCallbackInboxMapper extends BaseMapper<AiBusinessFlowCallbackInbox> {

    AiBusinessFlowCallbackInbox selectByEventId(@Param("tenantId") Long tenantId,
                                                 @Param("eventId") String eventId);

    AiBusinessFlowCallbackInbox selectByInboxId(@Param("tenantId") Long tenantId,
                                                 @Param("id") Long id);

    Long selectLatestCompletedSequence(@Param("tenantId") Long tenantId,
                                       @Param("aggregateKey") String aggregateKey);

    List<AiBusinessFlowCallbackInbox> selectRecoveryCandidates(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("batchSize") int batchSize);

    int claim(@Param("tenantId") Long tenantId,
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

    int markCompleted(@Param("inbox") AiBusinessFlowCallbackInbox inbox,
                      @Param("now") LocalDateTime now);

    int markFailed(@Param("inbox") AiBusinessFlowCallbackInbox inbox,
                   @Param("status") String status,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("errorType") String errorType,
                   @Param("now") LocalDateTime now);
}
