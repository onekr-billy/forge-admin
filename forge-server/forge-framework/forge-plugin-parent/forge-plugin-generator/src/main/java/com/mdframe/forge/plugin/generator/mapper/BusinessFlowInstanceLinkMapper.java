package com.mdframe.forge.plugin.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowInstanceLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
