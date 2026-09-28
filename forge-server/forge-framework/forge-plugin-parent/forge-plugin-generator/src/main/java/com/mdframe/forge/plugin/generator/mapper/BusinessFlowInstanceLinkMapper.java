package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface BusinessFlowInstanceLinkMapper extends BaseMapper<AiBusinessFlowInstanceLink> {

    AiBusinessFlowInstanceLink selectLatestByBusinessKey(@Param("tenantId") Long tenantId,
                                                         @Param("businessKey") String businessKey);

    List<AiBusinessFlowInstanceLink> selectLatestByBusinessKeys(@Param("tenantId") Long tenantId,
                                                                @Param("businessKeys") Collection<String> businessKeys);

    AiBusinessFlowInstanceLink selectRunningByBusinessKey(@Param("tenantId") Long tenantId,
                                                          @Param("businessKey") String businessKey);

    AiBusinessFlowInstanceLink selectByProcessInstanceId(@Param("tenantId") Long tenantId,
                                                         @Param("processInstanceId") String processInstanceId);

    List<AiBusinessFlowInstanceLink> selectByBusinessKey(@Param("tenantId") Long tenantId,
                                                         @Param("businessKey") String businessKey);

    AiBusinessFlowInstanceLink selectByLinkId(@Param("tenantId") Long tenantId,
                                              @Param("id") Long id);

    List<AiBusinessFlowInstanceLink> selectStatusSyncCandidates(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("batchSize") int batchSize);

    int claimStatusSync(@Param("tenantId") Long tenantId,
                        @Param("id") Long id,
                        @Param("lockOwner") String lockOwner,
                        @Param("now") LocalDateTime now,
                        @Param("staleBefore") LocalDateTime staleBefore,
                        @Param("maxRetryCount") int maxRetryCount);

    int markStatusSyncWaiting(@Param("link") AiBusinessFlowInstanceLink link,
                              @Param("remoteStatus") String remoteStatus,
                              @Param("nextSyncTime") LocalDateTime nextSyncTime,
                              @Param("now") LocalDateTime now);

    int markStatusSyncCompleted(@Param("link") AiBusinessFlowInstanceLink link,
                                @Param("remoteStatus") String remoteStatus,
                                @Param("now") LocalDateTime now);

    int markStatusSyncFailed(@Param("link") AiBusinessFlowInstanceLink link,
                             @Param("status") String status,
                             @Param("nextRetryTime") LocalDateTime nextRetryTime,
                             @Param("errorType") String errorType,
                             @Param("now") LocalDateTime now);
}
