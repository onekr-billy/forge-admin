package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowRemoteCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessFlowRemoteCommandMapper extends BaseMapper<AiBusinessFlowRemoteCommand> {

    AiBusinessFlowRemoteCommand selectByCommandKey(@Param("tenantId") Long tenantId,
                                                    @Param("commandKey") String commandKey);

    AiBusinessFlowRemoteCommand selectByCommandId(@Param("tenantId") Long tenantId,
                                                   @Param("id") Long id);

    List<AiBusinessFlowRemoteCommand> selectRecoveryCandidates(@Param("now") LocalDateTime now,
                                                                @Param("staleBefore") LocalDateTime staleBefore,
                                                                @Param("maxRetryCount") int maxRetryCount,
                                                                @Param("batchSize") int batchSize);

    int claim(@Param("tenantId") Long tenantId,
              @Param("id") Long id,
              @Param("lockOwner") String lockOwner,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore,
              @Param("maxRetryCount") int maxRetryCount);

    int markRemoteSucceeded(@Param("command") AiBusinessFlowRemoteCommand command,
                            @Param("processInstanceId") String processInstanceId,
                            @Param("now") LocalDateTime now);

    int markAttemptFailed(@Param("command") AiBusinessFlowRemoteCommand command,
                          @Param("status") String status,
                          @Param("nextRetryTime") LocalDateTime nextRetryTime,
                          @Param("errorType") String errorType,
                          @Param("now") LocalDateTime now);

    int markRecoveryFailed(@Param("tenantId") Long tenantId,
                           @Param("id") Long id,
                           @Param("status") String status,
                           @Param("nextRetryTime") LocalDateTime nextRetryTime,
                           @Param("errorType") String errorType,
                           @Param("now") LocalDateTime now,
                           @Param("maxRetryCount") int maxRetryCount);

    int markCompleted(@Param("tenantId") Long tenantId,
                      @Param("id") Long id,
                      @Param("now") LocalDateTime now);
}
