package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiLowcodePublishTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LowcodePublishTaskMapper extends BaseMapper<AiLowcodePublishTask> {

    AiLowcodePublishTask selectByRequestId(@Param("tenantId") Long tenantId,
                                           @Param("requestId") String requestId);

    List<AiLowcodePublishTask> selectCandidates(@Param("now") LocalDateTime now,
                                                @Param("staleBefore") LocalDateTime staleBefore,
                                                @Param("maxRetryCount") int maxRetryCount,
                                                @Param("batchSize") int batchSize);

    int claim(@Param("tenantId") Long tenantId,
              @Param("id") Long id,
              @Param("owner") String owner,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore,
              @Param("maxRetryCount") int maxRetryCount);

    int expireExhaustedLease(@Param("tenantId") Long tenantId,
                             @Param("id") Long id,
                             @Param("now") LocalDateTime now,
                             @Param("staleBefore") LocalDateTime staleBefore,
                             @Param("maxRetryCount") int maxRetryCount);

    AiLowcodePublishTask selectByTaskId(@Param("tenantId") Long tenantId,
                                        @Param("id") Long id);

    int markTerminal(@Param("task") AiLowcodePublishTask task,
                     @Param("status") String status,
                     @Param("now") LocalDateTime now);

    int markFailed(@Param("task") AiLowcodePublishTask task,
                   @Param("status") String status,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("errorType") String errorType,
                   @Param("now") LocalDateTime now);

    int advanceStage(@Param("task") AiLowcodePublishTask task,
                     @Param("expectedStage") String expectedStage,
                     @Param("nextStage") String nextStage,
                     @Param("now") LocalDateTime now);

    int releaseStage(@Param("task") AiLowcodePublishTask task,
                     @Param("expectedStage") String expectedStage,
                     @Param("nextStage") String nextStage,
                     @Param("now") LocalDateTime now);
}
